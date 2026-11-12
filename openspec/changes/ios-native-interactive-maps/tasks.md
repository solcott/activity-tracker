# Tasks

## 1. Kotlin rules (no visible change)

- [x] 1.1 `SegmentCreditTexts` and `SegmentMapContent.routeMap` (`:feature-segment-api`, presenter); `Credit` and `SegmentMapUi` ask them. Verify their tests and the screenshot tests unchanged.
- [x] 1.2 `SegmentsMap` (drawing order, `nearest`, `key`) and `MapViewport.ofDegrees` in `:app-common-api`; `SegmentsMap.nonWeb` and `SegmentsPreview` ask them. Verify `SegmentsMapTest`.
- [x] 1.3 `SegmentBrowseTexts` and `SegmentBrowseState`'s `listShows`, `offersScope`, `offersSearchEverywhere`, typed getters, `sports`, `segmentsMap`; `SegmentBrowseUi` asks them. Verify their tests, `SharedTextNamesTest` and the screenshot tests unchanged.
- [x] 1.4 `AcceptInviteTexts` and `AcceptInviteState`'s `busy`, `invite`, `routeMap`; `AcceptInviteUi` asks them. Verify their tests.
- [x] 1.5 `HostRecord.embed` with its navigator, run inside `RunRecord`. Verify host tests and a real-presenter test through the host.
- [x] 1.6 `SegmentMapSamples` and any missing browse and invite samples. Verify `ContentLoadingConformanceTest`.

## 2. The native views

- [x] 2.1 Type aliases and samples (appended blocks). Verify the app builds.
- [x] 2.2 `RouteMapView(height: nil)` and `SegmentMapView`. Verify its previews and `SegmentMapSnapshotTests`, light and dark, each image looked at.
- [x] 2.3 `SegmentsMapView` and `SegmentBrowseView`, phone and iPad layouts. Verify previews, `SegmentBrowseSnapshotTests` and a Swift test of the map's conversions.
- [x] 2.4 `EmbeddedScreenView`; the Search tab's Segments mode shows the native browse; the Compose embed and its entry point removed. Verify the app builds and the stub check passes.
- [x] 2.5 `VisibilityFormSections` (from Activity defaults) and `AcceptInviteView`. Verify Activity defaults' snapshots unchanged and `AcceptInviteSnapshotTests`.
- [x] 2.6 The registry entries. Verify `ScreenRegistryTests` names them native.

## 3. The whole change together

- [x] 3.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [ ] 3.2 The Swift tests on clones of the iOS 26.0 and 18.4 snapshot simulators. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [ ] 3.3 The Release configuration for the simulator. Verify it builds and passes the stub check.

## 4. See it

- [ ] 4.1 On the signed-in iPad Air `3F116204`, as alice on the local stack: Search's Segments mode (map, list, selection, a name search, opening a segment and back), a segment's enlarged map (pan, zoom, Recenter, credit), and Accept invite if the stack has a pending invite; nothing written or anything written put back. Verify by noting what was seen; record what the stack lacks under "Not verified".

## 5. Docs

- [ ] 5.1 The roadmap: a Phases item 4 bullet, "Phase 4 findings (interactive maps)", the open items. Verify `openspec validate ios-native-interactive-maps --strict` passes.
- [x] 5.2 `.agents/rules/compose-screens.md` and `adaptive-ui.md` where a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [ ] 5.3 A "Not verified" section listing what wasn't seen and why.
