# Spec Delta

## MODIFIED Requirements

### Requirement: Only ridden candidates that duplicate nothing are published
`publish` SHALL list the region's ridden candidates and check each one's line against the live segments of its sport group, by the same duplicate test the AppView applies when indexing. It SHALL check each page of up to 500 listed candidates with one call to the AppView, naming the candidates by id, not one call per candidate. It SHALL publish a candidate only when the check finds no duplicate. A candidate that isn't ridden, that duplicates a proposed or active segment, or that the check no longer finds staged in the region SHALL NOT be published. Only the importer account SHALL be able to make that check.

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
