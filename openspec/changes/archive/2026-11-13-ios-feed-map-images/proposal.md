# Proposal

## Why

Native iOS feed cards draw an activity's route as the line preview on a grey tile, with no map, while Android and desktop show a map
image. `feed/feed-route-map` kept iOS on the preview "until native maps exist"; `ios-native-map` has since brought MapKit in for every
live route map, so the condition is met and the feed is the one place left without a map.

## What Changes

- An activity card's route tile on iOS is a still map image: MapKit's snapshot of the region `RouteMap.frame` gives for the tile's size,
  in the current appearance, with the route drawn over it as the live map draws it (cased line, dashed connectors, break, start and
  end markers).
- The line preview stays in the image's place while it draws, whenever it can't be drawn, and outside a signed-in session (`\.liveMaps`),
  so previews and snapshot tests fetch nothing. A failure shows no error.
- Images are kept in memory by route, size and appearance, so scrolling back doesn't draw them again; a change of appearance draws the
  other style.
- `ActivityCardContent.routeMap` gives Swift the served route as a `RouteMap`. Only Apple's tiles are fetched; the route stays on the
  device.
- Import cards' small route sketches stay line previews.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `feed/feed-route-map`: native iOS cards show a map image too.

## Impact

- `:app-common-api` (`ActivityCardContent`), `iosApp` (`MediaStrip`, a new `RouteMapImage`), the roadmap.
