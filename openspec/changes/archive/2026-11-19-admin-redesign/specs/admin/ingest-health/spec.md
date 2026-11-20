# Spec Delta

## MODIFIED Requirements

### Requirement: Health page shows Jetstream's position
The health page SHALL show the stored Jetstream cursor as a time, the time of the last event this process indexed, when that event was received, and the catch-up lag: how far the event's own time was behind the moment it was received. A quiet stream SHALL NOT grow the catch-up lag. With Jetstream switched off it SHALL say so instead.

#### Scenario: Falling behind
- **WHEN** the last event the AppView indexed was stamped two minutes before it was received
- **THEN** the page shows that event's time, when it was received and a catch-up lag of about two minutes

#### Scenario: Quiet network
- **WHEN** the AppView received its last event three hours ago, one second after it was stamped, and nothing has arrived since
- **THEN** the page shows a catch-up lag of about one second and that the last event was received three hours ago

#### Scenario: Before the first event
- **WHEN** the AppView has indexed no event since it started
- **THEN** the page shows the stored cursor and says no event was indexed yet, rather than a lag

## ADDED Requirements

### Requirement: Health page summary
The health page SHALL offer a summary with tiles for workers, Jetstream, labelers, failing spaces and database size. Each tile's status SHALL follow the fixed rules in this capability. The database size tile SHALL have no status. A tile whose source is switched off or not configured SHALL say so with no status, or be left out when there is nothing to show.

#### Scenario: Healthy AppView
- **WHEN** every worker is running, Jetstream is keeping up, every labeler's stream is open and no space is failing
- **THEN** every Health tile is `ok` except the database size, which has no status

#### Scenario: No labeler configured
- **WHEN** the AppView runs with no labeler configured
- **THEN** Health's summary has no labelers tile

### Requirement: Workers tile thresholds
The workers tile SHALL count the workers that are neither running, finished, stopped nor disabled. It SHALL be `bad` while any worker is restarting after a crash. Otherwise it SHALL be `warn` while any worker is backing off, or has been starting for more than one minute. Otherwise it SHALL be `ok`. A disabled worker SHALL NOT change its status.

#### Scenario: Worker restarting
- **WHEN** the segment matcher's run ended with an error and it's waiting to run again
- **THEN** the workers tile is `bad`

#### Scenario: Worker backing off
- **WHEN** the Jetstream worker's last connection failed and it is backing off
- **THEN** the workers tile is `warn`

#### Scenario: Disabled worker
- **WHEN** the space syncer is disabled for want of its signing key and every other worker runs
- **THEN** the workers tile is `ok`

### Requirement: Jetstream tile thresholds
The Jetstream tile SHALL show the catch-up lag. It SHALL be `bad` when the lag is over 15 minutes or the Jetstream worker is restarting. It SHALL be `warn` when the lag is over 1 minute or the worker is backing off. Otherwise it SHALL be `ok`. Before the first event it SHALL go by the worker's state alone. How long ago the last event was received SHALL NOT change its status.

#### Scenario: Catching up
- **WHEN** the last event was received five minutes after it was stamped
- **THEN** the Jetstream tile is `warn`

#### Scenario: Far behind
- **WHEN** the last event was received twenty minutes after it was stamped
- **THEN** the Jetstream tile is `bad`

#### Scenario: Quiet network is not a problem
- **WHEN** no event has arrived for three hours and the last arrived a second after it was stamped
- **THEN** the Jetstream tile is `ok`

#### Scenario: Jetstream off
- **WHEN** Jetstream is switched off
- **THEN** the Jetstream tile says it is off and has no status

### Requirement: Labelers tile thresholds
The labelers tile SHALL count the configured labelers whose stream is open. It SHALL be `bad` while any configured labeler's stream is closed, and `ok` otherwise.

#### Scenario: Labeler disconnected
- **WHEN** one of two configured labelers' streams has dropped
- **THEN** the labelers tile shows 1 of 2 open and is `bad`

### Requirement: Failing spaces tile thresholds
The failing spaces tile SHALL count the spaces whose sync is failing. It SHALL be `bad` when any is failing for a reason other than its host reporting it missing. It SHALL be `warn` when every failing space is one its host reports missing. It SHALL be `ok` when none is failing.

#### Scenario: Spaces reported missing
- **WHEN** three spaces are failing and their host reports each of them missing
- **THEN** the tile shows 3 and is `warn`

#### Scenario: Space failing otherwise
- **WHEN** one space is failing because its host can't be reached
- **THEN** the tile is `bad`
