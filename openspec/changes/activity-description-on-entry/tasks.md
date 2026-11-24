# Tasks

## 1. Contracts and presenters

- [ ] 1.1 Add `description: TextFieldState` (and `about` for Swift) to `ManualActivityState` and `RecordState`; the presenters own it and save it trimmed into the draft's description, blank as none; presenter tests for both; verify with `./gradlew :feature-record-presenter:jvmTest`

## 2. Compose

- [ ] 2.1 Split `ActivityDescriptionField` out of `ActivityDetailsForm` and place it under the title on manual entry and Record; samples and UI tests; verify with `./gradlew :feature-record-ui:jvmTest`
- [ ] 2.2 Refresh the Android screenshot references (`./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite`)

## 3. iOS

- [ ] 3.1 Split `ActivityDescriptionSection` out of `ActivityDetailsSections` and place it after the title section in `ManualActivityView` and `RecordView`; refresh the snapshot references on the iOS 26.0 simulator

## 4. Verify

- [ ] 4.1 `./gradlew check`
