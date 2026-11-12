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

- **First walk, tunnel down.** Search's Segments mode drew native segment browse under the mode choice: on the iPad the list column
  (the name field, the sport menu chip) beside the live MapKit map, Apple's tiles and credit with Legal in its corner, opened at the
  simulator's location after the system's location prompt (answered Allow Once each time). Nothing the AppView serves loaded: the
  list said "Couldn't load the segments here" with Retry, and a name search "Couldn't search segments". A drag panned the map; switching
  to Athletes and back reopened it where it was left; typing a name showed This area and Everywhere; dark mode turned the tiles and page
  dark. The search field's label isn't clipped on the device (the iPad snapshot's offset is the test host's landscape safe area).
- **Second walk, tunnel up.** This worktree had no `local.properties`, so its first build refreshed alice's session with the local
  client ID and the PDS refused it (`InvalidClientMetadataError`): the earlier failures were that as much as the tunnel. With the other
  worktrees' device-mode `local.properties` copied in (untracked), the rebuilt app reached the AppView, still signed in. Then:
  - The area's runs on the live map (the importer's trails around the simulator's location) and the list nearest first, with the capped
    note ("More segments here than shown…"), each row's length, type and distance away. No lifts in that area.
  - **Two bugs found and fixed** (`c1057518`): the runs drew in the system's blue, since `Color.accentColor` inside a map's content isn't
    the app's tint (now `Color("AccentColor")`, in `RouteMapView` too); and a tap on a line did nothing, since a plain or simultaneous
    tap gesture on a `Map` never fires (a temporary log showed no call). With a high-priority spatial tap, a tap on a short line turned it
    amber and the list scrolled to and marked "Willow Creek: Willow Creek → Fayview Lane"; a drag still panned the map afterwards.
  - Opening a run from the list pushed its segment page (still the shared UI) on the Search tab; its map's expand opened the **native
    enlarged map**: the line in the app's teal with its start and end markers, filling the page under the segment's name, the OSM credit
    and licence top-leading, Recenter top-trailing. A pan moved it and Recenter framed the whole line again (a 0.31 mi line stops at the
    shared closest zoom; a 1.59 mi one fills the width below the credit). Back twice returned to browse with its map, list position and
    selection as left.
  - A name search: "ghost" listed the Ghost Falls segments page by page and the map drew only their lines; "ghostghost" said "No segments
    here match “ghostghost”." with Search everywhere, which switched the scope to Everywhere and said "No segments match “ghostghost”.".
    The field took the name in lowercase, as typed.
  - The sport menu chip opened with All and the six groups; choosing one never landed through the simulator's input (the tap reached
    the list row under the menu instead), so the filter's effect wasn't seen.
- **Put back:** the field was cleared and the Feed tab chosen; the appearance stayed light. Nothing was written. The location prompt was
  answered Allow Once, which lapses.
- **Simulators:** the Swift tests ran on `Interactive Maps iPhone 17` (iOS 26.0, `4B43CC7E`, cloned from `D6328F65`) and `Interactive
  Maps iPhone 16 Pro 18.4` (`01098C2A`, cloned from `958C0DC3`; snapshots skipped), 365 passing on each after the fix.

## Not verified

- **The sport filter's effect on a device**: the menu opened with its choices, but the simulator's input never landed a choice. The
  snapshots and `SegmentBrowseTextsTest` cover the choices; the presenter's filtering is `SegmentBrowsePresenterTest`'s.
- **Lifts on the live map**: the walked area has none. The stand-in snapshot draws one dashed under the runs.
- **Accept invite on a device**: the stack holds no activity invites at all, and making one would be a write. Its snapshots draw each
  state, accepting and sign-in-again.
- An iPhone layout on a device (the map over the list), iOS 18 signed in, VoiceOver and the largest text sizes.
- The highlighted stretch a new segment shows: the create-segment page isn't part of this change (roadmap open items).
