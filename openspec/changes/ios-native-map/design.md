# Design

## Context

The Compose map (`ActivityMap`, `:app-common-ui`) is an `expect` composable: MapLibre Compose on Android, iOS and desktop, the line
preview (`RoutePreview`) on web and under inspection mode. What it draws is worked out inside the composables: `SegmentedPolyline.pieces`
splits the line at its gaps, `RouteCurve.smooth` smooths each piece, `RouteMarkers.of(…).shownAt(ends)` (internal to `:app-common-ui`)
places the markers, a `BoundingBox` is taken from the points, and `fitCamera` (`RouteCamera.kt`, nonWeb, on MapLibre's types) frames
it for the feed's map images while the live map asks MapLibre's `cameraForBounds` with a 24 dp margin. The native card draws only a
`Canvas` sketch (`RouteSketch`) from `RouteShape`, which always marks the line's own start and end.

The dive site page (`DiveSiteUi`) is a list: the map of the site's outline (a closed polyline, ends hidden), the about block (original
name, radius, creator, credit, `PlaceStatus`, the Regulars `LeaderCard`), conditions, `PlaceNames` and the dives as `ActivityCard`s
with no author link and no Like. Its texts, and the names section's, are built in composables (`stringResource`, `Format`,
`handleLabel`, `segmentStatusLabel`, `monthName`, `effortDateLabel`). The presenter gives no viewer DID and no revealed set, which the
native card needs.

## Goals / Non-Goals

**Goals:**
- One map view any native page can place, whose inputs are a Kotlin value and a few layout flags, so the activity page, segment browse
  and the invite preview reuse it unchanged.
- The rule behind every mark and the framing lives once, in `:app-common-api`, and the Compose map asks it too.
- Deterministic previews and snapshots.

**Non-Goals:**
- Many routes on one map (segment browse's segments), a search box over a map, the highlighted stretch: their pages bring them.
- Offline tiles, custom tile styles, map snapshots for feed cards on iOS.

## Decisions

### MapKit, not MapLibre Native
The roadmap's stack table chose MapLibre Native for iOS because MapKit can't draw the OpenFreeMap styles the other platforms use. This
change takes MapKit's SwiftUI `Map` instead: it is the system's own (no package, no binary framework, no style URL to fetch), follows
the appearance and Dynamic Type, carries Apple's credit and legal link itself, and SwiftUI's `MapPolyline` and `Annotation` draw
everything a route map needs. The cost is that iOS maps look like Apple Maps rather than OpenFreeMap; the marks drawn over the tiles
are the same everywhere. Alternative: MapLibre Native's SwiftUI DSL, which keeps the style but adds a dependency (and its version churn)
for the look alone. The table and the reason are updated in the roadmap.

### `RouteMap`: a Kotlin value with everything but layout
`RouteMap.of(polyline, gaps, ends)` in `:app-common-api` (package `app`, beside `RouteShape`) decodes the line and returns null under two
points; otherwise it holds `pieces` (`MapLine`s of smoothed `GeoPoint`s), `start` and `end` (nullable, `RouteEnds` applied), `breaks`
(`MapBreak(stop, resume)` at the stored points), `bounds` (`MapBounds`, from the stored points, not the curves), `frame(width, height,
top)` (`MapFrame`: centre and zoom), and `sketch(width, height, pad)` (a `RouteShape` for the stand-in). It is non-generic so Swift
export types it, and holds `GeoPoint`, which `RouteEnds` already gives a Swift type.

`MapFrame.fit(bounds, width, height, margin, top)` is `fitCamera`'s Web Mercator math moved out of MapLibre's types (zoom 0 is a 512-unit
world, clamped to `[0, MAX_FIT_ZOOM = 16]`, `FIT_MARGIN = 24`), with the top inset that the Compose map's `overlayTop` passes.
`fitCamera` becomes an adapter, so the feed images and the native map frame alike. The live Compose map keeps MapLibre's `cameraForBounds` (same margin and top inset, no closest zoom), so nothing changes on Android or desktop; moving it onto `MapFrame` is left for a change that can see it on those devices.
Alternative: let MapKit fit a region with padding; rejected, since MapKit's fit has no closest-zoom rule and would frame a few metres
of route at street-furniture scale.

The Compose side asks `RouteMap` for its pieces, markers and bounds in `ActivityMap.nonWeb`, `RoutePreview` and `RouteSnapshots`; `RouteMarkers` keeps only the look (its
colours and sizes) and a constructor from a `RouteMap`; its `of`/`shownAt` rule and tests move to `RouteMapTest`. `RouteShape` gains
nullable `start`/`end` so the stand-in honours hidden ends (the card's `RouteShape.of` keeps both).

### `RouteMapView`: frame once from Kotlin, draw with MapKit
`RouteMapView(map:height:interactive:expand:)` in `iosApp/iosApp/Screens/` (beside `RouteSketch`; it names app types). A
`GeometryReader` gives the size; the camera is `MapCameraPosition.rect` of an `MKMapRect` centred on the frame's centre whose size in
map points is the view's size times `2^28 / (512 · 2^zoom)` (MapKit's world is 2^28 map points wide), so the frame is Kotlin's to the
point. It is set when the size or the route changes, and again by Recenter. Content: per piece a white `MapPolyline` 7 wide under the
accent one 4 wide; each break a dashed `MapPolyline` in the break colour; markers as `Annotation`s of `RouteMarkerDot` (fill and ring,
shared with `RouteSketch` through one `RouteMarkerStyle`), titles hidden. Static: `interactionModes: []`, and with `expand` a tap
gesture and an expand `AppIconButton` top-trailing. Interactive: `[.pan, .zoom]` and a Recenter button. POIs are excluded so the route
reads first.

Fallback: `@Environment(\.liveMaps)`, false by default and set true by the signed-in `SessionView`. Previews and snapshots never set it,
so they draw `RouteSketch { size in map.sketch(…) }` at the same height. Alternative: sniffing `XCODE_RUNNING_FOR_PREVIEWS` and the test
host; rejected as two checks that miss each other's case and that a pushed preview of a real session wouldn't follow.

### The dive site page
`DiveSitePage(state:)` (named so because `DiveSiteView` is the lexicon's alias), registered for `DiveSiteScreen`. Inside
`ContentStateView` over the typed `site` getter, a `PagedList` of `ActivityView` with `keepsTop: true` and a section per dive, as the
gear item and profile pages do: the map row (insets cleared, rounded), the about section, the Regulars card (a button row), the
conditions section, the names section (`PlaceNamesSection(fields:proposals:history:canPropose:canVote:)`, its own file so the segment
page can reuse it), and the dives' heading. `.placeNames(state.names)` shows the sheet and confirmation.

Cards: `ActivityCardView` with `authorClickable: false`, a new `likes: false` (the shared page passes no Like), Open and OpenMedia both
sending `DiveSiteEvent.OpenDive`, as the shared page's `onOpenMedia` opens the dive. The state gains `viewerDid` (for
`ActivityCardContent.of`) and `revealed` (`rememberRevealed()`), as `GearState` and `ProfileState` have; the shared page passes the same
to `ActivityCard`'s own reveal through nothing (it keeps its local reveal), so Compose is unchanged.

The name history's disclosure is SwiftUI `@State` (`DisclosureGroup`), as the shared one is `rememberSaveable` in the UI.

### Texts
- `PlaceTexts` (`:app-common-api`, `app.segment`): `createdBy(handle)`, `status(status)`, `statusHelp(status, proposed, duplicate,
  archived)` taking the place's own help strings' names, `offersOriginal(status, hasOriginal)`, `osmCredit`, `license(text)`.
  `CreatedBy`, `PlaceStatus` and `segmentStatusLabel` resolve them.
- `PlaceNameTexts` (`app.names`): `originally(name)`, `proposedBy(proposal)` (who · totals, counts `formatWhole`d), `voteUp(name)`,
  `voteDown(name)`, `votingUnavailable`, `historyEntry(adoption, zone)` (the date by `DateText`), `historyBy(adoption)`, and the fixed
  labels; `PlaceNames` and `OriginalName` resolve them. The vote state (`viewerVote`) is read from the lexicon view in both UIs, which is
  data, not a rule; toggling (tapping the chosen direction sends null) becomes `PlaceNameTexts.voteAfter(proposal, direction)` so Swift
  doesn't write it again.
- `DiveSiteTexts` (`:feature-divesite-api`): `radius(site, units)`, `regularsTitle`, `regularsNone`, `leaderVisits` (from
  `DiveSiteRegularsTexts.visits`), `yourVisits(entry)`, `conditions…` lines (`conditionLines(conditions, units): List<AppText>`, and
  `months(conditions, units)` only when more than one month), `dives`, `noDives`, and the status helps' names.

## Risks / Trade-offs

- [Apple's tiles differ from OpenFreeMap's] → the marks are identical; the roadmap records the choice so a later change can revisit it.
- [`MapCameraPosition.rect` letterboxes a rect whose aspect differs from the view's] → the rect is built from the view's own size.
- [A `Map` inside a `List` row] → MapKit views in lists are a supported layout; the row keeps a fixed height, and the map is static so
  it takes no drag from the list.
- [Snapshots can't show the live map] → the map's math is tested in Kotlin (`RouteMapTest`, `MapFrameTest`) and its Swift conversion in
  a Swift test; the walk on the iPad shows the live map.
- [The local stack may have no dive site] → the walk creates nothing; anything it can't show goes under "Not verified".
