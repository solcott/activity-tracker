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
- [x] 3.2 The Swift tests on clones of the iOS 26.0 and 18.4 snapshot simulators. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [x] 3.3 The Release configuration for the simulator. Verify it builds and passes the stub check.

## 4. See it

- [x] 4.1 On the signed-in iPad Air `3F116204`, as alice on the local stack: Search's Segments mode (map, list, selection, a name search, opening a segment and back), a segment's enlarged map (pan, zoom, Recenter, credit), and Accept invite if the stack has a pending invite; nothing written or anything written put back. Verify by noting what was seen; record what the stack lacks under "Not verified".

## 5. Docs

- [x] 5.1 The roadmap: a Phases item 4 bullet, "Phase 4 findings (interactive maps)", the open items. Verify `openspec validate ios-native-interactive-maps --strict` passes.
- [x] 5.2 `.agents/rules/compose-screens.md` and `adaptive-ui.md` where a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 5.3 A "Not verified" section listing what wasn't seen and why.

## Walk (iPad Air 11-inch `3F116204-14F9-4645-9E88-D02627E2ACC6`, iOS 26.0, signed in as alice on the local stack; this change's Debug build)

- **4.1.** Search's Segments mode drew native segment browse under the mode choice: on the iPad the list column (the name field, the
  sport menu chip) beside the live MapKit map, Apple's tiles and credit with Legal in its corner, opened at the simulator's location
  after the system's location prompt, which was answered Allow Once. The area's load failed, and the list said
  "Couldn't load the segments here" with Retry. A drag panned the map; switching to Athletes and back to Segments reopened it where it was
  left. Typing a name showed This area and Everywhere over the sport chip, and its own failure ("Couldn't search segments") with Retry.
  Dark mode turned the tiles and page dark. The search field's label isn't clipped on the device (the iPad snapshot's offset is the test
  host's landscape safe area).
- **Fixed after the walk:** the field capitalized its first letter; it now takes names as typed (no capitals or corrections), as the
  athlete search does. Seen only in code and the build: the simulator stopped taking taps on the field after the reinstall.
- **Put back:** the field was cleared, the appearance set back to light and the Feed tab chosen. Nothing was written.
- **Simulators:** the Swift tests ran on `Interactive Maps iPhone 17` (iOS 26.0, `4B43CC7E`, cloned from `D6328F65`) and `Interactive
  Maps iPhone 16 Pro 18.4` (`01098C2A`, cloned from `958C0DC3`; snapshots skipped).

## Not verified

- **Anything the AppView serves, on a device.** The local stack runs in device mode, so the app reaches the AppView through the named
  tunnel, which was down, and starting it wasn't permitted in this session. So the feed and every segment query failed: the area's runs
  and lifts on the live map, selecting a run by tapping its line (and the list marking and scrolling to it), opening a segment from the
  list and coming back, name results, Search everywhere and the sport filter's effect were not seen. The snapshots draw each list and
  the stand-in map; `SegmentsMapTest` and `InteractiveMapsTextTests` hold the tap rule and the viewport conversion;
  `SegmentBrowseThroughHostTest` opens a segment from the embedded browse on the Search tab's stack.
- **The enlarged segment map on a device** (pan, zoom, Recenter, the credit over the live map): it opens from the segment page, which
  needs a segment the AppView serves. Its snapshots draw each state and credit.
- **Accept invite on a device**: alice has no pending invite on the stack (and invites come from the AppView). Its snapshots draw each
  state, accepting and sign-in-again.
- An iPhone layout on a device (the map over the list), iOS 18 signed in, VoiceOver and the largest text sizes.
- The highlighted stretch a new segment shows: the create-segment page isn't part of this change (roadmap open items).
