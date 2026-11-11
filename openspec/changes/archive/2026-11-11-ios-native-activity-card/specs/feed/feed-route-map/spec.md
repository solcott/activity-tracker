# Spec Delta

## MODIFIED Requirements

### Requirement: Feed cards show the route on a map
On Android, iOS and desktop, a feed card with a route SHALL show it as a still image of the route drawn over a map. The image SHALL frame the whole route with a margin, north-up. It SHALL draw the line, breaks, dashed connectors and markers as the activity's page draws them for that viewer, so the existing route-markers, map-breaks and route-line-detail requirements hold for the image. A native iOS card SHALL show the route as the line preview instead, without a map, until native maps exist.

#### Scenario: Ride in the feed
- **WHEN** a follower's feed on Android shows a ride with a route
- **THEN** its card shows the ride's line over a map of the area it covers, with start and end markers

#### Scenario: Ride with a break
- **WHEN** a feed card on desktop shows a ride whose line breaks once
- **THEN** its map image draws two lines joined by a dashed connector, with stop and resume markers

#### Scenario: Card with photos
- **WHEN** a feed card shows an activity with a route and photos
- **THEN** the map image is the first tile of its media row, at the size the row gives the map

#### Scenario: Native card on iOS
- **WHEN** a native iOS page lists a ride with a route
- **THEN** its card draws the ride's line preview with its markers, and fetches no map tiles
