# Design

## Context

See proposal.md for why. What exists today and constrains the approach:

- `feed.searchSegments` (`api/SegmentViews.kt` `search`) serves segments crossing a box of at most 1 degree. It narrows with the stored box and `segment_cells`, checks each line with `crosses()`, examines a bounded number of rows per request, and orders by `indexedAt` for keyset paging. Its visibility filters are `segmentServed`, `unhidden` and `listedTo`. The client calls it only through `SegmentRepository.search`, and no screen uses it yet.
- `segments.current_name` holds the derived name, and `segments.kind` the kind. `ActorSearch` already ranks a LIKE search with a `Case` (exact, prefix, word, anywhere) and pages by offset.
- `ActivityMap` (`:app-common-ui`) is MapLibre Compose 0.20 on Android, iOS and desktop (`nonWebMain`) and a canvas `RoutePreview` on web. It draws a single route. Under `LocalInspectionMode` it falls back to the preview, which layoutlib screenshots rely on.
- The Search tab's root is `AthleteSearchScreen` (`TopLevelDestination.Search`). Its presenter shows the debounced search pattern (`SEARCH_DEBOUNCE`, `rememberRetainedPaging` with `OnKeyChange.KeepItems`).
- `rememberLocationPermissionRequest` (`:app-common-ui`, `app/record`) asks for permission on Android, answers granted on iOS and refused on desktop and web. `LocationTracker` (`core/location`, `AppScope`) gives `hasPermission()` and an `updates()` flow, and `isSupported = false` on desktop and web.

## Goals / Non-Goals

**Goals:**
- One query for name search that reuses the area search's visibility rules exactly, so the two can never disagree about what a viewer may see.
- A browse screen whose presenter doesn't know the platform: whether there's a map follows from whether the UI reports a viewport.
- Keep the map wrapper the only place that touches MapLibre's pre-1.0 API, as `ActivityMap` does.

**Non-Goals:**
- Changing `feed.searchSegments`' order or paging. Area browse sorts on the client.
- Full-text indexing (trigram or tsvector). The name search is a LIKE over `current_name`.
- Clustering, heatmaps, or vector tiles of segments.

## Decisions

### 1. A separate `feed.searchSegmentNames` query, not a `q` on `searchSegments`
`searchSegments` requires a box and promises bounded work with keyset paging in index order. A name search needs an optional box and a ranked order, which keyset paging over `indexedAt` can't give. A second query keeps both contracts simple.
- Params: `q` (required, max 100 graphemes), `minLat/minLng/maxLat/maxLng` (all four or none, else `InvalidRequest`; 1 degree limit via `requireSearchBox`), `nearLat/nearLng` (both or none), `sport`, `limit`, `cursor`. Output: `cursor?`, `segments: segmentView[]`.
- Where: the same predicates as `search` (`segmentServed(viewer)`, `unhidden(...)`, `listedTo(viewer)`, optional `sport`), plus `kind <> 'lift'` and `lower(current_name) LIKE %q%` (escaped as `ActorSearch` does). With a box, it also has the stored-box overlap and the `segment_cells` `exists`, then `crosses()` on the fetched rows.
- Order: a rank `Case` (equal, prefix, word prefix, anywhere), then distance from `near` to the box centre (`abs(dLat) + abs(dLng) * cos(nearLat)`, the cosine computed in Kotlin and bound as a literal, so the SQL stays H2-portable), then `uri`. Without `near`, order by `current_name` then `uri`.
- Paging: offset cursor, as `ActorSearch` does. With a box, the `crosses()` filter can make a page short; the cursor still continues, which the lexicon's description states, as `searchSegments` does.
- Alternative considered: a new optional `q` on `searchSegments` with the box made optional. That breaks its required-box contract and its bounded-work guarantee, which `segments/segment-records` specifies.

### 2. Area browse pages every cursor on the client and sorts by distance
For a viewport, the presenter follows `searchSegments` cursors until there are none or it has 300 segments (runs and lifts together), then sorts the runs by distance from the map centre to the nearest vertex of each line (decoded polyline). It runs on camera idle, debounced by `SEARCH_DEBOUNCE`, and only when the box is at most 1 degree on each side; wider, it reports "zoom in" without a request. The result is a `ContentState<ImmutableList<BrowsedSegment>>`, so the previous area's lines stay while the next loads (a refresh, not a new key). A change of sport group uses `key(sport)`, which drops them, per `ui/content-loading`.
- Alternative: server-side distance order. That needs the same rewrite of the bounded batch loop the name search avoids. It's deferred until dense regions show the cap matters (see Risks).

### 3. Name results reuse `rememberRetainedPaging`
Keyed by the trimmed query with `OnKeyChange.KeepItems` and the debounce in the first page, as `AthleteSearchPresenter` does. Scope and sport group sit outside it in `key(scope, sport)`, so changing them clears. The box (This area) and `near` are read when a page loads, not keyed, so panning doesn't restart a search. A "Search this area again" happens only on a new query, a scope change or Retry. The map draws the loaded name results that cross the viewport.

### 4. The presenter infers "has a map" from viewport events
`SegmentBrowseEvent.ViewportChanged(box, centre, zoomTooFar)` comes only from a map. Until one arrives, the presenter has no area: it doesn't browse, a name search runs Everywhere with no `near`, and `State.hasArea` is false, so the UI hides the scope switch. On web the map composable isn't placed at all (an `expect val interactiveMapSupported` in `:app-common-ui`, false on wasmJs), so this falls out without a platform branch in the presenter.

### 5. A `SegmentsMap` composable in `:app-common-ui`
`expect fun SegmentsMap(segments: ImmutableList<MapSegmentLine>, selected: AtUri?, camera: MapCameraRequest?, onViewport: (MapViewport) -> Unit, onSelect: (AtUri?) -> Unit, modifier)`. `MapSegmentLine` holds the uri, polyline and kind. The `nonWebMain` actual uses MapLibre with two `LineLayer`s over one GeoJSON source (runs in the primary colour, lifts dashed and in `outline`), a third layer for the selected line, a click query on the run layer for `onSelect` (a box 12dp either side of the tap, so a near miss still selects; not a wide clear layer, since MapLibre leaves a fully transparent layer out of hit testing and any opacity builds into a haze where imported segments overlap), and the camera's visible region on idle for `onViewport`. The wasmJs actual is a no-op, never placed. Under `LocalInspectionMode` it draws the lines on a canvas, reusing `RoutePreview`'s projection, so screenshot tests render. `MapCameraRequest` carries a centre and zoom the presenter wants (the first fix or latest activity), applied once per request id. The map saves where it last stopped, so a map made again when the athlete comes back (from a segment page or another tab) opens there instead of at the first request.

### 6. Starting position: a first fix, then the latest own activity
The UI asks for permission with `rememberLocationPermissionRequest` and sends the result. The presenter, given permission and `LocationTracker.isSupported`, takes the first sample of `updates()` within 5 s. Otherwise, or on timeout, it uses the first point of the polyline the AppView serves for the athlete's latest own activity with a route, from the cached feeds, else the first page of their activities from the AppView (`FeedRepository.latestOwnStart`). That line has privacy zones already trimmed, so the browse box isn't centred on a zone. With neither, the map stays at a default world view and the list shows the "move the map or search" prompt. The camera request is retained, so coming back keeps the athlete's last area, not a new fix.

### 7. The Search tab hosts both modes in `AthleteSearchScreen`
`AthleteSearchState` gains `mode: SearchMode` (Athletes, Segments) and a `ModeChanged` event, with the mode in `rememberSaveable`. In Segments mode the UI renders `CircuitContent(SegmentBrowseScreen, navigator = …)` under the switch, with a navigator that delegates to `Navigator.NoOp` and forwards `goTo` and `pop` as `GoTo(screen)` and `Back` events, which the presenter passes to the tab's navigator. (Circuit's `NavEvent` is in circuit-foundation, which presenters and `-api` modules don't depend on.) `feature/search` depends only on `:feature-segment-api`. The tab root stays `AthleteSearchScreen`, so `TopLevelDestination` doesn't change. The nested screen's retained state lives under the tab's record, so it survives tab switches and returning from a segment page.
- Alternative: a new `SearchScreen` root with two sub-screens. That's cleaner long-term but renames the tab root and moves athlete search, for no behaviour gain now.

## Risks / Trade-offs

- [Dense imported regions exceed the 300-segment cap in one viewport] → The cap keeps requests bounded. The list says more segments are here and to zoom in when it's hit. Server-side distance order or clustering is a follow-up if this shows up.
- [`LIKE '%q%'` scans every segment for an Everywhere search] → It's acceptable at today's segment counts (one region imported). A Postgres trigram index on `lower(current_name)` can be added later in a Postgres-only migration path without changing the query's contract. Note this in `appview.md` if done.
- [Offset paging can repeat or skip a segment when names change between pages] → The client de-duplicates by uri (`rememberRetainedPaging`'s `idOf`). A skip only costs a result the next search finds.
- [The browse box reveals the athlete's rough location to the AppView] → It's the same trust as the tracks the AppView already holds. The query isn't stored or logged, and the fallback start uses the privacy-trimmed public line, never the raw track.
- [MapLibre Compose is pre-1.0 and its click and camera APIs churn] → All of it stays in `SegmentsMap.nonWeb.kt`.
- [Nested `CircuitContent` in a tab root is a new pattern here] → It's covered by a composeApp jvmTest that switches mode, opens a segment and comes back.

## Migration Plan

Additive only. Ship the AppView with the new route first. Older clients never call it, and new clients against an older AppView get `MethodNotImplemented` for name search, which they show as a load failure; area browse still works. No database migration is needed. Rollback is removing the client entry point; the route can stay.

## Open Questions

- Exact visual style for lifts (dash pattern and colour token). It's settled during UI review and doesn't change behaviour.
- Whether 300 is the right area cap. It's tuned against the Vermont import once it runs.
