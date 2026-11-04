# Spec Delta

## MODIFIED Requirements

### Requirement: Only ridden candidates that duplicate nothing are published
`publish` SHALL list the region's ridden candidates and check their lines against the live segments of their sport groups, by the duplicate test the AppView applies when indexing, in one call per page of up to 500. It SHALL publish a candidate only when the check finds no duplicate. A candidate that isn't ridden, duplicates a proposed or active segment, or is no longer staged SHALL NOT be published. Only the importer SHALL be able to make the check. When the operator asks to publish every candidate (`publish --all` with a candidates file), the ridden condition SHALL NOT apply: `publish` SHALL take the file's candidates instead of the ridden list, and every other condition SHALL still apply.

#### Scenario: Ridden trail
- **WHEN** a staged candidate is ridden and no segment covers its line
- **THEN** `publish` writes a segment for it

#### Scenario: Already covered
- **WHEN** a ridden candidate follows an athlete's active segment
- **THEN** `publish` doesn't write a segment for it, and reports it as a duplicate

#### Scenario: Not ridden yet
- **WHEN** a staged candidate has two riders and the threshold is three
- **THEN** `publish` doesn't write a segment for it

#### Scenario: Many known duplicates
- **WHEN** a region has 300 ridden candidates that each duplicate a live segment, and `publish` runs again
- **THEN** it checks them with one call to the AppView, reports all 300 as duplicates, and writes nothing

#### Scenario: Duplicate no longer live
- **WHEN** the live segment a ridden candidate duplicated is deleted, and `publish` runs again
- **THEN** the check finds no duplicate, and `publish` writes a segment for the candidate

#### Scenario: Check by another account
- **WHEN** an athlete's account asks the AppView to check staged candidates
- **THEN** the AppView refuses it as not the importer

#### Scenario: Publishing every candidate
- **WHEN** an operator runs `publish --all` with a region's staged candidates file, and a candidate in it has no riders and duplicates nothing
- **THEN** `publish` writes a segment for it

#### Scenario: Every candidate still skips duplicates
- **WHEN** an operator runs `publish --all` and a candidate in the file follows a live segment
- **THEN** `publish` doesn't write a segment for it, and reports it as a duplicate

#### Scenario: Candidate in the file no longer staged
- **WHEN** an operator runs `publish --all` with a file holding a candidate the region's current run doesn't stage
- **THEN** `publish` doesn't write a segment for it, and reports it as gone

#### Scenario: File of another region
- **WHEN** an operator runs `publish --region us-utah --all` with a candidates file of `us-vermont`
- **THEN** the importer refuses it before calling the AppView or the PDS

## ADDED Requirements

### Requirement: Region presets fetch an import's inputs
The repository SHALL keep a preset for each importable region, naming its OpenStreetMap extract, its bounding box, the agency datasets that cover it and its language. Fetching a region SHALL download the extract, filtered to the features the importer reads, and each named agency's trails that touch the box. Importing a region by its id alone SHALL fetch its inputs and then extract, stage, publish and refine it.

#### Scenario: Import Utah by name
- **WHEN** an operator runs the import script with only `us-utah`
- **THEN** it fetches Utah's OpenStreetMap extract and its Forest Service, Park Service and BLM trails, and then extracts, stages, publishes and refines `us-utah` from them

#### Scenario: Extract unchanged
- **WHEN** an operator fetches a region again and Geofabrik's extract hasn't changed
- **THEN** the extract isn't downloaded again

#### Scenario: Failed download
- **WHEN** an agency's download fails partway
- **THEN** the fetch stops with an error, the import doesn't run, and no partial dataset is left where the import reads it

#### Scenario: Unknown region
- **WHEN** an operator runs the import script with only a region id that has no preset
- **THEN** it stops and says the region needs a preset or input files

#### Scenario: Missing tool
- **WHEN** `osmium` or `ogr2ogr` isn't installed
- **THEN** the fetch stops before downloading anything and says which tool to install
