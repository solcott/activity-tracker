# Spec Delta

## MODIFIED Requirements

### Requirement: A candidate holds what a segment needs
Each candidate SHALL hold its id, region, sport group, kind, name, line, length in metres, source, licence, whether it is one-way, and its climb when it has one (`uphill` or `downhill`). An OSM candidate SHALL have `source` `osm`, `license` `ODbL-1.0` and the ids of the OSM ways it follows. An agency candidate SHALL have `source` `agency`, `license` `CC0-1.0`, an attribution naming its agency, and the feature ids it follows. Its line SHALL be encoded as a `feed.segment` line is, stay within 1 m of the trail, and fit a `feed.segment` record.

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

### Requirement: A candidate's climb follows its trail
When extract was given terrain covering a candidate's line, its climb SHALL be its own line's clear climb or descent in its direction of travel (`segments/segment-direction`). When its own line neither clearly climbs nor descends, its climb SHALL be its trail's, judged over the whole trail's line where terrain covers it and reversed for travel against the trail's order. Otherwise it SHALL have none. Alpine runs and lifts SHALL have none.

#### Scenario: Flat piece of a downhill trail
- **WHEN** the one-way ride trail "Rush" descends clearly over its whole line, and its 522 m piece "Rush - Dreamline" is nearly flat by terrain
- **THEN** the piece's candidate has climb `downhill`

#### Scenario: Piece that climbs against its trail
- **WHEN** a piece of a trail that descends overall itself clearly climbs in its direction of travel
- **THEN** its candidate has climb `uphill`

#### Scenario: Rolling trail
- **WHEN** neither a piece nor its whole trail clearly climbs or descends
- **THEN** the piece's candidate has no climb

#### Scenario: No terrain given
- **WHEN** a region is extracted without terrain
- **THEN** no candidate has a climb
