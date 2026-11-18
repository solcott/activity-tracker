# Spec Delta

## MODIFIED Requirements

### Requirement: Extract writes the region's trails as paths
`extract` SHALL also write a paths file holding one trail path for each trail family: trails of one name, or of one family of sections (the name before " - ", or a parent trail's family), whose ways touch or come within 250 m, across every sport group. It SHALL be named after the family and carry its source, licence, attribution and the sport groups its trails are open to.

#### Scenario: Multi-use trail
- **WHEN** a region holds Ann's as a ride, run and hike trail
- **THEN** the paths file holds one trail path named "Ann's"

#### Scenario: Sectioned trail
- **WHEN** a region holds "Rush Trail - 2112 Section" and "Rush Trail - Mystic Rhythms Section", and the parent trails "Rush Trail via Exit Stage Left Section" and "Rush Trail via Through the Trees Trail Section" through them
- **THEN** the paths file holds one trail path "Rush Trail" covering all of them

#### Scenario: A trail split at forks
- **WHEN** a trail's same-named ways fork, or short unnamed connectors break them, into several chains that touch or come within 250 m
- **THEN** the paths file holds one path for them

#### Scenario: Foot path
- **WHEN** a region holds Hoof N Boot Path, tagged for walking only
- **THEN** its path is open to run and hike only

#### Scenario: Agency trail
- **WHEN** a Forest Service trail is extracted
- **THEN** its path carries the agency's source, licence and attribution

## ADDED Requirements

### Requirement: An agency trail that OSM also maps gives one path
When at least 90% of an agency trail path's length follows a single OSM trail path, extract SHALL write only the OSM path. The OSM path SHALL keep its own name, line, id, source, licence and attribution, and SHALL be open to the sport groups of both paths. An agency path that no OSM path covers this way SHALL be written as before.

#### Scenario: Trail in both datasets
- **WHEN** a region holds the OSM trail "Little Cottonwood Creek Trail" and the Forest Service trail "Little Cottonwood Creek" along the same line
- **THEN** the paths file holds only the OSM path "Little Cottonwood Creek Trail", crediting OpenStreetMap

#### Scenario: Agency opens a sport OSM doesn't
- **WHEN** the agency trail is open to ride, run and hike, and the OSM trail it follows only to run and hike
- **THEN** the OSM path is open to ride, run and hike, so the agency's ride segments lie on it

#### Scenario: Agency trail OSM lacks
- **WHEN** a Forest Service trail follows no OSM trail path for most of its length
- **THEN** the paths file holds its agency path

#### Scenario: Agency path already published
- **WHEN** a region's agency path was published by an earlier import, and the region is imported again after OSM maps the same trail
- **THEN** the new paths file no longer lists the agency path, and `publish-paths` deletes it once no region lists it
