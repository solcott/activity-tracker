# Spec Delta

## MODIFIED Requirements

### Requirement: Agency datasets are read as GeoJSON text sequences
The importer SHALL read each agency dataset as a GeoJSON text sequence of features in WGS 84, named with its kind: the Forest Service's National Forest System trails (`usfs`), the National Park Service's public trails (`nps`) or the Bureau of Land Management's public managed trails (`blm`). Each LineString, and each part of a MultiLineString, SHALL be one line of the trail network. A feature with no stable feature id, or whose geometry isn't a line, SHALL be skipped and counted in the run's report.

#### Scenario: Unknown kind
- **WHEN** an operator gives an agency dataset of a kind the importer doesn't know
- **THEN** the importer stops with its usage and writes nothing

#### Scenario: Multi-part feature
- **WHEN** a Park Service feature is a MultiLineString of two parts
- **THEN** each part is a line of the network, both under the feature's id

#### Scenario: Feature without an id
- **WHEN** a Forest Service feature has no control number
- **THEN** it gives no candidate, and the run reports it as skipped

#### Scenario: BLM dataset
- **WHEN** an operator extracts `us-utah` from a Geofabrik extract and BLM's managed trails for the same area
- **THEN** the candidates file holds BLM trail candidates with `source` `agency`, `license` `CC0-1.0` and the attribution "Bureau of Land Management"

### Requirement: Closed and hidden agency trails are left out
A Park Service feature SHALL NOT be a trail unless its status is existing and it is marked for public display and, when the dataset says so, open to the public. A Forest Service feature with no managed or accepted use for any sport group SHALL give no candidate. A BLM feature whose access is restricted to administrators, to authorized or permitted users, or to all users SHALL give no candidate.

#### Scenario: Park trail not for public display
- **WHEN** a named Park Service trail is marked not for public map display
- **THEN** it gives no candidate for any group

#### Scenario: Planned park trail
- **WHEN** a named Park Service trail has the status planned
- **THEN** it gives no candidate

#### Scenario: BLM trail for permitted users only
- **WHEN** a named BLM trail open to hikers and bicycles has its access restricted to authorized or permitted users
- **THEN** it gives no candidate for any group

## ADDED Requirements

### Requirement: BLM features qualify by their allowed modes
A BLM feature SHALL be a trail for each group its allowed mode of transport names: bicycles or mountain bikes for `ride`, hiking for `run` and `hike`, and nordic or cross-country skiing for `ski`. When its allowed mode is unknown, a non-motorized planned mode SHALL count as `ride`, `run` and `hike`, and a non-mechanized one as `run` and `hike`. It SHALL qualify only with a non-empty name.

#### Scenario: Shared non-motorized trail
- **WHEN** a named BLM trail's allowed mode is shared non-motorized (hiking, bicycle, equestrian)
- **THEN** it is a trail for `ride`, `run` and `hike`

#### Scenario: Hiking and horses only
- **WHEN** a named BLM trail's allowed mode is equestrian and hiking only
- **THEN** it is a trail for `run` and `hike`, but not for `ride`

#### Scenario: Shared motorized trail
- **WHEN** a named BLM trail's allowed mode is shared motorcycle, ATV, mountain bike, equestrian and hiking
- **THEN** it is a trail for `ride`, `run` and `hike`

#### Scenario: Motor vehicles only
- **WHEN** a named BLM trail's allowed mode is motorcycle only
- **THEN** it gives no candidate

#### Scenario: Unknown mode on a non-mechanized trail
- **WHEN** a named BLM trail's allowed mode is unknown and its planned mode of transport is non-mechanized
- **THEN** it is a trail for `run` and `hike`

#### Scenario: Nordic trail
- **WHEN** a named BLM trail's allowed mode is shared non-mechanized snow travel (nordic ski, snowshoe)
- **THEN** it is a trail for `ski`
