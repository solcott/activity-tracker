# Spec Delta

## ADDED Requirements

### Requirement: Duplicates and Add a recording on the native import screen
On iOS the native import screen SHALL show the duplicate search's progress and failure with Retry, each duplicate's warning with Ignore, Import anyway and Combine (Combine left out while the logged activity is still uploading), and, opened with Add a recording, the activity the recording is added to with no visibility choices, refusing a file that starts too far from it.

#### Scenario: Retrying the search
- **WHEN** the log search failed and alice taps Retry
- **THEN** the search runs again

#### Scenario: Adding a recording
- **WHEN** alice opens Add a recording from her activity's page and picks a file starting 20 hours later
- **THEN** the screen names her activity, says the file is too far from it, and Save is held back
