# Spec Delta

## Purpose

An enlarged view of an activity's route that the athlete can pan and zoom. It opens from the activity's detail page, so the detail page's own map can stay a static part of the page.

## ADDED Requirements

### Requirement: The detail map opens the enlarged map
On Android, iOS and desktop, the detail page's map SHALL stay static: a drag over it scrolls the page or the media row, and it never pans the map. Tapping the map, or its expand button, SHALL open the activity's route full screen.

#### Scenario: Tap the map
- **WHEN** a viewer taps the map on a ride's detail page
- **THEN** the ride's route opens full screen

#### Scenario: Drag over the map
- **WHEN** a viewer drags up over the map on the detail page
- **THEN** the page scrolls and the map does not move

#### Scenario: Expand button
- **WHEN** a viewer activates the map's expand button, with a pointer or with assistive technology
- **THEN** the route opens full screen

### Requirement: The enlarged map pans and zooms
The enlarged map SHALL first frame the whole route. The viewer SHALL be able to pan it and zoom it in and out, with touch gestures on phones and with drag and scroll wheel on desktop. It SHALL stay north-up, with no rotation or tilt.

#### Scenario: Opened
- **WHEN** the enlarged map opens
- **THEN** it shows the whole route, framed as on the detail page

#### Scenario: Pinch on a phone
- **WHEN** a viewer pinches out on the enlarged map
- **THEN** the map zooms in around the pinch

#### Scenario: Scroll wheel on desktop
- **WHEN** a viewer scrolls the wheel over the enlarged map
- **THEN** the map zooms

#### Scenario: Twist
- **WHEN** a viewer twists two fingers on the enlarged map
- **THEN** the map does not rotate

### Requirement: Recenter on the route
The enlarged map SHALL offer a recenter control that frames the whole route again, however far the viewer has panned or zoomed.

#### Scenario: Lost the route
- **WHEN** a viewer has panned the route off screen and activates recenter
- **THEN** the map frames the whole route again

### Requirement: The enlarged map shows the detail page's route
The enlarged map SHALL draw the same line, breaks and markers as the activity's detail page shows the same viewer. The owner SHALL see their untrimmed track, with start and end marked where others' routes start and end. Every other viewer SHALL see only the trimmed polyline they are served.

#### Scenario: Owner
- **WHEN** the owner of a ride that starts inside their home zone opens its enlarged map
- **THEN** it draws the whole track, with the start marker where the track leaves the zone

#### Scenario: Follower
- **WHEN** a follower opens the enlarged map of that ride
- **THEN** it draws the trimmed polyline, starting outside the zone

#### Scenario: Line with a break
- **WHEN** the enlarged map of a ride whose line breaks at a GPS gap opens
- **THEN** it draws two lines with break markers at each end of the gap

### Requirement: Leaving the enlarged map
The enlarged map SHALL show the activity's title, and it SHALL have a back control that returns to the detail page. The platform's back gesture or button SHALL do the same.

#### Scenario: Back
- **WHEN** a viewer activates back on the enlarged map
- **THEN** the activity's detail page shows again

### Requirement: The enlarged map can't be loaded
If the activity can't be loaded, the enlarged map SHALL show an error in place of the map, and back SHALL still return to the detail page.

#### Scenario: Offline
- **WHEN** the enlarged map opens and the activity fails to load
- **THEN** it shows a load error and back still works

### Requirement: Not on web
The web app SHALL keep the detail page's static route preview, with no expand button and no tap target, until it can draw a real map.

#### Scenario: Web detail page
- **WHEN** a viewer taps the route on an activity's detail page in the web app
- **THEN** nothing opens
