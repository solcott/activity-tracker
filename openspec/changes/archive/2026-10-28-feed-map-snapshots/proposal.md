# Proposal

## Why

A feed card draws an activity's route as a bare line on a blank tile (`RoutePreview`), so a viewer can't tell where it went: which town, which trail, which side of the river. The activity page already draws the route on a real MapLibre map. And maplibre-compose 0.20.0, which we already ship, now renders maps offscreen on Android, iOS and desktop (`MapSnapshotter`). So the feed can show the same map as an image, without a live map view per card and without any AppView work.

## What Changes

- On Android, iOS and desktop, a feed card's route tile shows the route drawn over a map image. The line, breaks, dashed connectors and markers are drawn as the activity page draws them, and the map is in the app's light or dark style.
- The image is drawn on the viewer's device from the polyline and breaks the viewer was served. So it shows only what that viewer may see of the route, at every visibility tier, and no route is sent anywhere new.
- Images are cached, so scrolling back over a card doesn't render it again. While an image renders, or when it can't (offline, tiles failing, no offscreen renderer), the card shows today's line preview in its place.
- Signing out drops the cached route images.
- Each image carries the map data credit (© OpenFreeMap © OpenStreetMap).
- Web keeps today's line preview until MapLibre has a web target.
- Previews, screenshot tests and UI tests keep drawing the line preview, so they stay deterministic.
- `BACKLOG.md`'s "Feed vs detail" note changes from server-rendered PNGs to device-rendered snapshots.

Out of scope:
- Switching Android from the OpenGL to the Vulkan renderer. An APK can carry only one MapLibre runtime, so that is a separate follow-up.
- Server-rendered images.
- Making the card's map interactive or tappable on its own.

## Capabilities

### New Capabilities
- `feed/feed-route-map`: how a feed card shows an activity's route: a map image drawn on the viewer's device from the route they were served, its fallback to the line preview, its caching and clearing, its credit, and web's line preview.

### Modified Capabilities
None. `feed/route-markers`, `feed/map-breaks` and `feed/route-line-detail` already require feed cards to draw markers, breaks, connectors and smooth curves, and the map image keeps meeting them.

## Impact

- **`composeApp`:**
  - `ActivityCard.kt` (the card's `map` slot).
  - `ActivityMap.nonWeb.kt` (its route layers move into a shared composable).
  - `App.kt` (the image loader's components and clearing them on sign-out).
  - New `nonWebMain` code: the snapshot renderer, Coil fetcher and keyer, camera fit and PNG encoding.
  - A new `wasmJs` actual.
- **Dependencies:** none added. It uses maplibre-compose 0.20.0 (`MapSnapshotter`, `DefaultMapRuntime`) and Coil 3.6.3, which the app already uses.
- **Network:** more OpenFreeMap tile requests, for each feed card rendered the first time.
- **Device:** the rendered images go in Coil's memory and disk caches until sign-out.
- **No lexicon, AppView or PDS change.**
