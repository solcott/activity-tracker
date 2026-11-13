# Tasks

## 1. Dead dive code

- [x] 1.1 Confirm `DiveStats.kt`, `DiveSettingsStats.kt`, `DepthProfile.kt`, `DiveFormat.kt` and `LapTableUi.kt`'s `spokenRow` and `cell` have no callers (sources, tests, fixtures, screenshots, Swift)
- [x] 1.2 Delete them
- [x] 1.3 Delete the strings only they used and rerun `generateIosResources`

## 2. Verification

- [x] 2.1 `./gradlew check -Pkotlin.incremental=false` passes (Android screenshot tests included)
- [x] 2.2 An ad-hoc-signed iOS Release build passes
- [x] 2.3 `xcodebuild test` passes on an iOS 26.0 simulator (446/446)
- [x] 2.4 Tick the dive group off in the roadmap's checklist and add this round's findings
