# Design

## Context

`RouteMapView` (`iosApp/iosApp/Screens/RouteMapView.swift`, from `ios-native-map`) draws Kotlin's `RouteMap` on MapKit's SwiftUI `Map`:
static, expandable, or interactive with Recenter, framed by `RouteMap.frame` through `MapCamera`, and drawn as `RouteSketch` outside a
session (`\.liveMaps`). It has a fixed `height`.

Three shared pages wait on it:

- **`SegmentMapUi`**: an `AppScaffold` titled with the name, `ActivityMap(interactive = true, height = Dp.Infinity, overlayTop)` with the
  credit (`Credit`, which builds its lines from `SegmentCredit` with `stringResource`) over the top-start corner and `RefreshStatus`
  under it. `SegmentMapContent` holds the polyline as text.
- **`SegmentBrowseUi`**: no bar of its own (the Search tab hosts it); a header (search field, scope control while a name is searched in
  an area, sport chips), the `SegmentsMap` (MapLibre on non-web: lines as GeoJSON layers in a fixed order, a box query around a tap,
  `MapViewport` from the visible bounds when the camera stops, each new `MapCameraRequest.id` applied once, the last stop restored on
  coming back), and a list whose branches (`searchingNames`, locating, nowhere, too wide, the area's runs with its capped and none-here
  notes, name results with an empty message that offers Search everywhere in an area) and rows (`distance · type · away`) are chosen in
  composables. `askLocation` triggers `rememberLocationPermissionRequest`, which on iOS answers granted at once.
- **`AcceptInviteUi`**: `InviteSummary` (already `ActivityInvitesTexts`), `RoutePreview` of the record's line and gaps, the intro,
  overlap warning, title field, `ActivityVisibilityForm(state.visibility)`, the error with Sign in again, Decline and Accept.

The native Search tab (`AthleteSearchView`) embeds the shared browse in Segments mode through `IosApp.segmentBrowseViewController(record:)`,
a Compose controller that runs `SegmentBrowseScreen`'s presenter in its own composition with a navigator sending `AthleteSearchEvent.GoTo`
and `Back` to the record's latest state.

## Goals / Non-Goals

**Goals:**
- The enlarged segment map, segment browse and Accept invite native on iOS, registered, with samples, previews and snapshots.
- No Compose view inside a native page: browse runs through the presenter host like every other native screen.
- Every rule that isn't layout asked from Kotlin, by both UIs.

**Non-Goals:**
- The create-segment page and its highlighted stretch, the activity's route viewer and page, and the segment page.
- Restyling or changing any behaviour on Android, desktop or web.

## Decisions

### Segment browse runs as a screen the Search record embeds (`HostRecord.embed`)
The browse presenter is a full Circuit presenter (it takes a `Navigator`), not a SubCircuit, so `HostRecord.sub` can't run it. A new
`HostRecord.embed(screen: Any): HostRecord` gives a child record whose presenter is created with the parent record's navigator (the tab's
`TabStack`), run inside `RunRecord` beside the subs under its own key, so its retained and saved state lives and dies with the parent's,
exactly as `CircuitContent` inside `AthleteSearchUi` does in Compose. An equal screen returns the same child. `HostRecord` gains an
internal `navigator` (TabStack passes itself; the single and sign-in records pass `Navigator.NoOp`). The parent's GoTo/Back round trip in
Compose ends in the same `navigator.goTo`/`pop`, so going straight to the stack is the same behaviour.

Swift: `EmbeddedScreenView(record, screen:) { (state: S) in … }` (`Shell/`, beside `SubScreenView`), following the child's `state` and
setting `\.hostRecord` to the child for its sheets. With no record (previews, snapshots) the Search page shows nothing in Segments mode,
and the browse view gets its own snapshots. Then `SegmentBrowseViewController`, `IosApp.segmentBrowseViewController`, the `SegmentBrowse`
composable and `SegmentBrowseController`/`SegmentBrowseEmbed` go.

Alternative: keep the Compose embed; rejected, it is the reason for this item. Alternative: make `SegmentBrowseScreen` a SubCircuit;
rejected, it changes the shared Search tab and the presenter's navigation for an iOS need.

### The many-segment map: `SegmentsMap` in Kotlin, `SegmentsMapView` in Swift
`SegmentsMap.of(lines, selected)` (`:app-common-api`, package `app`) decodes each `MapSegmentLine` once and holds `drawn`: `MapSegment`s
(`uri`, `points: ImmutableList<GeoPoint>`, `role`: `Lift`, `Run`, `Selected`) in drawing order, lifts first and the selected run last;
`nearest(lat, lng, withinMeters)`: the run (never a lift) whose line passes closest to the point within reach, by distance to each
segment on a local equirectangular projection (enough for a finger's width); `key` for reframing. `MapViewport.ofDegrees(south, west,
north, east, centerLat, centerLng)` moves the 1e-7 rounding out of `SegmentsMap.nonWeb`. `SegmentsPreview` and the MapLibre map ask
`drawn`'s order; MapLibre keeps its box query for taps (its own hit test, which works there).

`SegmentsMapView(map:selected:camera:onViewport:onSelect:)` in `iosApp/iosApp/Screens/`: a `MapReader` over `Map(position:, interactionModes:
[.pan, .zoom])` with `MapPolyline`s per role (white 6 casing under a 4 accent run, a 2-wide dashed outline-colour lift, a 6-wide highlight
for the selected one), `onMapCameraChange(frequency: .onEnd)` turning `context.region` into `MapViewport.ofDegrees`, a `SpatialTapGesture`
turned into coordinates by the proxy, and the reach in metres from two points 12 pt apart (the shared `TAP_SLOP`). A `MapCameraRequest`
whose `id` differs from the last applied sets the position with `MapCamera.rect(MapFrame(lat, lng, zoom), size)`. The last position stays
in a small `@Observable` the Search page holds, so switching modes and coming back reopens where the map was left, as the shared map's
`rememberSaveable` does. Outside a session it draws a `Canvas` stand-in (the lines fitted by `TileFit` over all points) in the same order
and colours.

### Browse's text and branches in Kotlin
`SegmentBrowseTexts` (`:feature-segment-api`): `details(segment, metersAway, units)` (`distance · type · away`, from `StatText.distance`,
`SegmentTexts.type`), `message(state)` (null when runs or results show: locating, nowhere, zoom in, web prompt), `capped`, `noneHere`,
`noResults(state)` (here or everywhere), `searchEverywhere`, `searchLabel`, `allSports`, `scope(scope)`, `sport(sport)`. `SegmentBrowseState`
gains `listShows` (an enum `BrowseList`: `Names`, `Locating`, `Nowhere`, `TooWide`, `Area`, `WebPrompt` decided with
`interactiveMapSupported` passed in), `offersScope`, `offersSearchEverywhere`, `areaRuns` and `nameResults` typed getters, and `sports`
(the chips' tokens, `SegmentSportGroup.all`). The Compose page asks the same.

The page layout: an iPhone puts the header, then the map (its 1.2 share), then the list; a regular width puts a 380 pt column (header and
list) beside the map, as the shared page does. The list is `PagedList` for name results, a `List` for the area, inside a
`ScrollViewReader` scrolling to the selected row. The search box is an `AppTextField` (not `searchable`, which belongs to the bar and the
Athletes mode; it sits in the header as the shared page's field does).

Location: when `askLocation`, the view sends `PermissionResult(granted: true)` once, as the shared iOS UI's request does (the location
tracker asks the system itself).

### The enlarged map
`SegmentMapContent` gains `routeMap: RouteMap?` (`RouteMap.of(polyline, ends = RouteEnds.HIDDEN?)`: the segment page's map marks its ends,
so `ends = null`, as `ActivityMap` does today). `SegmentMapView`: a `ContentStateView` over `content`, the `RouteMapView(height: nil,
interactive: true, overlayTop:)` ignoring the bottom safe area, the credit (`SegmentCreditTexts.lines(credit)`, the OSM one a link to
`PlaceTexts.OSM_COPYRIGHT_URL`) on `.regularMaterial` in a rounded box at most 260 wide, its measured height plus 8 as `overlayTop`, and
`RefreshStatusView` under it. `RouteMapView`'s `height` becomes optional: nil fills (`.frame(maxHeight: .infinity)`).

`SegmentCreditTexts` (`:feature-segment-api`): `osm: Boolean` (whether the first line links), `lines(credit): List<AppText>` (OSM credit,
agency named or not, licence via `PlaceTexts.license`). `Credit` asks it.

### Accept invite
`AcceptInviteView`: a `Form` inside `ContentStateView` over `content` (typed getter `invite`): the summary section (`ActivityInvitesTexts`
from, title, summary), the route row (`RouteSketch` from `AcceptInviteState.routeMap?.sketch`, the shared page's line preview, which
`RoutePreview` draws), the intro and overlap in the section footer, the title `AppTextField(layout: .row)`, the visibility sections, the
error with Sign in again, and Decline and Accept in the bottom section, disabled while `busy`. The gone message when the invite is null.
`AcceptInviteTexts` (`:feature-invites-api`): `intro(invite)`, `overlap`, `gone`, `accept(accepting)`, `decline`. `AcceptInviteState`
gains `busy`, `invite` and `routeMap`.

The visibility form: `ActivityDefaultsView`'s two sections move into `VisibilityFormSections(fields:enabled:note:)` (`Screens/`), which both
pages place; Activity defaults passes its page note, Accept invite none. `ActivityVisibilityForm` in Compose shows the same choices from
the same `VisibilityFields`, so no rule moves.

### Left
The highlighted stretch a new segment shows belongs to `CreateSegmentScreen`, a page with its own chart and trimming that this change
doesn't touch: recorded in the roadmap's open items.

## Risks / Trade-offs

- [SwiftUI `Map` has no polyline hit test] → taps go through Kotlin's `nearest`, tested in Kotlin; the reach converts 12 pt at the tap.
- [`onMapCameraChange` fires on the first layout] → the viewport is reported once the map has a size, as MapLibre's `awaitViewport`.
- [A new `HostRecord` kind] → tested in `HostRecordTest`-style JVM tests with fake presenters, and through the real browse presenter in
  `composeApp`'s host tests (`SearchTabSegmentsTest` style).
- [Snapshots can't show tiles] → stand-ins in snapshots; the walk on the iPad shows the live maps.
- [The charts session edits the segment page and may touch `RouteMapView`] → my `RouteMapView` change is additive; noted in the shared
  notes folder.
