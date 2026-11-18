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

### Requirement: Extract takes the region's language
`extract` SHALL take the region's language as an ISO 639-1 code, `en` when none is given, and drop generic words from names only for a language it has a word list for. For any other language it SHALL keep every name as its source has it, say so in its report, and otherwise extract as usual. It SHALL refuse a language option that isn't a two-letter code.

#### Scenario: Default language
- **WHEN** an operator extracts `us-utah` without a language
- **THEN** names are shortened with the English word list

#### Scenario: Language with no word list
- **WHEN** an operator extracts a region with the language `de`
- **THEN** "Hochwald Weg" keeps its name, the separators are still `›` and `→`, and the report says German names were kept as they are

#### Scenario: Malformed language
- **WHEN** an operator extracts with the language `english`
- **THEN** the importer stops with its usage and writes nothing

### Requirement: Extract takes the ref bridge limit
Extract SHALL take the longest a trail may follow ways carrying its own ref between two of its parts, in metres (`--ref-bridge-m`). The limit SHALL be 1,000 m when not given. Extract SHALL refuse a value that isn't a positive whole number. The import script SHALL pass the limit on to extract when the operator sets one.

#### Scenario: Default limit
- **WHEN** an operator extracts a region without `--ref-bridge-m`
- **THEN** a trail joins over ways carrying its ref for up to 1,000 m

#### Scenario: Limit given
- **WHEN** an operator extracts a region with `--ref-bridge-m 500`
- **THEN** a trail joins over ways carrying its ref for up to 500 m and no further

#### Scenario: Bad limit
- **WHEN** an operator runs extract with `--ref-bridge-m -5`
- **THEN** the importer stops with its usage and writes nothing

### Requirement: Trail ways qualify by sport group
A way SHALL be a trail for `ride` when it is a cycleway, or a path, track or bridleway not tagged `bicycle=no`, or carries `mtb:scale`. It SHALL be a trail for `run` and for `hike` when it is a path, footway, track or bridleway not tagged `foot=no`, or carries `sac_scale`. It SHALL be a trail for `ski` when tagged `piste:type=nordic`, and for `alpine` when tagged `piste:type=downhill`.

#### Scenario: Mountain bike path
- **WHEN** an extract has a named `highway=path` with `mtb:scale=2`
- **THEN** it is a trail for `ride`, `run` and `hike`

#### Scenario: Footway closed to bikes
- **WHEN** an extract has a named `highway=footway` without `bicycle=yes` or `bicycle=designated`
- **THEN** it is a trail for `run` and `hike`, but not for `ride`

#### Scenario: Piste mapped as an area
- **WHEN** a named `piste:type=downhill` way is tagged `area=yes`
- **THEN** it gives no candidate

### Requirement: A trail way needs a name
A way SHALL qualify only with a non-empty `name`, or for `alpine` a non-empty `piste:name` when it has no `name`.

#### Scenario: Unnamed path
- **WHEN** an extract has a `highway=path` with no `name`
- **THEN** it gives no candidate on its own

#### Scenario: Downhill piste named by its piste name
- **WHEN** an extract has a way tagged `piste:type=downhill` and `piste:name=Upper Lynx`, with no `name`
- **THEN** it is a trail for `alpine` named "Upper Lynx", and for no other group

### Requirement: Route relations are trails
A route relation tagged `route=mtb` or `route=bicycle` SHALL be a trail for `ride`; `route=hiking`, `route=foot` or `route=running` for `run` and `hike`; `route=ski` for `ski`; `route=piste` with `piste:type=downhill` for `alpine`; and `route=canoe` for `paddle`. Its member ways SHALL be joined in order into one trail named by the relation, whatever their own names. A relation whose ways can't be joined into one line SHALL give a trail for each joined stretch.

A road member of at most 200 m, open to the relation's sport groups and joining the members on both sides of it, SHALL be kept in the line rather than breaking it.

From each end of a joined stretch, member ways that belong to another named trail SHALL be dropped while a way of the relation's own name or family remains. A way belongs to another named trail when it has a name that, without its generic words, is neither the relation's name nor in its family. A stretch with no way of the relation's name or family SHALL keep all of its ways.

#### Scenario: Named hiking route over unnamed paths
- **WHEN** a `route=hiking` relation named "Ridge Loop" has three unnamed path members that join end to end
- **THEN** they form one `run` and one `hike` trail named "Ridge Loop"

#### Scenario: Canoe route
- **WHEN** a `route=canoe` relation follows a river
- **THEN** it is a trail for `paddle`, and no other group

#### Scenario: Downhill piste route
- **WHEN** a `route=piste` relation tagged `piste:type=downhill` and named "Lynx" joins two unnamed piste ways
- **THEN** they form one `alpine` trail named "Lynx"

#### Scenario: Relation ending on another trail
- **WHEN** a `route=mtb` relation named "Clarks Trail" joins three "Clarks Trail - Bikes Uphill Only Section" ways and then a way of "Ann's Trail"
- **THEN** its trail follows only the three Clarks ways

#### Scenario: Route over differently named ways
- **WHEN** a `route=mtb` relation named "Crest Loop" joins ways named "Ridge Trail" and "Saddle Trail" and none of its own name
- **THEN** its trail follows all of them

#### Scenario: Short road member
- **WHEN** a `route=mtb` relation's members are two trail ways joined by a 90 m `highway=service` way
- **THEN** they form one trail through the service road

### Requirement: Closed and unsuitable ways are left out
A way SHALL NOT be a trail when it is tagged `access=private`, `access=no`, `area=yes`, `footway=sidewalk` or `footway=crossing`, or is a road, steps or a building passage. It SHALL be left out of route relations too, except where a trail or relation crosses short shared tread on a road. A way tagged `access=private` or `access=no`, or closed to the trail's sport group, SHALL never be crossed that way, alone or as part of a chain of ways.

#### Scenario: Private track
- **WHEN** a named `highway=track` is tagged `access=private`
- **THEN** it gives no candidate for any group

#### Scenario: Sidewalk
- **WHEN** a named `highway=footway` is tagged `footway=sidewalk`
- **THEN** it gives no candidate

#### Scenario: Private road between two halves of a trail
- **WHEN** a trail's two halves meet a `highway=service` way tagged `access=private` 80 m apart
- **THEN** the trail isn't joined across it

#### Scenario: Private road in a chain
- **WHEN** a trail's two halves are 150 m apart over two roads, and one of them is tagged `access=private`
- **THEN** the trail isn't joined across them

### Requirement: Named ways join into trails
Ways that qualify for the same sport group, share a name once each name's generic words are dropped, and meet end to end SHALL join into one trail, named by the longest of its ways. Where more than two such ways meet at a node, the trail SHALL end there.

A trail that ends where no way of its name continues SHALL also join another trail of the same name and group when it shares tread with roads or other named ways to get there: the other trail starts at most 200 m away along a chain of connected ways, each open to the group. The trail SHALL follow those ways between the two trails, along the shortest such chain.

The chain MAY be longer, up to the extract's ref bridge limit, when every way in it lists one of the trail's own refs. The trail's ways at both ends of the chain SHALL carry that ref. A way's refs are its `ref` tag's values, split at `;` and trimmed.

#### Scenario: Trail mapped as several ways
- **WHEN** "Oak Trail" is mapped as four ways that meet end to end
- **THEN** they form one trail named "Oak Trail"

#### Scenario: Name with and without its generic word
- **WHEN** a 2 km "Canyon Hollow Trail" is mapped with 126 m of ways named "Canyon Hollow" in its middle
- **THEN** they form one trail

#### Scenario: Trail along a service road
- **WHEN** "Canyon Hollow Trail" meets the service road "Lower Corner Canyon Trail", follows it for 103 m, and leaves it
- **THEN** the trail continues along the road for those 103 m, and its candidates follow the road there

#### Scenario: Trail across two roads
- **WHEN** a trail's two halves are 180 m apart over two connected roads open to the group, and the roads carry no ref
- **THEN** they form one trail that follows both roads

#### Scenario: Too far along the road
- **WHEN** a trail's two halves meet the same road 400 m apart, and the road carries none of the trail's refs
- **THEN** they stay two trails

#### Scenario: Roads that carry the trail's number
- **WHEN** "Little Cottonwood Creek Trail", tagged `ref=1001`, leaves its path for Granite Cliffs Road (86 m) and Power Plant Lane (133 m), both tagged `ref=NF 87672;1001`, and resumes
- **THEN** it is one trail that follows both roads

#### Scenario: Refs past the limit
- **WHEN** a trail's two halves, both tagged `ref=1001`, are 1.4 km apart along roads that all carry `ref=1001`, and the limit is 1 km
- **THEN** they stay two trails

#### Scenario: One road without the ref
- **WHEN** a trail's two halves, both tagged `ref=1001`, are 600 m apart over three roads, and one of them carries no `1001`
- **THEN** they stay two trails

### Requirement: Each trail is cut at junctions into candidates
Each trail SHALL give one candidate for its whole length, and one for each stretch between junctions, where a junction is a node it shares with another named trail or a named road. A trail whose edges allow different directions SHALL instead be cut where they change, into runs that each allow one set of directions. Each run SHALL then give a candidate for its whole length and one for each of its stretches between junctions, and the trail SHALL give no whole-trail candidate.

A stretch shorter than the minimum segment length SHALL be joined to the next one. The minimum used for this is the largest of the sport groups that all of the trail's own ways are open to, so the trail's groups end their stretches at the same nodes. Candidates outside their own sport group's minimum and 50 km SHALL be dropped.

#### Scenario: Trail crossed by a fire road
- **WHEN** a 4 km ride trail is crossed by a named fire road 1.5 km from its start
- **THEN** it gives three `ride` candidates in each permitted direction: the whole trail, the first 1.5 km and the last 2.5 km

#### Scenario: Short stretch joined
- **WHEN** two junctions on a ride trail are 300 m apart
- **THEN** that stretch is joined to the next one rather than becoming a candidate

#### Scenario: Unnamed crossing
- **WHEN** an unnamed social path meets a trail
- **THEN** the trail isn't cut there

#### Scenario: Alternate line of the same name
- **WHEN** an informal "Canyon Hollow Trail" alternate leaves and rejoins "Canyon Hollow Trail"
- **THEN** the trail isn't cut where they meet, since a way of the trail's own name is no other trail

#### Scenario: Ride, run and hike cut alike
- **WHEN** a trail open to ride, run and hike has junctions 300 m and 700 m from its start
- **THEN** its `ride`, `run` and `hike` stretches all end 700 m from its start, not 300 m

#### Scenario: Relation through a one-way trail
- **WHEN** a 5 km `route=mtb` relation runs both ways for 3 km and then along a 2 km one-way climb
- **THEN** it gives no 5 km candidate, and gives the 3 km run both ways and the 2 km run uphill only, each with its own stretches

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

### Requirement: Candidates are named after their trail
A whole-trail candidate SHALL be named after its trail. A named-way trail whose ways are exactly those of a route relation's trail, of any sport group, SHALL be named after the relation.

A stretch, or a run cut at a change of direction, SHALL be named after its trail and the full names, sections included, of the named trails or roads at its ends, as "<trail>: <start> → <end>", or "<trail> (part n)" when an end has no name.

When a trail has candidates in both directions, a whole-trail name SHALL add its direction of travel: clockwise or counterclockwise for a loop, whose ends are within 30 m, otherwise the compass direction. Names SHALL be at most 100 graphemes.

#### Scenario: Stretch between two crossings
- **WHEN** a stretch of "Oak Trail" runs from where "Fire Road 3" crosses it to where "Pine Trail" meets it
- **THEN** it is named "Oak: Fire Road 3 → Pine"

#### Scenario: Stretch ending at a section
- **WHEN** a stretch of "Bonneville Shoreline Trail - Draper Section" runs from where "Rush Trail - Dreamline Section" meets it to where "Aqueduct Trail" meets it
- **THEN** it is named "Bonneville Shoreline - Draper: Rush - Dreamline → Aqueduct"

#### Scenario: Whole two-way trail
- **WHEN** "Oak Trail" runs mostly north from its start and may be ridden both ways
- **THEN** its whole-trail candidates are named "Oak (northbound)" and "Oak (southbound)"

#### Scenario: Two-way loop
- **WHEN** "Pond Loop" starts and ends at the same place and may be ridden both ways
- **THEN** its whole-trail candidates are named "Pond Loop (clockwise)" and "Pond Loop (counterclockwise)"

#### Scenario: Trail covered by a relation
- **WHEN** the `route=mtb` relation "Clarks Trail" follows exactly the ways named "Clarks Trail - Bikes Uphill Only Section"
- **THEN** the `run` and `hike` candidates of those ways are named "Clarks", like the relation's `ride` candidate

### Requirement: Generic words are dropped from names
Each OSM, route or agency name a candidate name is built from SHALL lose one trailing generic word of the region's language, matched as a whole word in any case: in English, Foot Path, Footpath, Trail, Path or Section. A `<Parent> - <Section>` name SHALL lose one from each side, and a name ending in a parenthesised qualifier one from before it. A name that would be left empty or only an article SHALL keep its word. Every other word, Road included, SHALL stay.

#### Scenario: Trail and path
- **WHEN** a stretch of "Gas Line Trail" runs from where "Lower Corner Canyon Trail" meets it to where "Hoof N Boot Path" meets it
- **THEN** it is named "Gas Line: Lower Corner Canyon → Hoof N Boot"

#### Scenario: Road kept
- **WHEN** a stretch of "Hog Hollow Road" runs from where "Pork U Pine Trail" meets it to where "Canyon Pointe Road" meets it
- **THEN** it is named "Hog Hollow Road: Pork U Pine → Canyon Pointe Road"

#### Scenario: Sectioned name
- **WHEN** a trail is named "Bonneville Shoreline Trail - Draper Section"
- **THEN** its whole-trail candidate is named "Bonneville Shoreline - Draper"

#### Scenario: Qualifier after the word
- **WHEN** "Peak View Trail (Lower)" may be ridden both ways and runs mostly east from its start
- **THEN** one of its whole-trail candidates is named "Peak View (Lower) (eastbound)"

#### Scenario: Nothing left without the word
- **WHEN** trails are named "Trail" and "The Trail"
- **THEN** their whole-trail candidates are named "Trail" and "The Trail"

#### Scenario: Generic word mid-name
- **WHEN** a trail is named "Section 2 Path"
- **THEN** its whole-trail candidate is named "Section 2"

#### Scenario: Agency name in capitals
- **WHEN** a Forest Service trail is named "SKYLINE TRAIL"
- **THEN** its whole-trail candidate is named "Skyline", title-cased as agency names are

### Requirement: Candidate ids are stable
A candidate's id SHALL be derived only from its sport group, its direction, the source features it follows in order, its ends, how often it passed its first end again, and how often it passed its last end before ending there. Ids SHALL be unique within a candidates file. Extracting the same data twice SHALL give the same ids, and an edit elsewhere SHALL NOT change a candidate's id.

#### Scenario: Re-extract unchanged
- **WHEN** an operator extracts the same region from a newer extract in which only other trails changed
- **THEN** every unchanged candidate keeps its id

#### Scenario: Loop both ways
- **WHEN** a loop mapped as one closed way gives a candidate each way round
- **THEN** the two candidates have different ids

#### Scenario: Lollipop and its stem
- **WHEN** a trail is one way that runs out to a loop and ends where the loop began, and a junction there cuts its stem into a stretch
- **THEN** the whole trail and the stem, travelled from the loop out, have different ids

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

### Requirement: Candidates don't duplicate one another
When two candidates of one sport group and kind duplicate each other, by the test the AppView uses for segments, the importer SHALL keep one, in this order of preference:
1. an agency's over OSM's;
2. a route relation's over a named way's;
3. a whole trail of one name over a parent trail;
4. a parent trail over a stretch;
5. then the lower id.

For this, a run cut at a change of direction SHALL count as a stretch.

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

#### Scenario: Relation over another relation's one-way run
- **WHEN** the Bonneville Shoreline relation's one-way run along Clarks duplicates the whole "Clarks Trail" relation's `ride` candidate
- **THEN** only the "Clarks" candidate is kept

### Requirement: Sectioned ways join into parent trails
OSM ways of one sport group named `<Parent>` or `<Parent> - <Section>` SHALL form a family named `<Parent>`. Each path through the family's ways joined end to end, from a way nothing joins into to a way that joins into nothing further, travelled in a direction all its ways allow, SHALL be a parent trail named `<Parent>` if it crosses at least two differently named ways. The trails of each name SHALL be unchanged.

#### Scenario: Descent mapped as named sections
- **WHEN** one-way ride ways "Rush Trail - Tom Sawyer Section", "Rush Trail", "Rush Trail - 2112 Section" and "Rush Trail - Limelight Section" meet end to end in that order, with no route relation over them
- **THEN** they form one `ride` parent trail named "Rush Trail" running from Tom Sawyer to Limelight, and each section still gives its own candidates

#### Scenario: Short section inside a parent trail
- **WHEN** a parent trail runs through a section shorter than its group's minimum segment length
- **THEN** the parent trail follows that section, though the section gives no candidate of its own

#### Scenario: One name only
- **WHEN** a family's ways that meet end to end are all named "Oak Trail - Upper Section"
- **THEN** they give no parent trail, only the trail named "Oak Trail - Upper Section"

#### Scenario: Two-way family
- **WHEN** two-way paths "Ridge Trail - North Section" and "Ridge Trail - South Section" meet end to end
- **THEN** they form a parent trail named "Ridge Trail" in each direction, named with its direction of travel like any two-way whole trail

### Requirement: A branching family gives a parent trail per path
Where a family's end-to-end joins branch, each path SHALL be its own parent trail, named `<Parent> › <section>` after the first section on it that is on none of the family's other paths, or `<Parent> from <section>` after its first section when every section on it is on another path too. A family SHALL give at most 4 parent trails per sport group, a path and its reverse counting as one; when it has more, only the longest SHALL be kept.

#### Scenario: Alternatives that rejoin
- **WHEN** "Rush Trail - Tom Sawyer Section" leads into both "Rush Trail - Exit Stage Left Section" and "Rush Trail - Through the Trees Trail Section", which both lead into the bare "Rush Trail" way
- **THEN** the family gives two parent trails, whose whole candidates are named "Rush › Exit Stage Left" and "Rush › Through the Trees Trail"

#### Scenario: Entry that joins partway down
- **WHEN** besides two paths from "Rush Trail - Tom Sawyer Section", a third path starts on a way named just "Rush Trail" and continues down "Rush Trail - Mystic Rhythms Section" and sections the other two also follow
- **THEN** the third parent trail's whole candidate is named "Rush from Mystic Rhythms"

#### Scenario: Too many paths
- **WHEN** a one-way family's joins give 6 paths
- **THEN** it gives one parent trail, along the longest path, named `<Parent>`

### Requirement: Ways that meet a family mid-way stay off its parent trails
A way SHALL join a parent trail only at its own end and the end of another family way. A family way that meets another only at a node in the middle of that way SHALL NOT be on any parent trail through it.

#### Scenario: Feeder into the middle of a section
- **WHEN** a second way named "Rush Trail - Limelight Section" runs from a foot path to a node in the middle of the main Limelight way
- **THEN** no parent trail follows the second way, and the parent trail follows the main Limelight way from end to end

### Requirement: Parent trails give their whole candidate and spans to junctions
A parent trail SHALL give its whole candidate and, at each junction, a span from its start to the junction and from the junction to its end, in each permitted direction. A junction is a node between two differently named sections of the trail where a named trail or road outside its family also meets it. A span following ways of only one name SHALL be left out. Ids, length bounds and closures SHALL apply as to any OSM candidate.

#### Scenario: Named trail at a section boundary
- **WHEN** the parent trail "Rush Trail" runs Tom Sawyer, 2112, Dreamline, Limelight, with "Canyon Hollow Trail" meeting it where 2112 ends and "Bonneville Shoreline Trail - Draper Section" where Dreamline ends
- **THEN** besides the whole trail it gives spans from its start to Canyon Hollow Trail, from its start to the Bonneville Shoreline Trail, and from Canyon Hollow Trail to its end, but none from the Bonneville Shoreline Trail to its end, which follows Limelight alone

#### Scenario: Parent trail crossed mid-section
- **WHEN** a named road crosses a parent trail in the middle of one of its sections
- **THEN** the parent trail gives no span there, and that section's own stretches end at the road

#### Scenario: Parent trail too long
- **WHEN** a family's path is longer than 50 km
- **THEN** it gives no whole candidate

### Requirement: Junction spans are named after their junction
A span ending at a junction SHALL be named `<trail> → <junction>`, and one starting at a junction `<trail> from <junction>`, in its direction of travel. `<trail>` is its parent trail's name when the span follows a way on none of the family's other parent trails, else `<Parent>`. `<junction>` is the family name of the first, by name, of the named ways outside the family there.

#### Scenario: Span down a branch
- **WHEN** the parent trail "Rush Trail" through "Rush Trail - Exit Stage Left Section", the only one of its family down that section, has a span from its start to where "Canyon Hollow Trail" and "Coyote Hollow Foot Path" meet it
- **THEN** the span is named "Rush › Exit Stage Left → Canyon Hollow"

#### Scenario: Span shared by every branch
- **WHEN** the spans of three parent trails of "Rush Trail" from Canyon Hollow Trail to their end follow the same ways
- **THEN** one candidate is kept for them, named "Rush from Canyon Hollow"

#### Scenario: Junction on a sectioned trail
- **WHEN** a span ends where "Bonneville Shoreline Trail - Draper Section" meets the parent trail
- **THEN** it is named after "Bonneville Shoreline", without its section

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
