# Spec Delta

## MODIFIED Requirements

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
