# Spec Delta

## Purpose

An enlarged view of a segment's line that the athlete can pan and zoom. It opens from the segment screen, so the segment screen's own map can stay a static part of the page.

## ADDED Requirements

### Requirement: The segment screen's map opens the enlarged map
On Android, iOS and desktop, the segment screen's map SHALL stay static: a drag over it scrolls the screen, and it never pans the map. Tapping the map, or its expand button, SHALL open the segment's line full screen.

#### Scenario: Tap the map
- **WHEN** a viewer taps the map on a segment's screen
- **THEN** the segment's line opens full screen

#### Scenario: Drag over the map
- **WHEN** a viewer drags up over the map on the segment screen
- **THEN** the screen scrolls and the map does not move

#### Scenario: Expand button
- **WHEN** a viewer activates the map's expand button, with a pointer or with assistive technology
- **THEN** the segment's line opens full screen

### Requirement: The enlarged segment map pans and zooms
The enlarged map SHALL first frame the whole segment. The viewer SHALL be able to pan it and zoom it in and out, with touch gestures on phones and with drag and scroll wheel on desktop. It SHALL stay north-up, with no rotation or tilt.

#### Scenario: Opened
- **WHEN** the enlarged map of a segment opens
- **THEN** it shows the whole segment, framed as on the segment screen

#### Scenario: Pinch on a phone
- **WHEN** a viewer pinches out on the enlarged map
- **THEN** the map zooms in around the pinch

#### Scenario: Scroll wheel on desktop
- **WHEN** a viewer scrolls the wheel over the enlarged map
- **THEN** the map zooms

#### Scenario: Twist
- **WHEN** a viewer twists two fingers on the enlarged map
- **THEN** the map does not rotate

### Requirement: Recenter on the segment
The enlarged map SHALL offer a recenter control that frames the whole segment again, however far the viewer has panned or zoomed.

#### Scenario: Lost the segment
- **WHEN** a viewer has panned the segment off screen and activates recenter
- **THEN** the map frames the whole segment again

### Requirement: The enlarged map shows the segment screen's line
The enlarged map SHALL draw the same line, with the same start and finish markers, as the segment screen draws for the same segment.

#### Scenario: Segment line
- **WHEN** a viewer opens the enlarged map of a segment
- **THEN** it draws the segment's line from start to finish with its start and finish marked

### Requirement: The enlarged map credits the segment's data
The enlarged map SHALL show the segment's data credit, as `segments/segment-records` defines it, over the map and clear of the map's own controls and attributions. It SHALL stay visible while the viewer pans and zooms. Framing the segment, when the map opens and on recenter, SHALL keep the line below it. An athlete's segment SHALL show no data credit on it.

#### Scenario: OpenStreetMap segment
- **WHEN** a viewer opens the enlarged map of a segment whose record has `source` `osm` and `license` `ODbL-1.0`
- **THEN** the map shows "© OpenStreetMap contributors" and the ODbL, and tapping the credit opens OpenStreetMap's copyright page

#### Scenario: Agency segment
- **WHEN** a viewer opens the enlarged map of a segment whose record has `source` `agency` and the attribution "USDA Forest Service"
- **THEN** the map credits trail data from the USDA Forest Service

#### Scenario: Athlete segment
- **WHEN** a viewer opens the enlarged map of a segment an athlete created from their ride
- **THEN** the map shows no data credit

#### Scenario: Line starting under the credit
- **WHEN** a viewer opens the enlarged map of an agency segment whose line starts in its top-left corner
- **THEN** the whole line, start marker included, shows below the credit

#### Scenario: Credit while zoomed
- **WHEN** a viewer zooms into an OpenStreetMap segment's enlarged map
- **THEN** the OpenStreetMap credit is still shown

### Requirement: Leaving the enlarged segment map
The enlarged map SHALL show the segment's name, and it SHALL have a back control that returns to the segment screen. The platform's back gesture or button SHALL do the same.

#### Scenario: Back
- **WHEN** a viewer activates back on the enlarged map
- **THEN** the segment screen shows again

### Requirement: The enlarged segment map can't be loaded
If the segment can't be loaded, the enlarged map SHALL show an error in place of the map, with a way to try again, and back SHALL still return to the segment screen.

#### Scenario: Offline
- **WHEN** the enlarged map opens and the segment fails to load
- **THEN** it shows a load error and back still works

### Requirement: No enlarged segment map on web
The web app SHALL keep the segment screen's static line preview, with no expand button and no tap target, until it can draw a real map.

#### Scenario: Web segment screen
- **WHEN** a viewer taps the line on a segment's screen in the web app
- **THEN** nothing opens
