# segments/segment-records Specification

## Purpose

Defines what a segment is: a named, public stretch of route for one sport group. Covers the record that holds it, the rules a segment must meet to count, and how the AppView indexes segments and serves them by URI and by area.

## Requirements

### Requirement: A segment is a public record
A segment SHALL be a `feed.segment` record in its creator's repo, never in a space. It SHALL hold a name, a sport group, its line as an ordered list of positions from start to end, its source, and the licence of the data it came from. It MAY name the activity it was traced from, and MAY name who to credit for its data as an attribution of at most 100 graphemes.

#### Scenario: Segment from an athlete
- **WHEN** an athlete's repo holds a `feed.segment` with a name, the sport group `ride` and a line
- **THEN** the AppView indexes it as a ride segment created by that athlete

#### Scenario: Segment in a space ignored
- **WHEN** a `feed.segment` record appears in an athlete's tier or `self` space
- **THEN** the AppView indexes nothing from it

#### Scenario: Segment with an attribution
- **WHEN** the importer's repo holds a `feed.segment` with `source` `agency` and the attribution "National Park Service"
- **THEN** the AppView indexes it and serves the attribution in the segment's record

#### Scenario: Attribution out of bounds
- **WHEN** a `feed.segment` has a blank attribution, or one over 100 graphemes
- **THEN** the AppView indexes the segment without it

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

### Requirement: Editing or deleting a segment updates it everywhere
Re-writing a segment record SHALL replace its indexed version. Deleting it SHALL remove the segment and every effort on it. Changing a segment's line or sport group SHALL recompute its efforts against the new version.

#### Scenario: Segment deleted
- **WHEN** a segment's creator deletes its record
- **THEN** the AppView serves neither the segment nor any effort on it

#### Scenario: Line changed
- **WHEN** a segment's creator moves its end further along the trail
- **THEN** its efforts are recomputed, and passes that no longer reach the new end lose their efforts

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
The AppView SHALL index at most 10 new segments per creator in any 24 hours and SHALL drop any further new segment from that creator in that window. Re-writing an already indexed segment SHALL NOT count. Segments from the account the AppView is configured to accept as the segment importer SHALL NOT be limited, and `feed.checkSegment` SHALL always report that account's allowance as the full 10.

#### Scenario: Eleventh segment
- **WHEN** an athlete's repo gains an eleventh new segment within 24 hours of their first
- **THEN** the AppView doesn't index it

#### Scenario: Renaming
- **WHEN** an athlete who created 10 segments today renames one
- **THEN** the AppView indexes the new name

#### Scenario: Importer publishes a region
- **WHEN** the segment importer's repo gains 200 new segments in an hour
- **THEN** the AppView indexes all 200

### Requirement: A proposed line can be checked before publishing
`feed.checkSegment` SHALL take a sport group and a line from a signed-in athlete and return every `proposed` or `active` segment the line would duplicate, by the same test as indexing, plus how many more new segments the athlete may create in the current 24 hours. A malformed line or an unknown sport group SHALL be refused as `InvalidRequest`. A viewer who isn't signed in SHALL be refused.

#### Scenario: Duplicate found
- **WHEN** an athlete checks a line over an existing segment's climb
- **THEN** the AppView returns that segment

#### Scenario: Nothing similar
- **WHEN** an athlete checks a line on a trail with no segments, having created 2 today
- **THEN** the AppView returns no segments and an allowance of 8

### Requirement: A segment's data source is credited where its line is drawn
Wherever the app draws a segment's line, it SHALL credit the data the line came from, by the record's `source`: an `osm` segment SHALL show "© OpenStreetMap contributors" linking to OpenStreetMap's copyright page, with its licence; an `agency` segment SHALL credit trail data from the agency its attribution names, or public agency trail data when it names none; an `athlete` segment SHALL show only its creator. Any other source SHALL show its licence identifier.

#### Scenario: OpenStreetMap segment
- **WHEN** a viewer opens a segment whose record has `source` `osm` and `license` `ODbL-1.0`
- **THEN** the segment screen shows "© OpenStreetMap contributors" and the ODbL, and tapping the credit opens OpenStreetMap's copyright page

#### Scenario: Agency segment
- **WHEN** a viewer opens a segment whose record has `source` `agency` and the attribution "USDA Forest Service"
- **THEN** the segment screen credits trail data from the USDA Forest Service

#### Scenario: Agency segment without an attribution
- **WHEN** a viewer opens a segment whose record has `source` `agency` and no attribution
- **THEN** the segment screen credits public agency trail data

#### Scenario: Attribution on another source ignored
- **WHEN** a viewer opens an athlete's segment whose record has an attribution
- **THEN** the segment screen shows "By" and the creator's handle, and not the attribution

#### Scenario: Athlete segment
- **WHEN** a viewer opens a segment an athlete created from their ride
- **THEN** the segment screen shows "By" and the creator's handle, and no data credit

#### Scenario: Unknown source
- **WHEN** a viewer opens a segment whose record has `source` `survey` and `license` `CC-BY-4.0`
- **THEN** the segment screen shows `CC-BY-4.0` as the data's licence

### Requirement: Seeded segments can carry an import source
The development seed script SHALL be able to write a segment with `source` `osm` and `license` `ODbL-1.0`, or `source` `agency`, `license` `CC0-1.0` and the attribution "USDA Forest Service", instead of an athlete segment, so the credit can be tested without an importer.

#### Scenario: Seeding an OSM segment
- **WHEN** a developer runs the seed script for `alice` with a sample GPX file and the OSM source
- **THEN** `alice.test`'s repo holds a `feed.segment` with `source` `osm` and `license` `ODbL-1.0`, and its segment screen shows the OpenStreetMap credit

#### Scenario: Seeding an agency segment
- **WHEN** a developer runs the seed script for `alice` with a sample GPX file and the agency source
- **THEN** `alice.test`'s repo holds a `feed.segment` with `source` `agency` and the attribution "USDA Forest Service", and its segment screen credits the Forest Service
