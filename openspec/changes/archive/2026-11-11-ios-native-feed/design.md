# Design

## Context

`FeedUi` is one `AppScaffold`: a title menu of scopes and an Add menu in the bar, the filter bar (`SubCircuitContent`) above the list,
`ContentStateLayout` around pull to refresh and a `LazyColumn` of upload rows, `RefreshStatus`, a failed action, two prompts, the
timeline's items and the empty message, then `ReportingHost` and `QuoteSheetHost`. The native pieces it needs exist: `FeedFilterBar`
through `SubScreenView`, `ActivityCardView`, `PostCardView`, `.quoteSheet` and `.reporting`. The rest builds its text in composables.

## Decisions

1. **`FeedTexts`, `FeedUploadTexts` and `ImportCardTexts` in `:feature-feed-api`.** Plain functions returning `AppText` (or null where a row doesn't
   show), as `UploadsTexts` does; the scopes are `FeedScopeOption`s and the Add menu `FeedAddChoice`s (kind, words, event), since a list
   of enums can't reach Swift. `FeedState.showsEmpty` holds the one condition (`isLoading` is a library extension Swift can't call) and
   `FeedState.keyOf(item)` the list identity both UIs key items by. The Compose feed keeps its layout and only resolves these.
2. **Counts are grouped.** The upload rows and import card pass `formatWhole` text, as every Kotlin count does for the catalog (whose
   `%d` placeholders are `%@`). The Compose feed passed raw ints, so a count of 1,000 or more now reads grouped there too, which
   `ui/number-format` asks.
3. **One list, the bar its first row.** The filter bar stays put above the Compose list because a lazy item scrolled off is disposed
   with its state. The host keeps a sub-screen for its record's life whatever its view does, so natively the bar is the list's first
   row and scrolls away under the large title, as iOS lists do. The first load's progress or failure is a row under the bar, so a new
   filter's load never hides the bar (`ContentShown` decides which, as `ContentStateView` does).
4. **The scope is a chip leading the filters, and the title's menu once it collapses.** A large title shows no title menu (found on the
   iPad: `toolbarTitleMenu` draws its chevron and opens only when the title is inline), so the scopes are an `AppMenuChip` at the start
   of the filter bar's line (`FeedFilterBar(leading:)`), never filled since a scope narrows nothing, and the same `Picker` is the
   title's menu (`toolbarTitleMenu`) for when the list has scrolled the title inline. It scales to clubs as the shared title menu does.
   Add is a `Menu` in the bar, its entries labelled with their icons.
5. **Pull to refresh** is SwiftUI's `refreshable`. Its action sends `Refresh` and waits until the state stops loading (`pullToRefresh`,
   `Design/`), so the system's spinner shows exactly as long as the refresh runs; the list's own refresh row shows no spinner, as on a
   phone in the shared UI, but shows a failure with Retry.
6. **Outside the host** (previews, snapshots) there is no record to run the bar, so `FeedPage` takes an optional sample bar state
   that only `#if DEBUG` code passes, and draws `FeedFilterBar` from it.
7. **The cards are placed as they are.** Activity cards with the author line clickable, post cards from
   `PostCardContent.of(post:viewerDid:reason:)` (a repost passing its reason), each in a section of its own. Report on a post waits for
   the card's menu, wired by another branch.
8. **Registered now.** With the cards, posts, import cards, uploads, prompts, sheets, refresh and empty and failure states, the native
   page shows everything a user needs from the feed; the only differences are the ones listed in the proposal's open items.

## Risks

- `toolbarTitleMenu` under a large title: checked on the iPad walk; it doesn't show (decision 4).
- A `List` row's sheet: the filter bar's sheets present from its row; the bar is on screen whenever it is tapped.
