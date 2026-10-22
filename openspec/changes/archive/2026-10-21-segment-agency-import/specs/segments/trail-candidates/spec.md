# Spec Delta

## MODIFIED Requirements

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

### Requirement: Candidate ids are stable
A candidate's id SHALL be derived only from its sport group, its direction, the source features it follows in order, its ends, and how often it passed its last end before ending there. For OSM the features are ways and the ends nodes; for an agency dataset the features are its stable feature ids and the ends their positions to about a metre. Ids SHALL be unique within a candidates file. Extracting the same data twice SHALL give the same ids, and an edit elsewhere SHALL NOT change a candidate's id.

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

## ADDED Requirements

### Requirement: Agency datasets are read as GeoJSON text sequences
The importer SHALL read each agency dataset as a GeoJSON text sequence of features in WGS 84, named with its kind: the Forest Service's National Forest System trails (`usfs`) or the National Park Service's public trails (`nps`). Each LineString, and each part of a MultiLineString, SHALL be one line of the trail network. A feature with no stable feature id, or whose geometry isn't a line, SHALL be skipped and counted in the run's report.

#### Scenario: Unknown kind
- **WHEN** an operator gives an agency dataset of a kind the importer doesn't know
- **THEN** the importer stops with its usage and writes nothing

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

### Requirement: Closed and hidden agency trails are left out
A Park Service feature SHALL NOT be a trail unless its status is existing and it is marked for public display and, when the dataset says so, open to the public. A Forest Service feature with no managed or accepted use for any sport group SHALL give no candidate.

#### Scenario: Park trail not for public display
- **WHEN** a named Park Service trail is marked not for public map display
- **THEN** it gives no candidate for any group

#### Scenario: Planned park trail
- **WHEN** a named Park Service trail has the status planned
- **THEN** it gives no candidate

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
