# Spec Delta

## Purpose

Defines how the AppView's background work is supervised and observed by its operators: the status every background worker reports, restarts of crashed workers, the record of segment jobs that gave up, the bounds on recorded error text, the readiness check and the internal metrics endpoint.

## ADDED Requirements

### Requirement: Every background worker reports its status
Every background worker the AppView starts SHALL report, for as long as the process runs, its name, whether it is running, backing off after a failed round, restarting, finished, stopped or disabled, when its current run started, when it last succeeded, its last error and when that happened, how many rounds or events it has completed and how many times it was restarted. The status SHALL be readable in-process without waiting on any worker.

#### Scenario: Healthy loop
- **WHEN** the segment matcher completes a round
- **THEN** its status is running, its last success is that round's time and its round count goes up by one

#### Scenario: Failed round
- **WHEN** a block backfill round fails because the database is briefly unreachable, and the next round succeeds
- **THEN** its status shows the error and its time after the first round, and running with a newer last success after the second, keeping the error

#### Scenario: Stream worker
- **WHEN** the Jetstream consumer's connection drops and it reconnects
- **THEN** its status shows the dropped connection as its last error, and its last success advances with each event after reconnecting

### Requirement: Workers switched off by configuration are listed as disabled
A worker that configuration switches off SHALL be listed with the state disabled and the reason, rather than left out.

#### Scenario: No Jetstream configured
- **WHEN** the AppView starts with no Jetstream URL
- **THEN** the Jetstream worker is listed as disabled, with the missing setting as its reason

#### Scenario: No signing key
- **WHEN** the AppView starts without its signing key
- **THEN** the space syncer and segment matcher are listed as disabled

### Requirement: A crashed worker is restarted
A background worker whose run ends with an error, or a long-running worker whose run ends at all, SHALL be restarted after a pause that grows with each consecutive crash, up to five minutes, and resets once a run has lasted ten minutes. Each restart SHALL be counted in its status and logged as an error. A crash of one worker SHALL NOT stop any other worker or the serving of requests. Shutdown SHALL stop workers without restarting them.

#### Scenario: Unexpected error escapes a loop
- **WHEN** an error escapes the handle sweep's loop
- **THEN** the handle sweep runs again after a pause, its restart count is one, and every other worker kept running meanwhile

#### Scenario: Repeated crashes
- **WHEN** a worker crashes immediately on each of several restarts
- **THEN** the pause before each restart is longer than the one before, never more than five minutes

#### Scenario: Shutdown
- **WHEN** the AppView shuts down
- **THEN** each worker's status becomes stopped and none is restarted

#### Scenario: One-off worker
- **WHEN** a one-off backfill that runs at start completes
- **THEN** its status is finished and it is not run again

### Requirement: Segment jobs that give up are kept
A segment job of any kind that exhausts its retries SHALL be removed from the queue and kept as a failed job recording its kind, its subject, how many attempts it made, its last error, when it first failed and when it gave up. A job enqueued again while its last attempt ran SHALL stay queued instead. A job still retrying SHALL record its last error.

#### Scenario: Track job gives up
- **WHEN** a `track` job fails on every attempt until its retries run out
- **THEN** it is no longer queued, and a failed job lists its kind, subject URI, attempts, last error and when it gave up

#### Scenario: Retrying job
- **WHEN** an `elevation` job fails once and is due again later
- **THEN** the queued job shows that failure's error

### Requirement: Failed segment jobs are cleared
A failed segment job SHALL be removed when a job of the same kind and subject later succeeds, when the account whose record is its subject is deleted, and thirty days after it gave up.

#### Scenario: Later success
- **WHEN** a failed `segment` job's subject is enqueued again and that job succeeds
- **THEN** the failed job is gone

#### Scenario: Account deleted
- **WHEN** alice's account is deleted while a failed `track` job names her segment track
- **THEN** that failed job is gone

#### Scenario: Old failure
- **WHEN** a failed job gave up more than thirty days ago
- **THEN** it is removed

### Requirement: Recorded errors never carry private content
Error text the AppView records for operators, in worker status and in any stored failure, SHALL be the error's type and the first line of its message, at most 500 characters. Errors from decoding a record or blob SHALL be recorded by type alone. Recorded error text SHALL NOT contain the content of a record, a blob or a stream.

#### Scenario: Undecodable record
- **WHEN** a segment job fails decoding a segment track whose data contains a distinctive string
- **THEN** the recorded error names the decoding error and does not contain that string

#### Scenario: Long remote message
- **WHEN** a PDS answers a sync with a 10,000-character multi-line error message
- **THEN** the recorded error is at most 500 characters and holds only the message's first line

### Requirement: Readiness check
The AppView SHALL answer a readiness check, `GET /xrpc/_ready`, with success only while its database answers a trivial query within two seconds, and with 503 otherwise. Background workers' states SHALL NOT affect readiness. The liveness check `GET /xrpc/_health` SHALL keep answering ok without touching the database. Repeated readiness checks within five seconds SHALL NOT each query the database.

#### Scenario: Database down
- **WHEN** the database can't be reached
- **THEN** `_ready` answers 503 with the database reported down, and `_health` still answers ok

#### Scenario: Database up, worker crashed
- **WHEN** the database answers and the Jetstream worker is restarting
- **THEN** `_ready` answers success

#### Scenario: Probe flood
- **WHEN** `_ready` is called a hundred times in one second
- **THEN** the database is queried at most once for them

### Requirement: Metrics on an internal port
When a metrics port is configured, the AppView SHALL serve Prometheus metrics at `/metrics` on that port only, bound to loopback unless another address is configured. The public port SHALL NOT serve metrics. With no metrics port configured, no metrics SHALL be served. The metrics port SHALL NOT be the public port.

#### Scenario: Metrics configured
- **WHEN** the AppView runs with a metrics port and Prometheus scrapes `/metrics` on it
- **THEN** it receives the AppView's metrics in Prometheus text format

#### Scenario: Public port
- **WHEN** anyone requests `/metrics` on the public port
- **THEN** no metrics are served

#### Scenario: Same port
- **WHEN** the metrics port is configured equal to the public port
- **THEN** the AppView refuses to start, saying why

### Requirement: Metrics cover the AppView's background work
The metrics SHALL include Jetstream lag, queued, due and failed segment jobs and completed jobs by outcome per job kind, synced and failing spaces and syncs by outcome, remembered streams fetch failures by kind, pending and backing-off block backfills, each labeler's cursor age and connection, each worker's up state, restarts, rounds and last success, request timings by method, route and status, and JVM and connection-pool metrics.

#### Scenario: Queue depth by kind
- **WHEN** three `track` jobs and one `elevation` job are queued and one `group` job has given up
- **THEN** the metrics show three queued `track` jobs, one queued `elevation` job and one failed `group` job

#### Scenario: Jetstream falling behind
- **WHEN** the last Jetstream event the AppView processed was stamped two minutes ago
- **THEN** the Jetstream lag metric reads about 120 seconds

#### Scenario: Worker restarted
- **WHEN** the segment matcher crashed and was restarted once
- **THEN** its restart count metric reads one

### Requirement: Metrics never identify people or records
No metric's name or label SHALL contain a DID other than a configured service's, a handle, an AT-URI, a CID or a raw request path; requests SHALL be labelled by route template, and requests matching no route by one shared label. A scrape SHALL NOT cost more than a few small database queries, however often it runs.

#### Scenario: Photo request
- **WHEN** a viewer loads `/img/media/did:plc:alice/bafy…`
- **THEN** its timing is recorded under the route template, and neither alice's DID nor the CID appears in the metrics

#### Scenario: Unknown paths
- **WHEN** a scanner requests a thousand distinct nonexistent paths
- **THEN** they are all counted under the one unmatched-route label

#### Scenario: Frequent scrapes
- **WHEN** `/metrics` is scraped every second
- **THEN** the database-backed counts are queried at most once every fifteen seconds
