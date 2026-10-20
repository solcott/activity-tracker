# feed/route-markers Specification

## Purpose

Markers on an activity's map that show where it starts and ends and where its line breaks.

## Requirements

### Requirement: The map marks the start and end
Every route map of an activity (its detail page, its enlarged map, its feed card and the import preview) SHALL mark the first position of its line with a start marker and the last with an end marker, the end drawn above the start.

#### Scenario: Out-and-back run
- **WHEN** a run that goes out 5 km and back by another street is shown
- **THEN** its map shows a start marker at the first position and an end marker at the last

#### Scenario: Loop
- **WHEN** a ride that finishes where it started is shown
- **THEN** its end marker is drawn on top of its start marker

### Requirement: Breaks are marked differently
Where the line breaks (`polylineGaps`), the map SHALL mark the last position before the break with a stop marker and the first after it with a resume marker. Both SHALL be at least as large as the start and end markers and share a colour that differs from the route's, the start marker's and the end marker's; the stop and resume markers SHALL differ from each other in shape. A line with no breaks SHALL show no break markers.

#### Scenario: Dead GPS
- **WHEN** a ride whose line breaks once at a GPS gap is shown
- **THEN** its map shows a start marker, an end marker, a stop marker at the last position before the gap and a resume marker at the first after it

#### Scenario: Combined activity
- **WHEN** an activity combined from two recordings is shown
- **THEN** its map shows a stop marker where the first recording ends, a resume marker where the second begins, and start and end markers only at the ends of the whole activity

#### Scenario: Break seen on a zoomed-out card
- **WHEN** a feed card shows a ride whose 288 m break spans a few points of the card
- **THEN** the break's markers stand out from the route line in colour and are no smaller than its start marker

### Requirement: Markers respect privacy zones
No start or end marker SHALL be drawn inside the athlete's privacy zones or hidden route ends. Viewers' maps SHALL mark the ends of the trimmed polyline they are shown. The owner's map, which draws their full untrimmed track, SHALL mark the start and end where the route others see starts and ends (each member trimmed on its own on a combined activity), and SHALL draw no start or end marker while their privacy settings can't be read or when they hide the whole route.

#### Scenario: Ride from home
- **WHEN** the owner of a ride that starts inside their 200 m home zone opens it
- **THEN** their map draws the whole track but puts the start marker where the track leaves the zone, where followers' maps start the line

#### Scenario: Zones not yet read
- **WHEN** the owner opens their activity before their privacy settings have been read
- **THEN** the map shows break markers but no start or end marker until they are
