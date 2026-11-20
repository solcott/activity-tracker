# Tasks

## 1. Shared passes and action outcomes

- [x] 1.1 Move the segment step of `SegmentMatcher.updateStatuses` and the per-segment step of `SegmentNaming.update` into `segments/SegmentPasses.kt` (`updateStatuses(only)`, `updateNames(only)`, design decision 3), with `SegmentMatcher` delegating and keeping its dive-site step for the full pass; verify the existing status, naming and pin tests (`SegmentStatusTest`, `SegmentNamesTest`, `SegmentPinsTest` or their current names) pass unchanged
- [x] 1.2 Add `SegmentPassesTest`: `updateStatuses(listOf(a))` promotes `a` at the athlete threshold and leaves an equally ridden `b` proposed; archives `a` past `ARCHIVE_AFTER` and re-checks its duplicates; leaves a status-pinned `a` alone; `updateNames(a)` adopts a qualifying proposal on `a` only and recomputes `a`'s current name; verify with `./gradlew :server-appview:test --tests "*.SegmentPassesTest"`
- [x] 1.3 Add `ActionResult.refused` and make `AdminSite`'s `action` record `refused: <code>` and answer `422` when it's set (design decision 5), unless a sibling change already landed it; verify in `AdminSiteTest` that a test action returning a refusal writes that outcome and status, and that the existing actions still record `ok`
- [x] 1.4 Expose `SegmentJobs.terrainConfigured` (read-only); verify by a unit assertion in `SegmentPassesTest` or `SegmentJobs`' test

## 2. Reads

- [x] 2.1 Write `admin/SegmentAdminQueries.kt` (design decision 2): search by URI, rkey, name, status, sport, kind and region with a URI cursor and 50 a page; segment detail with names only under `served(null, …)`; effort and distinct-athlete counts; elevation as served with effort values only at the threshold; queued job kinds; pins via `SegmentPins.pinsOf`; proposals with totals and names only under `listedProposal(null)`; importer runs grouped by region and revision; every `select` naming its columns; verify with `SegmentAdminQueriesTest` covering each spec scenario under "Admins search segments", "Names shown only where the AppView serves them", "The segment page counts efforts only", "The segment page shows elevation as served", "Admins see a segment's name proposals with totals" and "Admins see importer runs by region"

## 3. Page and actions

- [x] 3.1 Write `admin/SegmentsPage.kt` (design decision 1): the search form and results, importer runs (or "no importer configured"), pins on missing segments from `orphanedPins` with a cursor, and the segment page with a link to the audit log filtered by its URI; controls only for operators; verify in `AdminSegmentsTest` (Ktor test host, `AdminTestBase`) that a viewer's segment page loads with no forms and an operator's shows them
- [x] 3.2 Add the actions `status-pass`, `naming-pass`, `elevation`, `rematch` (design decisions 3 and 4), each answering with before and after, "terrain queued" or "terrain not configured", or `not-live`; verify in `AdminSegmentsTest` the spec scenarios for each, and that a rematch queues a `segment` job only for a live segment
- [x] 3.3 Add `pin-status`, `unpin-status`, `pin-name`, `unpin-name` over `indexer.segmentPins` with the `PinResult` mapping and the duplicate confirmation (design decisions 5 and 6); verify in `AdminSegmentsTest` the spec's pin scenarios: pin archived with note shown on the page, invalid cross-sport duplicate refused unchanged, unconfirmed duplicate refused, unpin with no pin says so, and pins on a deleted segment listed and unpinned
- [x] 3.4 Verify gating and auditing in `AdminSegmentsTest`: a viewer's POST to each action answers `403` and changes nothing; a POST without the CSRF token, and one with a foreign `Origin`, is refused; every attempt writes one `admin_actions` row with the segment URI as target, the form fields as params and `ok`, `refused: <code>`, or `failed: <Type>` as outcome
- [x] 3.5 Pass `adminPages = listOf(SegmentsPage(…))` from `main` to `appView`, with a `SegmentPasses` built from the config and `indexer.segmentJobs`; verify `./gradlew :server-appview:run` against the local stack lists "Segments" in the admin navigation

## 4. Privacy and docs

- [x] 4.1 Add `SegmentsPage` to `AdminPrivacyTest.pagesUnderTest` with `privacyProbes` (design decision 7), and extend the seed with a segment carol has an Only me effort on, an open proposal on it with a vote by a DID not otherwise shown, a `!takedown`-labelled segment with a marker in its name, and a candidate with one rider below the threshold; verify the test fails when a probe prints the activity marker, the voter's DID, the effort's elapsed time, the labelled name or a rider count, and passes with the page as written
- [x] 4.2 Add a segments-page line to `.agents/rules/appview.md`'s admin site bullet (reads in `SegmentAdminQueries`, per-segment passes through `SegmentPasses`, pins only through `Indexer.segmentPins`, refusals as `ActionResult.refused`); verify `scripts/check-agent-sync.sh` passes
- [x] 4.3 Run `./gradlew :server-appview:test` and then `./gradlew check` through the gradle-runner agent; verify both pass

## 5. Local check

- [x] 5.1 With `scripts/dev.sh` (and `docker/scripts/seed-segment.sh` / `seed-effort.sh` for a segment with efforts), open `http://localhost:8081/admin` in the browser pane, sign in as `siteadmin.test`, search the seeded segment by name and by URI, open it, run the status pass, naming pass, elevation recompute and rematch, pin it `archived` with a note and unpin it, pin and unpin a name, and try a duplicate pin unconfirmed; verify each outcome on the page, the segment's status in `feed.getSegment`, and one audit row per action on the audit page filtered by its URI
