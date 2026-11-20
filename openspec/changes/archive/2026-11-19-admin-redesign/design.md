# Design

## Context

- **Shell.** `PageCall.layout` draws every page as `header` (a `nav ul` of every `AdminPage`, then the DID, role and sign-out form) and `main` (an `h1` and the page's content). `respondHtmlPage` draws sign-in and errors without the nav. `AdminSite.pages` is `HomePage + operator pages + AuditPage`. `HomePage` (in `AuditPage.kt`) prints only who is signed in.
- **Styles.** `resources/admin/admin.css` (20 lines) styles bare elements, with a light/dark palette (`--fg`, `--bg`, `--muted`, `--line`, `--accent`, `--bad`, `--good`). It is served at `/admin/static/admin-$CSS_VERSION.css`. The pages use one class (`inline`), plus `who`, `role`, `error` and `outcome` from the shell.
- **CSP.** `default-src 'self'; script-src 'self'; style-src 'self'; …`. No inline `style` attribute or `<style>` element works, which is why `htmx-config` turns off `includeIndicatorStyles`. htmx comes from its WebJar, so the site needs no other host.
- **Duplication.** `actionForm` is written three times (Health, Accounts, Segments) with small differences: the outcome target, whether `target` is a field, and extra fields. `time()` is written twice: Health renders `Instant?` to whole seconds with `—` for null, Segments renders epoch millis. Health also has `bytes()`. Queues has its own `jobButton` and `bulkForm`.
- **Sources a summary can read.**
  - Health: `sources.workers` (`WorkerStatus`: `state`, `startedAt`, …), `JetstreamConsumer.lastEventUs` (the `time_us` of the last indexed event), labeler streams, `sources.spaceCounts()` (`synced`, `failing`, `notFound`, `due`) and `sources.sizes`.
  - Queues: `SegmentQueue`'s `KindSummary` (`due`, `oldestDueAt`, `retrying`, `failed`).
  - Segments: `pins.orphanedPins(...)` and `SegmentAdminQueries.importerRuns()` (`lastStagedAt`, null without an importer).
  - Accounts: `RepoReindexer.runs(did)`, which is per account only.

## Goals / Non-Goals

**Goals:**
- One view kit that every page draws with, so a state, time, number or action looks the same everywhere and the CSS has stable classes to target.
- Summaries are cheap enough that Home's 30 s refresh and every page's nav flags, across several open tabs, cost a few queries a minute.
- Threshold rules are pure functions, tested without a database or HTTP.

**Non-Goals:**
- Client-side rendering, a CSS framework or a build step for styles. The site stays server-rendered kotlinx.html with htmx and one hand-written stylesheet.
- Changing what any page lists or any action does, beyond the recent reindex runs list (admin/account-tools) and the Jetstream fields (admin/ingest-health).
- Icons. Badges carry their state's name as text.

## Decisions

**1. A view kit in `admin/AdminHtml.kt`, used by every page.** These are `FlowContent` extensions with the CSS classes they emit:
- `badge(text, status)` → `span.badge.ok|warn|bad|none`. Each page maps its own states to a status: `WorkerState`, labeler stream open or closed, `ReindexState`, segment status.
- `at(instant)` → `<time datetime="…" title="…">3 min ago</time>`, with `—` for null. The relative text is computed on the server from the page's `Clock`. The exact UTC time goes in `title` and `datetime`, so it needs no script and can't be refused by the CSP. One `ago(duration)` formatter is shared with tiles.
- `num(n)` / `bytes(n)` → `td.num` cells. `dataTable(headers) { … }` → `div.table-wrap > table`, so a wide table scrolls inside itself.
- `stats { stat(label, value, status?) }` → `dl.stats` drawn as a tile row. It replaces the bare `dl`s.
- `actionForm(path, target, outcome, danger = false, confirm = null) { fields }`: the one action form. It has the CSRF field, both `action=` and `hx-post`, and `hx-target` set to the page's outcome element. With `danger`, its button gets `.danger` and `hx-confirm`.
- `outcome(id)`: the element action results are swapped into.

Alternative considered: a CSS-only restyle on element selectors. Rejected because states, numbers and destructive buttons can't be told apart without classes (see proposal.md, Why).

**2. Relative times are rendered, not computed in the browser.** A small script that rewrote `<time>` elements would keep them fresh, but it would be a second script, and it would make tests depend on a browser. Pages are `no-store` and Home redraws every 30 s, so a time rendered on the server is never more than a page load old. A detail page's relative times age while it stays open. The exact time is always in `title`, so this is acceptable.

**3. `Tile` and `AdminPage.summary()`.**
```kotlin
enum class TileStatus { Ok, Warn, Bad }
data class Tile(val label: String, val value: String, val status: TileStatus?, val href: String)
interface AdminPage { …; suspend fun summary(now: Instant): List<Tile> = emptyList() }
```
`value` is a pre-formatted string (count, size, `ago`, duration), so Home needs no knowledge of units. Each page splits `summary` into reading its sources and a pure `tilesOf(snapshot, now)` holding the thresholds, which is unit-tested directly. Thresholds are `const`/`val`s in the page's companion (for example `JETSTREAM_WARN = 1.minutes`, `JETSTREAM_BAD = 15.minutes`, `QUEUE_DUE_WARN = 5.minutes`, `QUEUE_DUE_BAD = 1.hours`), named after the spec rules they implement. `href` points at a section anchor (`/admin/health#spaces`), so every section gets an `id`.

**4. One cached read of all summaries.** `AdminSite` owns a `Summaries` service. `read()` runs every page's `summary` concurrently (`coroutineScope { pages.map { async { … } } }`), each under a 5 s `withTimeout` and `runCatching`. A failure or timeout becomes `PageSummary.Unavailable(page)` and never fails the others. The result is cached for 10 s (a single-entry Caffeine cache, already a dependency, with the site's `Clock` as ticker). The cache is safe to share because summaries are the same for every admin: they are counts, with no role-dependent content. Home's refresh and every tab's nav flags then cost at most one set of summary queries per 10 s. Alternative: no cache. Rejected because each page load would then add every page's queries, including the catalog read behind database size, to every request. Through the cache, that read runs at most once per 10 s, which costs little.

**5. Home renders its tiles inline, then polls.** `GET /admin` draws the shell and the grid from `Summaries.read()`, so the first paint has data. The grid container carries `hx-get="/admin/tiles" hx-trigger="every 30s" hx-swap="outerHTML"`. The fragment orders tiles by status (`Bad`, `Warn`, then `Ok` and none), keeps navigation order within each group, and splits them into "Needs attention" and "Everything else", with the all-clear sentence when the first group is empty. An unavailable page draws one `none` tile reading "Unavailable".

**6. Nav flags are one lazy fragment.** The server draws the nav with every link and `aria-current="page"` on the current one, so the page is complete without flags. The `ul` carries `hx-get="/admin/nav?current=<path>" hx-trigger="load" hx-swap="outerHTML"`. The fragment redraws the same list with a `span.flag.warn|bad` (visually a dot, with text "needs attention" for assistive technology) on each page whose worst tile is `Warn` or `Bad`. If the request fails, htmx leaves the server-drawn list in place. `current` is matched against the known page paths only, never echoed.

**7. Layout without script.** `body` is a CSS grid: a fixed-width sidebar (site name, nav, then identity and sign-out at its foot) and `main`. Below 720 px the grid becomes one column: the sidebar becomes a top bar whose nav scrolls sideways inside itself (`overflow-x: auto`), and identity moves under it. No toggle button is needed, so no script. `respondHtmlPage` (sign-in, errors) uses a centred single card with no nav.

**8. The stylesheet.** It stays one hand-written `admin.css`, organised as tokens, base, layout, components (badge, tile, stats, table, form, button, flag) and utilities. Tokens give each status a foreground and a tinted background for light and dark. Every pairing is checked for at least 4.5:1 contrast. The type is the system font with `font-variant-numeric: tabular-nums` on numbers and times. Bump `CSS_VERSION` to `3`.

**9. Catch-up lag.** `JetstreamConsumer` takes a `Clock` (default `Clock.System`) and adds `lastReceivedAtMs: AtomicLong`, set beside `lastEventUs` when an event is indexed. Catch-up lag = `receivedAt − eventTime`, floored at zero, since Jetstream's clock and ours may differ by a little. The Health page shows the stored cursor, the last event's time, when it was received (`at(...)`) and the catch-up lag. The Prometheus gauge that measures "now − last event" is left as it is: metrics keep their own meaning, and dashboards alert on it with their own rules.

**10. Health's tiles.**
- Workers: from `WorkerStatus`. `RESTARTING` → `Bad`. `BACKING_OFF`, or `STARTING` with `startedAt` over 1 min ago → `Warn`. `DISABLED`, `FINISHED` and `STOPPED` are neutral.
- Jetstream: the catch-up lag combined with the Jetstream worker's state.
- Labelers: open over configured.
- Failing spaces: `spaceCounts` gains `failingOther`, the failing spaces not reported missing by their host, in the same single query. `Bad` when it's above zero, `Warn` when only missing ones fail.
- Database size: `bytes(databaseBytes)` with no status, left out when sizes aren't available.

**11. Accounts' recent runs.** `RepoReindexer.recentRuns(since, limit = 50)`: runs whose state is `queued` or `running`, plus `failed` runs whose `ended_at >= since`, ordered `requested_at DESC`. The accounts page draws it under the lookup form when no account is looked up, linking each DID to `/admin/accounts?did=…`. Its tiles take two `COUNT`s with the same predicates. `repo_reindexes` is pruned and small, so this needs no new index or migration.

**12. Destructive actions.** These get `danger` and a confirm text naming what is discarded: Drop (queued job or dead letter), both bulk requeues, Jetstream rewind, and unpin (segment page and orphaned pins). The bulk requeues keep their spec-required `confirm` checkbox. `hx-confirm` adds the browser dialog on top. It uses `window.confirm`, which neither `allowEval: false` nor the CSP blocks. A post without htmx (scripts off) skips the dialog. That is accepted: the server-side checks are unchanged.

**13. Guards in tests.**
- `AdminSiteTest`: renders every page and fragment as an operator and asserts no `style=` attribute and no `<style>`/inline `<script>` in the HTML. This holds the CSP requirement in CI instead of only in a browser.
- `AdminPrivacyTest`: adds `/admin`, `/admin/tiles` and `/admin/nav` to the probes.
- Each page's threshold rules: unit tests on `tilesOf`, one per spec scenario.

## Risks / Trade-offs

- [The 10 s cache shows a fixed problem for up to 10 s, plus up to 30 s until Home's next redraw] → Within the "about 30 seconds" the spec allows. A page's own sections, redrawn after actions, are never cached.
- [A slow summary source holds a Home load for up to 5 s] → The timeout turns it into "Unavailable" for that page only. Nav flags are lazy, so other pages never wait.
- [The workers tile and the Jetstream tile can both go red for one cause] → They answer different questions, and both link to the relevant section. Accepted.
- [Clock skew between Jetstream and the AppView distorts catch-up lag by the skew] → Floored at zero, and the thresholds (1 and 15 min) are far above normal NTP skew.
- [Rewriting every page's markup can break existing page tests that match HTML text] → Tests that match visible text keep working. Those that match exact tags are updated in the same task as the page.
- [`hx-confirm` adds a dialog the existing bulk-requeue checkbox already guards] → Two steps for bulk actions is intended for the actions that queue the most work.

## Migration Plan

No data migration and no configuration change. Deploying replaces the stylesheet under a new versioned URL, so cached copies of the old one are never mixed with new markup. Rollback is a revert of the change.
