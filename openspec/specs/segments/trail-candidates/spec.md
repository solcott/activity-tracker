# segments/trail-candidates Specification

## Purpose

Defines how public trail data becomes candidate segment lines before anyone rides them: which features qualify for which sport group, how trails are cut into candidates, how each is named and identified, and what a candidate holds.

## Requirements

### Requirement: Candidates are extracted offline from an OSM extract
The importer SHALL read a region id and, for that region, an OpenStreetMap `.osm.pbf` extract, any number of agency trail datasets, or both, and write the region's candidates from all of them to one file, one per line, sorted by id. It SHALL need no network access, account or AppView to do so, and SHALL refuse to run with no input.

#### Scenario: Extract a region
- **WHEN** an operator runs the importer's extract command on a Geofabrik extract with the region id `us-vermont`
- **THEN** it writes a candidates file for `us-vermont` without contacting any service

#### Scenario: OSM and agency data together
- **WHEN** an operator extracts `us-vermont` from a Geofabrik extract and the Forest Service's trails for Vermont
- **THEN** it writes one candidates file for `us-vermont` holding candidates from both sources

#### Scenario: No input
- **WHEN** an operator runs extract with a region id and neither an OSM extract nor an agency dataset
- **THEN** the importer stops with its usage and writes nothing

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
A candidate's id SHALL be derived only from its sport group, its direction, the source features it follows in order, its ends, and how often it passed its last end before ending there. Ids SHALL be unique within a candidates file. Extracting the same data twice SHALL give the same ids, and an edit elsewhere SHALL NOT change a candidate's id.

#### Scenario: Re-extract unchanged
- **WHEN** an operator extracts the same region from a newer extract in which only other trails changed
- **THEN** every unchanged candidate keeps its id

#### Scenario: Loop both ways
- **WHEN** a loop mapped as one closed way gives a candidate each way round
- **THEN** the two candidates have different ids

#### Scenario: Trail rerouted
- **WHEN** a trail's middle way is replaced by a new way
- **THEN** the candidates that followed it get new ids

#### Scenario: Agency feature reshaped
- **WHEN** a newer Forest Service dataset moves a trail feature's interior vertices but keeps its feature id and its ends
- **THEN** the candidates that follow it keep their ids

### Requirement: What an id's features and ends are, by source
For OSM data, the features a candidate's id follows SHALL be its ways and its ends SHALL be nodes. For an agency dataset, the features SHALL be the dataset's stable feature ids and the ends their positions to about a metre.

#### Scenario: OSM way split
- **WHEN** an OSM trail way a candidate follows is split into two ways
- **THEN** the candidate gets a new id, since the ways it follows changed

#### Scenario: Agency feature given a new id
- **WHEN** a newer agency dataset gives a trail a new feature id but keeps its line and ends
- **THEN** the candidates that follow it get new ids

### Requirement: A candidate holds what a segment needs
Each candidate SHALL hold its id, region, sport group, name, line, length in metres, source and licence. An OSM candidate SHALL have `source` `osm`, `license` `ODbL-1.0` and the ids of the OSM ways it follows. An agency candidate SHALL have `source` `agency`, `license` `CC0-1.0`, an attribution naming its agency, and the feature ids it follows. Its line SHALL be encoded as a `feed.segment` line is, stay within 5 m of the trail, and fit a `feed.segment` record.

#### Scenario: Candidate written
- **WHEN** an extract gives a candidate for a 2 km ride trail
- **THEN** its entry holds a line within 5 m of the trail, a length of about 2000 m, `source` `osm` and `license` `ODbL-1.0`

#### Scenario: Forest Service candidate written
- **WHEN** the Forest Service's trails give a candidate for a 3 km hiking trail
- **THEN** its entry holds `source` `agency`, `license` `CC0-1.0`, the attribution "USDA Forest Service" and the feature ids it follows

### Requirement: Candidates don't duplicate one another
When two candidates of one sport group duplicate each other, by the test the AppView uses for segments, the importer SHALL keep one: an agency's over OSM's, a route relation's over a named way's, a whole trail over a stretch, then the lower id.

#### Scenario: Route relation over a named trail
- **WHEN** a `route=mtb` relation follows exactly the ways of one named trail
- **THEN** only the relation's candidates are kept

#### Scenario: Agency trail over the same OSM trail
- **WHEN** a Forest Service trail and an OSM path of the same name give `hike` candidates whose lines duplicate each other
- **THEN** only the Forest Service's candidates are kept

#### Scenario: Different groups kept apart
- **WHEN** an agency trail gives only `hike` candidates and the OSM path along it gives `ride` and `hike` candidates
- **THEN** the OSM `ride` candidates are kept and the OSM `hike` candidates are dropped

### Requirement: Agency datasets are read as GeoJSON text sequences
The importer SHALL read each agency dataset as a GeoJSON text sequence of features in WGS 84, named with its kind: the Forest Service's National Forest System trails (`usfs`), the National Park Service's public trails (`nps`) or the Bureau of Land Management's public managed trails (`blm`).

#### Scenario: Unknown kind
- **WHEN** an operator gives an agency dataset of a kind the importer doesn't know
- **THEN** the importer stops with its usage and writes nothing

#### Scenario: BLM dataset
- **WHEN** an operator extracts `us-utah` from a Geofabrik extract and BLM's managed trails for the same area
- **THEN** the candidates file holds BLM trail candidates with `source` `agency`, `license` `CC0-1.0` and the attribution "Bureau of Land Management"

### Requirement: Agency features become lines of the trail network
Each LineString in an agency dataset, and each part of a MultiLineString, SHALL be one line of the trail network. A feature with no stable feature id, or whose geometry isn't a line, SHALL be skipped and counted in the run's report.

#### Scenario: Multi-part feature
- **WHEN** a Park Service feature is a MultiLineString of two parts
- **THEN** each part is a line of the network, both under the feature's id

#### Scenario: Feature without an id
- **WHEN** a Forest Service feature has no control number
- **THEN** it gives no candidate, and the run reports it as skipped

### Requirement: Forest Service features qualify by their managed and accepted uses
A Forest Service feature SHALL be a trail for `ride` when bicycles are managed or accepted on it, for `run` and `hike` when hikers are, for `ski` when cross-country skiers are, and for `paddle` when non-motorised watercraft are. It SHALL qualify only with a non-empty name.

#### Scenario: Forest Service trail open to bikes
- **WHEN** a named Forest Service trail has bicycles and hikers managed, with a season
- **THEN** it is a trail for `ride`, `run` and `hike`

#### Scenario: Wilderness trail
- **WHEN** a named Forest Service trail has hikers managed and bicycles neither managed nor accepted
- **THEN** it is a trail for `run` and `hike`, but not for `ride`

### Requirement: Park Service features qualify by their listed uses
A Park Service feature SHALL be a trail for each group its listed uses name: bicycle for `ride`, hiker or pedestrian for `run` and `hike`, cross-country ski for `ski`, and canoe, kayak or non-motorised watercraft for `paddle`. It SHALL qualify only with a non-empty name.

#### Scenario: Park trail for skiing
- **WHEN** a named Park Service trail lists hiker/pedestrian and cross-country ski uses
- **THEN** it is a trail for `run`, `hike` and `ski`

### Requirement: BLM features qualify by their allowed modes
A BLM feature SHALL be a trail for each group its allowed mode of transport names: bicycles or mountain bikes for `ride`, hiking for `run` and `hike`, and nordic or cross-country skiing for `ski`. When its allowed mode is unknown, a non-motorized planned mode SHALL count as `ride`, `run` and `hike`, and a non-mechanized one as `run` and `hike`. It SHALL qualify only with a non-empty name.

#### Scenario: Shared non-motorized trail
- **WHEN** a named BLM trail's allowed mode is shared non-motorized (hiking, bicycle, equestrian)
- **THEN** it is a trail for `ride`, `run` and `hike`

#### Scenario: Hiking and horses only
- **WHEN** a named BLM trail's allowed mode is equestrian and hiking only
- **THEN** it is a trail for `run` and `hike`, but not for `ride`

#### Scenario: Shared motorized trail
- **WHEN** a named BLM trail's allowed mode is shared motorcycle, ATV, mountain bike, equestrian and hiking
- **THEN** it is a trail for `ride`, `run` and `hike`

#### Scenario: Motor vehicles only
- **WHEN** a named BLM trail's allowed mode is motorcycle only
- **THEN** it gives no candidate

#### Scenario: Unknown mode on a non-mechanized trail
- **WHEN** a named BLM trail's allowed mode is unknown and its planned mode of transport is non-mechanized
- **THEN** it is a trail for `run` and `hike`

#### Scenario: Nordic trail
- **WHEN** a named BLM trail's allowed mode is shared non-mechanized snow travel (nordic ski, snowshoe)
- **THEN** it is a trail for `ski`

### Requirement: Closed and hidden agency trails are left out
A Park Service feature SHALL NOT be a trail unless its status is existing and it is marked for public display and, when the dataset says so, open to the public. A Forest Service feature with no managed or accepted use for any sport group SHALL give no candidate. A BLM feature whose access is restricted to administrators, to authorized or permitted users, or to all users SHALL give no candidate.

#### Scenario: Park trail not for public display
- **WHEN** a named Park Service trail is marked not for public map display
- **THEN** it gives no candidate for any group

#### Scenario: Planned park trail
- **WHEN** a named Park Service trail has the status planned
- **THEN** it gives no candidate

#### Scenario: BLM trail for permitted users only
- **WHEN** a named BLM trail open to hikers and bicycles has its access restricted to authorized or permitted users
- **THEN** it gives no candidate for any group

### Requirement: Agency features join into trails at shared ends
Within a run's agency datasets, line ends and vertices within 1 m of each other SHALL be one point of the network. Agency lines that qualify for the same sport group, share a name and meet end to end SHALL join into one trail, ending where more than two such lines meet. A junction SHALL be a point a trail shares with another named agency trail; agency trails SHALL NOT be cut where they meet OSM ways.

#### Scenario: Trail split at mileposts
- **WHEN** a Forest Service trail named "Long Trail" is published as three features that meet end to end
- **THEN** they form one trail named "Long Trail"

#### Scenario: Trails meeting at a junction
- **WHEN** a named Forest Service trail ends on a vertex of another named Forest Service trail
- **THEN** the second trail is cut there into stretches, as an OSM trail is at a junction

### Requirement: Agency trails go both ways
An agency trail SHALL give candidates in both directions, and be cut and named by the same rules as an OSM trail.

#### Scenario: Two-way agency trail
- **WHEN** a Park Service trail of 4 km has no junctions
- **THEN** it gives two candidates for each of its groups, one each way, named with their direction of travel

### Requirement: Agency names in capitals are title-cased
An agency feature's name written wholly in capital letters SHALL be turned into title case before it names a trail; any other name SHALL be kept as written.

#### Scenario: Capitalised Forest Service name
- **WHEN** a Forest Service feature is named "BEAVER POND TRAIL"
- **THEN** its trail is named "Beaver Pond Trail"

#### Scenario: Mixed-case name
- **WHEN** a Park Service feature is named "McKinley Bar Trail"
- **THEN** its trail keeps that name

### Requirement: Agency features close trails to sport groups
A Forest Service feature SHALL be closed to a sport group when that group's use is restricted all year and neither managed nor accepted in any season. A BLM feature SHALL be closed to each of `ride`, `run` and `hike` that its allowed mode of transport, when known and not over snow, leaves out, and to every group when its access is restricted to administrators, to authorized or permitted users, or to all users. A Park Service feature SHALL close nothing.

#### Scenario: Wilderness trail closed to bikes
- **WHEN** a Forest Service feature has bicycles restricted from January 1 to December 31 and hikers managed
- **THEN** it is closed to `ride`, and not to `run` or `hike`

#### Scenario: Seasonal restriction
- **WHEN** a Forest Service feature has bicycles restricted from November 1 to April 30 and managed in summer
- **THEN** it is not closed to `ride`

#### Scenario: BLM hiking and horses only
- **WHEN** a BLM feature's allowed mode is equestrian and hiking only
- **THEN** it is closed to `ride`

#### Scenario: BLM mode unknown
- **WHEN** a BLM feature's allowed mode is unknown
- **THEN** it closes nothing, whatever its planned mode of transport

#### Scenario: BLM trail for permitted users only
- **WHEN** a BLM feature's access is restricted to authorized or permitted users
- **THEN** it is closed to every group

#### Scenario: Park trail listing only hiking
- **WHEN** a Park Service feature lists only hiker/pedestrian use
- **THEN** it is not closed to `ride`

### Requirement: OSM candidates along an agency closure are left out
An OSM candidate of a sport group SHALL be left out when, for at least 100 m of its line, it lies within 15 m of an agency line closed to that group and not within 15 m of an agency line open to it. Candidates of other groups along the same line SHALL be kept, and agency candidates SHALL NOT be affected.

#### Scenario: OSM path through a wilderness
- **WHEN** an OSM path tagged for bikes and hikers follows, for 2 km, a Forest Service trail closed to `ride`
- **THEN** its `ride` candidates are left out and its `run` and `hike` candidates are kept

#### Scenario: Crossing a closed trail
- **WHEN** an OSM ride trail crosses a Forest Service trail closed to `ride` at right angles
- **THEN** its `ride` candidates are kept

#### Scenario: Parallel trail nearby
- **WHEN** an OSM ride trail runs 40 m from a Forest Service trail closed to `ride` along its whole length
- **THEN** its `ride` candidates are kept

#### Scenario: Short overlap
- **WHEN** an OSM ride candidate shares 60 m with a Forest Service trail closed to `ride`
- **THEN** it is kept

#### Scenario: Open in another feature
- **WHEN** a Forest Service summer feature closed to `ride` and a winter feature open to `ride` lie along the same OSM path
- **THEN** the OSM path's `ride` candidates are kept

#### Scenario: Stretches outside the closure
- **WHEN** an OSM ride trail is cut at junctions into three stretches and only the middle one follows a Forest Service trail closed to `ride`
- **THEN** the middle stretch's and the whole trail's `ride` candidates are left out, and the other two stretches' are kept

### Requirement: The run reports candidates left out by closures
The extract run SHALL report how many OSM candidates of each sport group it left out along agency closures.

#### Scenario: Report
- **WHEN** an extract leaves out four OSM `ride` candidates along Forest Service closures
- **THEN** its report says that 4 `ride` candidates were left out by agency closures
