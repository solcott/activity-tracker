# Tasks

## 1. Shared labels

- [x] 1.1 `LabelText.shownName`, `expanded` and `save`; `ImportActivityTexts.save`, `ProfileTexts.name`, `ActivityPageTexts.reportName` and `ModerationSubjectTexts` use them
- [x] 1.2 `GearListTexts.section`, `VisibilityTexts.activityNote`, `CommentsTexts.confirmation` (`CommentConfirmation`)
- [x] 1.3 `TopLevelDestination.title`
- [x] 1.4 Picker choices: `SegmentBrowseTexts.sportChoice`, `ImportActivityTexts.deviceKindChoice`/`deviceMountChoice`/`profileLinkChoice`, `ZoneTexts.modelChoice`
- [x] 1.5 Export steps: `ExportControl`/`ExportAction`, `AccountTexts.exportControls`, `LeaveTexts.exportLine`/`exportControls`; `SettingsTexts.accountSummary`; also Leave's `runNote` and Account's `didCopy`, which the item's line numbers named (the Account presenter's private `ExportAction` flight key is now `ExportFlight`, since it hid the new enum from Swift export)
- [x] 1.6 Kotlin tests for each rule, and the text-name tests for new names

## 2. Compose

- [x] 2.1 `PageCardsUi`, `ShownNameLabel`, `SportTypeList`, `GearListUi`, `PostPageUi`, the five forms' Save, the shell's tabs, `ActivityVisibilityForm`, `CommentMenuUi`, `NewDevicesForm`, `SegmentBrowseUi`, `LeaveUi`, `AccountUi`, `SettingsUi` ask the shared texts

## 3. Swift

- [x] 3.1 `CommentsSection`, `GearListView`, the five forms' Save, `SessionView`, the five visibility sections, `SegmentBrowseView`, `ImportBatchSections`, `ZoneRows`, `LeaveView`, `AccountView`, `SettingsView` ask the shared texts
- [x] 3.2 Swift text tests for the new functions

## 4. Pictures

- [x] 4.1 `:androidApp:testDebugScreenshotTestDefaultTestSuite` passes with no reference changed
- [x] 4.2 Rerecord the iOS 26.0 snapshots whose text changed (a form's Save while saving), and look at them: the five forms' `saving` samples, light and dark

## 5. Verification

- [x] 5.1 `./gradlew check -Pkotlin.incremental=false` passes
- [x] 5.2 An ad-hoc-signed iOS Release build passes
- [x] 5.3 `xcodebuild test` passes on an iOS 26.0 and an iOS 18.4 simulator (the 18.4 run on a build made for it): 454/454 on each
- [x] 5.4 Tick feature labels off in the roadmap's checklist and add this round's findings
