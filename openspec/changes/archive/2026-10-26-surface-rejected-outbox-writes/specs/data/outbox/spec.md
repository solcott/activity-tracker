# Spec Delta

## Purpose

Defines how the client applies the athlete's queued writes to their PDS: in order, which failures are retried and which mean the PDS refused the write, what a refused write holds back, and how the athlete sees, retries and discards writes that couldn't be published.

## ADDED Requirements

### Requirement: Queued writes are applied in order
The client SHALL apply the athlete's queued writes to their PDS in the order they were queued. A write that fails for a reason that may pass, such as no connection, a server error, an expired session or being asked to slow down, SHALL stop the run and be retried, with every write after it, on the next run.

#### Scenario: Offline publish
- **WHEN** the athlete publishes an activity, then likes another, while the device has no connection, and the connection comes back
- **THEN** the activity is published first and the like second

#### Scenario: Server error
- **WHEN** the PDS answers a queued write with a server error
- **THEN** that write and every write after it stay queued and are sent again on the next run

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
The feed SHALL show how many changes couldn't be published, apart from how many are still uploading, and SHALL open a list of them. Each item SHALL name the change, by the activity's name for an activity and by its kind (a like, a comment, a block…) otherwise, and SHALL say the server refused it, without raw error text.

#### Scenario: One refused activity
- **WHEN** the athlete's activity "Morning ride" was rejected
- **THEN** the feed says one change couldn't be published, and its list shows "Morning ride" as refused by the server, with no error code or message from the PDS

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
