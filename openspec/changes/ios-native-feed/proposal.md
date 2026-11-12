# Proposal

## Why

The feed is the Feed tab's root and the app's first page, and on iOS it is still the shared Compose UI: every other native piece it
shows now exists (the filter bar through the presenter host's sub-screens, the activity card, the post card with its quote sheet, the
report sheet), so the feed is what keeps the first thing an athlete sees from looking and moving like iOS. What it alone has (the scope
title menu, Add, the import cards, the upload rows, the two prompts, pull to refresh, the empty and failure states) still chooses its
words inside composables, which a native page can't ask.

## What Changes

- **The feed is native on iOS** (`FeedPage`), registered in `ScreenRegistry`: an inset grouped list with the large title of a tab root
  naming the chosen scope, with Following and Mine in a chip leading the filters (a large title shows no title menu) and in the title's
  menu once it collapses inline; Add in the bar, a menu of Record (where the device can record),
  a manual activity and an imported file; the native filter bar at the top of the list, run by the presenter host as the feed's
  sub-screen; the upload rows (refused changes, what is uploading, imports paused, the server's pause), each opening the upload queue;
  a refresh's progress or failure and a failed action; the segments and grouping prompts; the timeline as native activity cards, import
  cards and post cards (a repost with who reposted it), in the AppView's order; the empty message with Clear filters when the filters
  left nothing; the first load's progress or failure with Retry under the bar; pull to refresh; the quote sheet; and the report sheet,
  shown by `reporting(_:)` for when the native post card's Report is wired.
- **A native import card** (`FeedImportCard`): the author line with the months, how many, the sports and distance, and up to four route
  sketches, opening the batch.
- **Kotlin chooses what the feed says, both UIs ask** (`FeedTexts`, `FeedUploadTexts`, `ImportCardTexts`, `FeedState.showsEmpty` and `keyOf`): the title
  and scopes, the Add menu's entries, the upload rows, the empty message and whether it offers Clear, and the import card's lines. The
  Compose feed and import card ask the same. Counts in the upload rows and on the import card are grouped by thousands, as
  `ui/number-format` asks, where the Compose feed wrote "3000"; nothing else changes on Android, desktop or web.
- **Samples, previews and snapshots** of the page in every shared sample and new variants (Mine, a filter that left nothing, a phone
  that can't record, the segments prompt, a failed action, a pause ending tomorrow), light and dark; the registry entry and its test.

What waits (open items in the roadmap):

- **Paging.** The feed loads the cache and then the AppView's first page, on every platform; there is no next page to ask for, so the
  native page has none either. Paging the timeline is a shared change of its own.
- **Report on a feed post.** The native post card's menu has no Report yet; another branch wires it from `PostCardView`. The page
  already carries `.reporting(state.reporting)`, so it shows the sheet once the card sends `ReportingEvent.Open`.
- **The filter bar's type sheet** still has no search and no collapsed sports (`ios-host-subcircuits`); the bar is placed as it is.
- **Refresh on a pointer.** The shared feed shows a Refresh button where there is a pointer (desktop, web); an iPad with a pointer
  pulls to refresh, as an iPhone does.
- Record, a manual activity and an import open the shared UI's screens, which stay as they are.

Out of scope: editing `ActivityCardView` or `PostCardView`; paging; Record, manual entry and import going native.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/feed-filter`: the feed, its scope menu and its filter bar are native on iOS.
- `feed/import-cards`: the feed's import card is native on iOS.
- `ui/ios-design-system`: a native page pulls to refresh with the system's control.
- `dev/shared-display-text`: the feed's title, scopes, Add menu, upload rows and empty message, and the import card's lines, are chosen
  in shared code.

## Impact

- **`:feature-feed-api`:** `FeedTexts`, `FeedUploadTexts`, `FeedScopeOption`, `FeedAddChoice`, `FeedAddKind`, `ImportCardTexts`; `FeedState.showsEmpty`
  and `keyOf`; tests.
- **`:feature-feed-ui`:** `FeedUi` and `ImportCard` ask them.
- **`:app-fixtures`:** `FeedSamples.variants`.
- **`iosApp`:** `FeedPage`, `FeedImportCard`, `FeedUploadRows`, the prompts, `pullToRefresh`, type aliases, samples, previews,
  snapshot and text tests, the registry entry.
- **Docs:** the roadmap's phase 4 entry, findings and open items; `.agents/rules/compose-screens.md`.
- **Risk carried:** snapshots compare only on iOS 26.0; iOS 18 can't be walked signed in; the signed-in iPhone is another session's, so
  the walk is on the iPad.
