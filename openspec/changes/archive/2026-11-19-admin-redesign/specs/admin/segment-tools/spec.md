# Spec Delta

## ADDED Requirements

### Requirement: Segments page summary
The Segments page SHALL offer a summary with two tiles. Pins on missing segments: their count, `warn` when any, else `ok`, linking to their list. Latest import: when the importer last staged a candidate in any region, with no status, linking to the importer runs. Without a configured segment importer the latest import tile SHALL be left out. Neither tile SHALL show a rider count.

#### Scenario: Orphaned pin
- **WHEN** a pinned segment was deleted by its creator
- **THEN** the pins tile shows 1 and is `warn`

#### Scenario: Importer staged recently
- **WHEN** the importer last staged a candidate two hours ago
- **THEN** the latest import tile shows about two hours ago with no status

#### Scenario: No importer
- **WHEN** no segment importer is configured
- **THEN** the Segments summary has only the pins tile
