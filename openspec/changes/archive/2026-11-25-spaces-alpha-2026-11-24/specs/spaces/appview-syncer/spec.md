# Spec Delta

## ADDED Requirements

### Requirement: Stored space URIs follow the host's form
Space URIs the AppView and clients have stored (the AppView's `space_sync` key and every `origin`, a client's queued outbox writes) SHALL be rewritten to the `at:space/` form once, by a migration that changes only values still in the old form, so stored spaces keep matching what hosts send. Their notification tokens and rows SHALL be kept.

#### Scenario: AppView upgraded with synced spaces
- **WHEN** the AppView migrates a database holding `at://did:plc:alice/space/app.activitytracker.space.athlete/followers` rows
- **THEN** they read `at:space/did:plc:alice/app.activitytracker.space.athlete/followers`, `repo` origins are untouched, and the space keeps its notify token

#### Scenario: Queued space write survives an app update
- **WHEN** the app updates while a write to an old-form space is queued
- **THEN** the write is sent to the same space under its new-form URI
