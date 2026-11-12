# Spec Delta

## ADDED Requirements

### Requirement: A full run compares snapshots as reliably as one suite
A snapshot comparison SHALL give the same result whether its suite runs alone or within a full run of the app's tests on the same simulator, so that a full run with nothing changed passes every time.

#### Scenario: Two full runs
- **WHEN** the app's whole test suite runs twice in a row on the recorded simulator with nothing changed
- **THEN** both runs pass every snapshot comparison
