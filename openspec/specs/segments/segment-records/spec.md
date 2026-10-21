# segments/segment-records Specification

## Purpose

Defines what a segment is: a named, public stretch of route for one sport group. Covers the record that holds it, the rules a segment must meet to count, and how the AppView indexes segments and serves them by URI and by area.

## Requirements

### Requirement: A segment is a public record
A segment SHALL be a `feed.segment` record in its creator's repo, never in a space. It SHALL hold a name, a sport group, its line as an ordered list of positions from start to end, its source, and the licence of the data it came from. It MAY name the activity it was traced from.

#### Scenario: Segment from an athlete
- **WHEN** an athlete's repo holds a `feed.segment` with a name, the sport group `ride` and a line
- **THEN** the AppView indexes it as a ride segment created by that athlete

#### Scenario: Segment in a space ignored
- **WHEN** a `feed.segment` record appears in an athlete's tier or `self` space
- **THEN** the AppView indexes nothing from it

### Requirement: A segment's sport group decides what can match it
Every segment SHALL have exactly one sport group: `ride`, `run`, `hike`, `ski` or `paddle`. Each activity type SHALL map to at most one sport group, and an activity type without one SHALL never match a segment. E-bike rides SHALL map to `ride` and be marked as e-bike.

#### Scenario: Trail run on a run segment
- **WHEN** an athlete's trail run passes a `run` segment
- **THEN** it can match the segment

#### Scenario: Ride on a run segment
- **WHEN** an athlete's road ride passes a `run` segment
- **THEN** it never matches the segment

#### Scenario: Indoor activity
- **WHEN** an activity type is indoor, such as a treadmill run or an indoor ride
- **THEN** it has no sport group and matches no segment

#### Scenario: E-bike ride
- **WHEN** an athlete's e-mountain-bike ride passes a `ride` segment
- **THEN** it can match the segment, and its effort is marked as e-bike

### Requirement: Only valid segments are indexed
The AppView SHALL index a segment only if its line has at least two positions, its length along the line is between 200 m and 50 km, its name is non-empty after trimming, and its sport group is known. It SHALL drop any other segment record, and SHALL compute a segment's length itself rather than trust a length the record states.

#### Scenario: Too short
- **WHEN** an athlete writes a `feed.segment` whose line is 150 m long
- **THEN** the AppView doesn't index it

#### Scenario: Unknown sport group
- **WHEN** a `feed.segment` names the sport group `skydive`
- **THEN** the AppView doesn't index it

### Requirement: Editing or deleting a segment updates it everywhere
Re-writing a segment record SHALL replace its indexed version. Deleting it SHALL remove the segment and every effort on it. Changing a segment's line or sport group SHALL recompute its efforts against the new version.

#### Scenario: Segment deleted
- **WHEN** a segment's creator deletes its record
- **THEN** the AppView serves neither the segment nor any effort on it

#### Scenario: Line changed
- **WHEN** a segment's creator moves its end further along the trail
- **THEN** its efforts are recomputed, and passes that no longer reach the new end lose their efforts

### Requirement: A segment can be looked up by URI
`feed.getSegment` SHALL return a segment's view by its URI: its creator, name, sport group, line, length, source and licence. For a signed-in viewer it SHALL also give the viewer's own fastest effort on it, if they have one. A segment that isn't indexed SHALL be `NotFound`.

#### Scenario: Viewer has efforts
- **WHEN** a signed-in athlete with three efforts on a segment opens it
- **THEN** the view includes their fastest effort

#### Scenario: Unknown segment
- **WHEN** a viewer asks for a segment URI the AppView hasn't indexed
- **THEN** the AppView answers `NotFound`

### Requirement: Segments can be found by area
`feed.searchSegments` SHALL return the indexed segments whose line passes through a bounding box, optionally only those of one sport group, at most 100 per page with a cursor. A bounding box wider than 1 degree on either side SHALL be refused as `InvalidRequest`.

#### Scenario: Map area
- **WHEN** a viewer searches a 5 km box around a trail centre for `ride` segments
- **THEN** they get the ride segments whose lines cross that box and no run segments

#### Scenario: Box too large
- **WHEN** a viewer searches a box 3 degrees wide
- **THEN** the AppView answers `InvalidRequest`

### Requirement: Segments for local testing come from a seed script
Until segments can be created in the app, a development script SHALL write a `feed.segment` to a local test account's repo from a GPX file or a start and end along a sample route, with a name and sport group. The app SHALL NOT offer to create segments in this change.

#### Scenario: Seeding a segment
- **WHEN** a developer runs the seed script with `alice`, a sample GPX file and the sport group `run`
- **THEN** `alice.test`'s repo holds a `feed.segment` with that line, and the AppView indexes it
