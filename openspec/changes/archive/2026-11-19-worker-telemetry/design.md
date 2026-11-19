# Design

## Context

See proposal.md for why. What the code does today:

- `Application.kt` (`embeddedServer { … }`, ~lines 139-149) starts the loops on the application scope: `JetstreamConsumer.run()` (reconnects with its own backoff), `BskyBlockBackfill.start`, `HandleSweep.start`, the one-off `assignMissingGroups`, `SpaceSyncer.start` (which launches one child coroutine per due space on the same scope), `SegmentMatcher.start` (one-off backfills, then jobs, then an hourly status and naming pass, each step in `guarded`), `startLabelSubscribers` (one `LabelSubscriber.run()` per labeler plus an hourly expiry sweep) and `ModerationAccessLog.start`. Most catch per-round exceptions and log them; none stores status, and an exception that escapes (an `Error`, a bug outside the try) ends the loop for good.
- `SegmentMatcher.failed` doubles a job's delay from 30 s; once it would pass one hour (8 attempts) it logs "giving up" and deletes the job (`done`). The `SegmentMatchJobs` KDoc in `db/Tables.kt` names four kinds; `SegmentJobs` has seven (`track`, `segment`, `candidate`, `tiers`, `group`, `site`, `elevation`).
- `space_sync` keeps `failures` and `next_attempt_at`; `activity_stream_fetch_failures` keeps `kind`, `failures`, `retry_at`. Neither keeps why.
- `BskyBlockBackfill` keeps failing DIDs in a bounded Caffeine cache for a flat 10 minutes; a restart forgets it.
- `/xrpc/_health` (`api/Routes.kt`) returns `{"status":{"ok":true}}` unconditionally. Nothing exports metrics. HikariCP is the pool.
- The AppView is a single process (the indexer's per-URI lock assumes it), so in-memory status is the whole truth for that process.
- Migrations: V68 (admin-site took V67).

## Goals / Non-Goals

**Goals:**
- One place that knows every background worker's state, cheap to snapshot, that `admin-health` can render as a table without further plumbing.
- Dead-lettered segment jobs in a flat table `admin-job-queues` can list, replay (re-insert into `segment_match_jobs`) and discard with plain SQL.
- Failure reasons operators can read, with no private content in them.
- Standard Prometheus scraping without exposing anything on the public port.

**Non-Goals:**
- No admin UI, no replay or discard actions (those are `admin-job-queues`).
- No persisted worker history: the registry is per process and resets on restart; Prometheus holds history.
- No change to retry policies other than the block backfill's (segment job and space sync backoff stay as they are).
- No tracing, no log shipping, no alerting rules.
- No dead letter for space syncs or stream fetches: those never give up, they back off, so their rows already are the queue.

## Decisions

### 1. `WorkerRegistry` + `WorkerSupervisor` in a new `ops` package

```kotlin
enum class WorkerKind { LOOP, STREAM, ONE_OFF }
enum class WorkerState { STARTING, RUNNING, BACKING_OFF, RESTARTING, FINISHED, STOPPED, DISABLED }

data class WorkerStatus(
    val name: String,            // stable id, e.g. "jetstream", "label-subscriber:did:plc:…"
    val kind: WorkerKind,
    val state: WorkerState,
    val startedAt: Instant?,     // of the current run
    val lastSuccessAt: Instant?,
    val lastErrorAt: Instant?,
    val lastError: String?,      // ErrorSummary, ≤ 500 chars
    val iterations: Long,        // successful rounds (LOOP) or events (STREAM)
    val restarts: Int,
    val disabledReason: String?, // e.g. "JETSTREAM_URL is not set"
)

class WorkerRegistry(clock: Clock) {
    fun snapshot(): List<WorkerStatus>          // sorted by name; what admin-health reads
    fun handle(name: String, kind: WorkerKind): WorkerHandle
    fun disabled(name: String, kind: WorkerKind, reason: String)
}

interface WorkerHandle {
    fun success()                 // iterations++, lastSuccessAt = now, state = RUNNING
    fun failure(e: Throwable)     // lastError/At, state = BACKING_OFF (the loop keeps going)
}
```

Each worker's status is an immutable `WorkerStatus` in a `ConcurrentHashMap<String, AtomicReference<WorkerStatus>>`, so a snapshot never blocks a worker and a worker never blocks on a reader. The registry is an `AppScope`-style singleton created in `main`, passed to `appView(...)` (for `_ready` nothing; for later admin routes) and to the metrics binder.

`WorkerSupervisor(scope, registry)` owns a child `SupervisorJob` of the application scope and launches workers:

```kotlin
fun loop(name: String, body: suspend (WorkerHandle) -> Unit)    // must never return; returning counts as a crash
fun stream(name: String, body: suspend (WorkerHandle) -> Unit)
fun oneOff(name: String, body: suspend (WorkerHandle) -> Unit)  // FINISHED on return
```

If `body` throws anything but `CancellationException` (including an `Error` other than `OutOfMemoryError`/`VirtualMachineError`, which are rethrown), the supervisor records the failure, sets `RESTARTING`, waits a backoff (1 s doubling to 5 min, reset once a run has lasted 10 min) and calls `body` again with the same handle, incrementing `restarts`. A one-off that throws is retried the same way until it returns. Cancellation (shutdown) sets `STOPPED`. Alternative considered: wrapping each loop's body in its own try/catch and leaving launch as is — rejected, because the point is a single place that guarantees restart and status for every worker, including ones added later.

Existing loops keep their own per-round try/catch (a failed round must not cost a restart's backoff); inside it they call `handle.failure(e)` and, after a clean round, `handle.success()`. `SegmentMatcher.guarded` reports to the matcher's handle. `SpaceSyncer`'s per-space children are launched on the supervisor's scope; a single space's failure stays in `space_sync` (it's the space's problem, not the worker's), and the worker's success is a completed round of `launchDue`. `JetstreamConsumer` and `LabelSubscriber` call `success()` per event (an atomic replace, cheap at Jetstream rates; `lastSuccessAt` then means "last event") and `failure(e)` per dropped connection.

Worker names (stable; admin-health and metrics key on them): `jetstream`, `space-syncer`, `segment-matcher`, `bsky-block-backfill`, `handle-sweep`, `segment-groups-backfill` (one-off), `label-subscriber:<labeler DID>`, `label-sweep`, `moderation-access-log`. When configuration switches one off (no `JETSTREAM_URL`, no signing key so no syncer or matcher, no labelers) it is registered `DISABLED` with the reason, so the admin page can tell "off" from "missing".

### 2. Error summaries

`ErrorSummary.of(e: Throwable): String` = `"<SimpleClassName>: <first line of message>"`, cut to 500 characters. Exceptions whose message may echo the input they failed on — `SerializationException` and its subclasses (kotlinx.serialization appends "JSON input: …"), and the AppView's own record-decoding failures — are reduced to the class name alone. For an `XrpcException` from a remote host it is the error name and the remote message, cut the same way. The one function feeds the registry, `segment_match_jobs.last_error`, `segment_job_failures.last_error`, `space_sync.last_error`, `activity_stream_fetch_failures.last_error` and `bsky_block_backfill_failures.last_error`. Remote text is untrusted: it's stored as plain text and the admin site must escape it (kotlinx.html does). Alternative: store only the exception class — rejected, "IOException" alone tells an operator nothing.

### 3. Segment job dead letter

New table, flat on purpose so `admin-job-queues` needs no joins to list it:

```sql
CREATE TABLE segment_job_failures (
    kind         VARCHAR(16)  NOT NULL,
    subject_uri  VARCHAR(512) NOT NULL,
    attempts     INTEGER      NOT NULL,
    last_error   VARCHAR(500) NOT NULL,
    first_failed_at BIGINT    NOT NULL,  -- first failed attempt of the run that gave up
    failed_at    BIGINT       NOT NULL,  -- when it gave up
    PRIMARY KEY (kind, subject_uri)
);
CREATE INDEX segment_job_failures_failed_at ON segment_job_failures (failed_at);
ALTER TABLE segment_match_jobs ADD COLUMN last_error VARCHAR(500);
ALTER TABLE segment_match_jobs ADD COLUMN first_failed_at BIGINT;
```

`SegmentMatcher.failed` writes `last_error` (and `first_failed_at` on the first failure) on every retry; on giving up it upserts the dead letter and deletes the job in one transaction, still guarded by `enqueued_at` so a job re-enqueued while it ran is kept rather than dead-lettered. `done` deletes any dead letter for the same `(kind, subject_uri)`, so a later successful run (a new enqueue, a migration's rematch, or a replay) clears it. The hourly status pass prunes dead letters older than 30 days. `Indexer.deleteActor` deletes dead letters whose `subject_uri` starts with `at://<did>/`. Replay for the later admin change is `INSERT INTO segment_match_jobs (kind, subject_uri, enqueued_at, attempts) VALUES (…, now, 0)` plus a delete of the row; nothing here needs to change for it. Alternative: a `dead` flag on `segment_match_jobs` — rejected, it would make every due-job query filter it and the primary key would block a fresh enqueue of the same subject.

Fix the `SegmentMatchJobs` KDoc to name all seven kinds while there.

### 4. Last error on existing failure rows

- `space_sync`: add `last_error VARCHAR(500)` and `last_error_at BIGINT`, set in `SpaceSyncer.failed`, cleared with `failures = 0` on success. A drop (`SpaceDeleted`, a day of `SpaceNotFound`) deletes the row as today.
- `activity_stream_fetch_failures`: add `last_error VARCHAR(500)` and `last_failed_at BIGINT`. `fetch` passes a reason: the fetch exception's summary, "CID mismatch", "too large", or "not stream data" (never the bytes or a decode message, which could quote them).

### 5. Persistent block backfill backoff

New table `bsky_block_backfill_failures (did VARCHAR(255) PRIMARY KEY, failures INTEGER, last_error VARCHAR(500), next_attempt_at BIGINT)` replaces the Caffeine cache. `runDue` selects unsynced actors with no failure row or a due one (`LEFT JOIN … WHERE next_attempt_at IS NULL OR next_attempt_at <= now`), so backing-off actors still never crowd out a batch. A failure upserts with the pause 10 min × 2^(failures−1), capped at 24 h; success deletes the row with the stamp; `deleteActor` deletes it. A separate table rather than columns on `actors`, which is hot and wide and almost never failing. This removes `MAX_FAILING` and the cache's eviction caveat.

### 6. Readiness

`GET /xrpc/_ready` on the public port: runs `SELECT 1` through the pool with a 2 s timeout and answers `200 {"status":{"ok":true,"database":true}}` or `503 {"status":{"ok":false,"database":false}}`. The result is cached for 5 s so a flood of probes can't become a flood of queries. It does not look at workers: a stuck labeler shouldn't pull the instance out of a load balancer; workers are for alerts and the admin page. `_health` is unchanged (liveness: the process answers). The server only starts after Flyway, so "ready" also implies migrated.

### 7. Metrics

Dependencies: `io.micrometer:micrometer-registry-prometheus` and `io.ktor:ktor-server-metrics-micrometer` (same `ktor` version ref), added to `gradle/libs.versions.toml` and `server/appview/build.gradle.kts`. Both are maintained, Apache-2.0 and the standard choice; nothing hand-rolled.

`METRICS_PORT` (unset = no metrics server and no `PrometheusMeterRegistry`; the Ktor plugin isn't installed) and `METRICS_HOST` (default `127.0.0.1`; set `0.0.0.0` in a container whose network keeps the port private). A second `embeddedServer` serves only `GET /metrics`, so nothing reaches it through the public port and the public port never serves `/metrics`. Startup fails if `METRICS_PORT` equals `APPVIEW_PORT`.

Meters (prefix `appview_`):

| Meter | Type | Source |
|---|---|---|
| `jetstream_lag_seconds` | gauge | now − last processed event's `time_us` (in memory, from the consumer) |
| `segment_jobs_queued{kind}` | gauge | `COUNT(*) … GROUP BY kind` on `segment_match_jobs` |
| `segment_jobs_due{kind}` | gauge | same, `enqueued_at <= now` |
| `segment_job_failures{kind}` | gauge | `COUNT(*)` on `segment_job_failures` by kind |
| `segment_jobs_total{kind,outcome}` | counter | `done`, `retry`, `dead` from the matcher |
| `space_sync_spaces`, `space_sync_failing` | gauge | `space_sync` rows, rows with `failures > 0` |
| `space_syncs_total{outcome}` | counter | `ok`, `failed`, `dropped` |
| `stream_fetch_failures{kind}` | gauge | `activity_stream_fetch_failures` by kind |
| `bsky_block_backfill_pending`, `…_backing_off` | gauge | unsynced actors; failure rows |
| `label_cursor_age_seconds{labeler}` | gauge | seconds since that subscriber last advanced its stored cursor |
| `labeler_connected{labeler}` | gauge | 1 while its stream is open |
| `worker_up{worker}` | gauge | 1 when `RUNNING`/`BACKING_OFF`/`FINISHED`, 0 otherwise; disabled workers omitted |
| `worker_restarts_total{worker}`, `worker_iterations_total{worker}`, `worker_last_success_timestamp_seconds{worker}` | from the registry | |
| `http_server_requests_seconds{method,route,status}` | timer | Ktor plugin, `route` = the route template |
| JVM, process and HikariCP pool | binders | Micrometer's JVM binders; Hikari `setMetricRegistry` |

Database-backed gauges are read by one collector that runs the counts at most every 15 s and caches them, so a scrape never runs more than a handful of small `GROUP BY`s. Label cardinality: `kind`, `outcome`, `worker`, `labeler` (configured service DIDs, a fixed handful) and the route template are bounded; no meter is labelled with a viewer, author, AT-URI, CID or raw path. The Ktor plugin is configured to tag by route template (`/img/media/{did}/{cid}`, not the path) and to fold unmatched paths into one `route="unmatched"`.

A labeler that publishes nothing for hours ages its cursor while healthy; that's why `labeler_connected` sits beside it, and alert rules (out of scope) combine them.

## Risks / Trade-offs

- [Per-event `success()` on Jetstream adds an allocation per event] → one small immutable object per event is noise next to JSON decoding and a DB transaction; if profiling disagrees, throttle `lastSuccessAt` to once a second.
- [Error text from remote hosts is attacker-chosen] → bounded, first line only, stored and served as plain text; the admin site escapes it; no metric carries it.
- [Some exception message we didn't anticipate quotes record content] → decode and serialization failures are reduced to their class; the 500-char cap limits the rest; a test asserts a decode failure of a record with a sentinel string never stores the sentinel.
- [Restarting a crashed loop hides a bug] → each restart is counted, logged at `error`, and exported as `worker_restarts_total`, so it is louder than today's silent death, not quieter.
- [Dead letters accumulate if nobody looks] → pruned after 30 days and exported per kind so a rising count alerts.
- [Metrics port exposed by mistake] → loopback by default; it serves counts and route timings only, nothing identifying.
- [Migration number collides with a sibling change] → pick the next free `V<n>` at implementation; `MigrationVersionsTest` catches clashes.

## Migration Plan

One Flyway migration (portable SQL, H2-safe) adds the table, columns and index; existing rows get nulls, meaning "no error recorded". The in-memory block-backfill cache simply goes away: after deploy, actors that were backing off are retried once on the next round and then back off persistently. Metrics are off until `METRICS_PORT` is set. Rollback: redeploy the previous build; the added columns and tables are ignored by it (Flyway doesn't roll back, and the old code never reads them).
