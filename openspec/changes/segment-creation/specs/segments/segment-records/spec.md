# Spec Delta

## ADDED Requirements

### Requirement: Every segment has a status
The AppView SHALL give each indexed segment one status: `proposed`, `active`, `duplicate` or `archived`. It SHALL derive the status itself and never read it from the record. It SHALL serve the status in every segment view, and for a `duplicate`, the URI of the segment it duplicates.

#### Scenario: New segment
- **WHEN** the AppView indexes a new segment that duplicates nothing
- **THEN** its view shows the status `proposed`

#### Scenario: Status in the record ignored
- **WHEN** a `feed.segment` record carries a field claiming it is `active`
- **THEN** the AppView ignores it and the segment starts `proposed`

### Requirement: A proposed segment becomes active once ridden
A `proposed` segment SHALL become `active` once efforts on it come from at least a configured number of distinct athletes, 3 by default, its creator included. Efforts of every visibility SHALL count, but only the status SHALL be served, never who counted. An `active` segment SHALL stay `active` when efforts are later removed.

#### Scenario: Third athlete
- **WHEN** a proposed segment has efforts from its creator and one other athlete, and a third athlete's run matches it
- **THEN** the segment becomes `active`

#### Scenario: Private efforts count
- **WHEN** the third athlete's effort is on an Only me activity
- **THEN** the segment still becomes `active`, and no one else learns whose effort counted

#### Scenario: Athletes leave
- **WHEN** an active segment's athletes later turn matching off and their efforts are removed
- **THEN** the segment stays `active`

### Requirement: Unridden segments are archived
A segment still `proposed` 180 days after the AppView first indexed it SHALL become `archived`. An archived segment SHALL keep its existing efforts and SHALL be served by URI, but SHALL get no new efforts and SHALL NOT appear in search.

#### Scenario: Never ridden
- **WHEN** a segment created 181 days ago has efforts from only its creator
- **THEN** it becomes `archived` and stops appearing in search

### Requirement: Duplicates are detected on indexing
When the AppView indexes a new or changed segment, it SHALL mark it `duplicate` of the earliest-indexed `proposed` or `active` segment of the same sport group whose line it duplicates, and SHALL NOT match it. One line SHALL duplicate another when they run the same direction, their lengths differ by at most 20%, and following both from start to end never takes them more than 30 m apart.

#### Scenario: Same climb written straight to a PDS
- **WHEN** a client writes a ride segment over an existing ride segment's climb, with endpoints 10 m off
- **THEN** the AppView marks the new one `duplicate` of the existing one, and it gets no efforts

#### Scenario: Opposite direction
- **WHEN** a segment covers an existing segment's climb as a descent
- **THEN** it isn't a duplicate

#### Scenario: Different sport group
- **WHEN** a run segment follows an existing ride segment's line
- **THEN** it isn't a duplicate

#### Scenario: Longer stretch
- **WHEN** a segment covers an existing 1 km segment plus 2 km more of the trail
- **THEN** it isn't a duplicate

### Requirement: Duplicates are re-checked when their original changes
When a segment that others duplicate is deleted, archived, or changed so they no longer duplicate it, the AppView SHALL check each of those duplicates again, earliest-indexed first. One that duplicates nothing else SHALL become `proposed` and be matched against past segment tracks. Earliest-indexed SHALL be when the AppView first indexed the segment, not when it was last changed.

#### Scenario: Original deleted
- **WHEN** the creator of a segment that one other segment duplicates deletes theirs
- **THEN** the other segment becomes `proposed`, and past activities that pass it get their efforts

### Requirement: Segment creation is limited per athlete
The AppView SHALL index at most 10 new segments per creator in any 24 hours and SHALL drop any further new segment from that creator in that window. Re-writing an already indexed segment SHALL NOT count.

#### Scenario: Eleventh segment
- **WHEN** an athlete's repo gains an eleventh new segment within 24 hours of their first
- **THEN** the AppView doesn't index it

#### Scenario: Renaming
- **WHEN** an athlete who created 10 segments today renames one
- **THEN** the AppView indexes the new name

### Requirement: A proposed line can be checked before publishing
`feed.checkSegment` SHALL take a sport group and a line from a signed-in athlete and return every `proposed` or `active` segment the line would duplicate, by the same test as indexing, plus how many more new segments the athlete may create in the current 24 hours. A malformed line or an unknown sport group SHALL be refused as `InvalidRequest`. A viewer who isn't signed in SHALL be refused.

#### Scenario: Duplicate found
- **WHEN** an athlete checks a line over an existing segment's climb
- **THEN** the AppView returns that segment

#### Scenario: Nothing similar
- **WHEN** an athlete checks a line on a trail with no segments, having created 2 today
- **THEN** the AppView returns no segments and an allowance of 8

## MODIFIED Requirements

### Requirement: Only valid segments are indexed
The AppView SHALL index a segment only if its line has at least two positions, its length along the line is at least its sport group's minimum (500 m for `ride`, 200 m for every other group) and at most 50 km, its name is non-empty after trimming, and its sport group is known. It SHALL drop any other segment record, and SHALL compute a segment's length itself rather than trust a length the record states.

#### Scenario: Too short
- **WHEN** an athlete writes a `feed.segment` whose line is 150 m long
- **THEN** the AppView doesn't index it

#### Scenario: Ride under its minimum
- **WHEN** an athlete writes a `ride` segment whose line is 350 m long
- **THEN** the AppView doesn't index it, though a `run` segment of that length would be

#### Scenario: Unknown sport group
- **WHEN** a `feed.segment` names the sport group `skydive`
- **THEN** the AppView doesn't index it

### Requirement: A segment can be looked up by URI
`feed.getSegment` SHALL return a segment's view by its URI, whatever its status: its creator, name, sport group, line, length, source, licence and status, and for a duplicate the segment it duplicates. For a signed-in viewer it SHALL also give the viewer's own fastest effort on it, if they have one. A segment that isn't indexed SHALL be `NotFound`.

#### Scenario: Viewer has efforts
- **WHEN** a signed-in athlete with three efforts on a segment opens it
- **THEN** the view includes their fastest effort

#### Scenario: Duplicate
- **WHEN** a viewer asks for a segment marked `duplicate`
- **THEN** the view has the status `duplicate` and the URI of the segment it duplicates

#### Scenario: Unknown segment
- **WHEN** a viewer asks for a segment URI the AppView hasn't indexed
- **THEN** the AppView answers `NotFound`

### Requirement: Segments can be found by area
`feed.searchSegments` SHALL return the indexed segments whose line passes through a bounding box, optionally only those of one sport group, at most 100 per page with a cursor. It SHALL return `active` segments to every viewer, and a `proposed` segment only to its creator and to athletes who have an effort on it. It SHALL never return `duplicate` or `archived` segments. A bounding box wider than 1 degree on either side SHALL be refused as `InvalidRequest`.

#### Scenario: Map area
- **WHEN** a viewer searches a 5 km box around a trail centre for `ride` segments
- **THEN** they get the active ride segments whose lines cross that box and no run segments

#### Scenario: Proposed segment
- **WHEN** a proposed segment lies in the box
- **THEN** its creator and athletes with an effort on it get it, and other viewers don't

#### Scenario: Box too large
- **WHEN** a viewer searches a box 3 degrees wide
- **THEN** the AppView answers `InvalidRequest`

### Requirement: Segments for local testing come from a seed script
A development script SHALL write a `feed.segment` to a local test account's repo from a GPX file or a start and end along a sample route, with a name and sport group, so segments can be set up for local testing without the app.

#### Scenario: Seeding a segment
- **WHEN** a developer runs the seed script with `alice`, a sample GPX file and the sport group `run`
- **THEN** `alice.test`'s repo holds a `feed.segment` with that line, and the AppView indexes it
