# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
