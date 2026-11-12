# Tasks

## 1. Kotlin rules (no visible change beyond a grouped count)

- [ ] 1.1 `AthleteSearchTexts` in `:feature-search-api` and `LeftElsewhereTexts` in `:feature-settings-api` (decision 1). Verify their tests.
- [ ] 1.2 `AthleteSearchUi` and `LeftElsewhereUi` ask them. Verify both modules' `jvmTest` and the Android screenshot tests unchanged.
- [ ] 1.3 Sample variants where missing (Segments mode, a failed search). Verify `ContentLoadingConformanceTest`.
- [ ] 1.4 `SegmentBrowseViewController(record)` and `IosApp.segmentBrowseViewController` (decision 5). Verify the iOS build and the stub check.

## 2. The native views

- [ ] 2.1 Type aliases and samples (appended blocks). Verify a text test reads every sample's texts.
- [ ] 2.2 `AthleteSearchView` and `SegmentBrowseEmbed` (decisions 2 to 5). Verify their previews and `SearchSnapshotTests`, light and dark, each image looked at.
- [ ] 2.3 `LeftElsewhereView` and the stack in `SessionView` (decision 6). Verify its previews and `LeftElsewhereSnapshotTests`, each image looked at.
- [ ] 2.4 The registry entries (decision 7). Verify `ScreenRegistryTests` names both screens as native.

## 3. The whole change together

- [ ] 3.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [ ] 3.2 The Swift tests on the iOS 26.0 and 18.4 snapshot simulators. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [ ] 3.3 The Release configuration for the simulator. Verify it builds and passes the stub check.

## 4. See it

- [ ] 4.1 On the signed-in iPhone 17 Pro `E426A46D`, as alice on the local stack: the Search tab's title, the prompt, a search, no match, a profile opened and back, Segments with the browse screen (its field, its map, a segment opened and back), the mode kept across tabs. Verify by noting what was seen; record what the stack can't show under "Not verified".

## 5. Docs

- [ ] 5.1 The roadmap: a phase 4 bullet, "Phase 4 findings (search)", the open items (left elsewhere closed; native segment browse; the leave in progress without a stack). Verify `openspec validate ios-native-search --strict`.
- [ ] 5.2 `.agents/rules/compose-screens.md` and `adaptive-ui.md` where a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh`.
- [ ] 5.3 A "Not verified" section listing what wasn't seen and why.
