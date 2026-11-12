# Tasks

## 1. Kotlin rules (no visible change)

- [x] 1.1 `RouteMap`, `MapLine`, `MapBreak`, `MapBounds`, `MapFrame` in `:app-common-api`; `RouteShape`'s ends nullable (design: `RouteMap`). Verify `RouteMapTest` and `MapFrameTest`.
- [x] 1.2 `ActivityMap.nonWeb`, `RoutePreview`, `RouteSnapshots`, `fitCamera` and `RouteMarkers` ask `RouteMap`; `RouteMarkersTest` folded into `RouteMapTest`. Verify `:app-common-ui:jvmTest` and the Android screenshot tests unchanged.
- [x] 1.3 `PlaceTexts`, `PlaceNameTexts` (`:app-common-api`) and `DiveSiteTexts` (`:feature-divesite-api`); the Compose dive site page, names section, `PlaceStatus` and `CreatedBy` ask them. Verify their tests, `SharedTextNamesTest`, and the screenshot tests unchanged.
- [x] 1.4 `DiveSiteState.viewerDid` and `revealed`, filled by the presenter. Verify `DiveSitePresenterTest`.
- [x] 1.5 `DiveSiteSamples.variants` (renamed with proposals and history, duplicate, own proposal, failed vote). Verify `ContentLoadingConformanceTest`.

## 2. The native views

- [x] 2.1 Type aliases and samples (appended blocks). Verify the app builds.
- [x] 2.2 `RouteMapView` with `\.liveMaps` and the shared marker style; `RouteSketch` with optional ends; `MapCamera` math with a Swift test. Verify `RouteMapTests` and a preview.
- [x] 2.3 `DiveSitePage` and `PlaceNamesSection`, `.placeNames(state.names)`, `ActivityCardView(likes:)`. Verify their previews and `DiveSiteSnapshotTests`, light and dark, each image looked at.
- [x] 2.4 The registry entry. Verify `ScreenRegistryTests` names `DiveSiteScreen` as native.

## 3. The whole change together

- [x] 3.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [x] 3.2 The Swift tests on clones of the iOS 26.0 and 18.4 snapshot simulators. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [x] 3.3 The Release configuration for the simulator. Verify it builds and passes the stub check.

## 4. See it

- [x] 4.1 On the signed-in iPad Air 11-inch `3F116204`, as alice on the local stack: a dive site page with its live map (tiles, outline, static drag, light and dark), its names and sheet, Regulars, a dive card; nothing written or anything written put back. Verify by noting what was seen; record what the stack lacks under "Not verified".

## 5. Docs

- [x] 5.1 The roadmap: the stack table's maps row, a Phases item 4 bullet, "Phase 4 findings (map)", the open items. Verify `openspec validate ios-native-map --strict` passes.
- [x] 5.2 `.agents/rules/compose-screens.md` and `adaptive-ui.md` where a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 5.3 A "Not verified" section listing what wasn't seen and why.

## Walk (iPad Air 11-inch `3F116204-14F9-4645-9E88-D02627E2ACC6`, iOS 26.0, signed in as alice on the local stack; this change's Debug build)

- **4.1.** Search (shared) found diver1; the native profile listed the dives; a dive's page (shared) linked Blue Hole, which opened the
  native dive site page: the live MapKit map with Apple's tiles (coast and water), the site's outline framed in the middle with the
  shared margin, no start or end markers, and Apple Maps' credit with Legal in its corner; "Within 328 ft of its centre" in alice's
  imperial units, "By @diver1.test", "Data licence: CC0-1.0"; the Regulars card (diver1, 5 dives); the conditions in °F and ft from 6
  dives (one month, so no month lines); "Proposed names" with diver1's "Bells to Blue Hole" and its totals, and no votes or Propose,
  since alice has no visit there; then the dives as native cards with no Like and no author link. A drag that started on the map
  scrolled the page, and the map stayed where it was. The Regulars card opened the native board (diver1 5, diver2 1) and back
  returned. Dark mode turned the tiles, the line and the page dark at once. A dive's card opened that dive's page.
- **Put back:** nothing was written. The appearance was set back to light and the Feed tab chosen; the Search tab's stack keeps the
  pages walked, as session state.
- **iOS 18.4:** the Swift tests ran on `Map iPhone 16 Pro 18.4` (FB872883, cloned from 958C0DC3; snapshots skipped); not walked signed
  in, which iOS 18 can't be (roadmap open items).
- **Simulators:** the iOS 26.0 suite ran on `Map iPhone 17` (C2BD5960), created as an iPhone 17 on iOS 26.0 since `D6328F65` was
  booted by another session and a booted device can't be cloned.

## Not verified

- Voting, Propose, Edit and Delete on a device, the name sheet over the page, a failed vote, and Name history: alice has no visit to a
  site with open proposals of others, no proposal of her own and no adopted names on the stack's sites. The snapshots draw each, and
  the sheet itself was verified natively in `ios-native-report-sheet`.
- A proposed, duplicate or archived site, OpenStreetMap's credit link and month-by-month conditions on a device: the walked site is an
  athlete's active one with dives in one month. The snapshots draw them.
- The expand button and interactive mode with Recenter: no native page uses them yet (the enlarged maps and segment browse are still
  shared); the stand-in's expand button is in a snapshot.
- An iPhone, iOS 18 signed in, VoiceOver and the largest text sizes.
- The local stack's keys: `scripts/dev.sh` from this worktree generated new AppView and Ozone signing keys and recreated the PDS (this
  worktree's `docker/.env` lacked them). The AppView came back healthy and syncing; other worktrees' sessions may want to rerun
  `scripts/dev.sh` from their checkout.
