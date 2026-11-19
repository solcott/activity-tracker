# Tasks

## 1. Audit outcomes with detail

- [ ] 1.1 In `admin/AdminPage.kt` give `ActionResult` an optional `detail` (identifiers and counts) and a `refused(reason, status)` form; in `AdminSite.action` record `ok: <detail>` / `refused: <reason>` and answer refusals `400` (`409` for "already running"), keeping existing actions' `ok` outcome (design decision 5). Verify with an `AdminSiteTest` case: an action returning a detail and one returning a refusal each leave the expected `admin_actions.outcome`, and the existing admin tests still pass (`./gradlew :server-appview:test --tests "*.AdminSiteTest"`).

## 2. Queue operations

- [ ] 2.1 Add `V71__segment_job_queue_index.sql` (index on `segment_match_jobs (first_failed_at)`), with a header comment that the number is renumbered at merge; verify `MigrationVersionsTest` and the H2 migrate in every test base pass.
- [ ] 2.2 In `segments/SegmentJobs.kt` expose `terrain` and add `retryNow`, `requeue` (upsert plus dead-letter delete, one transaction), `requeueFailed(kind, limit = 500)` and `enqueueTracks(where)` (`INSERT … SELECT` from `heldSegmentTracks()`, skipping queued `track` jobs), each waking the matcher (design decision 2). Verify in a new `SegmentQueueTest` (H2, `SegmentTestBase`): retry keeps attempts and error and moves the due time to now; requeue leaves one fresh job and no dead letter; `requeueFailed` takes the 500 oldest of 700; `enqueueTracks` twice queues each held track once, leaves already queued ones' due time, and skips tracks of an athlete with matching and grouping off.
- [ ] 2.3 Add `segments/SegmentQueue.kt`: the per-kind summary (portable `GROUP BY` with `SUM(CASE …)`/`MIN(CASE …)`, every kind filled in), the keyset-paged retrying and dead-letter lists with a kind filter, drop (retrying rows and dead letters only), rematch (optionally by `activities.sport`) and elevation recompute (all or a box in degrees converted to E7, optionally only missing, through `enqueueElevations`), plus a `COUNT` preview for both bulk scopes. Verify in `SegmentQueueTest`: the summary for a seeded queue (including an empty kind and the oldest due age), paging across two pages without repeats, drop of a missing job reporting nothing dropped, the dive-only rematch, the box touching test, the only-missing filter, and refusal without terrain tiles.
- [ ] 2.4 Extend `SegmentJobFailuresTest` with an end-to-end replay: a `track` job that gives up, requeued through `SegmentQueue`, then succeeds once its blob is served, leaving no dead letter; and a dead letter from a malformed track blob holding a distinctive string whose `last_error` doesn't contain it. Verify with `./gradlew :server-appview:test --tests "*.SegmentJobFailuresTest"`.

## 3. Queues page

- [ ] 3.1 Add `admin/JobQueuesPage.kt` (`path = "queues"`, title "Queues"): the summary table, the "Retrying" and "Gave up" htmx fragments with kind filter and "Older" cursor, and for operators only the per-row Retry now / Requeue / Drop buttons and the bulk forms (rematch with optional sport; elevation with all-or-box and only-missing; requeue a kind's dead letters) with count previews and a `confirm` checkbox (design decisions 4, 6, 9). Declare `privacyProbes` for the page and both fragments filtered by kind.
- [ ] 3.2 Wire the actions through `AdminRoutes.action` with `target = subject` (single job) or `target = kind` (bulk) and scope settings as params; validate kind, subject length, box and sport before touching the queue; hold a page-level `Mutex` with `tryLock` around bulk actions; return counts as the audit detail.
- [ ] 3.3 List the page in the production `adminPages` in `Application.kt`, built from `indexer.segmentJobs` and `db`, so it appears in the site's navigation; verify by the test host in 3.4 finding the nav link.
- [ ] 3.4 Add `JobQueuesPageTest` (Ktor `testApplication` over `AdminTestBase`): viewer sees the summary and lists with no buttons or bulk forms; viewer POST to each action answers `403`, changes nothing and leaves a `refused: viewer` audit row; a POST without the CSRF token is refused; operator retry, requeue, requeue-kind, drop, rematch and elevation each change the queue as specified and leave an `ok: …` audit row with the count; unconfirmed bulk and invalid box, sport and kind are refused with no change; a second bulk while one holds the lock answers `409`; repeating a rematch queues nothing more. Verify with `./gradlew :server-appview:test --tests "*.JobQueuesPageTest"`.
- [ ] 3.5 Extend `AdminPrivacyTest`'s seed with a retrying `track` job and a dead letter whose subject is carol's Only me activity's segment track, and confirm the marker check passes for the queues page's probes (and fails if the page printed the track blob, as its negative case shows). Verify with `./gradlew :server-appview:test --tests "*.AdminPrivacyTest"`.

## 4. Rules and checks

- [ ] 4.1 Update `.agents/rules/appview.md`: describe the queues page in the admin-site bullet, and in the migrations bullet say segment work is replayed by an operator's bulk requeue after deploy, not by inserting into `segment_match_jobs` from a migration (design decision 10). Verify `scripts/check-agent-sync.sh` passes.
- [ ] 4.2 Run `./gradlew :server-appview:test` and `./gradlew :server-appview:check` (ktfmt, detekt) and confirm both pass.

## 5. Local stack check

- [ ] 5.1 Run `scripts/dev.sh`, sign in at `http://localhost:8081/admin` as `siteadmin.test` in the browser pane, open Queues, and check: the summary lists every kind; a rematch preview shows a count, refuses without confirm, then queues that many and the summary's `track` depth rises and drains as the matcher runs; an elevation recompute for a box (or its "terrain tiles aren't configured" refusal when `TERRAIN_TILES_DIR` is unset); a dead letter produced by pointing a test account's track at an unreachable blob can be requeued and dropped; and each action appears on the Audit log page with its count. Record any defects as fixes with tests before closing.

## Workflow follow-up

- Renumber V71 to the next free migration version if a sibling admin change merges first.
- Archive the change after review.
