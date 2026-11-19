# Spec Delta

## ADDED Requirements

### Requirement: Staged candidates carry their climb
`importer.putSegmentCandidates` SHALL take, with each candidate, its climb (`uphill` or `downhill`) when it has one, and `importer.checkSegmentCandidates` SHALL return the stored climb with each candidate. A candidate staged without a climb, or with any other value, SHALL be returned without one.

#### Scenario: Downhill candidate staged
- **WHEN** the importer stages the `ride` candidate "Rush - Dreamline" with climb `downhill`
- **THEN** checking it returns climb `downhill`

#### Scenario: Candidate without a climb
- **WHEN** the importer stages a candidate with no climb
- **THEN** checking it returns no climb
