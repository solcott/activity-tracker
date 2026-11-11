# Tasks

## 1. Probe (throwaway code, not merged)

- [x] 1.1 A throwaway Swift test on a fresh iOS 26.0 iPhone 17 simulator over the zones, sport zones, privacy zones and threshold history samples (design decision 1): what Swift gets for `ZoneSummary.kinds`, `SportZonesState.kinds`, each zone list's elements, `PrivacyZonesState.zones`' elements, and `shownRows` collapsed and with every row shown. Verify the run prints what each read, and whether the all-rows read stops the app.
- [x] 1.2 Remove the throwaway test. Verify `git status` shows only the change's own files.
- [x] 1.3 Write the results into `openspec/explorations/ios-native-ui.md` under "Phase 4 probes (zones contracts)". Verify it records each result.

## 2. Kotlin contracts (no visible change)

- [x] 2.1 `ZoneKindOption`, `ZoneKindOption.forSport`, and `kinds: ImmutableList<ZoneKindOption>` with `offers(kind)` on `ZoneSummary` and `SportZonesState`; the two presenters, the samples (same kinds as today) and the shared UIs follow (decision 2). Verify a `commonTest` in `:feature-settings-api` (the order, `offers`), the presenter tests (`:feature-settings-presenter:jvmTest`) and `:feature-settings-ui:jvmTest` pass.
- [x] 2.2 `ZoneTexts.label` and `privacyZoneName`; `ZonesUi`'s and `PrivacyZonesUi`'s rows ask them (decision 3); the two names join `SharedTextNamesTest` if it lists closed sets they belong to. Verify a `commonTest` for a label, a labelled and an unlabelled privacy zone, and `:app-common-ui:jvmTest`.
- [x] 2.3 `ThresholdHistorySection.shownRows` returns a copy when every row shows (decision 4). Verify `ThresholdHistoryPresenterTest` passes.
- [x] 2.4 Verify no visible change: `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite` passes with no reference changed.

## 3. Swift tests

- [x] 3.1 Type aliases, `Samples.sportZones`, and `ZonesContractsTests` (decision 5). Verify they pass on the iOS 26.0 iPhone 17 simulator, reading each blocker's field typed, and the all-rows read no longer stops the app.

## 4. The whole change together

- [x] 4.1 Run `./gradlew format`, `./gradlew check` and `./gradlew -p build-logic :convention:test`. Verify all pass.
- [x] 4.2 Run every Swift test (`xcodebuild test`) on the iOS 26.0 iPhone 17 simulator. Verify they pass, `:iosExport:checkSwiftExportStubs` passing in the build, and the stub report lists nothing new in a checked module (`PrivacyZone.Companion.serializer` joins `:core-lexicon`'s unchecked, allowed serializers).

## 5. Docs

- [x] 5.1 The roadmap's "Phase 4 findings (zones contracts)", and its open items: the three blockers struck or updated. Verify `openspec validate ios-zones-contracts --strict` passes.
- [x] 5.2 `.agents/rules/compose-screens.md`: the zone kinds as the second enum-option example, `ZoneTexts` in the text functions' list, and the `List` copy rule's example. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 5.3 A "Not verified" section listing what wasn't seen and why. Verify it names each.

## Probe results (1.1)

Before the fix, on a fresh iPhone 17 simulator (iOS 26.0): both kinds sets, both heart rate zone lists and the privacy zones read as opaque `_KotlinExistential` elements, and the export had no Swift type for `ZoneKind`, `Zone` or `PrivacyZone`. `shownRows` read as a typed array collapsed and with all five rows of `ThresholdHistorySamples.variants["allRows"]` shown; only a section with no rows and every row shown stopped the app (`-[_KotlinExistential count]: unrecognized selector`). Recorded in the roadmap under "Phase 4 probes (zones contracts)".

## Not verified

- **The convention suite's first run.** `IosResourcesTest` "a generated file edited by hand fails the check" failed once while `check` had just run (`UnexpectedBuildSuccess`), then passed alone and in a full `--rerun` (107 tests). This change doesn't touch `build-logic`; it looks like a timing flake in that test.

- **On a device or a signed-in simulator.** Nothing native reads these contracts yet, so there is nothing to walk: the shared pages draw them on iOS as before, and the Swift tests are the only Swift reader. The signed-in iPhone 17 Pro and iPad Air belong to other sessions and weren't touched.
- **On iOS 18.4.** The Swift tests ran on iOS 26.0 only; nothing in this change differs by OS version.
- **The Release configuration.** Not built: it exports no `:app-fixtures`, and the contracts' generated Swift is the same in both.
- **The empty-rows case through the fixed getter.** Swift can't build a section, and a section never has no rows; the Kotlin test holds that the getter returns a copy for an empty list too.
