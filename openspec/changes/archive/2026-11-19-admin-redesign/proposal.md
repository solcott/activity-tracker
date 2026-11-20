# Proposal

## Why

The admin site (admin/admin-site) and its four pages work, but you can't scan them: one 20-line stylesheet styles bare elements. The markup carries almost no classes, so a failing space, a gave-up job or a closed labeler stream looks like a healthy row, and Home shows only who is signed in. Operators need to see at a glance whether anything needs them, and which page to open about it.

## What Changes

- **Shell.** A sidebar navigation that collapses to a top bar on narrow screens. It marks the current page and flags each page that has a problem. Identity, role and sign-out sit beside the page heading. The CSP stays self-only: no inline styles, nothing from third-party hosts, no new dependency.
- **Page summaries.** `AdminPage` gains an optional `summary()`: a few tiles, each a label, a value (a count, size, time or duration only), a status of `ok`, `warn`, `bad` or none, and a link into the page. A page added later gets onto Home by implementing it, with no change to Home.
- **Triage Home.** Home shows every page's tiles, those needing attention (`bad`, then `warn`) first and the rest after. The tile grid redraws itself every 30 seconds.
- **Fixed thresholds.** Each page's spec states when its tiles turn `warn` or `bad`. The values are constants in code, not configuration.
- **Jetstream judged by keeping up, not by silence.** The Health page's Jetstream lag becomes how far behind the last received event was when it arrived. A quiet network no longer reads as a growing lag. The time since the last event is still shown, without a status.
- **Lazy nav flags.** The navigation's problem flags load as one fragment after the page. A page never waits on, or fails because of, other pages' summaries.
- **Shared building blocks** used by every page: status badges for states (workers, streams, reindex runs, segment status), times shown relative with the exact UTC time on hover, right-aligned numeric columns, stat tiles in place of bare definition lists, and destructive actions styled as such and confirmed before they post. The three copies of `actionForm` and the two of `time()` are replaced by one each.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `admin/admin-site`: "Pages share one site" grows to cover the sidebar, current-page marking and lazy problem flags. New requirements cover page summaries, the triage Home with its refresh, the consistent presentation of states, times and numbers, and confirmation of destructive actions. "Admin pages reveal no private content" now names Home and the summary fragments too.
- `admin/ingest-health`: "Health page shows Jetstream's position" redefines lag as catch-up lag. A new requirement gives Health's tiles and thresholds (workers, Jetstream, labelers, failing spaces, database size).
- `admin/job-queues`: a new requirement gives Queues' tiles and thresholds (oldest due job, retrying, gave up).
- `admin/segment-tools`: a new requirement gives Segments' tiles (pins on missing segments, latest importer run).
- `admin/account-tools`: a new requirement gives Accounts' tiles (reindex runs in flight, failed in the last day).

## Impact

- `server/appview` only:
  - `admin/` gets a shared view kit (`AdminHtml.kt`: badges, times, numbers, stat tiles, action forms, tiles) and a `Tile` type with the `summary()` contract on `AdminPage`.
  - `PageCall` gets the new layout. `HomePage` gets the tile grid and its fragment, and `AdminSite` the nav-flags fragment.
  - Each page gets its `summary()` and moves to the shared helpers.
  - `admin.css` is rewritten and `CSS_VERSION` bumped.
  - `JetstreamConsumer` records when its last event was received.
  - New count queries for reindex runs across accounts.
- Tests: `AdminSiteTest` (shell, flags fragment, Home ordering), each page's test (tiles and thresholds), `AdminPrivacyTest` probes extended to Home and the fragments.
- No lexicon, XRPC, client, migration, configuration or Docker change. No new dependency.
- Out of scope: history or trends (nothing stores past values), configurable thresholds, custom web fonts.
