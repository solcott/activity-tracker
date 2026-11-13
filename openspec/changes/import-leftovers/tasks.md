# Tasks

## 1. The import clock's start

- [ ] 1.1 Add `DateText.of(dateTime: LocalDateTime)` with tests (12- and 24-hour)
- [ ] 1.2 `ImportClock.localStart` becomes a `LocalDateTime`; `ImportActivityTexts.zoneNotice` writes it with the new function; `LOCAL_START` goes
- [ ] 1.3 Update `RecordSamples`, `ImportActivityTextsTest`, the presenter tests and the Swift `ImportTextTests`
- [ ] 1.4 Rerecord the iOS 26.0 snapshots and Android references whose zone notice changed, and look at each

## 2. Dead import preview state

- [ ] 2.1 Confirm by name across every Kotlin and Swift source set, the fixtures and the screenshot tests what of `ImportPreview.dive` and `GearTimePreview` is read
- [ ] 2.2 Delete `ImportPreview.dive`, `DivePreview` and its parts, `DepthPreviewPoint`, `ShiftingPreview.gears` and `GearTimePreview`, with the presenter code, samples and tests that only they need

## 3. Import's wide iPad snapshot

- [ ] 3.1 Reproduce the `wide` failure alone and find its cause
- [ ] 3.2 Fix it without raising the tolerance or dropping the sample; `ImportSnapshotTests` passes 5 runs in a row alone

## 4. Verification

- [ ] 4.1 `./gradlew check -Pkotlin.incremental=false` passes
- [ ] 4.2 An ad-hoc-signed iOS Release build passes
- [ ] 4.3 `xcodebuild test` passes on an iOS 26.0 and an iOS 18.4 simulator
- [ ] 4.4 Record the findings in the roadmap and tick its open items
