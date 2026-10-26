## Purpose

Defines the record keys the client generates for new records: timestamp identifiers (TIDs) that never repeat within the app, so a write to one new record never overwrites another.

## ADDED Requirements

### Requirement: Generated record keys are unique and increasing
Every record key the client generates SHALL be a TID that differs from every other key it generated while the app ran, and is later in sort order than the one before it. This SHALL hold when keys are generated at the same moment on different threads, and when the clock stands still or goes back.

#### Scenario: Import racing a like
- **WHEN** a batch import creates activities while the athlete likes an activity, in the same microsecond on different threads
- **THEN** each new record gets its own key, and neither write overwrites the other

#### Scenario: Clock goes back
- **WHEN** the device clock is set back while the app runs, and the app creates a record
- **THEN** the new record's key sorts after every key the app generated before
