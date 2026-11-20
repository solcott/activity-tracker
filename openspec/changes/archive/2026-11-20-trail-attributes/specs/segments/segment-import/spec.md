## MODIFIED Requirements

### Requirement: A published segment carries its candidate's data
A segment the importer publishes SHALL be a `feed.segment` in the service account's repo, keyed by the candidate's id. It SHALL hold the candidate's sport group, kind, name, line, source and licence, its attribution when it has one, `oneWay: true` when the candidate is one-way, and the candidate's trail attributes, as `importer.checkSegmentCandidates` returns them, in `trail` when it has any. It SHALL be indexed, matched and given a status like any other segment.

#### Scenario: Segment written
- **WHEN** `publish` publishes a ridden Vermont ride candidate named "Hogback Trail"
- **THEN** the service account's repo holds a `feed.segment` under the candidate's id, named "Hogback Trail", with sport `ride`, the candidate's line, `source` `osm` and `license` `ODbL-1.0`

#### Scenario: Agency segment written
- **WHEN** `publish` publishes a ridden candidate from the Forest Service's trails
- **THEN** its `feed.segment` has `source` `agency`, `license` `CC0-1.0` and the attribution "USDA Forest Service"

#### Scenario: Turns active from past rides
- **WHEN** the AppView indexes a segment published from a candidate that three athletes rode
- **THEN** once the matcher has run, those athletes have efforts on it and it is `active`

#### Scenario: Lift written
- **WHEN** `publish` publishes a ridden lift candidate named "Summit Quad"
- **THEN** its `feed.segment` has sport `alpine`, kind `lift` and the name "Summit Quad"

#### Scenario: One-way segment written
- **WHEN** `publish` publishes the one-way `ride` candidate "Clarks"
- **THEN** its `feed.segment` holds `oneWay: true`, and the `run` and `hike` segments published from the trail's two-way candidates don't

#### Scenario: Segment written with trail attributes
- **WHEN** `publish` publishes a candidate whose check returns `surfaces` of 100% `gravel` and `dog: leashed`
- **THEN** its `feed.segment` holds a `trail` with those attributes

#### Scenario: Segment written without trail attributes
- **WHEN** `publish` publishes a candidate whose check returns no trail attributes
- **THEN** its `feed.segment` has no `trail`

### Requirement: The importer never edits or deletes its segments
Neither `stage` nor `publish` SHALL change or delete a segment once it's published, even when its trail is changed or removed in OSM, so its efforts and leaderboards stay. `refine` SHALL change only a segment's line, name, `oneWay`, `climb` and `trail`, and only as their own requirements allow. No importer command SHALL delete a segment.

#### Scenario: Trail removed from OSM
- **WHEN** a published candidate's trail is gone from a newer extract, and the region is staged again
- **THEN** its candidate is no longer staged, and its segment, with its efforts, stays

#### Scenario: Trail rerouted
- **WHEN** a rerouted trail gives a new candidate id whose line duplicates the published segment
- **THEN** the new candidate isn't published, and the old segment stays

## ADDED Requirements

### Requirement: Refine keeps a segment's trail attributes current
`refine` SHALL set the `trail` of each segment the importer published under one of the file's candidate ids to its candidate's trail attributes when the two differ, removing it when the candidate has none. It SHALL do so whether or not it changes the segment's line, in the same write as any change to its line, name, `oneWay` or `climb`, and SHALL report how many segments' attributes it changed. Two attribute sets that differ only in the order of their fields SHALL count as equal.

#### Scenario: Attributes backfilled
- **WHEN** `refine` runs with a newer candidates file over a segment published with no `trail`, and its candidate has `surfaces` of 100% `dirt`
- **THEN** the segment is rewritten under the same key with that `trail`, and its efforts stay

#### Scenario: Attributes unchanged
- **WHEN** a segment's `trail` already equals its candidate's attributes
- **THEN** `refine` doesn't rewrite it for its attributes

#### Scenario: Attribute removed in OSM
- **WHEN** a segment's `trail` holds `lit: yes`, and its candidate in the newer file has no attributes
- **THEN** `refine` removes the segment's `trail`
