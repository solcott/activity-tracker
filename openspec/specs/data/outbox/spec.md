# data/outbox Specification

## Purpose

Defines how the client applies the athlete's queued writes to their PDS: in order, which failures are retried and which mean the PDS refused the write, what a refused write holds back, and how the athlete sees, retries and discards writes that couldn't be published.

## Requirements

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

### Requirement: A write the PDS refuses is rejected, not retried
When the PDS refuses a queued write with a client error, other than an expired session, a timed-out request or being asked to slow down, the client SHALL mark the write as rejected and keep it on the device. It SHALL NOT send a rejected write again until the athlete retries it.

#### Scenario: Invalid record
- **WHEN** the PDS refuses a queued activity as an invalid record
- **THEN** the write is kept as rejected, and later runs don't send it

#### Scenario: Expired session
- **WHEN** the PDS refuses a queued write because the session expired
- **THEN** the write is not rejected, and it is sent again once the athlete is signed in

### Requirement: A rejected write holds back only its own record's writes
Writes queued after a rejected write SHALL still be applied, except those for the same record (its key), which SHALL wait behind it. An activity's details, streams, track, original file, devices, zones and segment track count as the same record as the activity.

#### Scenario: Unrelated write goes on
- **WHEN** an activity is rejected and a like on another athlete's activity was queued after it
- **THEN** the like is published

#### Scenario: Activity's details wait
- **WHEN** an activity is rejected and its details and streams were queued after it
- **THEN** its details and streams stay queued and are not sent

### Requirement: The athlete sees what couldn't be published
The feed SHALL show how many changes couldn't be published, apart from what is still uploading, and SHALL open the upload queue, which lists them with its refused items. Each item SHALL name the change, by the activity's name for an activity and by its kind (a like, a comment, a block…) otherwise, and SHALL say the server refused it, without raw error text.

#### Scenario: One refused activity
- **WHEN** the athlete's activity "Morning ride" was rejected
- **THEN** the feed says one change couldn't be published, and the upload queue shows "Morning ride" as refused by the server, with no error code or message from the PDS

#### Scenario: Nothing rejected
- **WHEN** no queued write is rejected
- **THEN** the feed shows no "couldn't be published" row

### Requirement: The athlete can retry a rejected change
Retrying an item SHALL send its rejected write, and the writes held behind it, again in their order. If the PDS refuses it again, it SHALL stay in the list as rejected.

#### Scenario: Retry succeeds
- **WHEN** the athlete retries a rejected activity and the PDS now accepts it
- **THEN** the activity, then its held details and streams, are published, and the item leaves the list

#### Scenario: Retry refused again
- **WHEN** the athlete retries a rejected activity and the PDS refuses it again
- **THEN** the item stays in the list as rejected, and its held writes stay queued

### Requirement: The athlete can discard a rejected change
Discarding an item SHALL remove its rejected write and the writes held behind it from the device. The app SHALL then show what the PDS holds for that record: an activity that was never published SHALL disappear, and a refused edit SHALL show the last published version.

#### Scenario: Discard a new activity
- **WHEN** the athlete discards a rejected activity that was never published
- **THEN** the activity and its held writes are removed, and the activity no longer appears in the feed or on the athlete's profile

#### Scenario: Discard a refused edit
- **WHEN** the athlete discards a rejected edit to a published activity
- **THEN** the activity shows the version the PDS holds, not the edit

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
On Android and iOS, when the app leaves the foreground with writes still queued that may be sent, the client SHALL ask the system to run the upload in the background with a network connection. A paused upload SHALL ask to run no earlier than the pause ends. Each background run SHALL send writes as the app would, stop when the system asks, and ask for another run while such writes remain. Writes held only by the athlete's pause of imports SHALL NOT ask for a run. On desktop and web, uploads SHALL continue only while the app is open. Only one upload SHALL run at a time per account.

#### Scenario: Android app closed during an import
- **WHEN** the athlete saves an import of 1,000 activities on Android and closes the app
- **THEN** the upload carries on in the background, pausing for each rate limit, until every write is sent

#### Scenario: Signed out
- **WHEN** the athlete signs out with writes still queued
- **THEN** no background upload runs for that account

#### Scenario: Imports paused by the athlete
- **WHEN** the athlete pauses imports and closes the app with only import writes queued
- **THEN** no background upload runs until they resume

### Requirement: The athlete sees when uploads are paused
While the upload waits out a rate limit, the feed SHALL say, beside what is still uploading, that the server paused the upload and the local time it will go on. While the athlete has paused imports, it SHALL say so.

#### Scenario: Paused import
- **WHEN** 3,000 imported activities are queued and the server paused the upload until 3:40 PM
- **THEN** the feed says 3,000 imported activities are uploading, paused by the server until 3:40 PM

#### Scenario: Not paused
- **WHEN** changes are uploading and no pause is in effect
- **THEN** the feed shows only what is uploading

#### Scenario: Paused by the athlete
- **WHEN** the athlete paused imports with 900 imported activities waiting
- **THEN** the feed says 900 imported activities are waiting, paused

### Requirement: The feed counts activities uploading
The feed's uploading row SHALL count activities, not writes: the athlete's own activities and edits apart from imported ones, plus how many other changes are uploading. Tapping it SHALL open the upload queue.

#### Scenario: Recording during an import
- **WHEN** one recorded run and 950 imported activities are still uploading, along with 3 likes
- **THEN** the feed says 1 activity and 950 imported are uploading, and 3 other changes

### Requirement: Cancelled writes are never sent
Writes the athlete cancels SHALL be removed from the device before any of them is sent. Cancelling SHALL be refused for an activity once any of its writes has been sent or is being sent.

#### Scenario: Cancel just before sending
- **WHEN** the athlete cancels a waiting activity at the moment the upload reaches it
- **THEN** either no write of it is sent and it's cancelled, or it's sending and the cancel is refused, never part of it sent

### Requirement: The feed loads without waiting for imports
Before its first load and on each refresh, the feed SHALL send only the athlete's queued writes that aren't held behind an import. It SHALL NOT wait for an import's writes before showing the timeline.

#### Scenario: Open the app mid-import
- **WHEN** the athlete opens the feed with 5,000 import writes queued
- **THEN** the timeline loads without waiting for them to upload
