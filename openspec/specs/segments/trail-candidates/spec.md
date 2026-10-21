# segments/trail-candidates Specification

## Purpose

Defines how public trail data becomes candidate segment lines before anyone rides them: which features qualify for which sport group, how trails are cut into candidates, how each is named and identified, and what a candidate holds.

## Requirements

### Requirement: Candidates are extracted offline from an OSM extract
The importer SHALL read an OpenStreetMap `.osm.pbf` extract and a region id, and write that region's candidates to a file, one per line, sorted by id. It SHALL need no network access, account or AppView to do so.

#### Scenario: Extract a region
- **WHEN** an operator runs the importer's extract command on a Geofabrik extract with the region id `us-vermont`
- **THEN** it writes a candidates file for `us-vermont` without contacting any service

### Requirement: Trail ways qualify by sport group
A way SHALL be a trail for `ride` when it is a cycleway, or a path, track or bridleway not tagged `bicycle=no`, or carries `mtb:scale`. It SHALL be a trail for `run` and for `hike` when it is a path, footway, track or bridleway not tagged `foot=no`, or carries `sac_scale`. It SHALL be a trail for `ski` when tagged `piste:type=nordic`. A way SHALL qualify only with a non-empty `name`.

#### Scenario: Mountain bike path
- **WHEN** an extract has a named `highway=path` with `mtb:scale=2`
- **THEN** it is a trail for `ride`, `run` and `hike`

#### Scenario: Footway closed to bikes
- **WHEN** an extract has a named `highway=footway` without `bicycle=yes` or `bicycle=designated`
- **THEN** it is a trail for `run` and `hike`, but not for `ride`

#### Scenario: Unnamed path
- **WHEN** an extract has a `highway=path` with no `name`
- **THEN** it gives no candidate on its own

### Requirement: Route relations are trails
A route relation tagged `route=mtb` or `route=bicycle` SHALL be a trail for `ride`; `route=hiking`, `route=foot` or `route=running` for `run` and `hike`; `route=ski` for `ski`; and `route=canoe` for `paddle`. Its member ways SHALL be joined in order into one trail named by the relation, whatever their own names. A relation whose ways can't be joined into one line SHALL give a trail for each joined stretch.

#### Scenario: Named hiking route over unnamed paths
- **WHEN** a `route=hiking` relation named "Ridge Loop" has three unnamed path members that join end to end
- **THEN** they form one `run` and one `hike` trail named "Ridge Loop"

#### Scenario: Canoe route
- **WHEN** a `route=canoe` relation follows a river
- **THEN** it is a trail for `paddle`, and no other group

### Requirement: Closed and unsuitable ways are left out
A way SHALL NOT be a trail when it is tagged `access=private`, `access=no`, `area=yes`, `footway=sidewalk` or `footway=crossing`, or is a road, steps or a building passage. It SHALL be left out of route relations too.

#### Scenario: Private track
- **WHEN** a named `highway=track` is tagged `access=private`
- **THEN** it gives no candidate for any group

#### Scenario: Sidewalk
- **WHEN** a named `highway=footway` is tagged `footway=sidewalk`
- **THEN** it gives no candidate

### Requirement: Named ways join into trails
Ways that qualify for the same sport group, share a name and meet end to end SHALL join into one trail. Where more than two such ways meet at a node, the trail SHALL end there.

#### Scenario: Trail mapped as several ways
- **WHEN** "Oak Trail" is mapped as four ways that meet end to end
- **THEN** they form one trail named "Oak Trail"

### Requirement: Each trail is cut at junctions into candidates
Each trail SHALL give one candidate for its whole length, and one for each stretch between junctions, where a junction is a node it shares with another named trail or a named road. A stretch shorter than its sport group's minimum segment length SHALL be joined to the next one. Candidates outside the sport group's minimum and 50 km SHALL be dropped.

#### Scenario: Trail crossed by a fire road
- **WHEN** a 4 km ride trail is crossed by a named fire road 1.5 km from its start
- **THEN** it gives three `ride` candidates in each permitted direction: the whole trail, the first 1.5 km and the last 2.5 km

#### Scenario: Short stretch joined
- **WHEN** two junctions on a ride trail are 300 m apart
- **THEN** that stretch is joined to the next one rather than becoming a candidate

#### Scenario: Unnamed crossing
- **WHEN** an unnamed social path meets a trail
- **THEN** the trail isn't cut there

### Requirement: Candidates follow the trail's permitted directions
A trail SHALL give candidates in both directions, unless it is tagged one-way (`oneway`, or `oneway:bicycle` for `ride`), when it SHALL give them only in its permitted direction.

#### Scenario: Two-way trail
- **WHEN** a named path has no one-way tag
- **THEN** each of its candidates appears twice, once each way

#### Scenario: One-way descent
- **WHEN** a ride trail is tagged `oneway=yes`
- **THEN** its `ride` candidates follow the way's direction only

### Requirement: Candidates are named after their trail
A whole-trail candidate SHALL be named after its trail. A stretch SHALL be named after its trail and the named trails or roads at its ends, as "<trail>: <start> to <end>", or "<trail> (part n)" when an end has no name. When a trail has candidates in both directions, a whole-trail name SHALL add its direction of travel: clockwise or counterclockwise for a loop, whose ends are within 30 m, otherwise the compass direction. Names SHALL be at most 100 graphemes.

#### Scenario: Stretch between two crossings
- **WHEN** a stretch of "Oak Trail" runs from where "Fire Road 3" crosses it to where "Pine Trail" meets it
- **THEN** it is named "Oak Trail: Fire Road 3 to Pine Trail"

#### Scenario: Whole two-way trail
- **WHEN** "Oak Trail" runs mostly north from its start and may be ridden both ways
- **THEN** its whole-trail candidates are named "Oak Trail (northbound)" and "Oak Trail (southbound)"

#### Scenario: Two-way loop
- **WHEN** "Pond Loop" starts and ends at the same place and may be ridden both ways
- **THEN** its whole-trail candidates are named "Pond Loop (clockwise)" and "Pond Loop (counterclockwise)"

### Requirement: Candidate ids are stable
A candidate's id SHALL be derived only from its sport group, its direction, the OSM ways it follows in order, its first and last nodes, and how often it passed its last node before ending there. Ids SHALL be unique within an extract. Extracting the same data twice SHALL give the same ids, and an edit elsewhere in the extract SHALL NOT change a candidate's id.

#### Scenario: Re-extract unchanged
- **WHEN** an operator extracts the same region from a newer extract in which only other trails changed
- **THEN** every unchanged candidate keeps its id

#### Scenario: Loop both ways
- **WHEN** a loop mapped as one closed way gives a candidate each way round
- **THEN** the two candidates have different ids

#### Scenario: Trail rerouted
- **WHEN** a trail's middle way is replaced by a new way
- **THEN** the candidates that followed it get new ids

### Requirement: A candidate holds what a segment needs
Each candidate SHALL hold its id, region, sport group, name, line, length in metres, `source` `osm`, `license` `ODbL-1.0`, and the ids of the OSM ways it follows. Its line SHALL be encoded as a `feed.segment` line is, stay within 5 m of the trail, and fit a `feed.segment` record.

#### Scenario: Candidate written
- **WHEN** an extract gives a candidate for a 2 km ride trail
- **THEN** its entry holds a line within 5 m of the trail, a length of about 2000 m, `source` `osm` and `license` `ODbL-1.0`

### Requirement: Candidates don't duplicate one another
When two candidates of one sport group duplicate each other, by the test the AppView uses for segments, the importer SHALL keep one: a route relation's over a named way's, a whole trail over a stretch, then the lower id.

#### Scenario: Route relation over a named trail
- **WHEN** a `route=mtb` relation follows exactly the ways of one named trail
- **THEN** only the relation's candidates are kept
