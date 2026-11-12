# Tasks

## 1. Kotlin (no visible change)

- [x] 1.1 `RecordTexts`, `RecordAction` and `RecordControl` in `:feature-record-api`; `RecordUi` asks them; the three new strings and the catalog. Verify a texts test (each status's controls, the figures in both units, names resolve) and `RecordUiTest` and the Android record screenshots unchanged.
- [x] 1.2 `RecordSamples.variants` (noPermission, full, saving, failed). Verify `ContentLoadingConformanceTest` and the texts test over them.

## 2. Native

- [x] 2.1 `outdoorOnly` on `ActivityTypeRow` and `ActivityTypeSheet`. Verify the record snapshots offer no indoor sub-sport.
- [x] 2.2 `LocationAuthorization` and `RecordView` with aliases, samples, `#Preview`s, the registry entry and `ScreenRegistryTests`. Verify `RecordSnapshotTests` on iOS 26.0, each image looked at.
- [x] 2.3 Swift Testing texts tests over `RecordTexts` (`RecordTextTests`). Verify they pass.

## 3. The whole change together

- [x] 3.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [x] 3.2 `xcodebuild test` on an iOS 26.0 clone and an iOS 18.4 clone (built for 18.4). Verify both pass (snapshots skipped on 18.4).
- [x] 3.3 The Release configuration for the simulator, ad-hoc signed. Verify it builds and passes the stub check.

## 4. See it

- [x] 4.1 On a simulator with a simulated route (`xcrun simctl location`): Start (granting location), Pause, Resume, and Discard; Finish only on an account whose activity can be deleted. Verify by noting what was seen; record what couldn't be seen under "Not verified".

## 5. Docs

- [x] 5.1 The roadmap: a Phases bullet, "Phase 4 findings (record)", the open items (what still goes through Compose, what phase 5 needs). Verify `openspec validate ios-native-record --strict` passes.
- [x] 5.2 `.agents/rules/compose-screens.md` where a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 5.3 A "Not verified" section in this file listing what wasn't seen and why.

## Walk

- Not done on a signed-in simulator. The 26.0 clone of the snapshot simulator (`7E4FE1CB`) opened this change's Debug build at sign-in:
  the clone holds no session, and signing in is not this session's to do. Cloning a simulator that another session signed in would
  copy its OAuth session, and a refresh from the copy would rotate the token the original holds, so none was cloned. Both clones
  (`7E4FE1CB`, `67DE0C58`) are deleted.

## Not verified

- **A recording on a device:** Start with a simulated route (`xcrun simctl location`), the system's location prompt and its answer
  reaching the presenter, the elapsed time and figures moving each second, Pause, Resume, Discard's question and Finish & save opening
  the saved activity. Each state was seen only in snapshots; `LocationAuthorization`'s prompt and Open Settings were not seen at all.
  Needs a simulator signed in on the local stack.
- **Returning from Settings with access allowed** (the note clearing on `scenePhase` active): not tried, for the same reason.
- **VoiceOver on the clock** (read as Time with its value), and iPad widths: snapshots are a phone's width only.

## Checks

- `./gradlew check -Pkotlin.incremental=false`: passed (Android record screenshots unchanged).
- `xcodebuild test` on an iOS 26.0 clone: 437 tests in 82 suites passed, the new references recorded, looked at and compared.
- `xcodebuild test` on an iOS 18.4 clone, built for it: 437 tests passed, snapshots skipped.
- Release for the simulator, ad-hoc signed: built, `checkSwiftExportStubs` passed.
