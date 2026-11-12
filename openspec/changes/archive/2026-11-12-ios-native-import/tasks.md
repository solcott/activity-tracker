# Tasks

## 1. Kotlin rules (no visible change)

- [x] 1.1 `ImportActivityTexts` in `:feature-record-api` (picking, default titles, thresholds, duplicates, rows, batch summary, filters, Save, comparison, saved and after-import panels, zones and their search, new devices) with the choice classes, `ImportActivityState.canSave`, `canSaveBatch`, `saveEvent` and `saveBatchEvent`; `RecordingTexts.suggestedActivity`. Verify an `ImportActivityTextsTest` in the API module.
- [x] 1.2 The Compose import screen resolves them. Verify the record UI tests, a texts-names test in `:feature-record-ui` and the import screenshots unchanged.
- [x] 1.3 `IosApp.importSources(urls)`. Verify it compiles for iOS and passes the stub check.
- [x] 1.4 Fixture variants: a built page, a dive's zone, a single duplicate, a recording added (and too far), thresholds, a failed save, an opened row. Verify the record UI tests and `ContentLoadingConformanceTest` pass.

## 2. Native view

- [x] 2.1 `ImportActivityView` and its pieces (picking, preview, form, batch, row sheet, zone sheet, new devices, saved and after-import panels) with aliases, samples, `#Preview`s, the registry entry and `ScreenRegistryTests`. Verify it compiles and draws in previews.
- [x] 2.2 `ImportSnapshotTests` on iOS 26.0, light and dark, phone and iPad. Verify each image looked at.
- [x] 2.3 Swift Testing texts tests over the new Kotlin texts (`ImportTextTests`). Verify they pass.

## 3. The whole change together

- [x] 3.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [x] 3.2 `xcodebuild test` on an iOS 26.0 clone and an iOS 18.4 clone. Verify both pass (snapshots skipped on 18.4); delete the clones.
- [x] 3.3 The Release configuration for the simulator. Verify it builds and passes the stub check.

## 4. See it

- [x] 4.1 On a simulator on the local stack: the import screen opens natively and the document picker shows. Verify by noting what was seen; record what couldn't be seen under "Not verified". (Not seen: see below.)

## 5. Docs

- [x] 5.1 The roadmap: a Phases bullet, "Phase 4 findings (import)", and the open item listing the activity page's shared screens updated. Verify `openspec validate ios-native-import --strict` passes.
- [x] 5.2 `.agents/rules/compose-screens.md` where a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 5.3 A "Not verified" section in this file listing what wasn't seen and why.

## Not verified

- **The screen on a signed-in device.** The two simulators signed in on the local stack (the iPhone 17 Pro and iPad Air booted on
  iOS 26.0) were in use by the parallel Create segment session, and this session's clones aren't signed in, nor may it sign one in. So
  opening Import from the feed and Add a recording from the activity page, the document picker itself, picking one file and several
  (and an archive), Cancel while reading, a time zone picked from the sheet, a duplicate's choice, the batch's tick, filters, Select
  all and opened row, Save and Save batch, Retry after a failed log search, Dismiss on a stopped import, and the new devices' answers
  weren't seen live. The snapshots draw every sample state (phone light and dark, an iPad with the preview beside the form, the opened
  row, the zone sheet), and `ImportTextTests` reads the texts, choices and Save events on the samples.
- **Security-scoped files from Files and iCloud Drive.** `IosApp.importSources` relies on FileKit's `PlatformFile` opening each URL's
  scope while reading, as it does for the shared picker's; a batch save resumed after the app relaunched (bookmarks) wasn't tried.

## Checks

- `./gradlew check -Pkotlin.incremental=false`: passed (after fixing detekt's and the stability check's findings in the new code).
- `xcodebuild test` on an iOS 26.0 clone: 423 tests passed, the new snapshots compared after recording and being looked at. A first
  recording at 393 × 3,200 points drew blank pages (over 8,192 pixels); they were recorded again at 2,700.
- `xcodebuild test` on an iOS 18.4 clone, built for it: 423 tests passed, snapshots skipped.
- Release for the simulator, ad hoc signed: built, the stub check passed.
- The clones (`707EB0E7`, `26FF97CB`) are deleted.
