# Tasks

## 1. Kotlin rules (no visible change)

- [ ] 1.1 `ImportActivityTexts` in `:feature-record-api` (picking, default titles, thresholds, duplicates, rows, batch summary, filters, Save, comparison, saved and after-import panels, zones and their search, new devices) with the choice classes, `ImportActivityState.canSave`, `canSaveBatch`, `saveEvent` and `saveBatchEvent`; `RecordingTexts.suggestedActivity`. Verify an `ImportActivityTextsTest` in the API module.
- [ ] 1.2 The Compose import screen resolves them. Verify the record UI tests, a texts-names test in `:feature-record-ui` and the import screenshots unchanged.
- [ ] 1.3 `IosApp.importSources(urls)`. Verify it compiles for iOS and passes the stub check.
- [ ] 1.4 Fixture variants: a built page, a dive's zone, a single duplicate, a recording added (and too far), thresholds, a failed save, an opened row. Verify the record UI tests and `ContentLoadingConformanceTest` pass.

## 2. Native view

- [ ] 2.1 `ImportActivityView` and its pieces (picking, preview, form, batch, row sheet, zone sheet, new devices, saved and after-import panels) with aliases, samples, `#Preview`s, the registry entry and `ScreenRegistryTests`. Verify it compiles and draws in previews.
- [ ] 2.2 `ImportSnapshotTests` on iOS 26.0, light and dark, phone and iPad. Verify each image looked at.
- [ ] 2.3 Swift Testing texts tests over the new Kotlin texts (`ImportTextTests`). Verify they pass.

## 3. The whole change together

- [ ] 3.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [ ] 3.2 `xcodebuild test` on an iOS 26.0 clone and an iOS 18.4 clone. Verify both pass (snapshots skipped on 18.4); delete the clones.
- [ ] 3.3 The Release configuration for the simulator. Verify it builds and passes the stub check.

## 4. See it

- [ ] 4.1 On a simulator on the local stack: the import screen opens natively and the document picker shows. Verify by noting what was seen; record what couldn't be seen under "Not verified".

## 5. Docs

- [ ] 5.1 The roadmap: a Phases bullet, "Phase 4 findings (import)", and the open item listing the activity page's shared screens updated. Verify `openspec validate ios-native-import --strict` passes.
- [ ] 5.2 `.agents/rules/compose-screens.md` where a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [ ] 5.3 A "Not verified" section in this file listing what wasn't seen and why.
