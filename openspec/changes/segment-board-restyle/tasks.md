# Tasks

## 1. Shared rules in Kotlin

- [x] 1.1 Add the `leaderboard_behind_first_seconds`, `leaderboard_behind_first` and `mayors_more_to_be_mayor` (plural) strings. Add `BoardGapTexts.behindFirst(seconds)` and `moreToBeMayor(count)`. Verify with a `SegmentBoardTexts` test for 51 s, 65 s ("1:05") and 1 and 8 efforts, and `generateIosResources`.
- [x] 1.2 Add `gap` and `isOwn(entry)` to `SegmentLeaderboardState` and `SegmentMayorsState`, and `isOwn(entry)` to `DiveSiteRegularsState`, from `entries` and its meta's viewer, per design decision 1. Verify with `-api` tests covering the viewer first, no first entry yet, a filtered board's first entry, and a level count reached later.
- [x] 1.3 Give the leaderboard, Mayors and Regulars samples in `:app-fixtures` a pinned viewer with a gap, and a variant where the viewer is among the first page. Verify that `ContentLoadingConformanceTest` still passes.

## 2. Compose

- [x] 2.1 Add `bottomBar` to `ListScreen` and pass it to `AppScaffold`. Verify that an existing list screen's screenshot is unchanged.
- [x] 2.2 Restyle `RankRow`: value in `titleMedium` SemiBold with tabular figures, never wrapping; name wrapping; an `own` mark (tonal background, SemiBold name, "You" in its semantics). Verify with a UI test reading the own row's semantics and the value's single line at 2× font scale.
- [x] 2.3 Add `OwnPlaceBar` (heading, row, gap line, top hairline, no shadow) and drop `ownPlace` from `rankBoardRows`. Verify with UI tests that the bar shows only while the viewer's entry is apart and that the last row scrolls clear of it.
- [x] 2.4 Leaderboard filters: period segmented alone, scope as the first chip. Mayors filters: all chips, window first. Verify by updating `SegmentLeaderboardUiTest` and `SegmentMayorsUiTest` for the scope chip.
- [x] 2.5 Place the bar and own marks on `SegmentLeaderboardUi`, `SegmentMayorsUi` and `DiveSiteRegularsUi`. Verify that their UI tests pass.

## 3. iOS

- [x] 3.1 `RankRow`: headline monospaced value kept whole, own mark (tinted row background, semibold name, "You" in the accessibility label). Verify in the `#Preview`s and `SegmentListsTextTests` where text logic moved.
- [x] 3.2 Make `YourPlace` a bottom `safeAreaInset` bar with the heading, row and gap line, used by `SegmentLeaderboardView`, `SegmentMayorsView` and `DiveSiteRegularsView` in place of `after:`. Verify on the iOS 26.0 simulator that the list scrolls under the bar and its last row clears it.
- [x] 3.3 Filters: Leaderboard `segments:` holds the period alone, with scope as the first `BoardMenu`. Mayors has no segments and its menus are window, scope, type and brackets. Verify with the previews.
- [x] 3.4 Re-record the `SegmentListsSnapshotTests` leaderboard and Mayors snapshots and the `DiveSiteSnapshotTests` Regulars snapshots on the iOS 26.0 simulator: delete the old ones, record, compare clean, and look at each.

## 5. Filters folded (after the first device look)

- [x] 5.1 Kotlin: `bracketCount`, `bracketsLabel` and the Age, Weight, Gender headings; `typeChoice` null under two types. Verify with `-api` tests.
- [x] 5.2 Compose: one Filters chip opening a sheet of the three brackets; period and chips in one tight block. Verify with a UI test choosing an age group from the sheet.
- [x] 5.3 iOS: one Filters menu with three picker submenus, tighter filter block. Verify in `SegmentListsTextTests` and re-recorded snapshots.
- [x] 5.4 Re-record the Android references and iOS snapshots for the leaderboard and Mayors.
- [x] 5.5 `LoadMoreAtEnd` asks for the next page only from a layout of the rows it has: a first page that fills the screen no longer fetches the second at once, which had loaded the viewer's row and hidden the bar for ranks 31–60 on Android, desktop and web. Verify with `LoadMoreAtEndTest`, which fails on the old code.

## 6. One chip row and shared date ranges (after the second device look)

- [x] 6.1 Lexicon and AppView: `BoardPeriod` with `today`, the leaderboard on `boardWindow` with `year`/`month` kept for older clients. Verify with AppView tests for today, last week on the leaderboard and an old `year` value.
- [x] 6.2 Kotlin texts: one `SegmentBoardTexts.period` for both boards, separate age, weight and gender chips again, the Filters chip and its texts removed. Verify with `-api` tests.
- [x] 6.3 Compose: `BoardChipRow` pinned under the bar on both boards, no segmented control. Verify with UI tests choosing Today and an age group, and the row scrolling sideways at phone width.
- [x] 6.4 iOS: `BoardChipBar` in a top inset, list starting right under it. Verify in `SegmentListsTextTests` and re-recorded snapshots.
- [x] 6.5 Re-record the Android references and iOS snapshots for the three boards, and look at both boards on the emulator and the iOS 26 simulator.

## 4. References and checks

- [x] 4.1 Re-record the Android screenshot references for `SegmentLeaderboardScreenshots`, `SegmentMayorsScreenshots` and `DiveSiteRegularsScreenshots` with `updateDebugScreenshotTestDefaultTestSuite`, and look at each.
- [x] 4.2 Run `./gradlew check` and the iOS build. Both pass.
- [x] 4.3 See the boards on the running Android emulator and iOS simulator against the dev stack: a pinned place with its gap, the scope chip, and the own row marked.
