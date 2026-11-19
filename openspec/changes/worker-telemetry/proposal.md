# Proposal

## Why

The AppView runs about ten background loops (Jetstream, the space syncer, the segment matcher, label subscribers, the Bluesky block backfill, sweeps), all started bare in `Application.kt` with no supervisor and no stored status: a loop that crashes stays dead until restart, and every failure lives only in the log. Segment jobs that give up are deleted, sync and fetch failures keep a count but not why, `_health` says ok even with the database down, and nothing exports metrics. The planned `/admin` site (`admin-health`, `admin-job-queues`) needs this state to exist before it can show it, and running the AppView in production needs it regardless.

## What Changes

- Every background loop the AppView starts runs under one supervisor and reports to an in-memory worker registry: whether it is running, when it last succeeded, its last error and when, and how many rounds it has done. A loop that crashes is restarted with backoff instead of staying dead; a loop that is switched off by configuration is listed as disabled.
- A segment job that exhausts its retries is moved to a new `segment_job_failures` table (kind, subject, attempts, last error, failed at) instead of being deleted; a retrying job keeps its last error too. Dead letters are cleared when the same job later succeeds, when their account is deleted, and after 30 days.
- `space_sync` and `activity_stream_fetch_failures` record the last error's text and time beside the counts they already keep.
- The Bluesky block backfill keeps its failing actors and their backoff in the database instead of an in-memory cache, so a restart no longer retries them all at once, and the pause grows with each failure.
- Stored error text is bounded and never carries record or blob content.
- A new readiness check, `/xrpc/_ready`, answers 503 when the database can't be reached; `/xrpc/_health` stays a cheap liveness check.
- Prometheus metrics (Micrometer) on a separate internal port, `METRICS_PORT`, off when unset: Jetstream lag, segment job queue depth and dead letters per kind, space sync failures, labeler cursor age and connection, worker status, HTTP timings by route, and JVM and connection-pool metrics. No metric label carries a DID, handle, AT-URI or CID.
- No UI: the admin pages that read the registry and the dead letters are later changes.

## Capabilities

### New Capabilities

- `dev/worker-telemetry`: how the AppView's background work is supervised and observed — the worker registry, supervised restarts, the segment job dead letter, bounded error text, the readiness check and the Prometheus metrics endpoint.

### Modified Capabilities

- `spaces/appview-syncer`: a failing space's sync state records the last error's text and time, cleared on success.
- `graph/blocks`: a failed Bluesky block backfill is retried after a growing pause that survives a restart.
- `feed/detail-visibility`: a remembered streams-data fetch failure records why it failed.

## Impact

- Code: `server/appview` — `Application.kt` (every loop launched through the supervisor), a new `ops/` package (registry, supervisor, error summaries, metrics), `SegmentMatcher`, `SpaceSyncer`, `ActivityStreamsViews`, `BskyBlockBackfill`, `JetstreamConsumer`, `LabelSubscriber`, `HandleSweep`, `ModerationAccessLog`, `Indexer.deleteActor`, `api/Routes.kt` (`_ready`), `AppViewConfig` (`METRICS_PORT`, `METRICS_HOST`).
- Database: one Flyway migration adding `segment_job_failures`, `segment_match_jobs.last_error`, `space_sync.last_error`/`last_error_at`, `activity_stream_fetch_failures.last_error`/`last_failed_at`, and `bsky_block_backfill_failures`.
- Dependencies (through `gradle/libs.versions.toml`): `io.micrometer:micrometer-registry-prometheus` and `io.ktor:ktor-server-metrics-micrometer`.
- Docs: `README.md`, `docker/.env.example`, `.agents/rules/appview.md` and `AGENTS.md` gain the metrics port, `_ready` and the registry.
- Later changes `admin-health` and `admin-job-queues` read the registry snapshot and `segment_job_failures` as designed here.
