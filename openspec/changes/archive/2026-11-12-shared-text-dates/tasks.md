# Tasks

## 1. Inventory

- [x] 1.1 Replace the roadmap's "About 120 composables" item with the grouped inventory checklist (file:line, the other UI's behaviour); verify each listed file:line by reading it

## 2. Shared dates

- [x] 2.1 Add `DateText.of(date: LocalDate)` and test it in `:app-common-api` (`DateTextTest`: a day, a single-digit day, a year never grouped)
- [x] 2.2 Write gear's form dates (`EditGearTexts.date`, so acquired, mounted-on and retired) and the gear page's mount dates (`GearPageTexts`) with it; update `EditGearTextsTest` and `GearPageTextsTest`
- [x] 2.3 Write the upload queue's unnamed activity date (`UploadsTexts`) and the feed filter's custom range (`FeedFilterTexts`) with it; move threshold history's short date to `DateText.monthDay`; update `UploadsTextsTest`, `FeedFilterTextsTest`, `DateTextTest`
- [x] 2.4 Add `EditProfileTexts.birthDate(state)` with tests (set, unset)

## 3. Compose

- [x] 3.1 `EditProfileUi`'s birth date field shows `EditProfileTexts.birthDate`
- [x] 3.2 `CombinePickerUi`'s row is `CombinePickerTexts.detail`; `ActivitySegmentsUi`'s lift row is `ActivityPageTexts.liftRides` and `liftTime`
- [x] 3.3 Remove the unused `HistoryDates.kt` helpers (`monthName`, composable `longDate`)
- [x] 3.4 Rerecord the Android screenshot references whose dates changed, look at them, and pass `:androidApp:testDebugScreenshotTestDefaultTestSuite`

## 4. Swift

- [x] 4.1 `EditProfileView` shows `EditProfileTexts.birthDate`; `SegmentLeaderboardView` shows `SegmentBoardTexts.time(entry)`
- [x] 4.2 `ShownClock.date`; the birth date, acquired and custom range pickers end at it
- [x] 4.3 Update the Swift text tests and the iOS 26.0 snapshots whose dates changed, and look at the new images

## 5. Verification

- [x] 5.1 `./gradlew check -Pkotlin.incremental=false` passes
- [x] 5.2 An ad-hoc-signed iOS Release build passes
- [x] 5.3 `xcodebuild test` passes on an iOS 26.0 and an iOS 18.4 simulator (the 18.4 run on a build made for it)
- [x] 5.4 Tick dates and times off in the roadmap's checklist and add this round's findings

## Workflow follow-up

- Archive the change and sync its specs (`dev/shared-display-text`, `ui/settings-layout`).
