# Design

## Context

- **The queue.** `segment_match_jobs` (`SegmentMatchJobs` in `db/Tables.kt`) is keyed by `(kind, subject_uri)`; `enqueued_at` is the due time, pushed later on a failed attempt; `attempts`, `last_error` and `first_failed_at` (V68) describe a run of failures. Kinds are `SegmentJobs.KINDS`: `track`, `segment`, `candidate`, `tiers`, `group`, `site`, `elevation`. Subjects are AT-URIs (tracks, segments, activities, sites), except `candidate`, whose subject is the importer's candidate id.
- **The matcher.** `SegmentMatcher.runDue()` takes due jobs `BATCH` (20) at a time ordered by `enqueued_at`. `done()` and `failed()` act only on the row whose `enqueued_at` still equals the one it read (`dequeue`), so a job enqueued again while it runs stays queued. A failure backs off from 30 s doubling; once the pause would pass one hour the job is deleted and upserted into `segment_job_failures` (dead letter), which a later success, the account's deletion (`Indexer`, `subject_uri like 'at://did/%'`) or 30 days (`pruneFailedJobs`) clear.
- **Enqueueing.** `SegmentJobs.enqueue` upserts with `attempts = 0` and cleared errors, then signals `wakeups` (a conflated channel the matcher waits on). `enqueueElevations(where, now)` already does the bulk case right: one `INSERT … SELECT` skipping segments already queued, and nothing without terrain tiles. `Application.kt` passes `indexer.segmentJobs`, the instance the matcher listens to, into `appView(...)` as `segmentJobs`.
- **Requeue by migration.** V43, V50 and V53 queue a `track` job for every `segment_tracks` row not already queued; V51 only dives (joined to `activities.sport`); V54 an `elevation` job for every segment. All with `enqueued_at = 0`.
- **The admin site.** Pages implement `AdminPage` and register through `AdminRoutes`: `get` for pages and htmx fragments, `action(path, name)` for POSTs, which checks operator role, same origin and the CSRF token, and records an `admin_actions` row whose outcome is `ok`, `refused: …` or `failed: <type>`. `ActionResult` carries only the message shown. `AdminSite` takes extra pages as `adminPages`; production passes none yet. `AuditPage` is the model page; `AdminPrivacyTest` probes every page's `privacyProbes` for a marker planted in private records.
- **Error text.** Every stored `last_error` is `ErrorSummary.of(e)`: type plus first message line, at most 500 characters, and the type alone when any cause is a kotlinx.serialization or Jackson exception. The matcher's own `require` messages (`segmentTrackPieces`) are fixed strings with no interpolated data; remote failures are `XrpcException` codes and first lines from the athlete's PDS.

## Goals / Non-Goals

**Goals:**
- One page that answers "is the matcher keeping up, and what is stuck", from a few aggregate queries.
- Replay without a release, with the same idempotence the migrations had.

**Non-Goals:**
- Other queues and failure tables (`space_sync`, `activity_stream_fetch_failures`, `bsky_block_backfill_failures`, worker status): those belong to admin-health and admin-accounts.
- Per-segment actions (rematch one segment, recompute one segment's elevation): admin-segments.
- Priorities or a second queue; pausing the matcher.
- Showing anything about a subject beyond its identifier (no links into track or effort data).

## Decisions

**1. A `SegmentQueue` service beside `SegmentJobs`, and a page that only calls it.** `segments/SegmentQueue.kt` holds the reads (summary, retrying page, dead-letter page) and the operator operations (retry now, requeue, requeue kind, drop, rematch tracks, recompute elevation), each one transaction, taking `SegmentJobs` so every write wakes the matcher. `admin/JobQueuesPage.kt` parses forms, validates, and draws. This keeps queue SQL next to the matcher that owns the table, and keeps the page testable without HTTP. Alternative: put the queries in the page like `AuditPage` does with `AdminAudit`. Rejected because the bulk enqueue must stay identical to what the indexer and backfills do, and `SegmentJobs` already owns that.

**2. New `SegmentJobs` operations, no new semantics in the matcher.**
- `retryNow(kind, subject, now)`: `UPDATE … SET enqueued_at = now WHERE kind, subject AND first_failed_at IS NOT NULL`, attempts and error kept. If the matcher is running that job, its `dequeue`/`failed` match on the old `enqueued_at` and touch nothing, so the job simply runs again. That is the guard that already exists for re-enqueues, so no locking is added.
- `requeue(kind, subject, now)`: the existing upsert (`attempts = 0`, errors cleared) plus deleting the dead letter, in one transaction. Removing the dead letter at once (rather than on later success) follows the `SegmentJobFailures` doc ("enqueue the job again and delete the row") and keeps the dead-letter list meaning "given up and not being retried". The retrying list shows it again if it fails.
- `requeueFailed(kind, limit = 500, now)`: the oldest dead letters of a kind by `failed_at`, `batchUpsert` into the queue and delete them, one transaction. The cap keeps one request's transaction small; the outcome reports what remains.
- `enqueueTracks(where, now)`: the `enqueueElevations` pattern for tracks, `INSERT … SELECT` from `heldSegmentTracks()` (optionally joined to `Activities` on `activity_uri` with `sport = ?`) `WHERE NOT EXISTS` a queued `track` job. Held tracks rather than every `segment_tracks` row (which the migrations used): the matcher reads only held tracks anyway, so the rest would be no-op jobs.
- Elevation bulk reuses `enqueueElevations` with the box predicate `backfillTerrain` already builds (`max_lat_e7 >= south` and so on, in E7), plus the same `terrain_gain_m IS NULL OR terrain_max_grade IS NULL` filter for "only missing". `SegmentJobs` exposes `terrain: Boolean` so the page can refuse up front, and so a dead `elevation` letter isn't requeued into the no-op `enqueueElevation`.
- Drop: `deleteWhere` on the queue (only rows with `first_failed_at` set, the ones the page lists) or on `segment_job_failures`.

**3. Bulk jobs are due now, not at 0.** The migrations used `enqueued_at = 0`, which puts a full rematch ahead of every fresh indexing job for hours. `now` puts it behind what is already due. Fresh jobs enqueued later still wait behind the bulk (ordering is by due time); see Risks.

**4. Bulk requeues are bounded by confirmation, one statement and one at a time.** Each bulk form has a `confirm` checkbox, and the action refuses without it. A `Mutex` in `JobQueuesPage` (`tryLock`) refuses a second bulk action while one runs, which also avoids two concurrent `INSERT … SELECT … NOT EXISTS` racing into a primary-key violation. One AppView process serves `/admin`, so an in-process lock suffices. The statement itself is set-based, so its cost is one scan of `segment_tracks` or `segments`, not one round trip per row. Before confirming, the form shows the count the action would queue (a `get` fragment running the same predicate as a `COUNT`), so the operator sees "this queues 48,210 jobs".

**5. Audit outcomes carry counts.** `ActionResult` gains an optional `detail` (identifiers and counts only), which `AdminSite.action` records as `ok: <detail>`, and actions can return a refusal (`ActionResult.refused(reason)`) recorded as `refused: <reason>` and answered `422` (or `409` for a bulk already running; `422` matches admin-segments' refusals, which share the field). This is a small, backward-compatible change to `admin/AdminPage.kt` and `AdminSite.kt`; existing actions keep `ok`. Targets: single-job actions use `target = <subject>` with `kind` in params, so the audit page's exact-target filter finds every action on a subject; bulk actions use `target = <kind>` with the scope settings (`sport`, `box`, `missingOnly`) as params.

**6. Validation before the queue is touched.** `kind` must be in `SegmentJobs.KINDS`; `subject` non-empty and at most 512 characters (the column width); a box is four decimals with `-180 <= west < east <= 180` and `-90 <= south < north <= 90` (no antimeridian crossing, as `scripts/terrain-fetch.sh` boxes don't cross it either); `sport` must look like a sport id (ASCII letters, at most 64 characters, the `activities.sport` width) and is matched exactly; `Sport` is an open set with no list of values, so an unknown one just queues nothing. Invalid input returns a refusal and changes nothing.

**7. Queries stay portable and cheap.** Summary: one `GROUP BY kind` over `segment_match_jobs` with `COUNT(*)`, `SUM(CASE WHEN enqueued_at <= now …)`, `SUM(CASE WHEN first_failed_at IS NOT NULL …)` and `MIN(CASE WHEN enqueued_at <= now THEN enqueued_at END)`, plus one `GROUP BY kind` over `segment_job_failures`; no `FILTER` clause, so it runs on H2 in PostgreSQL mode. Retrying list: `WHERE first_failed_at IS NOT NULL ORDER BY first_failed_at, kind, subject_uri`, keyset-paged by that triple, 50 per page, backed by a new index on `segment_match_jobs (first_failed_at)` (V71) since a full rematch can make the queue hundreds of thousands of rows. Dead letters: `ORDER BY failed_at DESC, kind, subject_uri`, using the existing `segment_job_failures_failed_at` index. Kinds with no rows are filled in from `SegmentJobs.KINDS`.

**8. Privacy.** The page prints kind, subject identifier, counts, epoch times as instants or ages, and `last_error` as escaped text (kotlinx.html escapes). Error text is already bounded by `ErrorSummary`, which drops decoder messages; the tests plant a distinctive string in a malformed track blob and assert neither the dead letter nor the page contains it. The page declares `privacyProbes` for its path and its kind-filtered lists, so `AdminPrivacyTest` covers it.

**9. Page shape.** `/admin/queues`: the summary table, then "Retrying" and "Gave up" sections, each loaded as an htmx fragment (`/admin/queues/retrying`, `/admin/queues/failed`, with `kind` and `cursor`), so paging one list doesn't reload the other; then "Bulk requeue" with the two bulk forms and their count previews. Row buttons post with `hx-post`, include `target` and `kind`, and swap the row's outcome cell with `respondOutcome`'s paragraph. Viewers get the same tables without the button column or the bulk section (`admin.isOperator`).

**10. Requeue-by-migration stops.** `.agents/rules/appview.md`'s migrations bullet gains: a change that needs segment work replayed says so in its notes and an operator runs the matching bulk requeue after deploy; migrations no longer insert into `segment_match_jobs`. Committed migrations V43–V54 stay as they are.

## Risks / Trade-offs

- [A full rematch delays fresh indexing jobs until it drains] → Due-now rather than 0 keeps already-due work ahead; the summary's oldest-due age shows the backlog; the operator can drop nothing selectively, but can scope rematches by sport. A priority column is deferred (Non-Goals).
- [`retryNow` on a job the matcher is running makes it run twice] → Harmless: every job kind is idempotent (each replaces its subject's derived rows), which is why re-enqueue already works this way.
- [Requeueing a dead letter whose subject is gone] → The matcher's jobs already treat a missing subject as done (the job finds nothing and is dequeued), so it leaves the queue on its next run.
- [Dropping a `track` or `tiers` job leaves stale efforts or tiers until the next change to that track or activity] → Audited, operator-only, and a rematch can repair it.
- [Remote PDS error lines are untrusted text] → Bounded to one line of 500 characters, escaped in HTML, and never AppView-held private content; the athlete's PDS only learns its own error back.
- [Subject URIs of non-public records reveal that a record exists and its rkey] → Allowed by admin-site's privacy rule (record URIs are identifiers); the page shows nothing else about them.
- [The in-process bulk lock doesn't span several AppView processes] → One process serves `/admin` today; with several, a concurrent duplicate fails on the primary key inside its transaction and is audited as failed, changing nothing.
- [Shared files with sibling changes] → `AdminPage.kt`/`AdminSite.kt` (decision 5) and the `adminPages` list in `Application.kt` may also be touched by admin-health, admin-accounts or admin-segments; keep the `ActionResult` change additive so merges are mechanical.

## Migration Plan

- V71 (`V71__segment_job_queue_index.sql`): `CREATE INDEX segment_match_jobs_failing ON segment_match_jobs (first_failed_at);`. The number is provisional: the sibling admin changes may also add migrations, so it is renumbered to the next free version at merge (`MigrationVersionsTest` catches a clash).
- No data migration. Deploy, then the page appears for configured admins; rollback is removing the page from `adminPages` (the index is harmless to keep).

## Open Questions

- Whether "retry now" should also be offered for jobs that are queued but have never failed (e.g. to jump a backlog). Deferred: the spec limits it to retrying jobs, and adding it later is one more button.
- Whether elevation should also be scoped by an importer region name (`segment_candidates.region`, through the candidate ids the importer's segments are keyed by) in addition to a box. Deferred to admin-segments, which owns importer regions.
