# Tasks

## 1. Kotlin rules (no visible change)

- [ ] 1.1 `GearFieldTexts` in `:app-common-api`; `GearPicker` resolves it. Verify a `GearFieldTextsTest`, `SharedTextNamesTest`, and the gear picker's UI tests and screenshots unchanged.
- [ ] 1.2 `SportTypeGroups.matching` and `subSportChoices`/`choiceLabel`; `ActivityTypePicker` asks them. Verify a `SportTypeGroupsTest` and the record UI tests and screenshots unchanged.
- [ ] 1.3 `ManualActivityTexts` and `ManualActivityState.durationEntered`, filled by the presenter. Verify `ManualActivityPresenterTest` asserts it follows typing, and the manual screenshots unchanged.
- [ ] 1.4 `EditActivityTexts.coreChartsBody`; `CreateDiveSiteTexts` and `CreateDiveSiteState.routeMap`; the Compose forms resolve them. Verify texts tests in the features' `-ui` tests (names resolve) and screenshots unchanged.
- [ ] 1.5 Fixture variants: edit (dive with site and photos, gear, leaderboard choice, saving, failed, each confirm), manual (gear, imperial, saving), create dive site (publishing, limit, failed). Verify the features' UI tests and `ContentLoadingConformanceTest` pass, and new screenshot references are looked at.

## 2. Native views

- [ ] 2.1 `ActivityFormSections.swift`: details sections, gear row and sheet, type row and sheet. Verify they compile and draw in previews.
- [ ] 2.2 `EditActivityView` with aliases, samples, `#Preview`s, the registry entry and `ScreenRegistryTests`. Verify `ActivityFormsSnapshotTests` on iOS 26.0, each image looked at.
- [ ] 2.3 `ManualActivityView`, the same. Verify its snapshots on iOS 26.0, each image looked at.
- [ ] 2.4 `CreateDiveSiteView`, the same. Verify its snapshots on iOS 26.0, each image looked at.
- [ ] 2.5 Swift Testing texts tests over the new Kotlin texts (`ActivityFormsTextTests`). Verify they pass.

## 3. The whole change together

- [ ] 3.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [ ] 3.2 `xcodebuild test` on an iOS 26.0 clone and an iOS 18.4 clone. Verify both pass (snapshots skipped on 18.4); delete the clones.
- [ ] 3.3 The Release configuration for the simulator. Verify it builds and passes the stub check.

## 4. See it

- [ ] 4.1 On a signed-in simulator on the local stack: manual entry (type sheet, units, gear, visibility, save), edit that activity (title, discard, save), and Create dive site if a dive is at hand; delete what was written. Verify by noting what was seen; record what couldn't be seen under "Not verified".

## 5. Docs

- [ ] 5.1 The roadmap: a Phases bullet, "Phase 4 findings (forms)", and the open items updated. Verify `openspec validate ios-native-forms --strict` passes.
- [ ] 5.2 `.agents/rules/compose-screens.md` where a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [ ] 5.3 A "Not verified" section in this file listing what wasn't seen and why.

## Workflow follow-up

- Archive with `/opsx:archive`, syncing the specs, once everything above is done and verified.
