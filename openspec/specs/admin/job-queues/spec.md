# job-queues Specification

## Purpose
Lets the AppView's operators see the segment job queue and the jobs that gave up, and retry, requeue, drop or bulk requeue segment work from the admin site instead of shipping a migration.

## Requirements

### Requirement: Queue summary per job kind
The admin site SHALL have a queues page that shows, for every segment job kind, how many jobs are queued, how many are due now, how long the oldest due job has been due, how many are retrying after a failed attempt, and how many gave up. A kind with no jobs SHALL be listed with zeros.

#### Scenario: Jobs of several kinds
- **WHEN** three `track` jobs are queued, two of them due since ten minutes ago, and one `group` job gave up
- **THEN** the page shows `track` with three queued, two due and an oldest due age of about ten minutes, and `group` with one gave up

#### Scenario: Empty queue
- **WHEN** no segment job is queued and none gave up
- **THEN** the page lists every kind with zero queued, zero due, zero retrying and zero gave up

### Requirement: Retrying jobs are listed
The queues page SHALL list the queued jobs that have failed at least once, oldest failure first and paged, each with its kind, its subject, its attempts so far, its last error, when its failures began and when it is next due. The list SHALL be filterable by kind.

#### Scenario: Job backing off
- **WHEN** an `elevation` job failed twice and is due again in four minutes
- **THEN** it is listed with two attempts, its last error, when its first failure was and its next due time

#### Scenario: Filter by kind
- **WHEN** an admin filters the retrying jobs to `track`
- **THEN** only retrying `track` jobs are listed

### Requirement: Dead letters are listed
The queues page SHALL list the segment jobs that gave up, most recent first and paged, each with its kind, its subject, its attempts, its last error, when it first failed and when it gave up. The list SHALL be filterable by kind.

#### Scenario: Job gave up
- **WHEN** a `track` job gave up after nine attempts
- **THEN** it is listed with its subject, nine attempts, its last error, its first failure and when it gave up

### Requirement: Queue pages show identifiers, counts, times and recorded errors only
The queues page and its fragments SHALL show a job only by its kind, its subject's identifier, counts, times and its recorded error text. They SHALL NOT show a track, an effort, a time from a track or an effort, or any record's content, including for jobs whose subject is a non-public record.

#### Scenario: Track job of an Only me activity
- **WHEN** a `track` job for alice's Only me activity's segment track is retrying
- **THEN** the page shows its kind, its track's URI, its attempts, its error and its times, and nothing from the track, the activity or its efforts

#### Scenario: Error from a decoding failure
- **WHEN** a dead letter's job failed decoding a track whose data holds a distinctive string
- **THEN** the page shows the error's type and not that string

### Requirement: Retry a job now
An operator SHALL be able to make a retrying job due now. Its attempts so far and its last error SHALL be kept, so a further failure continues its backoff. Retrying a job that is no longer queued SHALL change nothing and say so.

#### Scenario: Retry a backing-off job
- **WHEN** alice, an operator, retries an `elevation` job due in an hour after three attempts
- **THEN** it is due now with three attempts, and the matcher is woken to run it

#### Scenario: Job finished meanwhile
- **WHEN** alice retries a job that succeeded since the page loaded
- **THEN** nothing is queued, and the outcome says the job is no longer queued

### Requirement: Requeue a dead letter
An operator SHALL be able to requeue a dead letter: its job SHALL be queued afresh, due now with no attempts, and the dead letter removed, together. Requeueing a dead letter that no longer exists SHALL change nothing and say so. A dead letter of a kind the AppView can't run now SHALL NOT be requeued.

#### Scenario: Requeue a track job
- **WHEN** alice requeues a dead `track` job
- **THEN** a `track` job for its subject is due now with no attempts, and the dead letter is gone

#### Scenario: Requeue fails again
- **WHEN** a requeued job fails on every attempt until its retries run out
- **THEN** it becomes a dead letter again

#### Scenario: Elevation without terrain tiles
- **WHEN** alice requeues a dead `elevation` job while no terrain tiles are configured
- **THEN** nothing is queued, the dead letter stays, and the outcome says terrain tiles aren't configured

### Requirement: Requeue a kind's dead letters
An operator SHALL be able to requeue the dead letters of one kind, oldest first, at most 500 per action, each as a single requeue would. The outcome SHALL say how many were requeued and how many of that kind remain.

#### Scenario: More than the limit
- **WHEN** 700 `track` jobs gave up and alice requeues the `track` dead letters
- **THEN** the 500 oldest are queued afresh and removed as dead letters, and the outcome says 500 requeued and 200 remain

### Requirement: Drop a job
An operator SHALL be able to drop a retrying job from the queue or remove a dead letter, without running it. Dropping one that no longer exists SHALL change nothing and say so.

#### Scenario: Drop a dead letter
- **WHEN** alice drops a dead `candidate` job
- **THEN** the dead letter is gone and no `candidate` job is queued for its subject

#### Scenario: Drop a retrying job
- **WHEN** alice drops a retrying `site` job
- **THEN** it is no longer queued and is not run

### Requirement: Rematch segment tracks
An operator SHALL be able to queue a `track` job for every segment track the AppView may read for matching or grouping now, or only those whose activity is of one given sport. A track whose `track` job is already queued SHALL keep its place. The outcome SHALL say how many jobs were queued.

#### Scenario: Rematch all
- **WHEN** 1,000 segment tracks are held, 40 of them already queued, and alice rematches all tracks
- **THEN** 960 `track` jobs are queued and the 40 keep their due times

#### Scenario: Rematch one sport
- **WHEN** alice rematches only `dive` tracks
- **THEN** only tracks whose activity is a dive are queued

#### Scenario: Tracks not held
- **WHEN** an athlete has turned off both segment matching and grouping
- **THEN** a rematch queues no job for their tracks

### Requirement: Recompute segment elevation
An operator SHALL be able to queue an `elevation` job for every segment, or for those whose bounding box touches a box of west, south, east and north degrees, optionally only those missing terrain elevation or maximum grade. A segment already queued for it SHALL keep its place. The outcome SHALL say how many jobs were queued.

#### Scenario: One region
- **WHEN** alice recomputes elevation for the box around Vermont
- **THEN** an `elevation` job is queued for each segment whose box touches it, and for no other

#### Scenario: Only missing
- **WHEN** alice recomputes elevation for all segments, only those missing it
- **THEN** only segments with no terrain elevation or no maximum grade are queued

#### Scenario: No terrain tiles
- **WHEN** alice recomputes elevation while no terrain tiles are configured
- **THEN** nothing is queued and the outcome says terrain tiles aren't configured

#### Scenario: Invalid box
- **WHEN** alice enters a box whose west is east of its east, or a latitude past 90
- **THEN** nothing is queued and the outcome says the box is invalid

### Requirement: Bulk requeues are confirmed, idempotent and one at a time
A bulk requeue (rematching tracks, recomputing elevation, requeueing a kind's dead letters) SHALL be refused unless the operator confirmed it in the same request. Running the same bulk requeue twice SHALL queue no job twice. While one bulk requeue runs, another SHALL be refused, saying one is running.

#### Scenario: Not confirmed
- **WHEN** alice posts a rematch without confirming it
- **THEN** nothing is queued and the outcome asks her to confirm

#### Scenario: Run twice
- **WHEN** alice rematches all tracks and then rematches all tracks again before the matcher runs any
- **THEN** the second queues no job

#### Scenario: Concurrent bulk requeues
- **WHEN** bob's elevation recompute is running and alice starts a rematch
- **THEN** alice's is refused and changes nothing

### Requirement: Queue actions are operator actions
Every queue action SHALL be a state-changing admin action: an operator-only POST carrying the session's forgery token, recorded in the audit log with its target job or kind, its settings and its outcome, including how many jobs it queued, requeued or dropped. Viewers SHALL see the queues page without its action controls.

#### Scenario: Viewer posts a retry
- **WHEN** bob, a `viewer`, posts a retry
- **THEN** the AppView answers `403`, changes nothing and records the refusal

#### Scenario: Viewer opens the page
- **WHEN** bob opens the queues page
- **THEN** he sees the summary and both lists, with no retry, requeue, drop or bulk controls

#### Scenario: Audited bulk requeue
- **WHEN** alice recomputes elevation for a box and 312 jobs are queued
- **THEN** the audit log records her DID, the action, the box and the only-missing setting, and an outcome naming 312 queued

#### Scenario: Missing forgery token
- **WHEN** a request with alice's session cookie posts a drop without the session's token
- **THEN** the AppView refuses it and drops nothing

### Requirement: Unknown jobs and kinds are refused
A queue action naming a kind that isn't a segment job kind, or a subject longer than a job subject can be, SHALL be refused without touching the queue, and the refusal recorded.

#### Scenario: Made-up kind
- **WHEN** alice posts a requeue for kind `nonsense`
- **THEN** nothing changes and the outcome says the kind is unknown

### Requirement: Queues page summary
The queues page SHALL offer a summary with three tiles over every job kind together. Oldest due job: `ok` when none is due or the oldest has been due under 5 minutes, `warn` up to 1 hour, `bad` beyond. Retrying jobs: `warn` when any, else `ok`. Jobs that gave up: `bad` when any, else `ok`. Each tile SHALL link to the part of the page that lists them.

#### Scenario: Backlog building
- **WHEN** the oldest due `track` job has been due for twenty minutes and no job is retrying or gave up
- **THEN** the oldest due tile shows about twenty minutes and is `warn`, and the other two are `ok`

#### Scenario: Long backlog
- **WHEN** the oldest due job has been due for two hours
- **THEN** the oldest due tile is `bad`

#### Scenario: Jobs gave up
- **WHEN** one `group` job gave up
- **THEN** the gave-up tile shows 1, is `bad` and links to the dead letters list

#### Scenario: Idle queue
- **WHEN** no job is queued and none gave up
- **THEN** all three tiles are `ok`
