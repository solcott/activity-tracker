# Design

## Context

See proposal.md for why. The current state:

- **`OutboxProcessor.drain(only)`** (`core/data/.../activity/OutboxProcessor.kt`):
  - Reads `OutboxDao.pending(did)` (`ORDER BY id`) and applies each write.
  - A transient failure (`isTransient`: 5xx, 401, 408, 429, `OAuthException`, network) calls `markFailed` (`attempts + 1`) and returns.
  - A refusal is marked rejected and holds back its rkey.
  - It's serialised by a `Mutex` on the processor, which is `@SingleIn(SessionScope)`, so there's one per `SessionGraph`.
- **Who calls `drain`:**
  - About 30 repository methods call it after their own write.
  - `ActivityRepository.sync()` calls it, and `FeedPresenter` calls `sync()` on first load and on refresh.
  - The batch import calls `publishBatch(drain = false)`, then `sync()`. Leaving the import screen cancels that drain, and the feed picks it up on its next load.
- **`XrpcClient.execute`** throws `XrpcException(status, error, message)` and drops the response headers. Successful procedures don't expose headers either. The `@atproto/xrpc-server` rate limiter sends these headers on limited routes:
  - `RateLimit-Limit`
  - `RateLimit-Remaining`
  - `RateLimit-Reset` (epoch seconds)
  - `RateLimit-Policy`
  - `Retry-After` on a 429 (`RateLimitExceeded`)
- **Spaces writes** (`SpaceClient.putRecord`) go to the same PDS through the same `XrpcClient`, so they see the same limits.
- **The local docker PDS** runs with rate limits off (the `PDS_RATE_LIMITS_ENABLED` default).
- **Sessions on each platform:**
  - `App.kt` restores the session through the app-scoped `AuthRepository`, then creates one `SessionGraph` per signed-in DID.
  - The Android `Application` holds `AndroidAppGraph`.
  - iOS has a SwiftUI `iOSApp` with no background modes beyond `location`.
- **Outbox writes in the database:** `OutboxEntity` (`AppDatabase` version 6) has no notion of who queued a write.

## Goals / Non-Goals

**Goals:**
- An import of several thousand records finishes on its own, across hours or days of rate limits, without the athlete touching the app on Android, or with it merely open on desktop and web.
- The athlete's interactive writes are never stuck behind an import, before or during a rate-limit pause.
- One upload at a time per account, whoever starts it: a screen, the loop or a background job.
- Rate-limit timing comes from the PDS, never from hard-coded Bluesky numbers.

**Non-Goals:**
- Per-import progress.
- Parallel uploads. Writes stay sequential, and rate limits make parallelism pointless.
- Changing rejection rules.
- Background upload on desktop or web.
- Uploading photos in the background.

## Decisions

### 1. Rate-limit info rides on XRPC responses

- **`RateLimit`:** `core/atproto` gains `RateLimit(limit, remaining, resetAt: Instant?, policy)`, parsed from the headers.
- **Reset time:**
  - `RateLimit-Reset` above 10⁹ is epoch seconds. Anything smaller is seconds from now, which is the IETF draft's form.
  - `Retry-After` is seconds, or an HTTP date.
- **`XrpcException`** gains `rateLimit: RateLimit?` and `retryAfter: Instant?`.
- **`XrpcClient`** takes an optional `onRateLimit: (RateLimit) -> Unit` and calls it for every response that carries the headers, successful or not.

  The alternative was returning headers from `procedure`. That would change every caller. An observer keeps them unchanged, and the PDS client is built once per session (`SessionClients`), so it feeds the account's `AccountPacing` (§4) through `SessionClients.pacing`.

A browser only exposes headers the PDS lists in `Access-Control-Expose-Headers`. If the web can't see them, it still gets the 429 status and falls back to the one-minute wait. Task 1.1 checks this against the pinned image.

**Found in the pinned image (task 1.1).** These come from `@atproto/xrpc-server`'s `rate-limiter-http.js` and the PDS's `rate-limits.js`. They're enabled only with `PDS_RATE_LIMITS_ENABLED`.
- **Headers on every response from a limited route, success or 429:**
  - `RateLimit-Limit`
  - `RateLimit-Remaining`
  - `RateLimit-Reset`: epoch seconds, `floor((now + msBeforeNext) / 1000)`
  - `RateLimit-Policy`: `<limit>;w=<seconds>`
- **Which limit the headers describe:** when several apply, the combined limiter reports the tightest.
- **On a 429:** the PDS also sends `Retry-After`, as whole seconds.
- **CORS:** every one of these headers is listed in `Access-Control-Expose-Headers`, so the web sees them.
- **Limits:**
  - `repo-write-hour`: 5,000 points per DID.
  - `repo-write-day`: 35,000 points per DID.
  - These two are shared by `repo.*` and `space.*` writes. `putRecord` costs 2 points, `createRecord` 3, and a delete 1.
  - `uploadBlob`: 1,000 a day.
  - `global-ip`: 3,000 requests per 5 minutes.
- **What this means for an import:** blobs, not records, are what limit a large import. Each imported activity uploads up to three blobs (streams, track and original file), so about 330 activities a day. The pacer waits on whichever limit the PDS reports, so nothing in the design depends on these numbers.

### 2. A 429 pauses the outbox; it isn't an attempt

- **On a 429:** `OutboxProcessor` doesn't call `markFailed`. It sets `paused: StateFlow<Instant?>` to:
  - `retryAfter`, else
  - `rateLimit.resetAt`, else
  - now + 1 minute.

  It then returns the remaining count, as for any transient failure.
- **Further drains:** while `now < paused`, `drain` returns the pending count without sending anything.
- **Scope:** the pause is per PDS account, because limits are per account. A new, not yet persisted, fact is fine to lose on restart: the first write after a restart gets a 429 again and re-learns the pause.
- **429 vs `isTransient`:** 429 leaves `RETRYABLE_STATUSES`'s attempt-counting path, but stays transient: it never rejects.

### 3. Bulk writes, ordering and the reserve

- **The `bulk` column:**
  - `OutboxEntity` gains `bulk: Boolean = false`, with a hand-written `Migration(6, 7)` like the earlier ones (`ALTER TABLE outbox ADD COLUMN bulk INTEGER NOT NULL DEFAULT 0`).
  - `OutboxWriter` takes a bulk flag through a coroutine-context element, `BulkWrites`, which `publishBatch` installs around its collect. That's the same pattern as `PrivacyZones.withReadOnce`.

    Every write `queue` makes for an imported draft is therefore bulk: details, record, streams, track, source, devices, zones and segment track. So is a combine the batch makes through `ActivityManager.combine` inside `saveBatch`, because it runs under the same element.
- **Ordering:** `drain` orders the selected writes so non-bulk come first. Each group keeps `id` order, and a non-bulk write whose rkey has a queued bulk write stays in `id` order among the bulk writes.

  This is done in memory over `pending(did)`. The query stays as it is, and a few thousand rows is cheap.
- **Reserve:**
  - `AccountPacing.rateLimits` keeps each limit the PDS stated, by its `RateLimit-Policy`. A response states only the limit it counted against, so a record put's roomy hourly limit must not hide a blob upload's nearly spent daily one.
  - Before each bulk write, if any of them has `remaining < limit / 10` and a `resetAt` in the future, `drain` stops bulk writes for this run. It goes on with non-bulk ones and sets `bulkPausedUntil` to the latest such reset.
  - `drain` and the pacer treat `bulkPausedUntil` like `paused`, but for bulk writes only.
  - The feed shows the later of the two as the pause.
- **Alternatives considered:**
  - A separate outbox table for imports. That would duplicate the rejection, held-rkey and blob-sharing logic.
  - A priority integer. Two levels are all we need.

### 4. One drain at a time per account, app-wide

The `Mutex` moves out of `OutboxProcessor` into an app-scoped `OutboxPacing` (`@SingleIn(AppScope)`), which hands out one `AccountPacing` per DID: the drain lock, the PDS's last stated `rateLimit`, and both pauses. `SessionClients` takes `OutboxPacing` and keeps its account's as `pacing`.

A background job builds its own `SessionGraph` while the UI may hold another. Both get the same lock and the same pause, so they never upload the same write twice, never race on deleting a shared blob, and never step on each other's pause.

### 5. `OutboxPacer`: the session loop

- **The class:** `@SingleIn(SessionScope)` `OutboxPacer(activities, processor, locks, clock)` with `suspend fun run()`, which never returns while the session lives, and `suspend fun runUntilIdleOrPaused(): Outcome`, for background jobs.
- **The loop:**
  1. `sync()`, which drains and re-trims stale routes as today.
  2. If writes remain:
     - If paused, wait until the pause ends.
     - If the last failure was transient, wait with backoff: 15 s doubling to 15 minutes, reset by any write that succeeds.
     - If nothing is sendable, because every remaining write is rejected or held, wait for the next queued write.
  3. Wait for `pendingCount` to change, or the next wake-up, whichever is first.
- **Starting it:** `App` launches `run()` in the session's `LaunchedEffect` block, next to `repairRoutesOnSave`. The existing direct `drain()` calls stay. They return at once while paused, and the lock serialises them with the loop.
- **Cancellation:**
  - The loop is cancelled with the session (sign-out, `key(did)`).
  - A drain cancelled mid-write leaves that write queued. It's idempotent, so a resend is harmless.
- **The import screen:** its post-save `sync()` stays for a small batch, so its suggestions still follow an immediate send. A large batch no longer needs `sync()`, because the pacer sees the new rows.

### 6. Android background: WorkManager

- **The worker:** `androidApp` gains `androidx.work:work-runtime` and an `OutboxWorker : CoroutineWorker`. It:
  1. Gets `AndroidAppGraph` from the `Application`.
  2. Calls `authRepository.restore()`. With no session, it cancels its unique work and returns success.
  3. Creates a `SessionGraph`, calls `outboxPacer.runUntilIdleOrPaused()`, and maps the result:
     - **Idle:** `success`.
     - **Paused until T:** `success`, after enqueueing a follow-up with an initial delay of T − now.
     - **Failed with something that may pass:** `retry()`, using WorkManager's exponential backoff.
- **The unique work:** called `outbox-drain`, with a `NetworkType.CONNECTED` constraint, `ExistingWorkPolicy.KEEP` when the app asks, and `APPEND_OR_REPLACE` for a follow-up after a pause.
- **Scheduling:** the app shells ask, so `composeApp` needs no `expect`/`actual`.
  - On Android, `ActivityTrackerApplication` observes `ProcessLifecycleOwner` and asks on every `ON_STOP`. A pass with nothing queued ends at once.
  - Signed out, `restore()` finds no session, so the pass ends without asking again.
  - Desktop and web ask for nothing.
- **Shared pass:** the worker and the iOS task both call `AppGraph.uploadQueuedWrites()` (`composeApp`), which restores the session and runs `runUntilIdleOrPaused()` on a session graph of its own.
- **Timeouts:** WorkManager stops a worker after about 10 minutes. The pacer is cancellable, and the worker re-enqueues while writes remain.
- **Rejected alternative:** a foreground service with a notification. It would be heavier than needed, and the athlete didn't ask for one.

### 7. iOS background: BGProcessingTask

- **`Info.plist`:**
  - `BGTaskSchedulerPermittedIdentifiers` lists `io.github.solcott.activitytracker.outbox`.
  - `UIBackgroundModes` gains `processing`.
- **`iOSApp.swift`:**
  - Registers the identifier at launch through Kotlin's `IosBackgroundUploads.register(fitDecoder:)`, which also creates the app graph when iOS launches the app only for the task (`appGraph`).
  - Its `expirationHandler` cancels the Kotlin job.
  - Rescheduling while writes remain and completing the task are done in Kotlin through `BGTaskScheduler`. Kotlin/Native reaches `BackgroundTasks` through platform interop.
- **Scheduling:** `IosBackgroundUploads.schedule(after:)` submits a `BGProcessingTaskRequest` with `requiresNetworkConnectivity = true` and `earliestBeginDate` set to the pause end. `iOSApp.swift` calls it when `scenePhase` goes to background, and each run calls it again after a pause, a failure, or running out of time.
- **Limits:** iOS decides when it runs, often on power overnight. The spec only promises that the app asks.
- **Rejected alternative:** a `BGAppRefreshTask`, whose roughly 30-second budget is too short for blob uploads.

### 8. The feed's pause row

- **State:** `FeedState` gains `uploadPausedUntil: Instant?`. `FeedPresenter` collects it from the pacer's `pausedUntil` flow, which is the later of the full pause and the bulk pause, while writes are pending.
- **The row:** the uploading row adds a second line, "Paused by the server until 3:40 PM". The time is formatted with the existing `startTime`/hour-clock helpers, through `LocalTimeZone`.
- **Fixtures:** `FeedSamples` gains a paused sample, and its screenshot reference is regenerated.

## Risks / Trade-offs

- **[Risk] Header names differ in the pinned PDS image.** → Task 1.1 reads them from a real 429 on the local PDS with limits on, before the parser is written. Without headers, the client still waits the one-minute fallback, which is slow but correct.
- **[Risk] Two writers share an account across devices.** Each device sees the other's spend only in `remaining`. → The reserve is from the PDS's live `remaining`, so that's fine.
- **[Trade-off] The pause isn't persisted.** A restart costs one extra 429 per pause.
- **[Risk] iOS rarely runs the processing task.** → Accepted. The foreground loop still finishes the upload whenever the app is open.
- **[Trade-off] Bulk-first ordering changes when an interactive write lands relative to an import.** Writes for one record keep their order, and no outbox write depends on another record being on the PDS first. A combine is bulk with its members, and likes and comments reference URIs the PDS doesn't check.
