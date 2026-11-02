## MODIFIED Requirements

### Requirement: The log search covers the whole batch
The search of the athlete's log SHALL cover every activity in the batch, from 14 hours before the earliest start to 14 hours after the latest end, however many years that spans and however many activities the log holds in it. The screen SHALL say if the search couldn't finish, and saving SHALL still be possible.

#### Scenario: Re-importing an export
- **WHEN** an athlete who imported their Strava export last month picks it again
- **THEN** every activity is flagged as already in their log, back to the export's oldest

#### Scenario: Search fails partway
- **WHEN** the AppView can't be reached while searching a ten-year batch
- **THEN** the screen says duplicates couldn't be fully checked, and the athlete can still save

#### Scenario: Single file when the search fails
- **WHEN** the athlete picks one file and their log can't be read
- **THEN** the preview says duplicates couldn't be fully checked, and the athlete can still save

## ADDED Requirements

### Requirement: Activities on the device are always checked
Every import SHALL be checked against the athlete's activities the device knows of, whether or not their log can be read: those waiting to upload, those in the cached feed, and those the device saved in the last 7 days. When the log can't be read, the screen SHALL say only these were checked.

#### Scenario: PDS down after an import
- **WHEN** the athlete's PDS can't be reached and they pick again files they imported on this device yesterday
- **THEN** those activities are flagged as already in their log, and the screen says only activities on this device were checked

#### Scenario: Waiting activities
- **WHEN** an import is still uploading and the athlete picks one of its files again while offline
- **THEN** that activity is flagged as already in their log

### Requirement: The log search can be retried
When the search of the athlete's log couldn't finish, the screen SHALL offer to search again, keeping the athlete's choices for activities whose matches don't change.

#### Scenario: Retry after the PDS comes back
- **WHEN** the search failed, the PDS comes back, and the athlete taps Retry
- **THEN** the whole batch is searched again, and the notice goes once it completes
