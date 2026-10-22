# Spec Delta

## ADDED Requirements

### Requirement: Agency features close trails to sport groups
A Forest Service feature SHALL be closed to a sport group when that group's use is restricted all year and neither managed nor accepted in any season. A BLM feature SHALL be closed to each of `ride`, `run` and `hike` that its allowed mode of transport, when known and not over snow, leaves out, and to every group when its access is restricted to administrators, to authorized or permitted users, or to all users. A Park Service feature SHALL close nothing.

#### Scenario: Wilderness trail closed to bikes
- **WHEN** a Forest Service feature has bicycles restricted from January 1 to December 31 and hikers managed
- **THEN** it is closed to `ride`, and not to `run` or `hike`

#### Scenario: Seasonal restriction
- **WHEN** a Forest Service feature has bicycles restricted from November 1 to April 30 and managed in summer
- **THEN** it is not closed to `ride`

#### Scenario: BLM hiking and horses only
- **WHEN** a BLM feature's allowed mode is equestrian and hiking only
- **THEN** it is closed to `ride`

#### Scenario: BLM mode unknown
- **WHEN** a BLM feature's allowed mode is unknown
- **THEN** it closes nothing, whatever its planned mode of transport

#### Scenario: BLM trail for permitted users only
- **WHEN** a BLM feature's access is restricted to authorized or permitted users
- **THEN** it is closed to every group

#### Scenario: Park trail listing only hiking
- **WHEN** a Park Service feature lists only hiker/pedestrian use
- **THEN** it is not closed to `ride`

### Requirement: OSM candidates along an agency closure are left out
An OSM candidate of a sport group SHALL be left out when, for at least 100 m of its line, it lies within 15 m of an agency line closed to that group and not within 15 m of an agency line open to it. Candidates of other groups along the same line SHALL be kept, and agency candidates SHALL NOT be affected.

#### Scenario: OSM path through a wilderness
- **WHEN** an OSM path tagged for bikes and hikers follows, for 2 km, a Forest Service trail closed to `ride`
- **THEN** its `ride` candidates are left out and its `run` and `hike` candidates are kept

#### Scenario: Crossing a closed trail
- **WHEN** an OSM ride trail crosses a Forest Service trail closed to `ride` at right angles
- **THEN** its `ride` candidates are kept

#### Scenario: Parallel trail nearby
- **WHEN** an OSM ride trail runs 40 m from a Forest Service trail closed to `ride` along its whole length
- **THEN** its `ride` candidates are kept

#### Scenario: Short overlap
- **WHEN** an OSM ride candidate shares 60 m with a Forest Service trail closed to `ride`
- **THEN** it is kept

#### Scenario: Open in another feature
- **WHEN** a Forest Service summer feature closed to `ride` and a winter feature open to `ride` lie along the same OSM path
- **THEN** the OSM path's `ride` candidates are kept

#### Scenario: Stretches outside the closure
- **WHEN** an OSM ride trail is cut at junctions into three stretches and only the middle one follows a Forest Service trail closed to `ride`
- **THEN** the middle stretch's and the whole trail's `ride` candidates are left out, and the other two stretches' are kept

### Requirement: The run reports candidates left out by closures
The extract run SHALL report how many OSM candidates of each sport group it left out along agency closures.

#### Scenario: Report
- **WHEN** an extract leaves out four OSM `ride` candidates along Forest Service closures
- **THEN** its report says that 4 `ride` candidates were left out by agency closures
