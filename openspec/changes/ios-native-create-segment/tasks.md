# Tasks

## 1. Kotlin (no visible change but the corrected sample)

- [ ] 1.1 `RouteMap.of(highlight:)`, `RouteMap.highlight` and `RouteShape.highlight`, kept out of `key`. Verify `RouteMapTest` covers the highlight, its sketch and the unchanged key.
- [ ] 1.2 `CreateSegmentTexts` and `CreateSegmentState.routeMap`; the Compose form resolves them. Verify a texts test (names resolve, the lines for each check) and the create-segment UI and screenshots.
- [ ] 1.3 The loaded sample's stretch (480 m to 1,440 m, highlighted) and `CreateSegmentSamples.variants`. Verify `ContentLoadingConformanceTest`, and re-recorded Android references looked at.

## 2. Native

- [ ] 2.1 The route map's highlight, live and in the sketch. Verify a snapshot of the form's map shows it.
- [ ] 2.2 `RangeSlider` in `Design/`, in the design gallery. Verify a snapshot and a preview.
- [ ] 2.3 `CreateSegmentView` with aliases, samples, `#Preview`s, the registry entry and `ScreenRegistryTests`. Verify `CreateSegmentSnapshotTests` on iOS 26.0, each image looked at.
- [ ] 2.4 Swift Testing texts tests over `CreateSegmentTexts` (`CreateSegmentTextTests`). Verify they pass.

## 3. The whole change together

- [ ] 3.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [ ] 3.2 `xcodebuild test` on an iOS 26.0 clone and an iOS 18.4 clone (built for 18.4). Verify both pass (snapshots skipped on 18.4); delete the clones.
- [ ] 3.3 The Release configuration for the simulator, ad-hoc signed. Verify it builds and passes the stub check.

## 4. See it

- [ ] 4.1 On a signed-in simulator on the local stack, if one is at hand: open Create segment from an own activity with a route, drag the stretch, type a name; publish only if it can be deleted again. Verify by noting what was seen; record what couldn't be seen under "Not verified".

## 5. Docs

- [ ] 5.1 The roadmap: a Phases bullet, "Phase 4 findings (create segment)", and the activity page's open item updated. Verify `openspec validate ios-native-create-segment --strict` passes.
- [ ] 5.2 `.agents/rules/compose-screens.md` where a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [ ] 5.3 A "Not verified" section in this file listing what wasn't seen and why.
