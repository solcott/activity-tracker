# Spec Delta

## MODIFIED Requirements

### Requirement: Segments are created from one's own activities
The app SHALL offer to create a segment only on the signed-in athlete's own activity, and only when the activity has a full track and its sport has a sport group. The segment's sport group SHALL be the activity's. The app SHALL NOT offer to draw a segment freehand or to trace one from another athlete's activity. A track that can't be read SHALL NOT be treated as no track: creation SHALL say it couldn't load the route and offer to try again.

#### Scenario: Own outdoor ride
- **WHEN** an athlete opens their own ride that has a track
- **THEN** the app offers to create a segment from it, in the `ride` group

#### Scenario: Someone else's activity
- **WHEN** an athlete opens a followed athlete's run
- **THEN** the app doesn't offer to create a segment

#### Scenario: Activity without a sport group
- **WHEN** an athlete opens their own treadmill run, or a resort ski day
- **THEN** the app doesn't offer to create a segment

#### Scenario: Track can't be read
- **WHEN** an athlete creates a segment from their own ride and its track can't be read because the device is offline
- **THEN** the screen says it couldn't load the route because the device is offline and offers to try again, not that the ride has no route

### Requirement: A segment's line stays outside the athlete's privacy zones
The athlete SHALL choose a start and an end along their activity's track as cut by their privacy zones and hide-start/end distance, and the segment's line SHALL be the track between them. The line SHALL NOT include any point inside a zone or within the hidden ends, and SHALL NOT span a gap where the cut track is broken. The app SHALL NOT offer creation while it can't read the athlete's zones, and SHALL say why and offer to try again.

#### Scenario: Stretch away from home
- **WHEN** an athlete picks a 2 km climb that lies outside their zones
- **THEN** the segment's line follows their track over that climb

#### Scenario: Stretch across a zone
- **WHEN** an athlete's chosen start and end lie on either side of one of their privacy zones
- **THEN** the app refuses the selection and says the stretch crosses a privacy zone

#### Scenario: Zones unreadable
- **WHEN** the app can't read the athlete's privacy zones in this session
- **THEN** it doesn't offer to create a segment, says it couldn't load their privacy zones and why, and offers to try again

#### Scenario: Zones readable on retry
- **WHEN** the athlete tries again once their zones can be read
- **THEN** the screen shows that it is loading, then the route as their zones cut it, ready to choose a stretch
