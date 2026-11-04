# Spec Delta

## MODIFIED Requirements

### Requirement: Only ridden candidates that duplicate nothing are published
`publish` SHALL list the region's ridden candidates and check their lines against the live segments of their sport groups and kinds, by the duplicate test the AppView applies when indexing, in one call per page of up to 500. It SHALL publish a candidate only when the check finds no duplicate. A candidate that isn't ridden, duplicates a proposed or active segment, or is no longer staged SHALL NOT be published. Only the importer SHALL be able to make the check.

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

#### Scenario: Lift beside an athlete's run
- **WHEN** a ridden lift candidate follows the line of an athlete's active `alpine` run
- **THEN** the check finds no duplicate, and `publish` writes the lift

### Requirement: A published segment carries its candidate's data
A segment the importer publishes SHALL be a `feed.segment` in the service account's repo, with the candidate's sport group, kind, name, line, source and licence, and its attribution when it has one, keyed by the candidate's id. It SHALL be indexed, matched and given a status like any other segment.

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
