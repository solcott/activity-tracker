## 1. Trim

- [x] 1.1 Confirm by name, across every Kotlin and Swift source set, fixtures and screenshot tests, which `ImportPreview` fields no UI reads
- [x] 1.2 Remove them and their preview types from `ImportActivityScreen.kt`, and their mapping from `ImportActivityPresenter.kt`
- [x] 1.3 Update `RecordSamples` and `ImportActivityTextsTest` for the smaller constructor
- [x] 1.4 Point the presenter tests that read the removed fields at the built page's figures

## 2. Verify

- [x] 2.1 `./gradlew check -Pkotlin.incremental=false`
- [x] 2.2 The ad-hoc Release simulator build
- [x] 2.3 `xcodebuild test` on iOS 26.0 and iOS 18.4 clones
- [x] 2.4 Record the findings in the roadmap and tick its note
