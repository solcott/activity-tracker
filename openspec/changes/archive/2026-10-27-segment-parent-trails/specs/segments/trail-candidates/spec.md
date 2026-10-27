# Spec Delta

## ADDED Requirements

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
Where a family's end-to-end joins branch, each path SHALL be its own parent trail, named `<Parent> via <section>` after the first section on it that is on none of the family's other paths, or `<Parent> from <section>` after its first section when every section on it is on another path too. A family SHALL give at most 4 parent trails per sport group, a path and its reverse counting as one; when it has more, only the longest SHALL be kept.

#### Scenario: Alternatives that rejoin
- **WHEN** "Rush Trail - Tom Sawyer Section" leads into both "Rush Trail - Exit Stage Left Section" and "Rush Trail - Through the Trees Trail Section", which both lead into the bare "Rush Trail" way
- **THEN** the family gives two parent trails, "Rush Trail via Exit Stage Left Section" and "Rush Trail via Through the Trees Trail Section"

#### Scenario: Entry that joins partway down
- **WHEN** besides two paths from "Rush Trail - Tom Sawyer Section", a third path starts on a way named just "Rush Trail" and continues down "Rush Trail - Mystic Rhythms Section" and sections the other two also follow
- **THEN** the third parent trail is named "Rush Trail from Mystic Rhythms Section"

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
A span ending at a junction SHALL be named `<trail> to <junction>`, and one starting at a junction `<trail> from <junction>`, in its direction of travel. `<trail>` is its parent trail's name when the span follows a way on none of the family's other parent trails, else `<Parent>`. `<junction>` is the family name of the first, by name, of the named ways outside the family there.

#### Scenario: Span down a branch
- **WHEN** the parent trail "Rush Trail via Exit Stage Left Section" has a span from its start to where "Canyon Hollow Trail" and "Coyote Hollow Foot Path" meet it
- **THEN** the span is named "Rush Trail via Exit Stage Left Section to Canyon Hollow Trail"

#### Scenario: Span shared by every branch
- **WHEN** the spans of three parent trails of "Rush Trail" from Canyon Hollow Trail to their end follow the same ways
- **THEN** one candidate is kept for them, named "Rush Trail from Canyon Hollow Trail"

#### Scenario: Junction on a sectioned trail
- **WHEN** a span ends where "Bonneville Shoreline Trail - Draper Section" meets the parent trail
- **THEN** it is named after "Bonneville Shoreline Trail"

## MODIFIED Requirements

### Requirement: Candidates don't duplicate one another
When two candidates of one sport group duplicate each other, by the test the AppView uses for segments, the importer SHALL keep one: an agency's over OSM's, a route relation's over a named way's, a whole trail of one name over a parent trail, a parent trail over a stretch, then the lower id.

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
