# Spec Delta

## ADDED Requirements

### Requirement: Trail attributes record their sources
`feed.defs#trailAttributes` SHALL accept an optional `sources` object, naming for any attribute it holds where it came from: `osm`, `usfs`, `nps` or `blm`. The importer SHALL fill it for each attribute it writes with the source whose ways or features give that attribute along the most of the line. The candidates and paths file formats SHALL carry it, and their versions SHALL go up.

#### Scenario: Mixed sources
- **WHEN** a candidate's surface comes from 1.2 km of Forest Service features and 0.3 km of OSM ways
- **THEN** its `sources` give `usfs` for surface

#### Scenario: Old record
- **WHEN** a segment's `trail` has no `sources`
- **THEN** it is served as before, its details' sources being `imported`

### Requirement: The Trail page rolls segment details up
On the Trail page, a detail SHALL read "Varies along the trail" when any of the path's served segments has a community value for it (`segments/trail-detail-suggestions`) that differs from the path's own. The row SHALL say on how many segments riders changed it, and list those segments with their values and sources, each opening its Segment page. Surface SHALL keep the path's percents above that list.

#### Scenario: Dogs vary
- **WHEN** a trail's record says `dog: leashed` and one of its four segments adopted "No dogs"
- **THEN** its Dogs row reads "Varies along the trail", says riders changed it on 1 segment, and lists that segment with "No dogs · Community · 9 riders"

#### Scenario: Same value adopted
- **WHEN** a segment on a trail adopted the trail's own dogs value
- **THEN** the trail's Dogs row reads as before

### Requirement: The folded line and the Trail page stay read-only for details
In the Trail page's folded line, a varying detail SHALL read as varying, such as "Dogs vary". Trail details SHALL NOT be suggested or confirmed from the Trail page; its rows open the Segment page instead.

#### Scenario: Folded line
- **WHEN** a trail's dogs vary along it and its folded line would show dogs
- **THEN** the line reads "Dirt · Singletrack · Dogs vary"

#### Scenario: No sheet on the Trail page
- **WHEN** an athlete taps a varying segment in a Trail page row
- **THEN** that segment's page opens, where they may confirm or suggest

## MODIFIED Requirements

### Requirement: Attributes are served only for the importer's records
The segment view SHALL carry a segment's effective trail details (`segments/trail-detail-suggestions`). The path view SHALL carry a path's `trail`. The AppView SHALL drop the following from a served `trail`: any field outside its vocabulary or range, `openFrom` without `openUntil` or the reverse, a `website` that isn't an absolute `http` or `https` URL, and an `operator` longer than 100 graphemes. It SHALL serve no `trail` when nothing is left.

#### Scenario: Athlete's segment with attributes
- **WHEN** an athlete publishes a `feed.segment` in their own repo with `trail` holding `mtbScale: 2`
- **THEN** its view carries no trail attributes

#### Scenario: Athlete's segment with a community value
- **WHEN** that athlete's segment then adopts a community `mtbScale` of 3
- **THEN** its view carries `mtbScale: 3` and nothing from its record's `trail`

#### Scenario: Unsafe website dropped
- **WHEN** an importer path's `trail.website` is `javascript:alert(1)`
- **THEN** its view carries the path's other attributes and no website
