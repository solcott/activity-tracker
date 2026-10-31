## MODIFIED Requirements

### Requirement: Queued writes are applied in order
The client SHALL apply the athlete's queued writes for one record (its key) to their PDS in the order they were queued. Writes for different records SHALL go in the order they were queued, except that writes queued by a batch import (bulk writes) SHALL go after every other queued write. A write that fails for a reason that may pass, such as no connection, a server error or an expired session, SHALL stop the run and be retried, with every write after it, on the next run.

#### Scenario: Offline publish
- **WHEN** the athlete publishes an activity, then likes another, while the device has no connection, and the connection comes back
- **THEN** the activity is published first and the like second

#### Scenario: Server error
- **WHEN** the PDS answers a queued write with a server error
- **THEN** that write and every write after it stay queued and are sent again on the next run

#### Scenario: Like during an import
- **WHEN** 2,000 imported activities are still queued and the athlete likes a friend's ride
- **THEN** the like is sent before the activities still queued

#### Scenario: Edit of an activity still uploading
- **WHEN** the athlete edits the title of an imported activity whose bulk writes are still queued
- **THEN** the edit is sent after that activity's queued writes, not before them

## ADDED Requirements

### Requirement: Being asked to slow down waits for the server
When the PDS answers a queued write that it is rate limited, the client SHALL NOT count it as a failed attempt. It SHALL send nothing more to that PDS from the outbox until the time the PDS gives for the limit to reset, or one minute later when it gives none. It SHALL then go on by itself, without the athlete doing anything.

#### Scenario: Hourly limit reached
- **WHEN** the PDS answers a write at 2:41 PM as rate limited until 3:40 PM
- **THEN** no outbox write is sent before 3:40 PM, and the upload continues after 3:40 PM with the app open

#### Scenario: Like while paused
- **WHEN** the upload is paused by the server and the athlete likes an activity
- **THEN** the like is queued, shown as liked, and sent when the pause ends, ahead of the import's remaining writes

### Requirement: An import leaves room for the athlete's own writes
The client SHALL NOT send a bulk write while the PDS reports less than a tenth of the current limit remaining. It SHALL wait for that limit to reset instead, and other writes SHALL still be sent in that time.

#### Scenario: Comment near the limit
- **WHEN** an import's writes have used all but 400 of an hourly limit of 5,000 and the athlete comments on an activity
- **THEN** the comment is sent at once, and the import's writes wait for the next hour

### Requirement: The outbox drains on its own
While the athlete is signed in and the app is open, the client SHALL send queued writes without the athlete refreshing anything:
- as soon as they're queued,
- when a rate-limit pause ends,
- and again after a failure that may pass, waiting longer after each failure in a row, up to 15 minutes.

Signing out SHALL stop it.

#### Scenario: Connection comes back
- **WHEN** an import's upload stops because the device went offline, and the connection comes back while the app is open on the record screen
- **THEN** the upload continues within 15 minutes without the athlete opening the feed

### Requirement: Uploads continue in the background on phones
On Android and iOS, when the app leaves the foreground with writes still queued, the client SHALL ask the system to run the upload in the background with a network connection. A paused upload SHALL ask to run no earlier than the pause ends. Each background run SHALL send writes as the app would, stop when the system asks, and ask for another run while writes remain. On desktop and web, uploads SHALL continue only while the app is open. Only one upload SHALL run at a time per account.

#### Scenario: Android app closed during an import
- **WHEN** the athlete saves an import of 1,000 activities on Android and closes the app
- **THEN** the upload carries on in the background, pausing for each rate limit, until every write is sent

#### Scenario: Signed out
- **WHEN** the athlete signs out with writes still queued
- **THEN** no background upload runs for that account

### Requirement: The athlete sees when uploads are paused
While the upload waits out a rate limit, the feed SHALL say, beside how many changes are still uploading, that the server paused the upload and the local time it will go on.

#### Scenario: Paused import
- **WHEN** 3,000 changes are queued and the server paused the upload until 3:40 PM
- **THEN** the feed says 3,000 changes are uploading, paused by the server until 3:40 PM

#### Scenario: Not paused
- **WHEN** changes are uploading and no pause is in effect
- **THEN** the feed shows only how many changes are uploading
