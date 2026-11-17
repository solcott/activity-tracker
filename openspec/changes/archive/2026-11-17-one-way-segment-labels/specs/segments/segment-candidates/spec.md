# Spec Delta

## ADDED Requirements

### Requirement: Staged candidates carry a one-way flag
`importer.putSegmentCandidates` SHALL take, with each candidate, whether it is one-way, and `importer.checkSegmentCandidates` SHALL return the stored flag with each candidate. A candidate that doesn't say SHALL not be one-way.

#### Scenario: One-way candidate staged
- **WHEN** the importer stages the `ride` candidate "Clarks" marked one-way
- **THEN** the AppView stores it as one-way, and checking it returns it marked one-way

#### Scenario: One-way not said
- **WHEN** the importer stages a candidate that doesn't say whether it is one-way
- **THEN** checking it returns it not one-way
