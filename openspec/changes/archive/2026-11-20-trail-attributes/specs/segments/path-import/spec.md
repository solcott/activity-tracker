## MODIFIED Requirements

### Requirement: A published path can be refreshed
`publish-paths` SHALL replace a published path's name, line, sport groups, sections and trail attributes with the paths file's when they differ, keeping its key, and removing its `trail` when the file's path has none. It SHALL name on each path the import regions whose files list it (`regions`): a held path the region's file lists gains the region, one the region listed that its file no longer holds loses it, and a path is deleted only once no region lists it, never for lying inside another region's area. A path whose record names no regions is never deleted.

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

#### Scenario: Trail attributes added
- **WHEN** a newer paths file gives a published path `surfaces` of 100% `gravel` where its record has no `trail`
- **THEN** the path is rewritten under the same key with that `trail`

## ADDED Requirements

### Requirement: A path holds its trail attributes
Each path in the paths file SHALL hold its trail attributes when any are known, combined over every way or feature of the path across all its sport groups, by the rules of `segments/trail-attributes`. When a path's line is split into pieces, its attributes SHALL cover the whole path. A path that `extract` keeps as an OSM path in place of an agency path that follows it SHALL hold the OSM path's attributes, filling in from the agency path's only the attributes the OSM path lacks. A road path SHALL hold attributes too. The paths file header SHALL name a format version newer than any without trail attributes, and `publish-paths` SHALL refuse any other version. A path `publish-paths` publishes SHALL hold its file entry's attributes in `trail` when it has any.

#### Scenario: Trail path attributes
- **WHEN** extract writes the trail path "Long Trail", whose ways are tagged `sac_scale=mountain_hiking` along 60% of it and `hiking` along the rest
- **THEN** its entry holds `sacScale: mountain_hiking`

#### Scenario: OSM path filled in from agency
- **WHEN** extract keeps an OSM path that has no trail class in place of a Forest Service path of trail class 3 that follows it
- **THEN** the OSM path's entry holds trail class 3

#### Scenario: Older paths file refused
- **WHEN** `publish-paths` is given a paths file written before paths held trail attributes
- **THEN** it refuses the file, naming its version and the version it reads
