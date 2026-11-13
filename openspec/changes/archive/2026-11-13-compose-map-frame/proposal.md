# Proposal

## Why

The live Compose map (Android and desktop: the activity page's map, the enlarged map, the segment and dive site pages) still frames a route
with MapLibre's `cameraForBounds`, which has no closest zoom, so a very short route fills the map with one building there, while the feed's
map images and the native iOS map frame it with the shared `MapFrame` (margin 24, closest zoom 16). The roadmap's last map item is to move
the live map onto the same frame.

## What Changes

- The live Compose map sets its camera from `RouteMap.frame` for its own measured size, with the space it draws over its top kept clear, as
  soon as it is measured: it no longer waits for MapLibre's viewport to ask `cameraForBounds`.
- Recenter on an interactive map flies to the same frame for the map's current size.
- The map frames again when its route or its size changes; an interactive map frames again only when its route changes, so a resize keeps
  the viewer's pan.
- `RouteCamera.kt` shrinks to the conversion from `MapFrame` to MapLibre's camera, which the feed's map images and the live map share;
  `fitCamera`, `FIT_MARGIN`, `MAX_FIT_ZOOM` and `RouteCameraTest` (a copy of `RouteMapTest`'s frame tests) go.
- Web is unchanged: it draws the line preview, with no live map.

Visible changes, Android and desktop only: a route a few metres long is drawn no closer than zoom 16, as on iOS and in the feed's images.
A longer route is framed as before (the same margin and Web Mercator fit).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `dev/shared-display-text`: the live Compose map asks the shared route frame too, so every UI's route maps frame a route the same way.

## Impact

`app/common/ui/src/nonWebMain/.../ui/ActivityMap.nonWeb.kt`, `RouteCamera.kt`, `RouteSnapshots.kt`, `jvmTest/.../RouteCameraTest.kt`.
No API, lexicon or dependency changes. Android screenshot references don't draw the live map (previews draw the line preview), so none is
expected to change.
