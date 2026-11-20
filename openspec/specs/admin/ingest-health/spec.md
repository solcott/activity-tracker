# ingest-health Specification

## Purpose
Shows the AppView's operators on the admin site how ingest and background work stand (workers, the Jetstream and label streams, space syncing, the database's size), and lets operators rewind Jetstream and restart a worker.

## Requirements

### Requirement: Health page lists every worker
The admin site SHALL have a health page, open to every admin, listing every background worker the AppView knows of: its name, kind, state, when its current run started, when it last succeeded, its last recorded error and when, how many rounds or events it completed, how many times it was restarted after a crash and on request, and, for a disabled worker, why it is off.

#### Scenario: Viewer opens the page
- **WHEN** bob, a `viewer`, opens the health page
- **THEN** he sees every worker with its state, last success and last error, and no action controls

#### Scenario: Disabled worker
- **WHEN** the AppView runs without its signing key
- **THEN** the page lists the space syncer and segment matcher as disabled, each with the missing setting as its reason

#### Scenario: Failing worker
- **WHEN** the Jetstream worker's last connection failed a minute ago
- **THEN** its row shows that error's recorded text and time beside its last success

### Requirement: Health page shows Jetstream's position
The health page SHALL show the stored Jetstream cursor as a time, the time of the last event this process indexed, and the lag between that event and now. With Jetstream switched off it SHALL say so instead.

#### Scenario: Falling behind
- **WHEN** the last event the AppView indexed was stamped two minutes ago
- **THEN** the page shows that event's time and a lag of about two minutes

#### Scenario: Before the first event
- **WHEN** the AppView has indexed no event since it started
- **THEN** the page shows the stored cursor and says no event was indexed yet, rather than a lag

### Requirement: Health page shows each labeler's stream
The health page SHALL show, for each configured labeler, its DID, its stored cursor, whether its stream is open now and when its cursor last advanced. With no labeler configured it SHALL say so.

#### Scenario: Labeler disconnected
- **WHEN** the Ozone labeler's stream dropped and hasn't reconnected
- **THEN** its row shows the stream closed, its stored cursor and when the cursor last advanced

### Requirement: Health page summarises space syncing
The health page SHALL show how many spaces the AppView syncs, how many have failed their latest syncs, how many their host reports missing and how many are due now, and list the spaces with the most consecutive failures, at most 50, each with its space URI, authority DID, failure count, last recorded error and its time, next attempt and last success.

#### Scenario: Failing space
- **WHEN** alice's followers space has failed three syncs in a row
- **THEN** it is listed with three failures, the last error's recorded text and time, and when it will be tried next

#### Scenario: Syncer disabled
- **WHEN** the AppView runs without its signing key
- **THEN** the section says space syncing is off

### Requirement: Health page shows database size where the database reports it
On Postgres, the health page SHALL show the database's total size and each AppView table's size including its indexes and its estimated row count, largest first. On a database that doesn't report sizes it SHALL say sizes aren't available. Reading sizes SHALL NOT scan any table, and SHALL be read at most once a minute however often the page loads.

#### Scenario: Postgres
- **WHEN** an admin opens the health page on the AppView's Postgres database
- **THEN** it shows the database size and every table's size and estimated rows, largest first

#### Scenario: Other database
- **WHEN** the AppView runs on a database that reports no sizes
- **THEN** the section says sizes aren't available and the rest of the page loads

#### Scenario: Repeated loads
- **WHEN** the page is loaded ten times in a minute
- **THEN** the database is asked for sizes at most once for them

### Requirement: Health page reveals no record content
The health page SHALL show only worker names, configured service DIDs, account DIDs, space URIs, states, counts, sizes, times and error text as the AppView recorded it, which carries no record content. It SHALL NOT show any record, stream event or label value.

#### Scenario: Space of an athlete with private activities
- **WHEN** alice's Only me space is failing to sync
- **THEN** the page shows its URI, her DID, the failure count and the recorded error, and nothing of her activities

### Requirement: Operators rewind Jetstream
An operator SHALL be able to rewind Jetstream to a given time. The AppView SHALL then resume Jetstream from that time, indexing every event from it again, and SHALL keep the rewound position even if the consumer was mid-stream. The action SHALL be refused when Jetstream is switched off.

#### Scenario: Replay after a fix
- **WHEN** alice, an `operator`, rewinds Jetstream to three hours ago
- **THEN** the consumer reconnects from three hours ago and every record created, changed or deleted since is indexed as it was the first time

#### Scenario: Consumer saving meanwhile
- **WHEN** alice rewinds while the consumer is indexing events
- **THEN** the stored cursor afterwards is her chosen time, not a position the consumer reached before stopping

#### Scenario: Jetstream off
- **WHEN** alice rewinds an AppView started without a Jetstream URL
- **THEN** the action is refused and nothing changes

### Requirement: Jetstream rewinds are bounded
A rewind SHALL be refused, changing nothing, when its time is further back than the configured maximum (24 hours unless configured), later than the stored cursor, in the future, or not a valid time. The page SHALL show the earliest time a rewind may reach.

#### Scenario: Too far back
- **WHEN** alice asks to rewind to three days ago with the default maximum
- **THEN** the action is refused, saying the earliest allowed time, and the cursor is unchanged

#### Scenario: Forward of the cursor
- **WHEN** alice asks to rewind to a time after the stored cursor
- **THEN** the action is refused, since it would skip events, and the cursor is unchanged

### Requirement: Operators restart a worker
An operator SHALL be able to restart a running or backing-off loop or stream worker by name: its current run ends and a new one starts at once. The action SHALL be refused for an unknown, disabled, stopped or one-off worker, and for one already restarting.

#### Scenario: Wedged syncer
- **WHEN** alice restarts the space syncer
- **THEN** its run ends, a new run starts without a pause, and its operator restart count goes up by one

#### Scenario: Disabled worker
- **WHEN** alice tries to restart the segment matcher on an AppView without its signing key
- **THEN** the action is refused and nothing changes

### Requirement: Health actions are operator-only and audited
The rewind and restart actions SHALL be state-changing POSTs that only operators may run with the session's forgery token, as every admin action. Each attempt SHALL be recorded in the audit log with the admin, the action, the worker or Jetstream as its target, the requested time for a rewind together with the cursor it replaced, and its outcome, refusals included.

#### Scenario: Viewer tries a rewind
- **WHEN** bob, a `viewer`, posts a rewind
- **THEN** the AppView answers `403`, the cursor is unchanged and the refusal is in the audit log

#### Scenario: Rewind audited
- **WHEN** alice rewinds Jetstream to 10:00 UTC
- **THEN** the audit log holds her DID, the rewind, the requested time, the cursor it replaced and the outcome

#### Scenario: Missing token
- **WHEN** a post to restart a worker carries a valid session but no forgery token
- **THEN** the AppView refuses it and no worker restarts
