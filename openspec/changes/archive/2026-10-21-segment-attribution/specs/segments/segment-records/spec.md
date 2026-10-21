# Spec Delta

## ADDED Requirements

### Requirement: A segment's data source is credited where its line is drawn
Wherever the app draws a segment's line, it SHALL credit the data the line came from, by the record's `source`: an `osm` segment SHALL show "© OpenStreetMap contributors" linking to OpenStreetMap's copyright page, with its licence; an `agency` segment SHALL credit public agency trail data; an `athlete` segment SHALL show only its creator. Any other source SHALL show its licence identifier.

#### Scenario: OpenStreetMap segment
- **WHEN** a viewer opens a segment whose record has `source` `osm` and `license` `ODbL-1.0`
- **THEN** the segment screen shows "© OpenStreetMap contributors" and the ODbL, and tapping the credit opens OpenStreetMap's copyright page

#### Scenario: Agency segment
- **WHEN** a viewer opens a segment whose record has `source` `agency`
- **THEN** the segment screen credits public agency trail data

#### Scenario: Athlete segment
- **WHEN** a viewer opens a segment an athlete created from their ride
- **THEN** the segment screen shows "By" and the creator's handle, and no data credit

#### Scenario: Unknown source
- **WHEN** a viewer opens a segment whose record has `source` `survey` and `license` `CC-BY-4.0`
- **THEN** the segment screen shows `CC-BY-4.0` as the data's licence

### Requirement: Seeded segments can carry an import source
The development seed script SHALL be able to write a segment with `source` `osm` and `license` `ODbL-1.0`, or `source` `agency` and `license` `CC0-1.0`, instead of an athlete segment, so the credit can be tested without an importer.

#### Scenario: Seeding an OSM segment
- **WHEN** a developer runs the seed script for `alice` with a sample GPX file and the OSM source
- **THEN** `alice.test`'s repo holds a `feed.segment` with `source` `osm` and `license` `ODbL-1.0`, and its segment screen shows the OpenStreetMap credit
