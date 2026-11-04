# Spec Delta

## ADDED Requirements

### Requirement: The segment importer's segments start active
A segment in the configured segment importer's repo that duplicates nothing SHALL be `active` when the AppView indexes it, changes it or checks it again, instead of `proposed`, so it is searchable by everyone and never archived for lack of riders. Its duplicate test SHALL be unchanged. An importer segment indexed as `proposed` before this SHALL become `active` at the next status pass. Other creators' segments SHALL be unaffected.

#### Scenario: Imported trail indexed
- **WHEN** the AppView indexes a new segment from the segment importer's repo that duplicates nothing
- **THEN** its view shows the status `active`, and any viewer's search over its box returns it

#### Scenario: Imported duplicate
- **WHEN** the AppView indexes a segment from the segment importer's repo whose line duplicates an athlete's active segment
- **THEN** it is `duplicate` of that segment

#### Scenario: Athlete's segment unchanged
- **WHEN** the AppView indexes a new segment from an athlete's repo that duplicates nothing
- **THEN** its view shows the status `proposed`

#### Scenario: Earlier imports promoted
- **WHEN** the importer's segment was indexed `proposed` before this change, and the status pass runs
- **THEN** it becomes `active`

#### Scenario: Never archived
- **WHEN** the importer's segment was first indexed 181 days ago and nobody has ridden it
- **THEN** it stays `active`

#### Scenario: No importer configured
- **WHEN** the AppView has no segment importer configured
- **THEN** every new segment that duplicates nothing starts `proposed`
