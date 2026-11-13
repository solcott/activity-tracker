## MODIFIED Requirements

### Requirement: A route map frames the same in every UI
The camera that frames a route north-up in a map of a given size, with a margin, any space a caller draws over the map's top kept clear, and never closer than the closest framing, SHALL be decided once in shared code, and the feed's map images, the native iOS map and the live Compose map on Android and desktop SHALL ask it, for the map's own size. The live map SHALL show the route framed as soon as it has a size, without first showing the world, and its Recenter SHALL frame the route the same way.

#### Scenario: Same frame
- **WHEN** a route is framed in a 360 by 240 point map by the native iOS map and by a feed map image of that size
- **THEN** both centre it on the same point at the same zoom

#### Scenario: Live Compose map
- **WHEN** an activity page on Android or desktop shows a route in a map 360 dp wide and 240 dp tall
- **THEN** the map centres it on the same point at the same zoom as the native iOS map and a feed map image of that size

#### Scenario: A very short route
- **WHEN** the live Compose map shows a route a few metres long
- **THEN** it is drawn no closer than the closest framing, as on iOS and in the feed's map images

#### Scenario: Recenter
- **WHEN** a viewer pans and zooms the enlarged map away from the route and taps Recenter
- **THEN** the map frames the route as it first did for its size
