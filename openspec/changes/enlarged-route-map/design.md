# Design

## Context

- `ActivityMap` (`composeApp/ui`) is an `expect` composable.
  - The nonWeb actual draws a `MaplibreMap` with `MapInteractions.None` and the collapsing attribution overlay (`RouteMapOverlay`).
  - The wasmJs actual falls back to `RoutePreview`.
- MapLibre's pre-1.0 API is kept inside the nonWeb actual on purpose.
- On the detail page, `PageMedia` places the map as the first tile of `MediaRow`. With no photos the tile is full width; with photos it is 80% of a `LazyRow`.
- What the map draws depends on the viewer:
  - the owner gets `state.ownRoute`/`ownRouteGaps`/`ownRouteEnds` from `rememberOwnRoute` in `:app-presenters`;
  - everyone else gets `record.polyline`/`polylineGaps` of the effective record.
- Photos already open full screen through `MediaGalleryScreen(uri, index)`, whose presenter loads the activity by uri through `FeedRepository` and its rewrites.

## Goals / Non-Goals

**Goals:** an enlarged map that pans and zooms, reached from the detail map, drawing exactly what the detail map draws.

**Non-Goals:**
- an interactive inline map;
- a web map;
- an enlarged map from feed cards or the import preview;
- linking chart scrubbing to a position on the map;
- choosing a map style or layers.

## Decisions

- **A new Circuit screen, `RouteMapScreen(uri)`, rather than expanding in place or using an overlay.** It follows `MediaGalleryScreen`.
  - It gets back-stack behaviour for free: the system back gesture and button, and the tab's back stack in `AppShell`.
  - The detail page's layout doesn't change.
  - Expanding in place would fight the three adaptive layouts, including the 840dp side pane.
- **The screen takes the activity's uri, not the polyline.** A polyline can be tens of KB, and screens are saved in the back stack's state, which risks hitting Android's transaction size limit.
  - `RouteMapPresenter` loads the activity again (`feed.activity(uri)` with `feed.rewrites`), takes the effective record and, for the owner, calls the same `rememberOwnRoute`.
  - The selection that `PageMedia` does in the UI today becomes a small presenter-side helper, which both presenters use so they can't disagree:
    - the owner's untrimmed line when it has loaded, otherwise the served polyline;
    - its gaps;
    - ends only on the owner's line.
  - Alternative: pass the route in the screen args. It is simpler but has the size and process-death problems above, and the owner's track is something only they read.
- **`ActivityMap` gets `onExpand: (() -> Unit)?` and `interactive: Boolean`.** Both are honoured only in the nonWeb actual, so MapLibre stays in one file and the web needs no special case at call sites.
  - Inline: interactions stay `None`, plus a click callback, or a clickable layer if MapLibre doesn't deliver clicks with every gesture off.
  - A small expand icon button sits in the top-end corner. It is the accessible target, and it keeps clear of the MapLibre logo (bottom-start) and the attribution (bottom-end).
  - `interactive = true` uses `MapInteractions(Standard)` with rotate and pitch disabled, and shows a recenter button that re-runs the same `cameraForBounds` fit used on first show.
- **`RouteMapUi`** is an `AppScaffold` with an `AppTopBar`: back, plus the title or activity type label as on the detail header. Its body is `ActivityMap(interactive = true)` filling the rest of the screen.
  - Loading shows `AppProgressIndicator`. A load error shows `errorText`, as `MediaGalleryUi` does.
  - It uses only `ui/adaptive` wrappers.
- **Web**: the `wasmJs` actual ignores both new parameters, so there is no tap target. `RouteMapScreen` still compiles for web because its UI only calls `ActivityMap`, but nothing navigates to it there.

## Risks / Trade-offs

- [MapLibre may not report clicks while every camera gesture is off] → Fall back to a transparent clickable `Box` under the overlay. The expand button covers accessibility either way.
- [On iOS, the edge-swipe back gesture can compete with panning near the left edge] → Check on the simulator. The back button in the top bar is always there.
- [The full-screen screen loads the activity a second time] → `FeedRepository` serves the same request and the cost is one query. In exchange, the screen keeps working after process death.
- [The attribution collapses on its first camera move] → That is the library's behaviour once gestures are on. `RouteMapOverlay` still auto-collapses after five seconds, which OSM's guidelines allow.
