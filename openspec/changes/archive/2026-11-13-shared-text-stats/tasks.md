# Tasks

## 1. Shared text

- [x] 1.1 `ZoneRow.title`, `percent` and `spoken` in `ZoneRows.of`, with their strings and tests
- [x] 1.2 `FigureParts.of` and `unbreakable` (`FigurePart`) in `:app-common-api`, with tests moved from `PageFiguresTest`
- [x] 1.3 New names checked by the text-name tests

## 2. Compose

- [x] 2.1 `ZoneBar`/`ZoneBars` draw the shared title, percent and sentence; `ActivityChartsUi` passes them; the chart screenshot's sample
- [x] 2.2 `AvgMaxTable` asks `FigureTexts.showsMin` and `StatText.missing`
- [x] 2.3 `withSmallUnits` draws from `FigureParts.of`; `unbreakableUnit` removed
- [x] 2.4 `MoreReplies` asks `CommentsTexts.moreReplies`, the comments header `CommentsTexts.count`
- [x] 2.5 Remove `elevationStat`, the `showsLength` wrapper (its test to `CardStatsText`) and `GearTimeChart`, after checking by name

## 3. Swift

- [x] 3.1 `ZoneTimesView` shows `percent` and reads `spoken`
- [x] 3.2 `FigureBlock` draws from `FigureParts.of` (Swift helper renamed `FigureRuns`); `FigurePart` aliased; figure tests updated

## 4. Pictures

- [x] 4.1 Android screenshot suite passes; any changed reference rerecorded and looked at
- [x] 4.2 iOS 26.0 snapshots whose view changed rerecorded and looked at

## 5. Verification

- [x] 5.1 `./gradlew check -Pkotlin.incremental=false` passes
- [x] 5.2 An ad-hoc-signed iOS Release build passes
- [x] 5.3 `xcodebuild test` passes on an iOS 26.0 simulator clone
- [x] 5.4 Tick stats and units off in the roadmap's checklist and add this round's findings
