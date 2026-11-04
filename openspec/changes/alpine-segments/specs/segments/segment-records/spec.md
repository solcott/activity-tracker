# Spec Delta

## MODIFIED Requirements

### Requirement: A segment's sport group decides what can match it
Every segment SHALL have exactly one sport group: `ride`, `run`, `hike`, `ski`, `alpine` or `paddle`. Each activity type SHALL map to at most one sport group, and an activity type without one SHALL never match a segment. E-bike rides SHALL map to `ride` and be marked as e-bike. Lift-served skiing (a `ski` activity that isn't backcountry or indoor) and snowboarding SHALL map to `alpine`. Cross-country and backcountry skiing SHALL map to `ski`.

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

#### Scenario: Resort ski day
- **WHEN** an athlete's resort ski day or snowboard day passes an `alpine` segment
- **THEN** it can match the segment

#### Scenario: Backcountry ski on a piste
- **WHEN** an athlete's backcountry ski tour passes an `alpine` segment
- **THEN** it never matches the segment, and can match `ski` segments

### Requirement: Only valid segments are indexed
The AppView SHALL index a segment only if its line has at least two positions, its length along the line is at least its sport group's minimum (500 m for `ride`, 200 m for every other group) and at most 50 km, its name is non-empty after trimming, and its sport group and kind are known. It SHALL drop any other segment record, and SHALL compute a segment's length itself rather than trust a length the record states.

#### Scenario: Too short
- **WHEN** an athlete writes a `feed.segment` whose line is 150 m long
- **THEN** the AppView doesn't index it

#### Scenario: Ride under its minimum
- **WHEN** an athlete writes a `ride` segment whose line is 350 m long
- **THEN** the AppView doesn't index it, though a `run` segment of that length would be

#### Scenario: Unknown sport group
- **WHEN** a `feed.segment` names the sport group `skydive`
- **THEN** the AppView doesn't index it

#### Scenario: Unknown kind
- **WHEN** a `feed.segment` names the kind `tunnel`
- **THEN** the AppView doesn't index it

### Requirement: Duplicates are detected on indexing
When the AppView indexes a new or changed segment, it SHALL mark it `duplicate` of the earliest-indexed `proposed` or `active` segment of the same sport group and kind whose line it duplicates, and SHALL NOT match it. One line SHALL duplicate another when they run the same direction, their lengths differ by at most 20%, and following both from start to end never takes them more than 30 m apart.

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

#### Scenario: Run beside a lift
- **WHEN** an `alpine` run follows an existing lift's line in the same direction
- **THEN** it isn't a duplicate

## ADDED Requirements

### Requirement: Every segment has a kind
A segment SHALL have a kind: `run`, a stretch an athlete travels under their own power or by gravity, or `lift`, a ski lift (`segments/segment-lifts`). A record that names no kind SHALL be a `run`. The kind SHALL be served in every segment view, and changing it SHALL recompute the segment's status checks and efforts as a change of sport group does.

#### Scenario: Existing segment
- **WHEN** the AppView indexes a `feed.segment` written before kinds existed
- **THEN** it indexes it as a `run`

#### Scenario: Kind changed
- **WHEN** an importer segment's record is rewritten with its kind changed from `run` to `lift`
- **THEN** the AppView checks it for duplicates among lifts and recomputes its efforts
