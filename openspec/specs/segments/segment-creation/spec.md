# segments/segment-creation Specification

## Purpose

Lets athletes create segments in the app from their own activities. Covers which activities and stretches qualify, the duplicate check and daily allowance shown before publishing, and what the public segment record may reveal about the activity it came from.

## Requirements

### Requirement: Segments are created from one's own activities
The app SHALL offer to create a segment only on the signed-in athlete's own activity, and only when the activity has a full track and its sport has a sport group. The segment's sport group SHALL be the activity's. The app SHALL NOT offer to draw a segment freehand or to trace one from another athlete's activity.

#### Scenario: Own outdoor ride
- **WHEN** an athlete opens their own ride that has a track
- **THEN** the app offers to create a segment from it, in the `ride` group

#### Scenario: Someone else's activity
- **WHEN** an athlete opens a followed athlete's run
- **THEN** the app doesn't offer to create a segment

#### Scenario: Activity without a sport group
- **WHEN** an athlete opens their own treadmill run, or a resort ski day
- **THEN** the app doesn't offer to create a segment

### Requirement: A segment's line stays outside the athlete's privacy zones
The athlete SHALL choose a start and an end along their activity's track as cut by their privacy zones and hide-start/end distance, and the segment's line SHALL be the track between them. The line SHALL NOT include any point inside a zone or within the hidden ends, and SHALL NOT span a gap where the cut track is broken. The app SHALL NOT offer creation while it can't read the athlete's zones.

#### Scenario: Stretch away from home
- **WHEN** an athlete picks a 2 km climb that lies outside their zones
- **THEN** the segment's line follows their track over that climb

#### Scenario: Stretch across a zone
- **WHEN** an athlete's chosen start and end lie on either side of one of their privacy zones
- **THEN** the app refuses the selection and says the stretch crosses a privacy zone

#### Scenario: Zones unreadable
- **WHEN** the app can't read the athlete's privacy zones in this session
- **THEN** it doesn't offer to create a segment until it can

### Requirement: The app warns that a segment is public
Before publishing, the app SHALL tell the athlete that the segment's line and name will be public, whatever the activity's visibility. The segment SHALL name its source activity only when the activity and its Map category are both Public. Otherwise it SHALL name none.

#### Scenario: Followers-only activity
- **WHEN** an athlete creates a segment from a Followers run
- **THEN** the app warns that the line will be public, and the published segment doesn't name the run

#### Scenario: Public activity
- **WHEN** an athlete creates a segment from a Public ride whose Map category is Public
- **THEN** the published segment names the ride as its source

### Requirement: Selections shorter than the minimum are refused
The app SHALL refuse a selection shorter than its sport group's minimum segment length (500 m for `ride`, 200 m for every other group) or longer than 50 km, and SHALL say why.

#### Scenario: Short ride stretch
- **WHEN** an athlete selects 350 m of a ride
- **THEN** the app refuses it and says ride segments must be at least 500 m

#### Scenario: Short run stretch
- **WHEN** an athlete selects 350 m of a run
- **THEN** the app accepts the selection

### Requirement: Duplicates are offered instead of published
Before publishing, the app SHALL ask the AppView whether the line duplicates an existing proposed or active segment of the same sport group. If it does, the app SHALL show the existing segment, let the athlete open it, and SHALL NOT publish the new one. If the check can't be made, the app SHALL NOT publish and SHALL let the athlete retry.

#### Scenario: Same climb exists
- **WHEN** an athlete selects a climb that another athlete already made a ride segment of
- **THEN** the app shows that segment and doesn't publish a new one

#### Scenario: Offline
- **WHEN** the duplicate check fails because the device is offline
- **THEN** the app shows an error with a retry, and publishes nothing

### Requirement: The daily allowance is shown before publishing
The duplicate check SHALL also report how many more segments the athlete may create in the current 24 hours. When none are left, the app SHALL say so and SHALL NOT publish.

#### Scenario: Limit reached
- **WHEN** an athlete who created 10 segments in the past 24 hours tries to create another
- **THEN** the app says they've reached today's limit and publishes nothing

### Requirement: A created segment is queued like other writes
Publishing SHALL queue the `feed.segment` record, with `source` `athlete`, a licence of `CC0-1.0`, the chosen name and the line, in the athlete's repo through the outbox. The app SHALL then return to the activity and confirm that the segment was created and will appear once processed.

#### Scenario: Segment published
- **WHEN** an athlete names a selection "Mill Hill climb" and publishes it
- **THEN** their repo gets a `feed.segment` with that name and line, and the app returns to the activity with a confirmation

#### Scenario: Matched later
- **WHEN** the AppView has indexed the new segment and matched the athlete's activity against it
- **THEN** the activity's segments list shows the effort, marked as proposed
