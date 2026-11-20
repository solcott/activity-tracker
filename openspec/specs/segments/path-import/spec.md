# path-import Specification

## Purpose

How the segment importer turns a region's trails and named roads into paths, and publishes only the paths that segments lie on.

## Requirements

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

### Requirement: Long trails keep one path
A trail longer than the segment length limit SHALL still be one path, its line split into pieces that each fit a path record's line limit, and a trail family's ways SHALL appear in its pieces once.

#### Scenario: Long trail
- **WHEN** a region holds a 440 km trail
- **THEN** the paths file holds one path for it with several line pieces

### Requirement: Extract writes the region's named roads as paths
The paths file SHALL also hold a road path for each run of same-named, end-to-end OSM road ways that are open to bikes or to people on foot. Motorways, motorway links, and roads where both bikes and people on foot are forbidden SHALL be left out. No candidate SHALL be cut from a road.

#### Scenario: Street
- **WHEN** a region holds "Pine St" as four joined residential ways
- **THEN** the paths file holds one road path "Pine St", and no candidate follows it

#### Scenario: Motorway
- **WHEN** a region holds a named motorway
- **THEN** no path is written for it

### Requirement: Path ids are stable
A path's id SHALL be derived only from its kind, its name and the source features at its two ends, and SHALL be the record key it is published under. Extracting the same data twice SHALL give the same ids, and an edit elsewhere in the region SHALL NOT change one.

#### Scenario: Re-extract unchanged
- **WHEN** an operator extracts a newer extract in which only other trails changed
- **THEN** every unchanged path keeps its id

### Requirement: Only paths that segments lie on are published
`publish-paths` SHALL ask the AppView for the region's live segments. It SHALL publish each path from the paths file that one of them lies on, by the AppView's test among the file's paths, unless it is already published. It SHALL report how many paths it published and how many segments lie on none.

#### Scenario: New trail segments
- **WHEN** the region's import has just published segments of Ann's, and Ann's has no path yet
- **THEN** `publish-paths` publishes Ann's path

#### Scenario: Road with an athlete's segment
- **WHEN** an athlete has made a segment along Pine St, and no other segment lies on any other street
- **THEN** only Pine St's road path is published from the region's roads

#### Scenario: Nothing new
- **WHEN** every path the region's segments lie on is already published
- **THEN** `publish-paths` publishes nothing

### Requirement: A published path can be refreshed
`publish-paths` SHALL replace a published path's name, line, sport groups and sections with the paths file's when they differ, keeping its key, and SHALL name on each path the import regions whose files list it (`regions`): a held path the region's file lists gains the region, one the region listed that its file no longer holds loses it, and a path is deleted only once no region lists it, never for lying inside another region's area. A path whose record names no regions is never deleted.

#### Scenario: Trail extended in OSM
- **WHEN** a newer extract adds 300 m to the end of a published trail's line, keeping its ends' features
- **THEN** the path is rewritten under the same key with the longer line

#### Scenario: Paths merged
- **WHEN** a newer extract makes one "Rush Trail" path where an older one made ten
- **THEN** the ten older paths are deleted

#### Scenario: Neighbouring region
- **WHEN** us-vermont's paths are published after us-new-hampshire's, and a New Hampshire trail lies inside Vermont's bounding box
- **THEN** the New Hampshire trail stays published

#### Scenario: Border trail
- **WHEN** a trail listed by both us-vermont's and us-new-hampshire's files leaves Vermont's file
- **THEN** its path stays published, naming only us-new-hampshire

#### Scenario: Only sections changed
- **WHEN** a newer extract keeps Rush Trail's name, line and sport groups but newly gives it sections
- **THEN** the path is rewritten under the same key with its sections

### Requirement: The import script publishes paths last
`scripts/segment-import.sh` SHALL run `publish-paths` after publishing and refining a region's segments, writing as the other importer commands do.

#### Scenario: Region import
- **WHEN** an operator runs `scripts/segment-import.sh us-vermont`
- **THEN** the run ends by publishing the paths its segments, and athletes' segments there, lie on

### Requirement: A trail path lists its family's sections
Each trail path in the paths file SHALL list the sections of its family that hold at least one of the extract's kept candidates (after duplicates are dropped) whose ways are on the path. Each section SHALL be named as candidates name it (`segments/trail-candidates`) and hold those candidates' ids. Sections SHALL be ordered by name and their ids sorted. A path with no such candidates SHALL list no sections.

#### Scenario: Rush Trail
- **WHEN** the extract keeps "Rush › Through the Trees Trail", its own spans and "Rush - Through the Trees Trail" in every sport group they're open to
- **THEN** the Rush Trail path lists a section "Through the Trees Trail" holding all their ids

#### Scenario: Duplicate dropped
- **WHEN** a section trail's whole candidate duplicates a parent trail's span and is dropped
- **THEN** only the span's id is listed in the section

#### Scenario: Plain trail
- **WHEN** a trail of one name with no sections is extracted
- **THEN** its path lists no sections

### Requirement: Published sections name only published segments
`publish-paths` SHALL write on each path record only the sections that hold the ids of the importer's published segments, listing only those ids, and SHALL leave out sections with no published segment.

#### Scenario: Unridden branch
- **WHEN** none of the Limelight section's candidates has been published
- **THEN** the published Rush Trail names no Limelight section
