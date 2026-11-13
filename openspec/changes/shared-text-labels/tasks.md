# Tasks

## 1. Shared labels

- [ ] 1.1 `LabelText.shownName`, `expanded` and `save`; `ImportActivityTexts.save`, `ProfileTexts.name`, `ActivityPageTexts.reportName` and `ModerationSubjectTexts` use them
- [ ] 1.2 `GearListTexts.section`, `VisibilityTexts.activityNote`, `CommentsTexts.confirmation` (`CommentConfirmation`)
- [ ] 1.3 `TopLevelDestination.title`
- [ ] 1.4 Picker choices: `SegmentBrowseTexts.sportChoice`, `ImportActivityTexts.deviceKindChoice`/`deviceMountChoice`/`profileLinkChoice`, `ZoneTexts.modelChoice`
- [ ] 1.5 Export steps: `ExportControl`/`ExportAction`, `AccountTexts.exportControls`, `LeaveTexts.exportLine`/`exportControls`; `SettingsTexts.accountSummary`
- [ ] 1.6 Kotlin tests for each rule, and the text-name tests for new names

## 2. Compose

- [ ] 2.1 `PageCardsUi`, `ShownNameLabel`, `SportTypeList`, `GearListUi`, `PostPageUi`, the five forms' Save, the shell's tabs, `ActivityVisibilityForm`, `CommentMenuUi`, `NewDevicesForm`, `SegmentBrowseUi`, `LeaveUi`, `AccountUi`, `SettingsUi` ask the shared texts

## 3. Swift

- [ ] 3.1 `CommentsSection`, `GearListView`, the five forms' Save, `SessionView`, the five visibility sections, `SegmentBrowseView`, `ImportBatchSections`, `ZoneRows`, `LeaveView`, `AccountView`, `SettingsView` ask the shared texts
- [ ] 3.2 Swift text tests for the new functions

## 4. Pictures

- [ ] 4.1 `:androidApp:testDebugScreenshotTestDefaultTestSuite` passes with no reference changed
- [ ] 4.2 Rerecord the iOS 26.0 snapshots whose text changed (a form's Save while saving), and look at them

## 5. Verification

- [ ] 5.1 `./gradlew check -Pkotlin.incremental=false` passes
- [ ] 5.2 An ad-hoc-signed iOS Release build passes
- [ ] 5.3 `xcodebuild test` passes on an iOS 26.0 and an iOS 18.4 simulator (the 18.4 run on a build made for it)
- [ ] 5.4 Tick feature labels off in the roadmap's checklist and add this round's findings
