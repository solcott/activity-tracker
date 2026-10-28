# segments/segment-import Specification

## Purpose

Defines how candidate segments from public trail data reach the map: how the importer stages a region's candidates in the AppView, which ridden candidates it publishes as segments and from which account, and what it never does to a segment once published.

## Requirements

### Requirement: The importer acts as one service account
The importer SHALL sign in to one account of its own on a PDS, with credentials the operator supplies, and SHALL make every AppView call and write every segment as that account. That account SHALL be the one the AppView is configured to accept as the segment importer. The importer SHALL NOT act for any athlete.

#### Scenario: Missing credentials
- **WHEN** an operator runs `stage` or `publish` without the service account's credentials
- **THEN** the importer stops before calling the PDS or the AppView, and says which setting is missing

#### Scenario: Wrong account
- **WHEN** the importer signs in to an account the AppView isn't configured to accept
- **THEN** the AppView refuses its calls, and the importer stops and reports that the account isn't the configured importer

### Requirement: Staging uploads a whole candidates file as one run
`stage` SHALL read a candidates file written by `extract`, refuse a file of an unknown format version, and upload every candidate to the AppView in batches of at most 500, all under one revision for the run. Once every batch is accepted, it SHALL finish the run so the region's candidates from earlier runs are removed. It SHALL report how many candidates were staged and list each one the AppView rejected, with why.

#### Scenario: Region staged
- **WHEN** an operator stages a Vermont candidates file of 1,200 candidates
- **THEN** the importer uploads them in three batches, finishes the run, and reports 1,200 staged

#### Scenario: Rejected candidate
- **WHEN** the AppView rejects one candidate of a batch
- **THEN** the importer reports its id and the reason, and stages the rest

#### Scenario: Interrupted upload
- **WHEN** a batch fails partway through a run
- **THEN** the importer stops without finishing the run, so the region's candidates from earlier runs stay staged, and running `stage` again uploads the whole file

#### Scenario: Unknown file version
- **WHEN** an operator stages a candidates file whose format version the importer doesn't know
- **THEN** the importer refuses it before uploading anything

### Requirement: Only ridden candidates that duplicate nothing are published
`publish` SHALL list the region's ridden candidates and check their lines against the live segments of their sport groups, by the duplicate test the AppView applies when indexing, in one call per page of up to 500. It SHALL publish a candidate only when the check finds no duplicate. A candidate that isn't ridden, duplicates a proposed or active segment, or is no longer staged SHALL NOT be published. Only the importer SHALL be able to make the check.

#### Scenario: Ridden trail
- **WHEN** a staged candidate is ridden and no segment covers its line
- **THEN** `publish` writes a segment for it

#### Scenario: Already covered
- **WHEN** a ridden candidate follows an athlete's active segment
- **THEN** `publish` doesn't write a segment for it, and reports it as a duplicate

#### Scenario: Not ridden yet
- **WHEN** a staged candidate has two riders and the threshold is three
- **THEN** `publish` doesn't write a segment for it

#### Scenario: Many known duplicates
- **WHEN** a region has 300 ridden candidates that each duplicate a live segment, and `publish` runs again
- **THEN** it checks them with one call to the AppView, reports all 300 as duplicates, and writes nothing

#### Scenario: Duplicate no longer live
- **WHEN** the live segment a ridden candidate duplicated is deleted, and `publish` runs again
- **THEN** the check finds no duplicate, and `publish` writes a segment for the candidate

#### Scenario: Check by another account
- **WHEN** an athlete's account asks the AppView to check staged candidates
- **THEN** the AppView refuses it as not the importer

### Requirement: A published segment carries its candidate's data
A segment the importer publishes SHALL be a `feed.segment` in the service account's repo, with the candidate's sport group, name, line, source and licence, and its attribution when it has one, keyed by the candidate's id. It SHALL be indexed, matched and given a status like any other segment.

#### Scenario: Segment written
- **WHEN** `publish` publishes a ridden Vermont ride candidate named "Hogback Trail"
- **THEN** the service account's repo holds a `feed.segment` under the candidate's id, named "Hogback Trail", with sport `ride`, the candidate's line, `source` `osm` and `license` `ODbL-1.0`

#### Scenario: Agency segment written
- **WHEN** `publish` publishes a ridden candidate from the Forest Service's trails
- **THEN** its `feed.segment` has `source` `agency`, `license` `CC0-1.0` and the attribution "USDA Forest Service"

#### Scenario: Turns active from past rides
- **WHEN** the AppView indexes a segment published from a candidate that three athletes rode
- **THEN** once the matcher has run, those athletes have efforts on it and it is `active`

### Requirement: Publishing never doubles or rewrites a segment
`publish` SHALL write a segment only where the service account has no record under that candidate's id. Running `publish` again, or running it before the AppView has indexed an earlier run's segments, SHALL leave each published segment as it is.

#### Scenario: Re-run
- **WHEN** an operator runs `publish` twice for the same region
- **THEN** each ridden candidate has one segment, written by the first run

#### Scenario: Line fixed in OSM
- **WHEN** a published candidate's line changes in a later extract while its id stays the same, and it is ridden again
- **THEN** `publish` leaves the existing segment's line unchanged

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
