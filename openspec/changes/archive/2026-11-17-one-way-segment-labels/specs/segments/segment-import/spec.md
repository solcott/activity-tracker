# Spec Delta

## MODIFIED Requirements

### Requirement: A published segment carries its candidate's data
A segment the importer publishes SHALL be a `feed.segment` in the service account's repo, with the candidate's sport group, kind, name, line, source and licence, its attribution when it has one, and `oneWay: true` when the candidate is one-way, keyed by the candidate's id. It SHALL be indexed, matched and given a status like any other segment.

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

### Requirement: The importer never edits or deletes its segments
Neither `stage` nor `publish` SHALL change or delete a segment once it's published, even when its trail is changed or removed in OSM, so its efforts and leaderboards stay. `refine` SHALL change only a segment's line, name and `oneWay`, and only as their own requirements allow; no importer command SHALL delete a segment.

#### Scenario: Trail removed from OSM
- **WHEN** a published candidate's trail is gone from a newer extract, and the region is staged again
- **THEN** its candidate is no longer staged, and its segment, with its efforts, stays

#### Scenario: Trail rerouted
- **WHEN** a rerouted trail gives a new candidate id whose line duplicates the published segment
- **THEN** the new candidate isn't published, and the old segment stays

### Requirement: Refining gives published segments a finer line along the same path
`refine` SHALL read a candidates file written by `extract` and, for each segment the importer published under one of its candidate ids, replace the segment's line with the candidate's when they differ and every point of each line is within 6 m of the other. Besides its name and `oneWay`, it SHALL change nothing else on the segment, and SHALL leave the line of a segment whose trail moved further, and the whole of a segment whose candidate is missing.

#### Scenario: Segment published at 5 m
- **WHEN** an operator runs `refine` with a new extract for a region whose segments were published with 5 m lines
- **THEN** each of those segments whose trail is unchanged gets its candidate's 1 m line, keeping its sport group, source, licence, attribution and URI

#### Scenario: Trail rerouted in OSM
- **WHEN** a published candidate's id stays the same but its trail now runs 20 m from the segment's line along part of its length
- **THEN** `refine` leaves that segment's line unchanged and reports it as moved

#### Scenario: Candidate gone
- **WHEN** a published segment's candidate id isn't in the file
- **THEN** `refine` leaves the segment as it is

#### Scenario: Already refined
- **WHEN** an operator runs `refine` twice with the same file
- **THEN** the second run writes nothing

#### Scenario: Efforts kept
- **WHEN** a segment's line is refined
- **THEN** the AppView recomputes its efforts, as for any line change, and passes that rode the trail still have efforts on it, since the finer line lies closer to the trail than the old one

#### Scenario: Only the importer's segments
- **WHEN** an athlete's own segment follows the same trail as a candidate in the file
- **THEN** `refine` doesn't touch it, since it's in the athlete's repo, not the importer's

## ADDED Requirements

### Requirement: Refining gives published segments their candidate's one-way flag
`refine` SHALL set each published segment's `oneWay` to its candidate's when the two differ, whether or not it changes the segment's line, in the same write as any rename or finer line, and SHALL report each segment it changed this way. It SHALL change nothing for a segment whose candidate is missing.

#### Scenario: Segment published before the flag existed
- **WHEN** the ride segment "Clarks" was published without `oneWay`, and its candidate in a new extract is one-way
- **THEN** `refine` writes it once with `oneWay: true`, keeping its URI and efforts, and reports it

#### Scenario: Flag and name together
- **WHEN** a published two-way segment's new candidate is named "Clarks (uphill)" in place of "Clarks (eastbound)"
- **THEN** `refine` renames it and leaves `oneWay` unset, in one write

#### Scenario: Already up to date
- **WHEN** an operator runs `refine` twice with the same file
- **THEN** the second run changes no segment's `oneWay`
