# Spec Delta

## ADDED Requirements

### Requirement: Refining renames published segments after their candidate
`refine` SHALL give each segment the importer published under one of the file's candidate ids its candidate's name when the two differ, whether or not it changes the segment's line, and SHALL report each segment it renamed. It SHALL rename a segment and refine its line in one write.

#### Scenario: Segment published with a long name
- **WHEN** a segment was published as "Gas Line Trail: Lower Corner Canyon Trail to Hoof N Boot Path" and its candidate in a new extract is named "Gas Line: Lower Corner Canyon → Hoof N Boot"
- **THEN** `refine` renames the segment, keeping its sport group, line, source, licence, attribution, URI and efforts, and reports it as renamed

#### Scenario: Trail moved
- **WHEN** a published segment's trail moved more than 6 m and its candidate has a shorter name
- **THEN** `refine` keeps the segment's line, gives it the candidate's name, and reports it as both moved and renamed

#### Scenario: Name and line both change
- **WHEN** a published segment's candidate has both a finer line along the same path and a shorter name
- **THEN** `refine` writes the segment once, with both

#### Scenario: Renamed in OSM
- **WHEN** a trail's name is changed in OSM and the region is extracted and refined again
- **THEN** its published segments take the new name

#### Scenario: Already renamed
- **WHEN** an operator runs `refine` twice with the same file
- **THEN** the second run renames nothing

## MODIFIED Requirements

### Requirement: The importer never edits or deletes its segments
Neither `stage` nor `publish` SHALL change or delete a segment once it's published, even when its trail is changed or removed in OSM, so its efforts and leaderboards stay. `refine` SHALL change only a segment's line and name, and only as their own requirements allow; no importer command SHALL delete a segment.

#### Scenario: Trail removed from OSM
- **WHEN** a published candidate's trail is gone from a newer extract, and the region is staged again
- **THEN** its candidate is no longer staged, and its segment, with its efforts, stays

#### Scenario: Trail rerouted
- **WHEN** a rerouted trail gives a new candidate id whose line duplicates the published segment
- **THEN** the new candidate isn't published, and the old segment stays

### Requirement: Refining gives published segments a finer line along the same path
`refine` SHALL read a candidates file written by `extract` and, for each segment the importer published under one of its candidate ids, replace the segment's line with the candidate's when they differ and every point of each line is within 6 m of the other. Besides its name, it SHALL change nothing else on the segment, and SHALL leave the line of a segment whose trail moved further, and the whole of a segment whose candidate is missing.

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
