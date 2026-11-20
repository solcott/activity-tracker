## MODIFIED Requirements

### Requirement: A candidate holds what a segment needs
Each candidate SHALL hold its id, region, sport group, kind, name, line, length in metres, source, licence, whether it is one-way, its climb when it has one (`uphill` or `downhill`), and its trail attributes when any are known (`segments/trail-attributes`). An OSM candidate SHALL have `source` `osm`, `license` `ODbL-1.0` and the ids of the OSM ways it follows. An agency candidate SHALL have `source` `agency`, `license` `CC0-1.0`, an attribution naming its agency, and the feature ids it follows. Its line SHALL be encoded as a `feed.segment` line is, stay within 1 m of the trail, and fit a `feed.segment` record.

#### Scenario: Candidate written
- **WHEN** an extract gives a candidate for a 2 km ride trail
- **THEN** its entry holds a line within 1 m of the trail, a length of about 2000 m, kind `run`, `source` `osm`, `license` `ODbL-1.0` and whether it is one-way

#### Scenario: Switchbacks kept
- **WHEN** an extract gives a candidate for a trail mapped with nodes about 2 m apart around switchbacks about 10 m wide
- **THEN** its line follows each switchback around its turn, rather than cutting across it

#### Scenario: Too long for a record at 1 m
- **WHEN** a trail's line within 1 m of it would not fit a `feed.segment` record
- **THEN** its candidate's line is coarser only as far as it needs to be to fit

#### Scenario: Forest Service candidate written
- **WHEN** the Forest Service's trails give a candidate for a 3 km hiking trail
- **THEN** its entry holds `source` `agency`, `license` `CC0-1.0`, the attribution "USDA Forest Service" and the feature ids it follows

#### Scenario: Lift candidate written
- **WHEN** an extract gives a candidate for a named chairlift
- **THEN** its entry holds sport group `alpine`, kind `lift`, `source` `osm` and the id of the chairlift's way

#### Scenario: Candidate with trail attributes
- **WHEN** an extract gives a candidate for a ride trail whose ways are all tagged `surface=gravel` and `mtb:scale=1`
- **THEN** its entry holds trail attributes with `surfaces` of 100% `gravel` and MTB scale 1

#### Scenario: Candidate with no attribute data
- **WHEN** an extract gives a candidate whose ways carry none of the tags trail attributes read
- **THEN** its entry holds no trail attributes

## ADDED Requirements

### Requirement: The candidates file names its format version
The candidates file's header SHALL name its format version, and a version that adds trail attributes SHALL be newer than any version that didn't. `stage`, `publish --all` and `refine` SHALL refuse a candidates file of any other version and name the version they read.

#### Scenario: Older file refused
- **WHEN** `stage` is given a candidates file written before candidates held trail attributes
- **THEN** it refuses the file, naming its version and the version it reads
