# Spec Delta

## MODIFIED Requirements

### Requirement: Region presets fetch an import's inputs
The repository SHALL keep a preset for each importable region, naming its OpenStreetMap extract, its bounding box, the agency datasets that cover it and its language. Fetching a region SHALL download the extract, filtered to the features the importer reads, each named agency's trails that touch the box, and the terrain tiles covering the box. Importing a region by its id alone SHALL fetch its inputs and then extract (with that terrain), stage, publish and refine it.

#### Scenario: Import Utah by name
- **WHEN** an operator runs the import script with only `us-utah`
- **THEN** it fetches Utah's OpenStreetMap extract, its Forest Service, Park Service and BLM trails and the terrain tiles over its box, and then extracts with that terrain, stages, publishes and refines `us-utah`

#### Scenario: Extract unchanged
- **WHEN** an operator fetches a region again and Geofabrik's extract hasn't changed
- **THEN** the extract isn't downloaded again

#### Scenario: Terrain already fetched
- **WHEN** an operator fetches a region again and its terrain tiles are already on disk
- **THEN** no tile is downloaded again

#### Scenario: Failed download
- **WHEN** an agency's download fails partway
- **THEN** the fetch stops with an error, the import doesn't run, and no partial dataset is left where the import reads it

#### Scenario: Unknown region
- **WHEN** an operator runs the import script with only a region id that has no preset
- **THEN** it stops and says the region needs a preset or input files

#### Scenario: Missing tool
- **WHEN** `osmium` or `ogr2ogr` isn't installed
- **THEN** the fetch stops before downloading anything and says which tool to install

## ADDED Requirements

### Requirement: A published segment carries its candidate's climb
A segment the importer publishes SHALL hold its candidate's climb, as returned by `importer.checkSegmentCandidates`, in `climb`, and no `climb` when its candidate has none.

#### Scenario: Publishing a downhill piece
- **WHEN** `publish` publishes the ride candidate "Rush - Dreamline" with climb `downhill`
- **THEN** its `feed.segment` holds `climb: "downhill"`

### Requirement: Refining gives published segments their candidate's climb
`refine` SHALL set each published segment's `climb` to its candidate's when the two differ, removing it when the candidate has none, in the same write as any change to its line, name or `oneWay`, and SHALL report the segments it changed.

#### Scenario: Segment published before climbs
- **WHEN** "Rush - Dreamline" was published without `climb`, and its candidate in a new extract with terrain has climb `downhill`
- **THEN** `refine` writes it once with `climb: "downhill"`, keeping its URI and efforts, and reports it

#### Scenario: Refining twice
- **WHEN** an operator runs `refine` twice with the same file
- **THEN** the second run changes no segment's `climb`
