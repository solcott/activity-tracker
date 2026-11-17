# Spec Delta

## MODIFIED Requirements

### Requirement: The Trail page lists its segments by segment group
The Trail page SHALL list the segments on the path that the viewer may see, one row per segment group (`segments/segment-groups`), each named and described as a segment group's row is. Rows SHALL run from the longest to the shortest, then by name.

#### Scenario: Whole trail and stretches
- **WHEN** Ann's holds its whole line and two stretches, each for three sports both ways
- **THEN** the page lists three rows, "Ann's" first

#### Scenario: Combined segment
- **WHEN** an athlete's segment covers part of Ann's and part of Hogback
- **THEN** it is listed on both trails' pages

#### Scenario: One-way for one sport
- **WHEN** the Clarks trail holds, on one line, a one-way ride segment that clearly climbs and run and hike segments both ways, all named "Clarks"
- **THEN** it lists one row, "Clarks", whose details read "<length> · Run, Hike both ways · Ride uphill only"

#### Scenario: Neighbouring trails' imported segments
- **WHEN** the importer's Ann's stretches and its Bonneville Shoreline segment share only part of their length with Clarks
- **THEN** none of them is listed on the Clarks page
