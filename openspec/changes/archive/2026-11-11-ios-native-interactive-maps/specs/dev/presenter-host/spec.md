# Spec Delta

## ADDED Requirements

### Requirement: A screen can embed another screen
A UI SHALL be able to ask a host record for a screen its page shows inside itself (the Search tab's segment browse), which the host runs
inside that record's presenter while it runs and whose navigation goes to the record's stack, as a screen the shared UI places with
`CircuitContent`. Asking again for an equal screen SHALL give the same embedded record, and its state SHALL be kept and dropped with its
record's.

#### Scenario: Opening from an embedded screen
- **WHEN** the Search tab's root embeds segment browse and alice opens a segment from it
- **THEN** the segment's page goes on the Search tab's stack, and back returns to the root with browse as it was

#### Scenario: Asking again
- **WHEN** a UI asks the Search record for segment browse twice
- **THEN** both get the same embedded record with the same state
