# Spec Delta

## MODIFIED Requirements

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
A way SHALL NOT be a trail when it is tagged `access=private`, `access=no`, `area=yes`, `footway=sidewalk` or `footway=crossing`, or is a road, steps or a building passage. It SHALL be left out of route relations too, except where a trail or relation crosses short shared tread on a road. A way tagged `access=private` or `access=no`, or closed to the trail's sport group, SHALL never be crossed that way.

#### Scenario: Private track
- **WHEN** a named `highway=track` is tagged `access=private`
- **THEN** it gives no candidate for any group

#### Scenario: Sidewalk
- **WHEN** a named `highway=footway` is tagged `footway=sidewalk`
- **THEN** it gives no candidate

#### Scenario: Private road between two halves of a trail
- **WHEN** a trail's two halves meet a `highway=service` way tagged `access=private` 80 m apart
- **THEN** the trail isn't joined across it

### Requirement: Named ways join into trails
Ways that qualify for the same sport group, share a name once each name's generic words are dropped, and meet end to end SHALL join into one trail, named by the longest of its ways. Where more than two such ways meet at a node, the trail SHALL end there.

A trail that ends where no way of its name continues SHALL also join another trail of the same name and group when it shares tread with a road or another named way to get there: the other trail starts at a node of that same way, at most 200 m along it, and the way is open to the group. The trail SHALL follow that way between the two nodes.

#### Scenario: Trail mapped as several ways
- **WHEN** "Oak Trail" is mapped as four ways that meet end to end
- **THEN** they form one trail named "Oak Trail"

#### Scenario: Name with and without its generic word
- **WHEN** a 2 km "Canyon Hollow Trail" is mapped with 126 m of ways named "Canyon Hollow" in its middle
- **THEN** they form one trail

#### Scenario: Trail along a service road
- **WHEN** "Canyon Hollow Trail" meets the service road "Lower Corner Canyon Trail", follows it for 103 m, and leaves it
- **THEN** the trail continues along the road for those 103 m, and its candidates follow the road there

#### Scenario: Too far along the road
- **WHEN** a trail's two halves meet the same road 400 m apart
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
