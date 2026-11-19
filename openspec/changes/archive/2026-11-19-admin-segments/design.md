# Design

## Context

What exists, from the code:

- **Admin site** (`admin/`). A page is an `AdminPage` (`path`, `title`, `AdminRoutes.install()`, `privacyProbes(subject)`), listed in `appView(adminPages = …)`. `main` doesn't pass any yet (`Application.kt`, the `appView(...)` call). `AdminRoutes.get` is admin-only. `AdminRoutes.action(path, name)` is the only state change: a POST that refuses viewers, a foreign `Origin` and a bad CSRF token, and writes one `admin_actions` row, with the `target` form field as target and the other fields as params (capped at 20 params, 200 characters each). The outcome is `ok` whenever the handler returns, `failed: <Type>` when it throws (`AdminSite.SiteRoutes.action`). `AuditPage` is the model page. `AdminPrivacyTest` seeds carol's private activity and comment with a marker and fails if any page in `pagesUnderTest` prints it.
- **Pins** (`segments/SegmentPins.kt`, reached as `Indexer.segmentPins`). `pinStatus`, `unpinStatus`, `pinName`, `unpinName`, `pinsOf`, `orphanedPins` return `PinResult` (`Pinned`, `Unpinned`, `NoPin`, `NotFound(uri)`, `Invalid(reason)`) and check no roles. `SegmentPin.inEffect` says whether a pin beats the derived value now.
- **Status pass** (`SegmentMatcher.updateStatuses`): `promote` (proposed to active at `activeAthletes` distinct effort authors, skipping fixed pins via `withoutFixedStatusPins`), then archive proposed rows older than `ARCHIVE_AFTER` with `noFixedStatusPin()`, `recheckDuplicatesOf` and `rederiveGroups`, then the dive-site step. Everything is private to the matcher, which `startWorkers` builds, so the HTTP side can't reach it.
- **Naming pass** (`SegmentNaming.update`): `updateSegment(segment, now, since)` for each segment with open proposals, then `recomputeAllAdoptedNames`. `updateSegment` is private.
- **Elevation**: `SegmentJobs.enqueueElevation` (a no-op without terrain tiles), `recomputeEffortElevation(uris)` (`SegmentElevation.kt`), `servedElevation(row, activeAthletes)`.
- **Rematch**: `SegmentJobs.enqueueSegment` queues a `segment` job; `matchSegment` then queues a `track` job for each usable track in the segment's cells, only while the segment is in `LIVE_STATUSES`.
- **Tables** (`db/Tables.kt`): `segments` has no region. Candidates (`segment_candidates`) have `region`, `revision`, `ridden`, `staged_at`. A published candidate is the importer's segment at `at://<importer>/app.activitytracker.feed.segment/<candidate id>` (`SegmentCandidateViews.ridden`). Indexes exist on `(region, revision)`, `(region, ridden, id)` and `segments (status, sport)`.
- **Serving rules**: `served(null, author, record, cid)` (`api/Moderation.kt`) is what a signed-out viewer gets, so `!takedown` on the segment or its author's account hides it. `listedProposal(null)` (`SegmentNames.kt`) is a signed-out proposal listing. `tallies(segment, proposals)` gives counted totals.

## Goals / Non-Goals

**Goals:**
- Each value the pages print is read by a query that selects only identifier, status, count and time columns, plus names under the serving rules. No page selects `segments.record`, `segment_efforts` rows, tracks, or votes by voter.
- Per-segment passes run the same code as the hourly passes, so the two can't drift.
- No migration.

**Non-Goals:**
- Bulk requeues (rematch all, elevation for a region): `admin-job-queues`.
- Job queue depth, failures and dead letters beyond "kinds queued for this segment": `admin-job-queues`.
- Dive sites. They share the shape, and can get a page later.
- A "status if unpinned" preview (segment-pins' open question). The page shows `prior_status` as stored instead.

## Decisions

### 1. One page object, sections as fragments

`admin/SegmentsPage.kt` is one `AdminPage` (`path = "segments"`, title "Segments") that registers:

| Route | What |
|---|---|
| `GET segments` | search form and results, importer runs, pins on missing segments |
| `GET segments/segment?uri=` | the segment page |
| `POST segments/status-pass`, `naming-pass`, `elevation`, `rematch` | one-segment actions |
| `POST segments/pin-status`, `unpin-status`, `pin-name`, `unpin-name` | pins |

Each action takes `target` = the segment URI, so the audit log's target filter finds every action on a segment, and the page links to `/admin/audit?target=<uri>`. Forms post with htmx (`hx-post`, `hx-target` an outcome slot), which sends the CSRF header from the layout; each form also carries `_csrf` so it works without htmx. Controls are drawn only when `admin.isOperator`. Segment URIs go in a query parameter, not the path, since they hold `/` and `:`.

*Alternative:* separate pages for search, importer and pins. Rejected: three nav entries for one area, and the sibling changes each add their own.

### 2. Column-limited reads in `admin/SegmentAdminQueries.kt`

All reads live in one file of `db.tx` functions returning small data classes (`SegmentRow`, `SegmentDetail`, `ProposalRow`, `ImporterRun`). Each `select(...)` names its columns; none uses `selectAll()` on `segments`, `segment_efforts`, `segment_tracks` or `segment_name_votes`. That makes the privacy rule reviewable in one place.

- **Search**: `Segments` filtered by exact `uri`, or `uri` ending in `/<rkey>`; status `eq`; sport and kind `eq`; name with `lower(current_name) like` / `lower(segment_name) like` on an escaped `%q%`, and `served(null, authorDid, uri, cid)` ANDed in for any name term; region as `uri in (concat('at://<importer>/<nsid>/', segment_candidates.id) where region = ?)`. Ordered by `uri`, cursor = last URI, limit 50 + 1. Name search scans; that's acceptable for an operator page and bounded by the limit.
- **Name shown**: the row's names are returned only when `served(null, …)` holds, else null and the page prints "withheld".
- **Counts**: `count(*)` and `count(distinct author_did)` over `segment_efforts where segment_uri = ?`. Nothing else from that table.
- **Elevation**: `servedElevation(row, activeAthletes)` for the served value and source; terrain columns as is; `effort_gain_m`/`effort_loss_m` only when `effort_athletes >= activeAthletes`, which is the rule `servedElevation` already follows.
- **Queued jobs**: `select kind from segment_match_jobs where subject_uri = ?`.
- **Proposals**: `uri, proposer_did, proposal_state, opened_at, closed_at, final_up, final_down` plus `proposed_name` only where `listedProposal(null)` holds; open ones get live totals from `tallies`. `tallies` reads votes internally but returns only totals.
- **Importer runs**: `select region, revision, count(*), sum(case ridden and not published), sum(case published), max(staged_at) group by region, revision`, where published = `exists segments.uri = concat(prefix, id)`. Only when `SEGMENT_IMPORTER_DID` is configured. The ridden count is the size of the list `importer.getRiddenSegmentCandidates` already gives the importer, so it reveals nothing about riders that isn't already revealed. `staged_at` is when the importer staged, not when anyone rode.

*Alternative:* reuse `SegmentViews.get` (the XRPC view). Rejected: it applies viewer rules for a signed-in athlete and serves fields (leaderboard, profile, viewer state) this page shouldn't need.

### 3. One-segment passes through `segments/SegmentPasses.kt`

The status pass's segment step and the naming pass's per-segment step move into `SegmentPasses(db, jobs, clock, activeAthletes, names, importer)`:

- `updateStatuses(only: Collection<String>? = null)`: today's body with `only` narrowing both the promote list and the stale archive query. The dive-site step stays in `SegmentMatcher` and runs only when `only == null`.
- `updateNames(only: String? = null)`: `SegmentNaming.update` with `only` narrowing the open-proposal segment list; with `only`, it ends with `recomputeCurrentName(only)` instead of `recomputeAllAdoptedNames`.

`SegmentMatcher` builds one and delegates, so its hourly passes are unchanged. `main` builds a second for the page from the same config values (`segmentActiveAthletes`, `segmentNames`, `segmentImporter`) and `indexer.segmentJobs`. The status action reads the status before and after in the same transaction; the naming action reads the proposal states before and after.

*Alternative:* make `SegmentMatcher` reachable from the page. Rejected: it's built inside `startWorkers` under supervision, and the page needs only two stateless passes.

Concurrency: like the hourly passes, these take no indexer lock. Their updates are guarded (`status = proposed`, `noFixedStatusPin()`), so a concurrent hourly pass or upsert at worst repeats the work.

### 4. Elevation and rematch call the existing queue

- **Elevation**: one `db.tx` running `recomputeEffortElevation(listOf(uri))` and `jobs.enqueueElevation(uri, now)`. The result says "terrain queued" or "terrain not configured", from `SegmentJobs`' `terrain` flag (exposed read-only as `terrainConfigured`).
- **Rematch**: one `db.tx` checking `status in LIVE_STATUSES` and then `jobs.enqueueSegment(uri, now)`; a segment that isn't live, or isn't indexed, is refused (decision 5).

### 5. Actions can report a refusal outcome

Today a handler can only return (`ok`) or throw (`failed: <Type>`, a 500). An invalid pin or a rematch of an archived segment is neither. `ActionResult` gains `refused: String? = null`: when set, `SiteRoutes.action` records `refused: <reason>` as the outcome and answers `422` with the message. The reason is a short fixed code (`invalid`, `not-found`, `no-pin`, `not-live`, `unconfirmed`), and the human message, with identifiers only, goes to the page. Existing pages are unaffected by the default.

`PinResult` maps: `Pinned`/`Unpinned` → ok; `NoPin` → `no-pin`; `NotFound(uri)` → `not-found`; `Invalid(reason)` → `invalid`, the reason shown (it names URIs only, per `SegmentPins`).

*Alternative:* throw from the handler. Rejected: the audit row would say `failed: IllegalStateException`, and the operator would get a 500 for a validation message.

The sibling changes (`admin-health`, `admin-job-queues`, `admin-accounts`) may need the same; whichever lands first adds it, the others reuse it.

### 6. Pin forms

- Status: a select of `active`, `archived`, `duplicate`, a `duplicateOf` URI field, a `confirm` checkbox, a note (≤ 500). The handler refuses `duplicate` without `confirm=yes` before calling the service (`unconfirmed`), since the pin deletes efforts (segment-pins' risks).
- Name: a free-text field, or a select of the segment's proposals whose names are shown (decision 2). Exactly one is sent; the service rejects both or neither.
- Audit params are the form fields as posted. The note and free-text name are operator-written; the audit's 200-character cap truncates a long note there, while the pin keeps it whole.
- `by` is `admin.did`. Each call goes straight to `indexer.segmentPins`, which takes the indexer's URI locks itself.

### 7. Privacy test

`AdminPrivacyTest.pagesUnderTest` gains `SegmentsPage`, and the seed gains a segment carol has an Only me effort on (with the marker in the effort's activity title), an open proposal by carol with a vote whose voter DID is a marker-bearing value, and a staged candidate she rides once. `SegmentsPage.privacyProbes(subject)` returns the search by the subject, the segment page for the seeded segment, and the importer section. The test asserts that no probe prints the activity marker, the voter's DID, the effort's elapsed time or start offset, or the candidate's rider count.

## Risks / Trade-offs

- [With one or two riders, the athlete count on a proposed segment says that someone, maybe privately, rode it] → It's a count, which the admin-site rule allows, and the same count already decides the public status. No DID, date or time goes with it.
- [`ActionResult` changes shape in a file three parallel changes also touch] → The field is additive with a default; merge is a one-line conflict at most.
- [An operator runs the status pass on a proposed segment just under 180 days old expecting an archive] → The result shows before and after, and the pass only applies the usual rules.
- [Name search with `like '%q%'` scans `segments`] → Limit 51, operator traffic only. The public name search scans the same column.
- [Region search covers only segments whose candidates are still staged] → A candidate removed by a later run drops its segment from region search; search by URI still finds it. Said on the page.

## Migration Plan

No migration. Deploy and the page appears for admins. Rollback: remove the page from `adminPages`; `SegmentPasses` stays as the matcher's code.

## Open Questions

- Whether unpinning a status should also say when the segment now overlaps a later live segment (segment-pins' first risk). `PinResult.Unpinned` carries nothing today; the page can add it later from `duplicatesOf` without changing these specs.
