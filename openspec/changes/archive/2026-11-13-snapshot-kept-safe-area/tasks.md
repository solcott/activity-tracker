# Tasks

## 1. The kept safe area everywhere

- [x] 1.1 `assertAppSnapshot` always draws through `KeptSafeArea`, with no safe-area regions for the view's own content; drop `keepsSafeArea` and Import's argument
- [x] 1.2 Rerecord on iOS 26.0 every reference that no longer matches
- [x] 1.3 Look at each changed reference: only the safe-area layout differs

## 2. Verification

- [x] 2.1 `./gradlew check -Pkotlin.incremental=false` passes
- [x] 2.2 An ad-hoc-signed iOS Release build passes
- [x] 2.3 `xcodebuild test` passes on iOS 26.0 with every snapshot matching, and on iOS 18.4 with snapshots skipped
- [x] 2.4 Record the findings in the roadmap and tick its open item
