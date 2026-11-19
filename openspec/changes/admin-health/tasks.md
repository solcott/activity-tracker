# Tasks

## 1. Restart a worker on request (dev/worker-telemetry)

- [x] 1.1 Add `manualRestarts: Int = 0` to `WorkerStatus` and an internal `restartedOnRequest(name)` to `WorkerRegistry` (`ops/WorkerRegistry.kt`); verify with a `WorkerRegistryTest` case that it counts apart from `restarts`.
- [x] 1.2 Give `WorkerSupervisor.launch` a cancellable run job per worker and add `restart(name, whileStopped)` returning a result or a refusal (unknown, `ONE_OFF`, `DISABLED`/`STOPPED`/`FINISHED`, already pending), per design D3: join the cancelled run, run `whileStopped`, start the next run with no pause and `crashes` unchanged, cancelling a crash pause if it's in one; verify with `WorkerSupervisorTest` cases for each delta scenario (restart a loop with no pause and crash count unchanged, step runs between runs, a child launched in `supervisor.scope` survives, each refusal leaves status unchanged, a later crash's pause isn't lengthened, shutdown during a restart ends `STOPPED`).
- [x] 1.3 Update the `appview.md` "Background work is supervised" bullet to mention restarts on request; verify `scripts/check-agent-sync.sh` passes.

## 2. Jetstream rewind support

- [x] 2.1 Add `AppViewConfig.jetstreamRewindMax` from `JETSTREAM_REWIND_MAX` (Kotlin duration, positive, default `24h`, parsed as `REINDEX_TIMEOUT` is) and document it in `docker/.env.example`; verify with an `AppViewConfig` test for the default, a valid value and an invalid one falling back.
- [x] 2.2 Add `JetstreamConsumer.storedCursorUs()` and `storeCursor(timeUs)` over `jetstream_cursor` (reusing `loadCursor`/`saveCursor`); verify with a test that a restart through the supervisor with `storeCursor` as its step leaves the stored cursor at the chosen value although the consumer was saving cursors (a fake websocket feeding events), and that the next connection asks Jetstream for that cursor less 5 s.

## 3. Health page reads

- [x] 3.1 Add `HealthSources` (supervisor, `StartedWorkers`, `Database`, rewind bound, syncer present, `DatabaseSizes`, `Clock`) and the space syncer summary queries of design D5 (counts, the 50 most-failing spaces, identifier/count/time/`last_error` columns only); verify with an H2 test seeding `space_sync` rows (failing, not found, due, healthy) that the counts and ordering match.
- [x] 3.2 Add `DatabaseSizes` with `Unavailable`, `PostgresDatabaseSizes` (catalog query of design D6) and `CachedDatabaseSizes` (60 s, `Mutex`), chosen in `main` by Exposed dialect; verify with a unit test that the cache asks its delegate once for ten reads within a minute and again after it, and that H2 in PostgreSQL mode selects `Unavailable`.

## 4. Health page and actions

- [x] 4.1 Add `admin/HealthPage.kt`: `path = "health"`, the full page plus fragments `health/workers`, `health/jetstream`, `health/labelers`, `health/spaces`, `health/database` (D1), drawing the fields of the spec's page requirements, with action controls only for operators; `privacyProbes` lists the page and every fragment. Verify with Ktor test-host tests in `HealthPageTest.kt` (on `AdminTestBase`): a viewer sees every worker row from a registry with running, backing-off and disabled workers and no forms; Jetstream shows cursor, last event and lag, and "no event yet" before the first; labeler rows show DID, cursor, connected and advanced-at; the spaces section lists a seeded failing space and says syncing is off without a syncer; the database section says unavailable on H2 and renders a fake `DatabaseSizes` largest first.
- [x] 4.2 Add the `restart-worker` action (`health/workers/restart`, target the worker name) calling `supervisor.restart`; verify with test-host tests: an operator restarts a loop and its `manualRestarts` is one; restarting a disabled worker and an unknown name are refused with nothing changed; a viewer's POST answers `403`; a POST without the CSRF token is refused; each attempt has an `admin_actions` row with its outcome.
- [x] 4.3 Add the `rewind-jetstream` action (`health/jetstream/rewind`, target `jetstream`, field `at`) with the checks of design D4 and audit params `at` and `previous`; verify with test-host tests: a rewind within the bound stores the cursor and restarts Jetstream; too far back, forward of the cursor, in the future, unparsable and Jetstream disabled are each refused with the cursor unchanged; a viewer's POST answers `403`; the audit row holds the requested time, the replaced cursor and the outcome.
- [x] 4.4 Add `HealthPage` to `AdminPrivacyTest.pagesUnderTest`, seeding a failing `space_sync` row for the athlete with private activities; verify `AdminPrivacyTest` passes and still catches its deliberately leaking page.

## 5. Wiring

- [x] 5.1 In `Application.kt` `main`, start workers before `appView(…)` and pass `adminPages = listOf(HealthPage(HealthSources(…)))` (D2); verify `./gradlew :server-appview:test` and `./gradlew :server-appview:detekt` (or `check` through the `gradle-runner` agent) pass.
- [x] 5.2 Update the `appview.md` admin-site bullet to name the health page, the rewind bound and that sizes are Postgres-only; verify `scripts/check-agent-sync.sh` passes.

## 6. Local-stack check

- [ ] 6.1 Run `scripts/dev.sh`, open `http://localhost:8081/admin` in the browser pane, sign in as `siteadmin.test` and open Health: verify every worker is listed (Jetstream running, label subscribers and sweep per the stack's labeler), the Jetstream lag is a few seconds, the database section shows real Postgres sizes, and the spaces section's counts match `select count(*), sum(case when failures > 0 then 1 else 0 end) from space_sync` in the local Postgres.
- [ ] 6.2 On the same page, restart `handle-sweep` and confirm its operator restart count goes to one; rewind Jetstream to ten minutes ago and confirm the lag jumps then falls back to seconds and that a record published before the rewind is still served unchanged; try a rewind two days back and confirm it's refused; open `/admin/audit` and confirm all three attempts are listed with their outcomes.
