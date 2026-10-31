# Tasks

Each group lands its own tests. Rate limits are faked in `FakeAthletePds`. The real PDS is checked once, in 1.1 and 6.3.

## 1. Rate-limit headers in `core/atproto`

- [x] 1.1 Find the pinned PDS image's rate-limit headers.
  - Run the local PDS with `PDS_RATE_LIMITS_ENABLED=true` and hit a limited route (for example `createSession` repeatedly) until it answers 429.
  - Note the header names, the format of `RateLimit-Reset`, whether `Retry-After` is sent, and whether `Access-Control-Expose-Headers` lists them.
  - Verify: the findings are recorded in design.md §1, and the parser in 1.2 matches them.
- [x] 1.2 Add `RateLimit` and its parser, and add `rateLimit` and `retryAfter` to `XrpcException`.
  - Verify: a `core/atproto` commonTest covers epoch-second and delta-second resets, `Retry-After` as seconds and as an HTTP date, and missing headers (null).
- [x] 1.3 Give `XrpcClient` an `onRateLimit` observer, called for every response carrying the headers. Wire the PDS client in `SessionClients` to the account's `AccountPacing` (`SessionClients.pacing`).
  - Verify: a `MockEngine` test sees `remaining` from a 200 and from a 429.

## 2. Bulk writes in `core/data`

- [x] 2.1 Add `bulk` to `OutboxEntity`, with a `Migration(6, 7)`, and commit the regenerated `core/data/schemas/.../7.json`.
  - Verify: `:core-data:jvmTest` passes, including a migration test that opens a version 6 database holding a queued write and reads it back with `bulk = false`.
- [x] 2.2 Add the `BulkWrites` context element. `OutboxWriter` marks writes bulk under it, and `publishBatch` installs it.
  - Verify: an `ActivityRepositoryTest` publishes a batch of two drafts and finds all their queued rows (record, details, streams, track, source, devices) marked bulk, and a single `publish`'s rows not marked.
- [x] 2.3 Order `drain`'s writes: non-bulk first, each in `id` order, with a non-bulk write that shares an rkey with a queued bulk write staying behind it.
  - Verify: an outbox test queues 50 bulk activities, then a like, then an edit to the 10th activity. It drains with a fake that records the order: the like goes first, and the edit follows the 10th activity's writes.

## 3. Pausing and the reserve

- [x] 3.1 Move the drain lock and pause state into the app-scoped `OutboxPacing`, one `AccountPacing` per DID.
  - Verify: a test drains the same account from two `OutboxProcessor`s built over one `OutboxPacing`, at the same time, and each write is sent once.
- [x] 3.2 On a 429, set the pause from `retryAfter`, else the reset, else one minute, without `markFailed`. A drain while paused sends nothing.
  - Verify: a `FakeAthletePds` limit of N writes answers 429 with a reset time. Then:
    - `attempts` stays 0.
    - A second drain before the reset makes no request.
    - A drain after the reset (test clock) sends the rest.
- [x] 3.3 Add the bulk reserve: below a tenth of `limit` remaining, bulk writes pause until the reset while other writes go on.
  - Verify: with `remaining` reported at 400 of 5,000, a drain sends a queued comment, sends no bulk write, and reports the bulk pause until the reset.

## 4. The pacer

- [x] 4.1 Add `OutboxPacer.run()` and `runUntilIdleOrPaused()` (design §5): drain, wait out pauses, back off after transient failures (15 s doubling, capped at 15 min, reset on success), and wake on new queued writes.
  - Verify: `runTest` virtual-time tests with `FakeAthletePds` cover:
    - 2,000 bulk writes against an hourly budget, all sent over the simulated hours with no 429 counted as an attempt.
    - A write queued while the loop idles is sent without any other call.
    - Offline then back online resumes within the backoff.
    - Cancellation leaves the remaining writes queued.
- [x] 4.2 Start `run()` in `App`'s session effects, and drop the large-batch `sync()` after an import (design §5).
  - Verify: `ImportBatchPresenterTest` still passes. A `composeApp` jvmTest, or a presenter test with the pacer running, sees an import's writes sent with no feed load.

## 5. Feed pause row

- [x] 5.1 Add `FeedState.uploadPausedUntil` from the pacer's `pausedUntil` while writes are pending, and add the row's second line and string.
  - Verify:
    - `FeedPresenterTest` sees the pause time while paused and null after.
    - `FeedUiTest` finds "Paused by the server until 3:40 PM" in a paused sample, rendered in a fixed zone with a 12-hour clock.
- [x] 5.2 Add the paused `FeedSamples` entry and regenerate the screenshot references, then look at them.
  - Verify: `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite` passes.

## 6. Background on Android and iOS

- [x] 6.1 Android:
  - Add `work-runtime` to the version catalog and `androidApp`, plus `OutboxWorker` (the unique `outbox-drain` work, asked for on each `ON_STOP`, ending without asking again once signed out).
  - Desktop and web ask for no background pass.
  - Verify:
    - `./gradlew :androidApp:assembleDebug` and `lint` pass.
    - On an emulator against the local stack, with rate limits on: save an import of 50 activities, close the app, and confirm with `adb shell dumpsys jobscheduler` and the AppView that the writes arrive while the app is closed.
  - Checked 2026-10-31, with limits off and one write: queued offline, Home scheduled `outbox-drain`, the worker returned RETRY while the PDS was down and SUCCESS once it was back, with the launcher in front, and the record reached the PDS.
  - Checked again 2026-10-31, with limits on: 50 synthetic runs from one zip queued 200 writes while the emulator had no route to the PDS; Home, then `am kill`. With the route back, the worker started a new process, returned SUCCESS with the launcher in front, and all 50 reached the PDS. The AppView first indexed only 10, because Jetstream had dropped events during task 6.3's 2,500-write burn (below); after Jetstream's state was reset it has all 50.
- [x] 6.2 iOS:
  - Add the `Info.plist` identifier and `processing` mode, register in `iOSApp.swift`, and add the Kotlin `IosBackgroundUploads`, which submits a `BGProcessingTaskRequest`.
  - Verify:
    - The xcodebuild simulator build from CLAUDE.md succeeds.
    - In the simulator, after backgrounding with pending writes, run `e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"io.github.solcott.activitytracker.outbox"]` from the debugger, and the writes are sent.
  - Checked 2026-10-31: the build installs and migrates to version 7, and the app submits the request on backgrounding, but the simulator's scheduler never records it ("No task request … has been scheduled"), so the launch can't be simulated there. `schedule` now logs it when iOS refuses the request; the simulator accepts it (its log shows `submitTaskRequest` and no refusal).
  - Checked on an iPad (Personal Team signing): after the app had been backgrounded once, `_simulateLaunchForTaskWithIdentifier` from lldb launched the task, the handler ran (`IosBackgroundUploads.run` and its upload coroutine), and it called `setTaskCompletedWithSuccess:` with YES. Signed out, since a device can't reach the local stack, so it had nothing to send.   - Checked on the iPad 2026-10-31 against the local stack in device mode (`device-local-stack`), signed in as alice.test: with the tunnel to the PDS down, a manual activity was saved, the app sent to the background and the iPad locked on its charger. iOS launched the task by itself about ten minutes after an earlier backgrounding (it ended with `setTaskCompletedWithSuccess:NO` while the PDS was unreachable). With the tunnel back at 22:28 and the iPad still locked, the activity reached the PDS at 22:35:56, the account's only write since, without the app being opened. Forcing the task from the debugger needs the app in front, where its own loop races the task, so the locked run is the one that shows the task sending.
- [x] 6.3 Run end to end on desktop against the local stack with `PDS_RATE_LIMITS_ENABLED=true`:
  - Import a synthetic batch (generated files, never real exports) that runs into a limit.
  - Like an activity while paused.
  - Verify:
    - The feed shows the pause row with the reset time.
    - The like is sent first once the pause ends.
    - Every activity reaches the AppView without refreshing the feed.
  - Checked 2026-10-31, with the blob budget at 59 of 1,000:
    - Five synthetic GPX imports queued 27 bulk writes, and the feed showed "Paused by the server until tomorrow at 6:46 AM" (the reset, across the DST change).
    - The paused writes were not counted as attempts.
    - A manual activity (the account can't like its own activities) reached the PDS while the bulk writes waited.
    - Checked again with bob's hourly record budget burned to 0 (2,500 `putRecord`s to a throwaway `dev.local.burn` record): the 27 queued writes paused until the reset ("Paused by the server until 11:29 AM"), a like on alice's activity queued behind the pause with no attempt counted, and at the reset the like reached the PDS first (17:29:32.267, before any import record) and the queue emptied by 17:29:42 with no feed refresh.
    - The burn overflowed Jetstream's verify queue, leaving it a chain break for bob and carol that its resync can't mend locally (it fetches `https://localhost:2583`, and the local PDS serves HTTP only). After deleting Jetstream's volume and restarting the AppView, the AppView has all five imported activities and the like. A rerun should hit the limit without flooding Jetstream (the daily blob limit, as in the first run).

## 7. Docs and checks

- [x] 7.1 Update `.agents/rules/data-and-auth.md`'s outbox paragraph to cover bulk ordering, the reserve, the pause, the pacer, the app-wide lock and background jobs.
  - Verify: `scripts/check-agent-sync.sh` passes.
- [x] 7.2 Run `./gradlew check`.
  - Verify: it passes.

## Workflow follow-up

- Archive with `/opsx:archive paced-outbox` once applied.
