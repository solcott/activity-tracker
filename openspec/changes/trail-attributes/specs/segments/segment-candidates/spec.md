## ADDED Requirements

### Requirement: Staged candidates carry their trail attributes
`importer.putSegmentCandidates` SHALL take, with each candidate, its trail attributes (`feed.defs#trailAttributes`) when it has any, and `importer.checkSegmentCandidates` SHALL return the stored attributes with each candidate. A candidate staged without attributes SHALL be returned without them. A field outside its vocabulary or range SHALL be dropped when staged, as the AppView drops it when serving (`segments/trail-attributes`). Staging a region again SHALL replace each candidate's attributes with the new run's.

#### Scenario: Candidate with attributes staged
- **WHEN** the importer stages the `ride` candidate "Hogback Trail" with `surfaces` of 100% `gravel` and `mtbScale: 1`
- **THEN** checking it returns those attributes

#### Scenario: Candidate without attributes
- **WHEN** the importer stages a candidate with no trail attributes
- **THEN** checking it returns none

#### Scenario: Restaged with new attributes
- **WHEN** a region is staged again and "Hogback Trail" now has `mtbScale: 2`
- **THEN** checking it returns `mtbScale: 2`
