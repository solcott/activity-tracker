# Tasks

## 1. Dead dive code

- [ ] 1.1 Confirm `DiveStats.kt`, `DiveSettingsStats.kt`, `DepthProfile.kt`, `DiveFormat.kt` and `LapTableUi.kt`'s `spokenRow` and `cell` have no callers (sources, tests, fixtures, screenshots, Swift)
- [ ] 1.2 Delete them
- [ ] 1.3 Delete the strings only they used and rerun `generateIosResources`

## 2. Verification

- [ ] 2.1 `./gradlew check -Pkotlin.incremental=false` passes (Android screenshot tests included)
- [ ] 2.2 An ad-hoc-signed iOS Release build passes
- [ ] 2.3 `xcodebuild test` passes on an iOS 26.0 simulator
- [ ] 2.4 Tick the dive group off in the roadmap's checklist and add this round's findings
