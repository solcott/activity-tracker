# Spec Delta

## ADDED Requirements

### Requirement: A saved batch is one import in the upload queue
Each saved batch SHALL be listed in the upload queue as one import. It SHALL name what was imported, by the export recognized or else the picked files, and SHALL list the activities saved and the ones skipped at save.

#### Scenario: Strava export saved
- **WHEN** an athlete saves a Strava export's 1,362 activities with 3 skipped
- **THEN** the upload queue lists one Strava export import of 1,362 activities, 3 of them skipped

### Requirement: The saved screen opens the upload queue
After a batch is saved, the screen SHALL offer to open the upload queue.

#### Scenario: View uploads
- **WHEN** the athlete taps View uploads after saving a batch
- **THEN** the upload queue opens with that import listed
