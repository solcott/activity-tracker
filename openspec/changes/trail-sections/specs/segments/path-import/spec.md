# Spec Delta

## MODIFIED Requirements

### Requirement: A published path can be refreshed
`publish-paths` SHALL replace an already published path's name, line, sport groups and sections with the paths file's when they differ, keeping its key, and SHALL name on each path record the import regions whose files list it (`regions`): a held path the region's file lists gains the region, one the region listed that its file no longer holds loses it, and a path is deleted only once no region lists it, never for lying inside another region's area. A path whose record names no regions is never deleted.

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

## ADDED Requirements

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
