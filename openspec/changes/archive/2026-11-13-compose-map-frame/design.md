# Design

## Context

`MapFrame.fit` (`:app-common-api`, `RouteMap.kt`) is the one framing rule: Web Mercator with a 512-unit world at zoom 0 (MapLibre's zoom),
margin 24, closest zoom 16, an optional top inset. `RouteMap.frame(width, height, top)` asks it for a route's bounds. The feed's map images
use it through `fitCamera` (`RouteCamera.kt`), the native iOS map through `RouteMapView`. The live Compose map
(`ActivityMap.nonWeb.kt`) instead asks `MapState.cameraForBounds(bounds, CameraFit(padding))`, which suspends until MapLibre attaches a
viewport and has no zoom limit, then `setCameraPosition`s the result; Recenter calls `animateCameraToBounds`.

MapLibre Compose 0.20's `MapState.setCameraPosition` sets a durable camera and applies it when a surface attaches, so a camera computed
without the map's viewport can be set at once. Compose measures in dp, MapLibre's logical units, so a frame for the map's dp size is the
camera MapLibre would draw at that size.

## Goals / Non-Goals

**Goals:** the live map frames with `RouteMap.frame` for its measured size; the jump to the route doesn't wait for a viewport; one
`MapFrame` → `CameraPosition` conversion shared by the map and its images.

**Non-Goals:** a live map on web; changing the frame rule itself; the segments browse map (it frames by the viewer's area, not a route).

## Decisions

- **Measure the map with `onSizeChanged`** on the `MaplibreMap`'s own modifier, kept as a `DpSize?` state. Until it's measured nothing is
  set, which is before MapLibre has a surface anyway. Alternative: `BoxWithConstraints` — another layout around the map for the same width,
  and its height would be the caller's `height` anyway; rejected.
- **When to frame:** a `LaunchedEffect` keyed on the route's `key`, `overlayTop` and the size. For an interactive map the size is in the key
  only as "measured or not", so resizing the window or rotating keeps the viewer's pan (as iOS reframes only on `key`); a static map frames
  again on every size, so the route stays framed in a resized card. Keying on `RouteMap.key` rather than the map object leaves a moving
  highlight unframed, as `RouteMap.key` intends.
- **Recenter** animates to `RouteMap.frame` for the current size with `animateCamera(CameraUpdate(center, zoom), CameraAnimation.Fly.Standard)`,
  the same flight `animateCameraToBounds` used. The `CameraFit` and its padding go.
- **`RouteCamera.kt`** keeps one function, `MapFrame.toCameraPosition()`, used by `ActivityMap` and `RouteSnapshots` (which now asks
  `RouteMap.frame(width, height)` instead of rebuilding a `BoundingBox`). `fitCamera`, `FIT_MARGIN` and `MAX_FIT_ZOOM` lose their callers;
  `RouteCameraTest` duplicates `RouteMapTest`'s frame tests through a wrapper that only converts types, so it goes.

## Risks / Trade-offs

- [MapLibre's camera padding or insets differ from zero] → the camera sets no padding, and the frame already accounts for the margin and
  `overlayTop`; checked by eye on desktop against the feed image.
- [The measured size lags a frame behind a resize] → the next `onSizeChanged` reframes a static map; an interactive one keeps its camera by
  design.
