# Design

## Context

See proposal.md (Why). What exists today, which this design builds on:

- **Admin site** (`admin/AdminPage.kt`, `AdminSite.kt`, `PageCall.kt`). A page is an `AdminPage` passed in `appView(adminPages = …)`; `AdminRoutes.get` is admin-only, `AdminRoutes.action` is the only way to change state (POST, operators only, foreign `Origin` refused, CSRF from `hx-headers` or `_csrf`, an `admin_actions` row whatever the outcome). An action returns an `ActionResult(message)`, which `respondOutcome` shows as is, so messages hold identifiers, counts and times only. `AuditPage` is the model page. `main` passes no pages yet (`adminPages` defaults to empty).
- **Workers** (`ops/`). `WorkerSupervisor.launch` runs each worker in one coroutine that loops `runOnce` → crash → `pauseAfter(crashes)` → run again; a `CancellationException` while the coroutine is still active counts as a crash. `WorkerRegistry` keeps one `AtomicReference<WorkerStatus>` per name; `snapshot()` never blocks. `WorkerNames` holds the stable names; label subscribers are `label-subscriber:<did>`.
- **Order in `main`.** `appView(…)` is called before `startWorkers(…)`, which returns `StartedWorkers(jetstream, labelSubscribers)`; the registry exists before both.
- **Jetstream** (`index/JetstreamConsumer.kt`). One row in `jetstream_cursor` (`id = 1`, `time_us`). Each connection loads it, subtracts `REWIND_US` (5 s) and connects with `cursor=`; it saves the cursor every 100 events and at the end of a connection that ends normally. `lastEventUs` is the last indexed event's time in this process (0 before the first).
- **Labelers** (`labels/LabelSubscriber.kt`, `LabelStore.cursor(src)`, `label_cursors`). Each subscriber exposes `did`, `connected` and `cursorAdvancedAt`.
- **Space syncer** (`sync/SpaceSyncer.kt`, `space_sync`). Per space: `failures` (consecutive), `last_error`/`last_error_at` (already `ErrorSummary`), `next_attempt_at` (indexed, V15), `synced_at`, `not_found_since`, `authority_did` (indexed). Per-space syncs are launched in the scope `run` is given, which is `supervisor.scope`, not the worker's own coroutine.
- **Metrics** (`ops/AppViewMetrics.kt`) already count failing spaces (`failures > 0`) every 15 s with no index on `failures`.
- **Rules.** `appview.md`: indexing is idempotent ("rewinding the Jetstream cursor replays safely"); SQL in migrations must run on H2 in PostgreSQL mode; stored error text goes through `ErrorSummary`; every new admin page joins `AdminPrivacyTest.pagesUnderTest`.

## Goals / Non-Goals

**Goals:**
- One health page that reads only in-memory status and a handful of small queries, so it stays usable while ingest is struggling.
- A rewind that can't race the consumer's own cursor saves and can't silently skip events.
- A restart that reuses the supervisor's run loop rather than a second way of starting workers.

**Non-Goals:**
- Rewinding a labeler's cursor, or setting any cursor forward. Labels replay safely (newest `cts` wins) but nothing has needed it; left out until it does.
- Restarting one-off workers (`segment-groups-backfill`): they finish and aren't meant to run again.
- Job queue depth and dead letters (admin-job-queues) and per-account sync rows (admin-accounts). The health page links to neither; each sibling page adds its own nav entry.
- Live auto-refresh. A plain reload, or an htmx `hx-get` refresh button per section, is enough.

## Decisions

### D1. One page, sections as htmx fragments
`HealthPage : AdminPage` at `path = "health"` with one `get("health")` drawing the whole page, and `get("health/<section>")` fragments (`workers`, `jetstream`, `labelers`, `spaces`, `database`). The full page renders `workers`, `jetstream` and `labelers` inline (memory only) and loads `spaces` and `database` with `hx-get … hx-trigger="load"`, so a slow database never delays the in-memory status. Actions answer with the outcome and an `HX-Trigger` the affected section listens to, so it redraws. Alternative: separate pages per area. Rejected: the point is one glance at ingest; five sections are small.

### D2. Workers are started before `appView(…)`
`main` calls `startWorkers` first, then `appView(…, adminPages = listOf(HealthPage(HealthSources(…))))`. `HealthSources` holds the `WorkerSupervisor` (its `registry` included), `StartedWorkers`, the `Database`, `config.jetstreamRewindMax`, whether a signing key is configured, and a `DatabaseSizes`. Nothing serves requests until `embeddedServer(…).start`, so starting workers a moment earlier changes nothing observable. Alternative: a late-bound holder filled after `startWorkers`. Rejected: a nullable that's null for a window is a trap for a page that's mounted immediately.

### D3. Supervisor restart: an inner run job per worker
`WorkerSupervisor.launch` keeps, per name, a record of the current run: the run is a child `Job` (`coroutineScope { launch { runOnce… } }` or an explicit child) whose cancellation the outer loop can tell apart from shutdown and from a crash. `restart(name, whileStopped: suspend () -> Unit = {})`:
1. refuses (returns a `RestartRefusal`) if the name is unknown, its kind is `ONE_OFF`, its state is `DISABLED`/`STOPPED`/`FINISHED`, or a restart is already pending (an atomic flag);
2. marks the restart pending and cancels the current run job, then `join`s it;
3. runs `whileStopped` (outside the worker, in the caller's coroutine);
4. signals the outer loop to start the next run immediately, skipping `pauseAfter` and leaving `crashes` unchanged; a run that ended this way isn't passed to `registry.crashed`.
`WorkerRegistry` gains `manualRestarts: Int` on `WorkerStatus` and an internal `restartedOnRequest(name)`. A restart requested while the worker is sleeping in its crash pause cancels the pause and starts at once (same path: the "run job" is then the pause). Work the worker launched in `supervisor.scope` (the syncer's per-space syncs, the reindexer's runs) is a sibling, not a child, so it survives, as the delta spec requires. Alternative: cancel the whole worker coroutine and `launch` a new one. Rejected: it duplicates the crash/backoff bookkeeping and loses the `crashes` counter.

The metrics (`bindWorkers`) gain no new tag; whether to export `manualRestarts` as its own counter is left to implementation (no spec requires it).

### D4. Rewind = restart Jetstream with a cursor write while stopped
`JetstreamConsumer.rewindTo(timeUs)` is not called directly by the page; the page calls `supervisor.restart(WorkerNames.JETSTREAM) { consumer.storeCursor(targetUs) }`. Because the write happens after the run's `join`, no in-flight `saveCursor` from the old connection (every 100 events) can land after it, and a cancelled connection skips its final save (the cancellation escapes the `for` loop before `if (processed > 0) saveCursor`). The next connection subtracts its usual 5 s, which only replays a little more. `lastEventUs` is left alone; it moves back as replayed events arrive, so the lag shows the replay catching up.

Checks, in the action before restarting, each a refusal with a message and nothing changed:
- Jetstream disabled (`StartedWorkers.jetstream == null`);
- `at` not an ISO-8601 instant (`Instant.parse`), or in the future;
- `at` earlier than `now - jetstreamRewindMax`;
- `at` later than the stored cursor (that would skip events). With no stored cursor yet, any time within the bound is a rewind.

Audit params: `at` (requested, ISO), `previous` (the stored cursor it replaced, ISO); target `jetstream`. The form is a `datetime-local`-free text input prefilled with `now - 1h` in UTC plus the earliest allowed time shown beside it, so no browser time zone is involved.

**Why replay is safe.** Every write is an upsert or delete keyed by AT-URI (`appview.md`), and replay is in original order, so a record ends in its latest state. Side effects are idempotent too: segment and group jobs are queued by subject and coalesce. A reindex running meanwhile (admin/repo-reindex) already orders live commits against its snapshot by `rev` through `Indexer.watch`, so replayed older commits don't overwrite it.

**Bound.** `JETSTREAM_REWIND_MAX`, a Kotlin duration like `REINDEX_TIMEOUT` (`AppViewConfig.reindex` parses those with `Duration.parseOrNull`), default `24h`, positive only. 24 hours is chosen because a Jetstream instance keeps only a bounded window of events and a rewind past it silently starts at its oldest event; past a day, `admin/repo-reindex` per account is the right tool. Alternative: no bound. Rejected: an hours-long replay competes with live ingest and a typo (a year) would be harmful.

### D5. Space syncer summary: two small queries, no migration
Counts in one pass: `count(*)`, `count(*) filter failures > 0` written portably as `sum(case when … then 1 else 0 end)`, `not_found_since is not null`, `next_attempt_at <= now`. Failing list: `failures > 0 order by failures desc, last_error_at desc limit 50`, selecting only `space_uri`, `authority_did`, `failures`, `last_error`, `last_error_at`, `next_attempt_at`, `synced_at` (never `notify_token`, `set_hash` or `repo_rev`). `space_sync` is one row per synced space, and the metrics already scan it for `failures > 0` every 15 s, so no index on `failures` is added and this change needs **no migration**. (Had one been needed it would be V71, renumbered at merge, since sibling admin changes may also take V71.) With no syncer the section says syncing is off instead of querying.

### D6. Database sizes: Postgres catalog, behind an interface, cached
`DatabaseSizes` is an interface with `suspend fun read(): Sizes?` (null = not available). `PostgresDatabaseSizes` runs, in one transaction, `select pg_database_size(current_database())` and `select c.relname, pg_total_relation_size(c.oid), c.reltuples from pg_class c join pg_namespace n on n.oid = c.relnamespace where n.nspname = current_schema() and c.relkind = 'r' order by 2 desc`. These read catalog statistics only (no table scan; `reltuples` is the planner's estimate, `-1` before the first analyze, shown as "unknown"). `main` picks the Postgres implementation when the connection's dialect is Exposed's `PostgreSQLDialect`, else `DatabaseSizes.Unavailable`. H2 in PostgreSQL mode reports H2's own dialect, so tests get "unavailable". A `CachedDatabaseSizes` wrapper keeps the last read for 60 s behind a `Mutex` (as `AppViewMetrics.refreshIfStale` does for its 15 s counts), meeting "at most once a minute".

Why Postgres-only rather than portable: neither `information_schema` nor any portable SQL reports on-disk sizes; H2's `DISK_SPACE_USED` is H2-only and production never runs H2. The H2-portability rule (`appview.md`) is about migrations and the queries the AppView serves from, which tests must exercise; this read runs only on the admin page, behind an interface tests replace with a fake to check rendering, and the real query is exercised by the local-stack browser check (task 6). Alternative: exact `count(*)` per table, portable. Rejected: a full scan of every large table (efforts, streams) on each page view.

### D7. Privacy
The page reads only: `WorkerStatus` (error text already `ErrorSummary`), cursor numbers, configured labeler DIDs, `space_sync`'s identifier, count and time columns and its `ErrorSummary` `last_error`, and catalog names and numbers. It never touches record tables' content columns. `privacyProbes` returns `health` and each fragment path; the privacy test seeds a failing space for the athlete with private activities (its `last_error` set to a summary) and checks no seeded content appears.

## Risks / Trade-offs

- [A restart races a crash: the run fails just as it's cancelled] → The pending flag decides: if set when the run ends, the end is treated as the requested restart and not recorded as a crash; the error, if any, is still recorded as `lastError`.
- [Rewind while the consumer is reconnecting with backoff (sleeping in `run`'s own `delay`)] → That sleep is inside the run, so cancelling the run cancels it too; the next run loads the new cursor at once.
- [A long replay raises Jetstream lag and segment job load] → The bound caps it at a day; the page shows the lag falling as it catches up; the audit row says who did it.
- [Restarting the syncer drops its in-memory `inFlight` set] → The set lives on the `SpaceSyncer` object, not the run, so it survives the restart and a slow sync isn't launched twice.
- [Postgres size query untested in CI] → Small, catalog-only, isolated behind `DatabaseSizes`; checked against the local Postgres in task 6.
- [Operators restart a worker repeatedly] → Each is audited; restarts never stack because a pending restart refuses another.

## Migration Plan

No schema change. Deploy as usual; the page appears in the admin navigation once `main` passes it. `JETSTREAM_REWIND_MAX` is optional. Rollback is a plain redeploy of the previous build; a rewind already done simply finishes replaying or is overtaken.

## Open Questions

- Whether to export operator restarts as a metric (`appview.worker.restarts.requested`). Doesn't change the page or the specs.
- Whether the 24 h default should track the production Jetstream instance's actual retention once one is chosen; it's a config value either way.
