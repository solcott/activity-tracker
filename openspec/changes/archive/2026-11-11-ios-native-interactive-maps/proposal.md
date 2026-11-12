# Proposal

## Why

`ios-native-map` gave iOS a native MapKit route map (`RouteMapView`) with an interactive mode no page uses yet, and the pages built on a
map the viewer moves are still the shared Compose UI: the enlarged segment map, segment browse (which the native Search tab embeds as a
Compose view controller in Segments mode) and Accept invite's route preview. Each is a fallback page in an otherwise native stack, and
the Search embed is the last Compose view a native page holds inside itself.

## What Changes

- **The enlarged segment map is native on iOS** (`SegmentMapView`), registered in `ScreenRegistry`: the segment's line filling the page
  on `RouteMapView` in its interactive mode (pan and zoom, north-up, Recenter), titled with the segment's name, its data credit over the
  top-leading corner with the route framed below it, and the shared load failure and refresh status.
- **Segment browse is native on iOS** (`SegmentBrowseView`): the search box, the This area / Everywhere choice while a name is searched,
  the sport chips, and the segments on a native MapKit map of many lines (runs on a white casing, lifts dashed and muted, the selected
  run highlighted; a tap selects the nearest run or clears the choice; each stop reports what the map shows; each new camera request
  moves it once) beside the list on an iPad and above it on an iPhone, the selected run marked in the list and scrolled to. The list,
  its messages (locating, nowhere, zoom in, capped, none here, no match, Search everywhere) and its rows are the shared page's.
- **The Search tab's Segments mode shows the native browse view**, run through the presenter host as a screen its record embeds
  (`HostRecord.embed`, whose presenter navigates the Search tab's stack). The Compose embed (`IosApp.segmentBrowseViewController`,
  `SegmentBrowseViewController` in `composeApp`'s `IosHost.kt`, `SegmentBrowseEmbed`) is removed.
- **Accept invite is native on iOS** (`AcceptInviteView`): who invited the athlete and the activity's summary, its route as the line
  preview, the intro and overlap warning, the title field, the native visibility form (shared with Activity defaults), the failure with
  Sign in again, and Decline and Accept.
- **Kotlin decides, both UIs ask** (dev/shared-display-text): a segment's credit lines (`SegmentCreditTexts`), segment browse's rows,
  messages and choices (`SegmentBrowseTexts`, `SegmentBrowseState.listShows`, `offersSearchEverywhere`), the many-segment map's drawing
  order, tap rule and viewport (`SegmentsMap` in `:app-common-api`), the enlarged map's and invite's `RouteMap`, and Accept invite's
  texts (`AcceptInviteTexts`). The Compose pages ask the same, so nothing changes on Android, desktop or web.
- **Samples, previews and snapshots** of each page in its shared samples, light and dark (the maps as their line stand-ins), and the
  registry entries with their test.

What waits (open items in the roadmap): the highlighted stretch a new segment shows (the create-segment page, which this work doesn't
reach), the activity's route viewer and page, and the segment page (the parallel charts change).

Out of scope: any behaviour change on Android, desktop or web; MapKit tiles in snapshots; location permission prompts beyond what the
shared iOS UI already does (it answers granted, the tracker asks the system).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `segments/segment-map-viewer`: the enlarged segment map is native on iOS.
- `segments/segment-browse`: segment browse is native on iOS, and the Search tab's Segments mode shows it instead of the shared screen.
- `feed/activity-invites`: Accept invite is native on iOS.
- `dev/presenter-host`: a record can run a screen its state's UI embeds, navigating the record's stack.
- `dev/shared-display-text`: segment credit, segment browse, the many-segment map's rules and Accept invite's texts are chosen in shared
  code.
- `ui/ios-design-system`: a native map of many segments, and the route map filling its page.

## Impact

- **`:app-common-api`:** `SegmentsMap` (lines in drawing order, `nearest`, `MapViewport.ofDegrees`); tests.
- **`:app-common-ui`:** `SegmentsMap.nonWeb` and `SegmentsPreview` ask `SegmentsMap`.
- **`:feature-segment-api`, `-presenter`, `-ui`:** `SegmentCreditTexts`, `SegmentBrowseTexts`, `SegmentBrowseState` getters,
  `SegmentMapContent.routeMap`; `Credit`, `SegmentBrowseUi` and `SegmentMapUi` ask them.
- **`:feature-invites-api`, `-ui`:** `AcceptInviteTexts`, `AcceptInviteState.routeMap`; `AcceptInviteUi` asks them.
- **`:app-host`:** `HostRecord.embed` and its tests.
- **`composeApp`, `:iosShell`:** the browse embed's entry point removed.
- **`:app-fixtures`:** `SegmentMapSamples`, Accept invite samples.
- **`iosApp`:** `SegmentMapView`, `SegmentBrowseView` with `SegmentsMapView`, `AcceptInviteView`, a shared `VisibilityFormSection`,
  `RouteMapView(height: nil)`, `EmbeddedScreenView`; aliases, samples, previews, snapshot and logic tests, registry entries.
- **Docs:** the roadmap's Phase 4 entry, findings and open items; `.agents/rules/compose-screens.md` and `adaptive-ui.md`.
- **Risk carried:** MapKit's SwiftUI `Map` has no line hit testing, so taps are tested against Kotlin's rule with a slop converted from
  points; snapshots never show tiles; the walk depends on what segments the local stack has.
