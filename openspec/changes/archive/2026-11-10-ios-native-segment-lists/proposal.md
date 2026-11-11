# Proposal

## Why

Phase 4 of the native iOS UI (`openspec/explorations/ios-native-ui.md`) moves features one change at a time, cheapest infrastructure first. Most segment and dive-site screens draw a map or a chart and wait for those, but four are plain lists: Starred segments (opened from the athlete's own profile), a segment's leaderboard and its Mayors board (opened from the segment page), and a dive site's Regulars board (opened from the site page). They are lists of segments and of athletes, which the native follow lists (`ios-native-follow-lists`) already draw with `PagedList`, `AppItemRow` and `Avatar`, so they can go now. What they still lack is their text in Kotlin: the boards' filter labels, a written-out date, a segment's type and the counts are chosen in composables today.

## What Changes

- **Starred segments is native on iOS.** Titled "Starred segments"; each star with the segment's current name and its length and type ("1.6 km · Ride", "Ski lift"), opening the segment, and a filled star that unstars it, unavailable while that unstar is written; a star whose segment is gone reads "This segment is no longer available", opens nothing and can still be unstarred; "Star a segment to keep it here" when empty; that the session must sign in again to see stars, with no empty message; a failed unstar above the stars, which stay; page by page with the loading, refresh, next-page and failure states of `ui/content-loading`.
- **A segment's leaderboard is native on iOS.** Titled with the segment's name; the period (All time, This year, This month) and the scope (Everyone, Following) as segmented choices; the board's type as a menu when the segment lists two or more types, else as a label; the age, weight (in the viewer's units) and gender brackets as menus, "All ages", "All weights", "All genders" by default; each entry with its rank (the first three in the accent colour), the athlete's picture and name, the day of the effort and its time, opening the activity; the viewer's own place under "Your place" when it isn't among those loaded; "No one is on this leaderboard yet" when empty; page by page as above.
- **A segment's Mayors board is native on iOS.** Titled "Mayors of …"; the scope as a segmented choice; the window (the last 90 days by default, seven others), the type and the brackets as menus; each entry with its rank (the first in the accent colour), picture and name, "Reached …" with the day it was reached, and its count ("14 efforts"), opening the athlete's profile; the viewer's own place, the empty message and the loading as above.
- **A dive site's Regulars board is native on iOS.** Titled "Regulars of …"; the scope as a segmented choice and the window (All time, This year, Last year) as a menu; each diver with rank, picture, name, "Reached …" and their count ("3 dives"), opening their profile; the viewer's own place, the empty message and the loading as above.
- **All four open as they do today**, pushed over the shared profile, segment page or dive site page in its tab's stack; what they open (a segment, an activity, a profile) is unchanged.
- **Kotlin chooses the text and conditions, both UIs ask.** A board's choices as lists of options with their labels and which is chosen (`BoardOption`, `BoardChoice`, `RankBoardTexts` in `:app-common-api`; `SegmentBoardTexts` in `:feature-segment-api` with the leaderboard and Mayors windows, the type and the three brackets, the weight classes in the viewer's units; the Regulars windows in `:feature-divesite-api`), a written-out date (`DateText`, which `longDate` and `effortDateLabel` now resolve), a segment's sport group and type (`SegmentTexts`, which `segmentSportLabel` and `segmentTypeLabel` resolve), a starred segment's line, a board entry's rank, day and count, and getters on the four states (which ranks are accented, whether the type is a menu, a star's availability and removal, the sign-in notice). No visible change on Android, desktop or web, except that a count of a thousand or more is now grouped as every other count is ("1,204 efforts").
- **The design system gains a filter menu** (`AppMenuChip`: a capsule naming the current choice that opens a menu of the options, filled while it narrows the list), and `PagedList` a place for rows after the items (the viewer's own place), above the empty message and the next page's status, as the shared board puts it.
- **Samples, previews and snapshots** of the four pages in every shared sample, plus variants beside `all` (an unavailable and an unstarring star, a failed unstar, sign in again; a board with several types, chosen brackets and the viewer's own place, in imperial units; a Mayors board with a window chosen and the viewer's place; a Regulars board likewise), so the shared screenshot set and the content-loading conformance keys are unchanged. Registry entries and their test.

Out of scope: the segment page, its map, segment browse, segment creation and the dive site page (maps and charts), the Mayor and Regulars cards on those pages (their counts ask the new texts, with no visible change), pull to refresh (the shared pages have none), and swipe to unstar (the shared page shows the star).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `segments/segment-favorites`: the starred list is drawn natively on iOS, saying and offering what the shared list does.
- `segments/segment-leaderboards`: the leaderboard screen is drawn natively on iOS, with the same choices and entries.
- `segments/segment-mayors`: the Mayors screen is drawn natively on iOS, with the same choices and entries.
- `dive-sites/site-regulars`: the Regulars screen is drawn natively on iOS, with the same choices and entries.
- `dev/shared-display-text`: a board's choices and entries, a written-out date, a segment's type and the starred list's words and conditions are chosen in shared code.
- `ui/ios-design-system`: a filter menu, and rows after a paged list's items.

## Impact

- **`:app-common-api`:** `BoardOption`, `BoardChoice`, `RankBoardTexts`, `DateText`, `SegmentTexts`; tests. **`:app-common-ui`:** `RankBoards.kt` (`ChoiceChip`, `MenuChip`, `ScopeControl`, `RankRow` callers) asks them; `longDate`, `effortDateLabel`, `segmentSportLabel`, `segmentTypeLabel` resolve them; `SharedTextNamesTest` gains the new names.
- **`:feature-segment-api`:** `SegmentBoardTexts`, `StarredSegmentsTexts`, getters on `StarredSegmentsState`, `SegmentLeaderboardState` and `SegmentMayorsState`; tests. **`:feature-segment-ui`:** `StarredSegmentsUi`, `SegmentLeaderboardUi`, `SegmentMayorsUi`, `BoardParts` ask them. **`:feature-divesite-api`:** `DiveSiteRegularsTexts`, getters on `DiveSiteRegularsState`; **`:feature-divesite-ui`:** `DiveSiteRegularsUi`.
- **`:ui-resources`:** one new string (`starred_segments_summary`); the iOS catalog regenerated.
- **`:app-fixtures`:** `StarredSegmentsSamples`, `SegmentLeaderboardSamples`, `SegmentMayorsSamples`, `DiveSiteRegularsSamples` gain `variants`.
- **`iosApp`:** `StarredSegmentsView`, `SegmentLeaderboardView`, `SegmentMayorsView`, `DiveSiteRegularsView` and their shared rank-board parts, `AppMenuChip` with a gallery entry, `PagedList`'s rows after the items, type aliases and imports, samples, previews, snapshot and text tests, registry entries.
- **Docs:** the roadmap's phase 4 entry, a "Phase 4 probes (segment lists)" and findings section, its open items; `.agents/rules/compose-screens.md`.
- **Risk carried:** the test account may have no stars and no efforts, so most of the pages may be seen on a device only empty; iOS 18 can't be walked signed in; snapshots compare only on iOS 26.0.
