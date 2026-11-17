# Spec Delta

## MODIFIED Requirements

### Requirement: Candidates follow the trail's permitted directions
A trail SHALL give candidates in both directions, unless it is tagged one-way (`oneway`, or `oneway:bicycle` for `ride`), when it SHALL give them only in its permitted direction. A candidate given in one direction only, by a one-way tag or because it is `alpine`, SHALL be marked one-way; a candidate given both ways SHALL not. An `alpine` trail SHALL give candidates only in the direction its ways are drawn, top to bottom by OSM convention, whatever its one-way tags.

#### Scenario: Two-way trail
- **WHEN** a named path has no one-way tag
- **THEN** each of its candidates appears twice, once each way

#### Scenario: One-way descent
- **WHEN** a ride trail is tagged `oneway=yes`
- **THEN** its `ride` candidates follow the way's direction only and are marked one-way

#### Scenario: Downhill piste
- **WHEN** a named downhill piste with no one-way tag is extracted
- **THEN** its `alpine` candidates follow the way's drawn direction only

#### Scenario: One-way for bikes only
- **WHEN** a named path is tagged `oneway:bicycle=yes` and has no `oneway` tag
- **THEN** its `ride` candidates follow the way's direction only and are marked one-way, and its `run` and `hike` candidates appear both ways and aren't

### Requirement: A candidate holds what a segment needs
Each candidate SHALL hold its id, region, sport group, kind, name, line, length in metres, source, licence, and whether it is one-way. An OSM candidate SHALL have `source` `osm`, `license` `ODbL-1.0` and the ids of the OSM ways it follows. An agency candidate SHALL have `source` `agency`, `license` `CC0-1.0`, an attribution naming its agency, and the feature ids it follows. Its line SHALL be encoded as a `feed.segment` line is, stay within 1 m of the trail, and fit a `feed.segment` record.

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

## ADDED Requirements

### Requirement: Extract takes terrain to name trails by climb
`extract` SHALL take an optional directory of terrain tiles, the same tiles the AppView reads for segment elevation. It SHALL count a candidate's gain and loss along its line, smoothed, as the AppView counts terrain elevation (`segments/segment-elevation`), wherever the tiles cover the whole line. Without tiles, or where they don't cover a line, it SHALL name that line by compass.

#### Scenario: Partial coverage
- **WHEN** extract is given tiles covering one trail of a region and not another
- **THEN** the covered trail's whole two-way candidates may be named by climb, and the other's are named by compass

### Requirement: Whole two-way trails that clearly climb are named uphill and downhill
When extract was given terrain covering a whole two-way trail's line, and the line clearly climbs or descends by it (`segments/segment-direction`), the trail's whole-trail names SHALL add uphill or downhill in place of the compass direction. A loop SHALL keep its turn.

#### Scenario: Whole two-way trail that climbs
- **WHEN** "Clarks Trail" may be run both ways, and the terrain given to extract shows its line clearly climbing from its start
- **THEN** its whole-trail `run` candidates are named "Clarks (uphill)" and "Clarks (downhill)"

#### Scenario: No terrain given
- **WHEN** the same region is extracted without terrain
- **THEN** those candidates are named by compass direction, as before
