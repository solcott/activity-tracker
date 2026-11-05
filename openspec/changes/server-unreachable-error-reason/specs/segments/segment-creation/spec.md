## MODIFIED Requirements

### Requirement: Segments are created from one's own activities
The app SHALL offer to create a segment only on the signed-in athlete's own activity, and only when the activity has a full track and its sport has a sport group. The segment's sport group SHALL be the activity's, and its kind SHALL be `run`. The app SHALL NOT offer to create a lift, to draw a segment freehand or to trace one from another athlete's activity. A track that can't be read SHALL NOT be treated as no track: creation SHALL say it couldn't load the route and offer to try again.

#### Scenario: Own outdoor ride
- **WHEN** an athlete opens their own ride that has a track
- **THEN** the app offers to create a segment from it, in the `ride` group

#### Scenario: Someone else's activity
- **WHEN** an athlete opens a followed athlete's run
- **THEN** the app doesn't offer to create a segment

#### Scenario: Activity without a sport group
- **WHEN** an athlete opens their own treadmill run or indoor ride
- **THEN** the app doesn't offer to create a segment

#### Scenario: Resort ski day
- **WHEN** an athlete opens their own resort ski day that has a track
- **THEN** the app offers to create a segment from it, in the `alpine` group, of kind `run`

#### Scenario: Track can't be read
- **WHEN** an athlete creates a segment from their own ride and its track can't be read because the device is offline
- **THEN** the screen says it couldn't load the route because the server couldn't be reached and offers to try again, not that the ride has no route
