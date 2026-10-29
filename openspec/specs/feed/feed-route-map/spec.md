# feed/feed-route-map Specification

## Purpose

How a feed card shows an activity's route: a map image on phones and desktop, drawn on the viewer's device from the route they were served, with the line preview as its fallback and as web's map.

## Requirements

### Requirement: Feed cards show the route on a map
On Android, iOS and desktop, a feed card with a route SHALL show it as a still image of the route drawn over a map. The image SHALL frame the whole route with a margin, north-up. It SHALL draw the line, breaks, dashed connectors and markers as the activity's page draws them for that viewer, so the existing route-markers, map-breaks and route-line-detail requirements hold for the image.

#### Scenario: Ride in the feed
- **WHEN** a follower's feed on Android shows a ride with a route
- **THEN** its card shows the ride's line over a map of the area it covers, with start and end markers

#### Scenario: Ride with a break
- **WHEN** a feed card on desktop shows a ride whose line breaks once
- **THEN** its map image draws two lines joined by a dashed connector, with stop and resume markers

#### Scenario: Card with photos
- **WHEN** a feed card shows an activity with a route and photos
- **THEN** the map image is the first tile of its media row, at the size the row gives the map

### Requirement: The map shows only the route the viewer was served
A feed card's map image SHALL be drawn on the viewer's device from the polyline and breaks the viewer was served for that activity, and from nothing else. A viewer not served the activity's map SHALL see no map tile. The route SHALL NOT be sent to any service to draw the image; only the map's tiles and style are fetched.

#### Scenario: Map hidden from followers
- **WHEN** a follower's feed shows a run whose Map category is Only me
- **THEN** its card shows no map tile

#### Scenario: Map shared with mutuals
- **WHEN** a mutual's feed shows a ride whose Map category is Mutuals
- **THEN** its card's map image draws the trimmed polyline that mutual was served

#### Scenario: Own activity
- **WHEN** an athlete's feed shows their own ride that starts inside a privacy zone
- **THEN** its card's map image draws the trimmed polyline, as their followers see it

### Requirement: The map follows the app's theme
The map image SHALL use the light map style in the light theme and the dark map style in the dark theme, and SHALL change when the theme does.

#### Scenario: Switch to dark
- **WHEN** the viewer switches the device to dark mode with the feed open
- **THEN** the feed cards' map images are redrawn in the dark map style

### Requirement: The line preview stands in for the map
While a card's map image is being drawn, and whenever it can't be drawn, the card SHALL show the route as a line preview in the image's place, at the same size, with its breaks and markers. A failure SHALL NOT show an error, leave the tile empty or crash the feed. Causes include no network, tiles failing to load and no offscreen renderer on the device.

#### Scenario: Offline feed
- **WHEN** the viewer opens the feed with no network and no cached map images
- **THEN** each card with a route shows its line preview

#### Scenario: Image ready
- **WHEN** a card's map image finishes drawing while the card is on screen
- **THEN** the image replaces the card's line preview in the same place

### Requirement: Drawn maps are reused
A map image drawn for a card SHALL be reused when the same route is shown again at the same size and theme, including after the app restarts, without drawing it again. A route whose polyline or breaks change SHALL get a new image.

#### Scenario: Scroll back
- **WHEN** the viewer scrolls past a card and back up to it
- **THEN** its map image shows at once, without the line preview

#### Scenario: Retrimmed route
- **WHEN** an athlete adds a privacy zone and their activity's polyline is retrimmed
- **THEN** the activity's feed card draws a new image of the trimmed route

### Requirement: Signing out drops cached maps
When the athlete signs out, the app SHALL remove the feed map images it drew, from memory and from the device's storage.

#### Scenario: Sign out
- **WHEN** an athlete who viewed followers-only rides in their feed signs out
- **THEN** none of those rides' map images remain on the device

### Requirement: The map credits its data
Every feed map image SHALL show the map data's credit (© OpenFreeMap © OpenStreetMap) legibly within its own bounds.

#### Scenario: Card credit
- **WHEN** a feed card shows a map image
- **THEN** the credit is readable in a corner of the image

### Requirement: Web keeps the line preview
The web app SHALL show a feed card's route as the line preview until it can draw a real map.

#### Scenario: Web feed
- **WHEN** a viewer opens the feed in the web app
- **THEN** each card with a route shows its line preview

### Requirement: Previews and tests draw the line preview
Compose previews, screenshot tests and UI tests SHALL draw a feed card's route as the line preview, with no map renderer, network or tiles.

#### Scenario: Screenshot test
- **WHEN** the screenshot tests render a feed card with a route
- **THEN** the card shows the line preview and its reference image is unchanged
