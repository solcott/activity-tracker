# Spec Delta

## MODIFIED Requirements

### Requirement: A segment is a public record
A segment SHALL be a `feed.segment` record in its creator's repo, never in a space. It SHALL hold a name, a sport group, its line as an ordered list of positions from start to end, its source, and the licence of the data it came from. It MAY name the activity it was traced from, and MAY name who to credit for its data as an attribution of at most 100 graphemes.

#### Scenario: Segment from an athlete
- **WHEN** an athlete's repo holds a `feed.segment` with a name, the sport group `ride` and a line
- **THEN** the AppView indexes it as a ride segment created by that athlete

#### Scenario: Segment in a space ignored
- **WHEN** a `feed.segment` record appears in an athlete's tier or `self` space
- **THEN** the AppView indexes nothing from it

#### Scenario: Segment with an attribution
- **WHEN** the importer's repo holds a `feed.segment` with `source` `agency` and the attribution "National Park Service"
- **THEN** the AppView indexes it and serves the attribution in the segment's record

#### Scenario: Attribution out of bounds
- **WHEN** a `feed.segment` has a blank attribution, or one over 100 graphemes
- **THEN** the AppView indexes the segment without it

### Requirement: A segment's data source is credited where its line is drawn
Wherever the app draws a segment's line, it SHALL credit the data the line came from, by the record's `source`: an `osm` segment SHALL show "© OpenStreetMap contributors" linking to OpenStreetMap's copyright page, with its licence; an `agency` segment SHALL credit trail data from the agency its attribution names, or public agency trail data when it names none; an `athlete` segment SHALL show only its creator. Any other source SHALL show its licence identifier.

#### Scenario: OpenStreetMap segment
- **WHEN** a viewer opens a segment whose record has `source` `osm` and `license` `ODbL-1.0`
- **THEN** the segment screen shows "© OpenStreetMap contributors" and the ODbL, and tapping the credit opens OpenStreetMap's copyright page

#### Scenario: Agency segment
- **WHEN** a viewer opens a segment whose record has `source` `agency` and the attribution "USDA Forest Service"
- **THEN** the segment screen credits trail data from the USDA Forest Service

#### Scenario: Agency segment without an attribution
- **WHEN** a viewer opens a segment whose record has `source` `agency` and no attribution
- **THEN** the segment screen credits public agency trail data

#### Scenario: Attribution on another source ignored
- **WHEN** a viewer opens an athlete's segment whose record has an attribution
- **THEN** the segment screen shows "By" and the creator's handle, and not the attribution

#### Scenario: Athlete segment
- **WHEN** a viewer opens a segment an athlete created from their ride
- **THEN** the segment screen shows "By" and the creator's handle, and no data credit

#### Scenario: Unknown source
- **WHEN** a viewer opens a segment whose record has `source` `survey` and `license` `CC-BY-4.0`
- **THEN** the segment screen shows `CC-BY-4.0` as the data's licence

### Requirement: Seeded segments can carry an import source
The development seed script SHALL be able to write a segment with `source` `osm` and `license` `ODbL-1.0`, or `source` `agency`, `license` `CC0-1.0` and the attribution "USDA Forest Service", instead of an athlete segment, so the credit can be tested without an importer.

#### Scenario: Seeding an OSM segment
- **WHEN** a developer runs the seed script for `alice` with a sample GPX file and the OSM source
- **THEN** `alice.test`'s repo holds a `feed.segment` with `source` `osm` and `license` `ODbL-1.0`, and its segment screen shows the OpenStreetMap credit

#### Scenario: Seeding an agency segment
- **WHEN** a developer runs the seed script for `alice` with a sample GPX file and the agency source
- **THEN** `alice.test`'s repo holds a `feed.segment` with `source` `agency` and the attribution "USDA Forest Service", and its segment screen credits the Forest Service
