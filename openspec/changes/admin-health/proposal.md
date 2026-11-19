# Proposal

## Why

The AppView now records every worker's status (`WorkerRegistry`, dev/worker-telemetry), keeps the Jetstream cursor (`jetstream_cursor`), each labeler's cursor (`label_cursors`) and each synced space's failures (`space_sync.failures`, `last_error`, `next_attempt_at`), but an operator can only see them through Prometheus on the internal metrics port, if it's configured, or in the database. The admin site (admin/admin-site) is there to put them in front of operators. Two fixes also still need a shell: replaying Jetstream after an indexing bug means stopping the AppView and editing `jetstream_cursor` by hand, and a wedged worker can only be cleared by restarting the whole process.

## What Changes

- A **Health** page on the admin site (an `AdminPage` at `/admin/health`) that any admin can open, showing:
  - every worker from `WorkerRegistry.snapshot()`: name, kind, state, current run's start, last success, last error text and time, rounds or events, crash restarts, operator restarts, and the reason a disabled worker is off;
  - Jetstream: the stored cursor as a time, the last event indexed in this process (`JetstreamConsumer.lastEventUs`) and the lag behind now;
  - each configured labeler: its DID, stored cursor `seq`, whether its stream is open and when its cursor last advanced;
  - the space syncer: counts of synced, failing, not-found and due spaces, and the most-failing spaces with their consecutive failures, last error text and time, next attempt and last success;
  - the database's size and each table's size and estimated row count, on Postgres; on any other database the section says sizes aren't available.
- Two operator actions, each an `AdminRoutes.action` (POST, operators only, CSRF, audited whatever the outcome):
  - **Rewind Jetstream** to a given time no further back than a configured bound (`JETSTREAM_REWIND_MAX`, 24 hours by default) and never forward of the current cursor; the consumer reconnects from that time and replays, which indexing's idempotence makes safe.
  - **Restart a worker**: end a running loop or stream's current run and start it again at once, without counting it as a crash.
- `WorkerSupervisor` learns to restart a named worker on request, optionally running a step while the worker is stopped (how the rewind writes the cursor without racing the consumer's own saves).
- `main` passes the health page in `appView(adminPages = …)`, with the started workers it reads.

## Capabilities

### New Capabilities
- `admin/ingest-health`: what the admin site shows operators about ingest and background work (workers, Jetstream, labelers, space syncing, database size), and the operator actions that steer ingest (rewinding Jetstream, restarting a worker).

### Modified Capabilities
- `dev/worker-telemetry`: adds an operator-requested restart of a worker, counted apart from crash restarts, beside the existing crash restart rule.

## Impact

- New: `server/appview/.../admin/HealthPage.kt`, `admin/DatabaseSizes.kt`, `ops/` restart support in `WorkerSupervisor.kt` and `WorkerRegistry.kt` (a `manualRestarts` count on `WorkerStatus`), `JetstreamConsumer.rewindTo`, a stored-cursor read on `JetstreamConsumer`, `AppViewConfig.jetstreamRewindMax`.
- `Application.kt`: workers are started before `appView(…)` so the page can be given the supervisor, the Jetstream consumer and the label subscribers.
- Tests: Ktor test-host tests in `server/appview/src/test/.../HealthPageTest.kt`, supervisor restart tests, and the health page added to `AdminPrivacyTest.pagesUnderTest`.
- No migration, no lexicon, no XRPC method, no client change. `docker/.env.example` documents `JETSTREAM_REWIND_MAX`.
