# Tasks

## 1. Kotlin (no visible change)

- [ ] 1.1 `RecordTexts` and `RecordAction` in `:feature-record-api`; `RecordUi` asks them; the three new strings and the catalog. Verify a texts test (each status's controls, the figures in both units, names resolve) and `RecordUiTest` and the Android record screenshots unchanged.
- [ ] 1.2 `RecordSamples.variants` (noPermission, full, saving, failed). Verify `ContentLoadingConformanceTest` and the texts test over them.

## 2. Native

- [ ] 2.1 `outdoorOnly` on `ActivityTypeRow` and `ActivityTypeSheet`. Verify the record snapshots offer no indoor sub-sport.
- [ ] 2.2 `LocationAuthorization` and `RecordView` with aliases, samples, `#Preview`s, the registry entry and `ScreenRegistryTests`. Verify `RecordSnapshotTests` on iOS 26.0, each image looked at.
- [ ] 2.3 Swift Testing texts tests over `RecordTexts` (`RecordTextTests`). Verify they pass.

## 3. The whole change together

- [ ] 3.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [ ] 3.2 `xcodebuild test` on an iOS 26.0 clone and an iOS 18.4 clone (built for 18.4). Verify both pass (snapshots skipped on 18.4).
- [ ] 3.3 The Release configuration for the simulator, ad-hoc signed. Verify it builds and passes the stub check.

## 4. See it

- [ ] 4.1 On a simulator with a simulated route (`xcrun simctl location`): Start (granting location), Pause, Resume, and Discard; Finish only on an account whose activity can be deleted. Verify by noting what was seen; record what couldn't be seen under "Not verified".

## 5. Docs

- [ ] 5.1 The roadmap: a Phases bullet, "Phase 4 findings (record)", the open items (what still goes through Compose, what phase 5 needs). Verify `openspec validate ios-native-record --strict` passes.
- [ ] 5.2 `.agents/rules/compose-screens.md` where a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [ ] 5.3 A "Not verified" section in this file listing what wasn't seen and why.
