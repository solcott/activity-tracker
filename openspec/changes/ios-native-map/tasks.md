# Tasks

## 1. Kotlin rules (no visible change)

- [ ] 1.1 `RouteMap`, `MapLine`, `MapBreak`, `MapBounds`, `MapFrame` in `:app-common-api`; `RouteShape`'s ends nullable (design: `RouteMap`). Verify `RouteMapTest` and `MapFrameTest`.
- [ ] 1.2 `ActivityMap.nonWeb`, `RoutePreview`, `RouteSnapshots`, `fitCamera` and `RouteMarkers` ask `RouteMap`; `RouteMarkersTest` folded into `RouteMapTest`. Verify `:app-common-ui:jvmTest` and the Android screenshot tests unchanged.
- [ ] 1.3 `PlaceTexts`, `PlaceNameTexts` (`:app-common-api`) and `DiveSiteTexts` (`:feature-divesite-api`); the Compose dive site page, names section, `PlaceStatus` and `CreatedBy` ask them. Verify their tests, `SharedTextNamesTest`, and the screenshot tests unchanged.
- [ ] 1.4 `DiveSiteState.viewerDid` and `revealed`, filled by the presenter. Verify `DiveSitePresenterTest`.
- [ ] 1.5 `DiveSiteSamples.variants` (renamed with proposals and history, duplicate, own proposal, failed vote). Verify `ContentLoadingConformanceTest`.

## 2. The native views

- [ ] 2.1 Type aliases and samples (appended blocks). Verify the app builds.
- [ ] 2.2 `RouteMapView` with `\.liveMaps` and the shared marker style; `RouteSketch` with optional ends; `MapCamera` math with a Swift test. Verify `RouteMapTests` and a preview.
- [ ] 2.3 `DiveSitePage` and `PlaceNamesSection`, `.placeNames(state.names)`, `ActivityCardView(likes:)`. Verify their previews and `DiveSiteSnapshotTests`, light and dark, each image looked at.
- [ ] 2.4 The registry entry. Verify `ScreenRegistryTests` names `DiveSiteScreen` as native.

## 3. The whole change together

- [ ] 3.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [ ] 3.2 The Swift tests on clones of the iOS 26.0 and 18.4 snapshot simulators. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [ ] 3.3 The Release configuration for the simulator. Verify it builds and passes the stub check.

## 4. See it

- [ ] 4.1 On the signed-in iPad Air 11-inch `3F116204`, as alice on the local stack: a dive site page with its live map (tiles, outline, static drag, light and dark), its names and sheet, Regulars, a dive card; nothing written or anything written put back. Verify by noting what was seen; record what the stack lacks under "Not verified".

## 5. Docs

- [ ] 5.1 The roadmap: the stack table's maps row, a Phases item 4 bullet, "Phase 4 findings (map)", the open items. Verify `openspec validate ios-native-map --strict` passes.
- [ ] 5.2 `.agents/rules/compose-screens.md` and `adaptive-ui.md` where a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [ ] 5.3 A "Not verified" section listing what wasn't seen and why.
