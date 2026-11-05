# Spec Delta

## ADDED Requirements

### Requirement: Segments can be found by name
`feed.searchSegmentNames` SHALL return the segments whose current name contains a query, ignoring case, at most 100 per page with a cursor. It SHALL serve exactly the segments `feed.searchSegments` would serve the same viewer: active ones to everyone, a proposed one only to its creator and riders, never duplicate, archived, hidden or taken down ones. It SHALL never return lifts.

#### Scenario: Current name
- **WHEN** a viewer searches "wiggly" and a segment named "Trail 214" has adopted the name "Wiggly Woods"
- **THEN** the segment is returned, and a search for "214" doesn't return it

#### Scenario: Lift
- **WHEN** a viewer searches "summit" and a chairlift and a piste are both named "Summit"
- **THEN** only the piste is returned

#### Scenario: Proposed segment
- **WHEN** a proposed segment's name matches
- **THEN** its creator and athletes with an effort on it get it, and other viewers don't

#### Scenario: Blank query
- **WHEN** a viewer searches with a query that is blank after trimming
- **THEN** the AppView returns no segments and no cursor

### Requirement: Name results are ranked by match, then distance
`feed.searchSegmentNames` SHALL order its results by how well the name matches: the whole name, then names starting with the query, then names with a word starting with it, then the rest. Within a rank it SHALL order them nearest first to a given centre, measured to the segment's bounding box, or by name when no centre is given. Following the cursors SHALL return every match exactly once.

#### Scenario: Prefix before anywhere
- **WHEN** a viewer searches "hog" and segments "Hogback Climb" and "Big Hog Loop" and "Shoghill" match
- **THEN** "Hogback Climb" comes first, then "Big Hog Loop", then "Shoghill"

#### Scenario: Nearest within a rank
- **WHEN** two segments both named "Summit Trail" match and a centre is given near the second
- **THEN** the second comes first

### Requirement: Name search can be limited to an area and a sport group
`feed.searchSegmentNames` SHALL take an optional bounding box and return only matches whose line passes through it, under the same 1 degree limit as `feed.searchSegments`, answering `InvalidRequest` beyond it. It SHALL take an optional sport group and return only matches of that group.

#### Scenario: Within a box
- **WHEN** a viewer searches "summit" in a box around one of two "Summit Trail" segments
- **THEN** only the one whose line crosses the box is returned

#### Scenario: Box too large
- **WHEN** a viewer searches "summit" in a box 3 degrees wide
- **THEN** the AppView answers `InvalidRequest`

#### Scenario: Sport group
- **WHEN** a viewer searches "summit" for `run` segments and a ride and a run segment match
- **THEN** only the run segment is returned
