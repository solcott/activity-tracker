# Tasks

## 1. View kit and stylesheet

- [x] 1.1 Add `admin/AdminHtml.kt` with `badge`, `at`/`ago`, `num`/`bytes`, `dataTable`, `stats`/`stat`, `outcome` and the single `actionForm` (with `danger` and `confirm`), as in design decision 1. Unit-test the pure formatters (`ago` across seconds, minutes, hours and days; `bytes` across units; `at(null)` → `—`; `at` carrying the exact UTC time in `datetime` and `title`) in a new `AdminHtmlTest`. Verify with `./gradlew :server-appview:test --tests "*.AdminHtmlTest"`.
- [x] 1.2 Rewrite `resources/admin/admin.css` as tokens, base, layout, components and utilities, with light and dark status tokens (design decisions 7 and 8), and bump `AdminSite.CSS_VERSION` to `3`. Verify that every status foreground on its background reaches 4.5:1 contrast in both palettes (record the pairs checked in a CSS comment), and that the existing test asserting the stylesheet route still passes.

## 2. Shell and navigation

- [x] 2.1 Redraw `PageCall.layout` as sidebar plus `main`: the site name, the nav with `aria-current="page"` on the current page, and identity, role and sign-out. Give the nav `ul` its lazy `hx-get="/admin/nav?current=…"`. Draw `respondHtmlPage` as a centred card without nav. Update `AdminSiteTest`: every page still lists every page, shows the DID, role and sign-out, and marks exactly the current page. Verify with `./gradlew :server-appview:test --tests "*.AdminSiteTest"`.
- [x] 2.2 Add `TileStatus`, `Tile` and `suspend fun summary(now)` (defaulting to none) to `AdminPage`. Add the `Summaries` service in `AdminSite`: concurrent reads, a 5 s timeout per page, `Unavailable` on failure, and a 10 s cache on the site clock (design decision 4). Test in `AdminSiteTest` with fake pages: a throwing page and a page slower than the timeout each come back `Unavailable` while the others return tiles, and a second read within 10 s calls no page again.
- [x] 2.3 Add the `/admin/nav` fragment: the same list with `span.flag` at each page's worst `Warn`/`Bad` status, text for assistive technology, and `current` matched only against known page paths. Test that a fake page with a `Bad` tile is flagged `bad`, a page with no summary is never flagged, and an unknown `current` marks nothing and isn't echoed.
- [x] 2.4 Add the HTML guard from design decision 13: render every page and fragment as an operator (with the fake and real pages) and assert no `style=` attribute, no `<style>` element and no inline `<script>`. Verify with `./gradlew :server-appview:test --tests "*.AdminSiteTest"`.

## 3. Home

- [x] 3.1 Replace `HomePage`'s body with the tile grid: drawn inline from `Summaries.read()`, polling `/admin/tiles` `every 30s`. "Needs attention" holds `Bad` then `Warn` tiles in navigation order, "Everything else" the rest, with the all-clear sentence when none needs attention and "Unavailable" for a failed page (design decision 5). Each tile links to its `href`.
- [x] 3.2 Test Home in `AdminSiteTest` with fake pages: problems-first ordering across pages, the all-clear sentence, the unavailable tile, the viewer seeing the same tiles, and `/admin/tiles` answering the same grid as a fragment.
- [x] 3.3 Add `/admin`, `/admin/tiles` and `/admin/nav` to `AdminPrivacyTest`'s probes, with real pages' summaries over its seeded private data. Verify with `./gradlew :server-appview:test --tests "*.AdminPrivacyTest"`.

## 4. Health page

- [x] 4.1 Give `JetstreamConsumer` a `Clock` and `lastReceivedAtMs`, set beside `lastEventUs`. Show the stored cursor, the last event, when it was received and the catch-up lag, floored at zero (design decision 9). Add a `JetstreamConsumer` test with a fake clock and frames: an event stamped 2 min before receipt gives a 2 min lag that doesn't grow while no frame arrives.
- [x] 4.2 Add `failingOther` to `HealthSources.spaceCounts()` in the same query, and test it in `HealthSourcesTest` with spaces failing as missing and failing otherwise.
- [x] 4.3 Implement Health's `summary` through a pure `tilesOf` with the threshold constants (design decision 10). Add one unit test per scenario in admin/ingest-health's tile requirements (workers restarting, backing off, starting over 1 min, disabled; Jetstream at 5 and 20 min, quiet, off and before the first event; labelers; spaces missing only and failing otherwise; database size with no status; no labeler configured).
- [x] 4.4 Move the Health page to the view kit: badges for worker and stream states, `at` for times, numeric columns, `stats` for the Jetstream and space counts, section ids for tile links, and the shared `actionForm` with rewind as `danger`. Delete its private `actionForm`, `time` and `bytes`. Verify `HealthPageTest` passes, updated only where it matched exact markup.

## 5. Queues page

- [x] 5.1 Implement Queues' `summary` through a pure `tilesOf` over the `KindSummary` list with the 5 min and 1 h due thresholds. Unit-test each admin/job-queues tile scenario (backlog at 20 min and 2 h, jobs that gave up, idle).
- [x] 5.2 Move the Queues page to the view kit: numeric columns, `at` for times, section ids for the retrying and dead-letter lists, `jobButton` and `bulkForm` through the shared `actionForm`, and Drop and both bulk requeues as `danger` with a confirm text (the `confirm` checkbox stays). Verify `JobQueuesPageTest` passes and that Drop's form carries `hx-confirm` while Retry now's doesn't.

## 6. Segments page

- [x] 6.1 Implement Segments' `summary`: the orphaned-pins count (`warn` when any) and the latest import (the newest `lastStagedAt`, no status, left out without an importer), with no rider count. Unit-test each admin/segment-tools tile scenario in `AdminSegmentsTest` or a pure `tilesOf` test.
- [x] 6.2 Move the Segments page to the view kit: a badge for segment status and pins, `at` for times, numeric columns, the shared `actionForm` with unpin as `danger`, and section ids. Delete its private `actionForm` and `time`. Verify `AdminSegmentsTest` and `SegmentAdminQueriesTest` pass.

## 7. Accounts page

- [x] 7.1 Add `RepoReindexer.recentRuns(since, limit = 50)` and the two tile counts (design decision 11). Test them in the reindexer's test: queued and running runs listed, a failed run within 24 h listed, one older not, an `unavailable` one never, ordered newest first.
- [x] 7.2 Draw the recent runs list on the accounts page before any lookup (DID linking to the account page, requester, state badge, phase, times, error code), and implement Accounts' `summary` (in flight with no status, failed in 24 h `warn` when any). Move the account page to the view kit and delete its private `actionForm`. Verify `AccountsPageTest` covers the list, the links and each admin/account-tools tile scenario.

## 8. Audit log and integration

- [x] 8.1 Move the audit log page to the view kit (`at` for times, an outcome badge for `ok`/`refused`/`failed`, `dataTable`), with no summary. Verify `AdminSiteTest`'s audit cases pass.
- [x] 8.2 Update `.agents/rules/appview.md`'s admin-site bullet: pages draw with `AdminHtml.kt`, may offer a `summary()` with thresholds as constants, and never use inline styles. Verify that `scripts/check-agent-sync.sh` passes.
- [x] 8.3 Run `./gradlew :server-appview:test` and `./gradlew :server-appview:check` (ktfmt, detekt) and confirm both pass.
- [x] 8.4 Run the AppView on the local stack (`scripts/run-appview.sh`) and check in the browser pane, signed in, at 1280 px and 375 px, light and dark:
  - Home's tile order, and its refresh after 30 s.
  - Nav flags and the current-page marker.
  - Every page's badges and times, with the exact time on hover.
  - A `danger` action's confirm dialog, dismissed.
  - No CSP refusals in the console.
  - No sideways page scroll at 375 px.

  Keep screenshots out of the repository.

## Workflow follow-up

- Archive the change after review, syncing its deltas into admin/admin-site, admin/ingest-health, admin/job-queues, admin/segment-tools and admin/account-tools.
