# Spec Delta

## ADDED Requirements

### Requirement: A native route map draws the shared route
On iOS a native page SHALL show a route on the system's map, drawn from the polyline and breaks the viewer was served: each piece smoothed as the shared map smooths it, in the accent colour on a white casing, a dashed connector across each break with its stop and resume markers, and the start and end markers where the viewer's map shows them.

#### Scenario: Ride with a break
- **WHEN** a native page shows a ride whose line breaks once
- **THEN** the map draws two lines joined by a dashed connector, with start, stop, resume and end markers

#### Scenario: Hidden ends
- **WHEN** the map shows a dive site's outline, whose ends are hidden
- **THEN** it draws the closed line with no start or end marker

### Requirement: A native route map frames the whole route
A native route map SHALL first frame the whole route north-up with the shared map's margin, never closer than the shared map's closest framing, and SHALL follow the light and dark appearance.

#### Scenario: A few metres of route
- **WHEN** the route spans ten metres
- **THEN** the map frames it no closer than the shared map's closest framing

### Requirement: A native route map sends the route nowhere
The route SHALL NOT be sent to any service; only the map's tiles are fetched, for the region shown, and the map SHALL show its provider's credit.

#### Scenario: Drawing a route
- **WHEN** a native page draws a route on its map
- **THEN** the line and markers are drawn on the device over the tiles, and the provider's credit shows on the map

### Requirement: A native route map is static unless asked otherwise
A native route map SHALL be static by default: a drag over it scrolls the page and never pans the map. Given an enlarged map to open, a tap on the map or its expand button SHALL open it. An interactive map SHALL pan and zoom, stay north-up, and offer Recenter, which frames the whole route again.

#### Scenario: Static on a page
- **WHEN** a viewer drags over the dive site page's map
- **THEN** the page scrolls and the map stays where it was

#### Scenario: Recenter
- **WHEN** a viewer pans an interactive map away and taps Recenter
- **THEN** the whole route is framed again

### Requirement: Previews and snapshots draw a route map without tiles
Xcode previews and snapshot tests SHALL draw a native route map as the line preview the native card draws, at the map's size, with the markers the map would show, and SHALL load no map tiles.

#### Scenario: A snapshot of a page with a map
- **WHEN** a snapshot suite renders the dive site page
- **THEN** the map's place shows the site's outline as a line preview, and the snapshot matches its reference on every run
