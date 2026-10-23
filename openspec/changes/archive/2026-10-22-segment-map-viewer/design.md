# Design

## Context

- `ActivityMap` already does everything this needs. `onExpand` makes the static map tappable with an expand button, and `interactive = true` gives pan, zoom, north-up and a recenter button. Both are ignored by the wasmJs actual. It came from `2026-10-19-enlarged-route-map`, which added `RouteMapScreen` for activities.
- The nonWeb actual's `RouteMapOverlay` places the MapLibre logo bottom-start, the collapsing tile attribution bottom-end, and the expand or recenter button top-end. Top-start is free.
- The nonWeb actual frames the route, on first show and on recenter, with a fixed 24 dp margin on every side. Anything drawn over the map's edge can cover a line that ends near it.
- `SegmentUi` draws `ActivityMap(line, Modifier.fillMaxWidth())` as a `LazyColumn` item, with no `onExpand`. The credit is a private `Credit(SegmentCredit)` composable further down the list.
- `SegmentPresenter` loads the segment through `SegmentRepository.segmentOutcome(uri)` (one `getSegment`, not cached). It converts the precision-6 line with a private `mapLine` and derives the credit with `creditOf`, which is already `internal`.
- `RouteMapScreen` is built around activities. Its presenter loads an `ActivityView`, applies rewrites and picks the owner's untrimmed track. None of that applies to a segment, which is one public line.

## Goals / Non-Goals

**Goals:** a full-screen segment map reached from the segment screen, drawing the same line, with the data credit on the map.

**Non-Goals:**
- overlaying the viewer's efforts or any activity track on the segment;
- opening the enlarged map from Create Segment, leaderboards, Mayors or starred lists;
- any change to `RouteMapScreen`, or to `ActivityMap` beyond the framing margin below;
- a web map.

## Decisions

- **A separate `SegmentMapScreen(uri)` in `app/screens/.../app/segment/`, not a generalised `RouteMapScreen`.** Its state mirrors `RouteMapState`: a `ContentState<SegmentMapContent?>` holding the precision-5 polyline, the segment's name and its `SegmentCredit?`, with `Back` and `Retry` events.
  - Alternative: one map screen with an activity-or-segment source. It would mix the activity presenter's privacy and owner-track logic with segment loading for little shared code, since the shared part, `ActivityMap`, is already a reusable composable.
- **The screen takes the segment's uri and reloads it, not the line.** This matches `RouteMapScreen`'s reasoning: screens are saved in the back stack's state, and a uri survives process death and deep links. The cost is one extra `getSegment`.
  - Alternative: pass the line, name and credit in the screen args. It opens instantly with no error state, but puts data in saved state and breaks the pattern every other screen follows.
- **`SegmentMapPresenter` uses `produceRetainedContentState` with a `loadKey`, as `RouteMapPresenter` does.** `mapLine` moves out of `SegmentPresenter` next to `creditOf` (both `internal` in the segment package of `:app-presenters`), so both presenters draw and credit a segment the same way.
- **`SegmentPresenter` gets `SegmentEvent.OpenMap`** and handles it with `navigator.goTo(SegmentMapScreen(uri))`. `SegmentUi` passes `onExpand = { eventSink(SegmentEvent.OpenMap) }`. On web the actual ignores `onExpand`, so there's no web-specific code at the call site.
- **The credit is overlaid by `SegmentMapUi`, outside `ActivityMap`.** `SegmentMapUi` puts `ActivityMap(interactive = true, height = Dp.Infinity)` in a `Box` and aligns the credit top-start, the one corner `RouteMapOverlay` leaves free.
  - It sits on a rounded surface-coloured background (the same treatment as `MapAttribution`'s attribution chip) so it reads over any tiles in light and dark themes.
  - It never collapses. The segment-records requirement asks for the credit wherever the line is drawn, and unlike the tile attribution it isn't something the map library manages.
  - Alternative: add an overlay slot to `ActivityMap`. It would change the shared map API to serve one caller; a `Box` does the same job.
- **`ActivityMap` takes an optional `overlayTop: Dp = 0.dp`, added to the top framing margin, so the chip never covers the line.** On the simulator, a segment starting in the top-left corner had its start marker under the chip.
  - `SegmentMapUi` measures the chip (`onSizeChanged`) and passes its height plus its 8 dp margin, or 0 when there's no credit. The first fit and recenter both use it, so the whole line shows below the chip; the viewer can still pan the line under it.
  - Every other caller passes nothing and frames as before. The wasmJs actual ignores it, like `onExpand` and `interactive`.
  - Alternative: a bar under the map instead of an overlay. It needs no `ActivityMap` change, but the credit then isn't on the map, which is what was asked for.
  - Alternative: a bigger fixed top margin for every interactive map. It wastes room on the activity map, which has no overlay, and can't follow the chip's height, which varies with the source and wraps for a long agency name.
- **`Credit` moves to its own file in `composeApp/.../app/segment/`** as an `internal` composable, used by `SegmentUi` (unchanged look in the list) and by `SegmentMapUi` (inside the chip). Its `OSM_COPYRIGHT_URL` moves with it.
- **`SegmentMapUi`** is an `AppScaffold` with an `AppTopBar` (segment name, back), then `ContentStateLayout` with `UiError.Kind.LoadSegment` and `RefreshStatus`, as `RouteMapUi` does. It uses only `ui/adaptive` wrappers.

## Risks / Trade-offs

- [The top-start credit chip can overlap `RefreshStatus` at top-center on a narrow phone] → `RefreshStatus` only shows after a failed refresh of an already-loaded segment. Place it below the chip, or accept the brief overlap, and check on a phone-width screen.
- [A long agency attribution (up to 100 graphemes) makes a wide chip that covers part of the map] → Cap the chip's width to a fraction of the screen and let the text wrap.
- [The full-screen map loads the segment a second time] → One small query. In exchange the screen survives process death, as `RouteMapScreen` does.
- [On iOS, the edge-swipe back gesture can compete with panning near the left edge] → Already checked for `RouteMapScreen`, which uses the same map: the edge swipe went back without panning.
