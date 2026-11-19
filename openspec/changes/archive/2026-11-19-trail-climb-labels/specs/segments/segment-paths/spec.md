# Spec Delta

## MODIFIED Requirements

### Requirement: A path's view says what it covers for the viewer
A path view SHALL carry the path's URI, name, kind, length, line pieces, source, licence and attribution, and, counting only the segments on it that the viewer could get from a segment search: their sport groups in the app's order, how many segment groups they form, and, for each sport group whose segments on it are all served one-way and that isn't alpine, the way it goes.

#### Scenario: Multi-use trail
- **WHEN** a viewer gets the view of Ann's, which holds ride, run and hike segments in 3 segment groups
- **THEN** it reads Ride, Run, Hike and 3 segment groups

#### Scenario: Proposed segment someone else made
- **WHEN** a path's only segment is proposed by someone else and the viewer has no effort on it
- **THEN** the view lists no sport groups and no segment groups

#### Scenario: One-way downhill trail
- **WHEN** a viewer gets the view of Rush, whose 21 ride segments are all one-way and served with `climb: "downhill"`
- **THEN** it says ride goes one way only, downhill

#### Scenario: One-way for one sport only
- **WHEN** Clarks holds one-way ride segments and two-way run and hike segments
- **THEN** its view says ride goes one way only and says nothing about run or hike

## ADDED Requirements

### Requirement: A one-way sport group's way on a path is its longest segment's
A path's way for a one-way sport group SHALL be its longest such segment's direction (`segments/segment-direction`): its served climb, else its served elevation's climb, else its compass heading or turn.

#### Scenario: Trail with no climb data
- **WHEN** a path's one-way ride segments have no served climb or elevation, and the longest heads mostly west
- **THEN** its view says ride goes one way only, westbound
