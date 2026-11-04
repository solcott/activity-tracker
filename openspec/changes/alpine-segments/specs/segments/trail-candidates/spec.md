# Spec Delta

## MODIFIED Requirements

### Requirement: Trail ways qualify by sport group
A way SHALL be a trail for `ride` when it is a cycleway, or a path, track or bridleway not tagged `bicycle=no`, or carries `mtb:scale`. It SHALL be a trail for `run` and for `hike` when it is a path, footway, track or bridleway not tagged `foot=no`, or carries `sac_scale`. It SHALL be a trail for `ski` when tagged `piste:type=nordic`, and for `alpine` when tagged `piste:type=downhill`. A way SHALL qualify only with a non-empty `name`, or for `alpine` a non-empty `piste:name` when it has no `name`.

#### Scenario: Mountain bike path
- **WHEN** an extract has a named `highway=path` with `mtb:scale=2`
- **THEN** it is a trail for `ride`, `run` and `hike`

#### Scenario: Footway closed to bikes
- **WHEN** an extract has a named `highway=footway` without `bicycle=yes` or `bicycle=designated`
- **THEN** it is a trail for `run` and `hike`, but not for `ride`

#### Scenario: Unnamed path
- **WHEN** an extract has a `highway=path` with no `name`
- **THEN** it gives no candidate on its own

#### Scenario: Downhill piste named by its piste name
- **WHEN** an extract has a way tagged `piste:type=downhill` and `piste:name=Upper Lynx`, with no `name`
- **THEN** it is a trail for `alpine` named "Upper Lynx", and for no other group

#### Scenario: Piste mapped as an area
- **WHEN** a named `piste:type=downhill` way is tagged `area=yes`
- **THEN** it gives no candidate

### Requirement: Route relations are trails
A route relation tagged `route=mtb` or `route=bicycle` SHALL be a trail for `ride`; `route=hiking`, `route=foot` or `route=running` for `run` and `hike`; `route=ski` for `ski`; `route=piste` with `piste:type=downhill` for `alpine`; and `route=canoe` for `paddle`. Its member ways SHALL be joined in order into one trail named by the relation, whatever their own names. A relation whose ways can't be joined into one line SHALL give a trail for each joined stretch.

#### Scenario: Named hiking route over unnamed paths
- **WHEN** a `route=hiking` relation named "Ridge Loop" has three unnamed path members that join end to end
- **THEN** they form one `run` and one `hike` trail named "Ridge Loop"

#### Scenario: Canoe route
- **WHEN** a `route=canoe` relation follows a river
- **THEN** it is a trail for `paddle`, and no other group

#### Scenario: Downhill piste route
- **WHEN** a `route=piste` relation tagged `piste:type=downhill` and named "Lynx" joins two unnamed piste ways
- **THEN** they form one `alpine` trail named "Lynx"

### Requirement: Candidates follow the trail's permitted directions
A trail SHALL give candidates in both directions, unless it is tagged one-way (`oneway`, or `oneway:bicycle` for `ride`), when it SHALL give them only in its permitted direction. An `alpine` trail SHALL give candidates only in the direction its ways are drawn, top to bottom by OSM convention, whatever its one-way tags.

#### Scenario: Two-way trail
- **WHEN** a named path has no one-way tag
- **THEN** each of its candidates appears twice, once each way

#### Scenario: One-way descent
- **WHEN** a ride trail is tagged `oneway=yes`
- **THEN** its `ride` candidates follow the way's direction only

#### Scenario: Downhill piste
- **WHEN** a named downhill piste with no one-way tag is extracted
- **THEN** its `alpine` candidates follow the way's drawn direction only

### Requirement: A candidate holds what a segment needs
Each candidate SHALL hold its id, region, sport group, kind, name, line, length in metres, source and licence. An OSM candidate SHALL have `source` `osm`, `license` `ODbL-1.0` and the ids of the OSM ways it follows. An agency candidate SHALL have `source` `agency`, `license` `CC0-1.0`, an attribution naming its agency, and the feature ids it follows. Its line SHALL be encoded as a `feed.segment` line is, stay within 1 m of the trail, and fit a `feed.segment` record.

#### Scenario: Candidate written
- **WHEN** an extract gives a candidate for a 2 km ride trail
- **THEN** its entry holds a line within 1 m of the trail, a length of about 2000 m, kind `run`, `source` `osm` and `license` `ODbL-1.0`

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

### Requirement: Candidates don't duplicate one another
When two candidates of one sport group and kind duplicate each other, by the test the AppView uses for segments, the importer SHALL keep one: an agency's over OSM's, a route relation's over a named way's, a whole trail of one name over a parent trail, a parent trail over a stretch, then the lower id.

#### Scenario: Route relation over a named trail
- **WHEN** a `route=mtb` relation follows exactly the ways of one named trail
- **THEN** only the relation's candidates are kept

#### Scenario: Agency trail over the same OSM trail
- **WHEN** a Forest Service trail and an OSM path of the same name give `hike` candidates whose lines duplicate each other
- **THEN** only the Forest Service's candidates are kept

#### Scenario: Different groups kept apart
- **WHEN** an agency trail gives only `hike` candidates and the OSM path along it gives `ride` and `hike` candidates
- **THEN** the OSM `ride` candidates are kept and the OSM `hike` candidates are dropped

#### Scenario: Parent trail barely longer than a section
- **WHEN** a parent trail is one long section plus a short one, and its line duplicates the long section's whole-trail candidate
- **THEN** only the section's candidate is kept

#### Scenario: Route relation over a parent trail
- **WHEN** a `route=mtb` relation named "Rush" follows the same ways as the parent trail "Rush Trail"
- **THEN** only the relation's candidates are kept

#### Scenario: Lift and run kept apart
- **WHEN** a lift candidate and an `alpine` run candidate follow the same line in the same direction
- **THEN** both are kept

## ADDED Requirements

### Requirement: Named aerialways are lifts
A way tagged `aerialway` as `cable_car`, `gondola`, `mixed_lift`, `chair_lift`, `drag_lift`, `t-bar`, `j-bar`, `platter` or `rope_tow`, with a non-empty `name`, SHALL give one `alpine` candidate of kind `lift`. Its line SHALL be the whole way in its drawn direction, bottom to top by OSM convention. It SHALL NOT be joined to other ways, cut at junctions or given a reverse candidate. Every other `aerialway` value, such as `zip_line`, `goods` or `magic_carpet`, SHALL give no candidate.

#### Scenario: Chairlift
- **WHEN** an extract has a 1.4 km way tagged `aerialway=chair_lift` named "Summit Quad"
- **THEN** it gives one lift candidate named "Summit Quad", following the way's drawn direction

#### Scenario: Lift crossing a piste
- **WHEN** a named chairlift's way shares a node with a named downhill piste
- **THEN** the lift still gives one candidate for its whole line, and the piste may be cut there

#### Scenario: Zip line
- **WHEN** an extract has a named way tagged `aerialway=zip_line`
- **THEN** it gives no candidate

#### Scenario: Short lift
- **WHEN** a named `aerialway=rope_tow` is 120 m long
- **THEN** it gives no candidate, being under the 200 m minimum

### Requirement: Lift candidates are named after the lift
A lift candidate SHALL be named after its way's `name`, without the generic words dropped from trail names and without a direction.

#### Scenario: Gondola named with a generic word
- **WHEN** a named `aerialway=gondola` is called "Peak Gondola"
- **THEN** its candidate is named "Peak Gondola"
