# Spec Delta

## MODIFIED Requirements

### Requirement: The map marks the start and end
Every route map of an activity (its detail page, its enlarged map, its feed card and the import preview) SHALL mark the first position of its line with a start marker and the last with an end marker, the end drawn above the start.

#### Scenario: Out-and-back run
- **WHEN** a run that goes out 5 km and back by another street is shown
- **THEN** its map shows a start marker at the first position and an end marker at the last

#### Scenario: Loop
- **WHEN** a ride that finishes where it started is shown
- **THEN** its end marker is drawn on top of its start marker
