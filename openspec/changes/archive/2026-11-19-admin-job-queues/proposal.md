# Proposal

## Why

The segment matcher's queue (`segment_match_jobs`, seven kinds in `SegmentJobs.KINDS`) is invisible to operators except as Prometheus gauges, and the only way to replay work is a Flyway migration: V43, V50, V51 and V53 each `INSERT … SELECT` a `track` job for every segment track, V54 an `elevation` job for every segment. Since dev/worker-telemetry, jobs that give up are kept in `segment_job_failures` with their error, but nobody can see or replay them. The admin site (admin/admin-site) now gives a place to watch and steer this, so requeueing can stop needing a release.

## What Changes

- A **Queues** page on the admin site (`/admin/queues`, an `AdminPage`): per job kind, how many are queued, how many are due now, the age of the oldest due job, how many are retrying and how many gave up; a paged list of retrying jobs (kind, subject, attempts, last error, when its failures began, when it is next due); a paged list of dead letters (kind, subject, attempts, last error, first failure, when it gave up). Both lists filter by kind.
- Operator actions, every one a CSRF-checked POST through `AdminRoutes.action`, so audited and refused to viewers:
  - **Retry now**: make a retrying job due now, keeping its attempt count.
  - **Requeue** a dead letter: enqueue it afresh and remove the dead letter; **requeue a kind's dead letters**, at most 500 per action.
  - **Drop** a queued job or a dead letter.
  - **Rematch tracks**: queue a `track` job for every held segment track, optionally only those of one sport's activities.
  - **Recompute elevation**: queue an `elevation` job for every segment, or those whose box touches a given west,south,east,north box, optionally only those missing terrain elevation; refused when no terrain tiles are configured.
- Bulk requeues are idempotent (a job already queued keeps its place, as the past migrations and `SegmentJobs.enqueueElevations` do), run as one statement, one at a time, need an explicit confirmation, and report how many jobs they queued.
- Requeueing or dropping a dead letter removes it, adding to the ways dev/worker-telemetry clears one.
- Bulk replays of segment work are done from this page from now on, not by new requeue-only migrations (recorded in `.agents/rules/appview.md`).

## Capabilities

### New Capabilities
- `admin/job-queues`: the admin site's view of the segment job queue and dead letters, and the operator actions that retry, requeue, drop and bulk requeue segment jobs.

### Modified Capabilities
- `dev/worker-telemetry`: "Failed segment jobs are cleared" gains two removals: an operator requeueing or dropping the dead letter.

## Impact

- `server/appview` only: a new `admin/JobQueuesPage.kt` (page and actions) and `segments/SegmentQueue.kt` (reads and operator operations on `segment_match_jobs` and `segment_job_failures`, sharing `SegmentJobs`' wakeups), new `SegmentJobs` methods (requeue by kind, retry now, bulk track enqueue), the page listed in `Application.kt`'s `adminPages`, one Flyway migration (V71, an index for the retrying list; renumbered at merge).
- No lexicon, XRPC, client or Docker change. No new dependency.
- Depends on admin-site (shell, roles, audit) and worker-telemetry (dead letters, `last_error`), both on main.
