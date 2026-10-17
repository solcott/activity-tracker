# Spec Delta

## Purpose

Keeps a pool swim's pool length and each length it records (stroke, stroke count and time), and shows them on the swim's page, so an imported swim keeps more than its session totals.

## ADDED Requirements

### Requirement: Pool length is published
A pool swim imported from FIT SHALL publish its pool length on the activity record, at the activity's visibility, in the file's unit converted to centimeters.

#### Scenario: 25 m pool
- **WHEN** an athlete imports a FIT pool swim whose session's pool length is 25 m
- **THEN** the published activity's pool length is 2500 cm

#### Scenario: 25 yd pool
- **WHEN** an athlete imports a FIT pool swim whose pool length is 25 yards
- **THEN** the published activity's pool length is 2286 cm

#### Scenario: Open water swim
- **WHEN** an athlete imports a FIT open water swim
- **THEN** the published activity has no pool length

### Requirement: Lengths are kept
A pool swim imported from FIT SHALL store each length it records, in order, with its start, elapsed time, stroke type and stroke count, and whether it was a rest. Lengths SHALL be stored with the streams, at their visibility. Stroke types SHALL be an open set, so a stroke the app doesn't know is kept as recorded.

#### Scenario: Mixed strokes
- **WHEN** an athlete imports a FIT pool swim of 40 lengths, 36 freestyle and 4 breaststroke
- **THEN** its streams hold 40 lengths in order, each with its own time, stroke and stroke count

#### Scenario: Rest between sets
- **WHEN** an athlete imports a FIT pool swim with an idle length between two sets
- **THEN** the idle length is stored as a rest, with its time and no stroke

#### Scenario: Swim recorded without lengths
- **WHEN** an athlete imports a FIT pool swim with no length messages
- **THEN** its streams hold no lengths

### Requirement: Swim page shows lengths
A pool swim's page SHALL show its pool length among its stats, to viewers the activity admits, and a lengths table, to viewers the streams admit. Each row SHALL show the length's number, stroke, time, stroke count and SWOLF (its time in seconds plus its stroke count). Rest lengths SHALL show their time and no SWOLF.

#### Scenario: Viewer who may see streams
- **WHEN** a viewer who may see the streams opens a pool swim with lengths stored
- **THEN** its stats show the pool length and a lengths table lists each length with its SWOLF

#### Scenario: Viewer who may not see streams
- **WHEN** a viewer opens a Public pool swim whose streams are Only me
- **THEN** its stats show the pool length and no lengths table is shown

### Requirement: Merging fills missing lengths
Merging another recording into a pool swim SHALL add its pool length and lengths when the swim lacks them, and SHALL NOT replace lengths the swim has.

#### Scenario: Watch adds lengths
- **WHEN** an athlete merges a watch's FIT pool swim into a manual entry of the same swim
- **THEN** the manual entry gains the pool length and the watch's lengths

### Requirement: Older swims are unchanged
Swims imported before lengths were kept SHALL show as before, with no pool length or lengths table, and SHALL NOT be backfilled.

#### Scenario: Swim imported earlier
- **WHEN** a viewer opens a pool swim imported before lengths were kept
- **THEN** its page shows the stats it showed before and no lengths table
