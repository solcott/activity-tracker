# Tasks

## 1. Dependencies and configuration

- [x] 1.1 Add `micrometer-registry-prometheus` (with a `micrometer` version) and `ktor-server-metrics-micrometer` (on the `ktor` ref) to `gradle/libs.versions.toml` and `server/appview/build.gradle.kts`; verify `./gradlew :server-appview:compileKotlin` and the sorted-dependencies check pass
- [x] 1.2 Add `metricsPort: Int?` and `metricsHost` (default `127.0.0.1`) to `AppViewConfig.fromEnv` from `METRICS_PORT`/`METRICS_HOST`, failing when the port equals `APPVIEW_PORT`; cover unset, set and equal-port cases in `AppViewConfigTest`

## 2. Error summaries

- [x] 2.1 Add `ops/ErrorSummary.kt` (`ErrorSummary.of(Throwable)`: type plus first message line, ≤ 500 chars; type only for `SerializationException` and record-decoding failures; `XrpcException` as error name plus message) with unit tests for the long multi-line message, the decode failure carrying a sentinel string, and the XRPC error

## 3. Worker registry and supervisor

- [x] 3.1 Add `ops/WorkerRegistry.kt` (`WorkerKind`, `WorkerState`, immutable `WorkerStatus`, `WorkerHandle.success/failure`, `disabled(...)`, sorted lock-free `snapshot()`) with tests for success, failure-then-success keeping the error, and disabled entries
- [x] 3.2 Add `ops/WorkerSupervisor.kt` (`loop`, `stream`, `oneOff` on a child `SupervisorJob`; restart backoff 1 s doubling to 5 min, reset after a 10-min run; `STOPPED` on cancellation; fatal VM errors rethrown; restarts logged at error) with tests under a virtual-time clock: crash then restart, growing pauses capped at five minutes, a returning loop counted as a crash, a finished one-off, shutdown stopping everything, and one crashing worker leaving another running
- [x] 3.3 Launch every worker in `Application.kt` through the supervisor under the names in design §1 (`jetstream`, `space-syncer`, `segment-matcher`, `bsky-block-backfill`, `handle-sweep`, `segment-groups-backfill`, `label-subscriber:<did>`, `label-sweep`, `moderation-access-log`), registering configuration-disabled ones with their reason; verify by starting the AppView locally (`scripts/run-appview.sh`) and seeing every worker reach running or disabled in a debug log of the snapshot
- [x] 3.4 Report rounds and failures from each loop: `handle.success()`/`failure(e)` in the existing per-round catches of `BskyBlockBackfill`, `HandleSweep`, `ModerationAccessLog`, `SpaceSyncer.start`, the label sweep, and `SegmentMatcher.guarded`; per event and per dropped connection in `JetstreamConsumer` and `LabelSubscriber`; launch `SpaceSyncer`'s per-space children on the supervisor's scope. Extend each class's existing tests to assert the handle saw the success or failure

## 4. Storage changes

- [x] 4.1 Add the next free `V<n>__worker_telemetry.sql` (portable SQL): `segment_job_failures` with its `failed_at` index, `segment_match_jobs.last_error`/`first_failed_at`, `space_sync.last_error`/`last_error_at`, `activity_stream_fetch_failures.last_error`/`last_failed_at`, `bsky_block_backfill_failures`; mirror them in `db/Tables.kt` and fix the `SegmentMatchJobs` KDoc to list all seven kinds; verify `MigrationVersionsTest` and the H2 migration run in `:server-appview:test`

## 5. Segment job dead letter

- [x] 5.1 In `SegmentMatcher.failed`, record `last_error` (and `first_failed_at` on the first failure) on each retry, and on giving up upsert into `segment_job_failures` and delete the job in one transaction guarded by `enqueued_at`; make `done` delete the same `(kind, subject)` dead letter. Test in `SegmentMatcherTest`: a job failing past its retries becomes a dead letter with kind, subject, attempts, error and times; a job re-enqueued mid-run stays queued; a retrying job shows its error; a later success clears the dead letter
- [x] 5.2 Prune dead letters older than 30 days in the hourly status pass, and delete an account's dead letters (`subject_uri` under `at://<did>/`) in `Indexer.deleteActor`; test both, the second end to end through an account-deletion event in `AppViewTest`
- [x] 5.3 Count job outcomes (`done`, `retry`, `dead`) per kind for the metrics in group 8 through a small listener the matcher calls, defaulting to no-op; covered by 5.1's tests asserting the counts

## 6. Last errors on existing failure rows

- [x] 6.1 `SpaceSyncer.failed` writes `last_error`/`last_error_at` via `ErrorSummary`; a successful sync clears them. Extend `SpaceSyncerTest`: an unreachable PDS records the error, an `UpstreamFailure` refusal records its name, a later success clears it
- [x] 6.2 `ActivityStreamsViews.failed` takes a reason (fetch error summary, "CID mismatch", "too large", "not stream data") and stores it with `last_failed_at`; extend its tests to check an unreachable host and undecodable data each record the right reason and none of the fetched bytes

## 7. Persistent block backfill backoff

- [x] 7.1 Replace `BskyBlockBackfill`'s Caffeine cache with `bsky_block_backfill_failures`: select due actors with no failure row or a due one, upsert on failure with 10 min × 2^(n−1) capped at 24 h and the error summary, delete on success; delete the row in `Indexer.deleteActor`. Update its tests: three failures wait forty minutes, a new instance over the same database honours the pause, backing-off actors don't crowd out due ones, success clears the row

## 8. Readiness and metrics

- [x] 8.1 Add `GET /xrpc/_ready` in `api/Routes.kt` (`SELECT 1` with a 2 s timeout, result cached 5 s, 200 or 503 with `database` true/false); test in `AppViewTest` with a working database, a closed data source (503 while `_health` stays ok), and many calls within five seconds querying once
- [x] 8.2 Add `ops/Metrics.kt`: a `PrometheusMeterRegistry` built only when `metricsPort` is set, JVM/process binders, Hikari `metricRegistry`, registry-backed worker meters, the Jetstream lag and labeler gauges, the matcher and syncer counters, and one DB collector caching the queue, failure, space and backfill counts for 15 s; unit-test the collector's caching and the meter names and tags against design §7
- [x] 8.3 Install `MicrometerMetrics` on the public server tagged by route template with unmatched paths folded into one label, and start a second `embeddedServer` on `metricsHost:metricsPort` serving only `GET /metrics`; test that `/metrics` on the public port is 404, that a request to `/img/media/{did}/{cid}` appears only under its template, and that the scrape contains the per-kind job gauges after enqueueing jobs

## 9. Documentation

- [x] 9.1 Document `METRICS_PORT`/`METRICS_HOST`, `/xrpc/_ready` and the worker registry in `README.md`, `docker/.env.example` and `.agents/rules/appview.md` (and the AppView paragraph of `AGENTS.md` if it lists operational endpoints); verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass

## 10. Integration check

- [x] 10.1 Run `./gradlew :server-appview:test` and `./gradlew check` (via the gradle-runner agent) and confirm both pass; with the local stack up and `METRICS_PORT=9464`, confirm `curl localhost:9464/metrics` shows `appview_worker_up` for every enabled worker and `appview_jetstream_lag_seconds`, and that stopping `appview-db` turns `_ready` to 503 while `_health` stays ok
