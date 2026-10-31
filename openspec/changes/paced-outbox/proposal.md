# Proposal

## Why

Batch import now queues thousands of activities, each with several records. The outbox can't keep up with that:

- **It ignores the PDS's rate limits.** A PDS limits how many writes an account makes. Bluesky's defaults are 5,000 points an hour and 35,000 a day for records (2 points a `putRecord`), and 1,000 blob uploads a day. Importing both of one athlete's exports queues about 1,300 activities and some 8,000 records, which is more than a day's allowance. When the PDS answers 429, `OutboxProcessor` stops the run and counts a failed attempt. Nothing tries again until the feed is refreshed or another write drains the outbox, and that retry hits the same limit at once.
- **Uploads only run while a screen asks.** Leaving the app, or not refreshing the feed, stops the upload. On a phone, an import of a full export can't finish without the athlete babysitting it.
- **An import holds everything else back.** The outbox sends writes in the order they were queued. A like or comment sent after an import waits behind every activity of it, possibly for a day.
- **The athlete can't see why.** The feed says "Uploading 6,000 changes…" whether the upload is running, waiting out a limit, or offline.

## What Changes

- **Waiting out rate limits:**
  - When the PDS answers 429, the outbox waits until the time the PDS gives (`Retry-After`, or the `RateLimit-Reset` of the limit hit) and then goes on by itself. A 429 isn't counted as a failed attempt.
  - While it waits, other drains don't send the write that hit the limit, or anything after it, so a like sent during the wait doesn't hit the limit again.
- **A reserve for the athlete's own actions:**
  - Writes queued by a batch import are marked as bulk.
  - A bulk write isn't sent while the PDS reports less than a reserve of its current limit remaining. What's left is kept for likes, comments, edits and other writes the athlete makes while the import uploads.
- **The athlete's own writes go first:** an outbox write that isn't bulk is sent before queued bulk writes. The exception is a write for the same record as a queued bulk write, which still waits behind it.
- **Draining without being asked:** a session-wide loop drains the outbox:
  - whenever writes are queued,
  - when a rate-limit wait ends,
  - and with backoff after a failure that may pass, such as no connection.

  It runs while the app is open on every platform, so the feed no longer needs refreshing for an upload to continue.
- **Draining in the background:**
  - **Android:** a WorkManager job, which needs a network connection, carries on after the app closes.
  - **iOS:** a `BGProcessingTask` that requires network connectivity does the same, when iOS runs it.
  - **Desktop and web:** upload only while the app is open.
- **Seeing a pause:** while the upload waits out a limit, the feed's uploading row also says until when ("Paused by the server until 3:40 PM").

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `data/outbox`:
  - Queued writes are still applied in order per record. Across records, the athlete's own writes go ahead of a batch import's, and bulk writes leave a reserve of the rate limit.
  - Being asked to slow down waits until the time the server gives, rather than counting as a failed attempt.
  - The outbox drains on its own, while the app is open and in the background on Android and iOS.
  - The feed says when uploads are paused by the server, and until when.

## Impact

- **`core/atproto`:** `XrpcClient` reads the `RateLimit-*` and `Retry-After` headers. They're carried on `XrpcException`, and every PDS response's remaining limit is reported to an observer.
- **`core/data`:**
  - `OutboxEntity` gains `bulk`. That bumps `AppDatabase` to version 7, with a migration and a regenerated schema.
  - `OutboxProcessor` gains pause, reserve and ordering logic, and its lock becomes app-wide per account so a background drain and the UI can't run at once.
  - A new session-scoped `OutboxPacer` runs the loop.
  - `ActivityRepository.publishBatch` marks its writes as bulk.
- **`feature/feed`:** `FeedState` gains the time an upload is paused until, along with the UI row, its strings, samples and screenshots.
- **`composeApp`:** `App` starts the pacer for the session. The Android and iOS app graphs expose what the background jobs need.
- **`androidApp`:** adds the WorkManager dependency (`androidx.work:work-runtime`), a worker, and the code that schedules it.
- **`iosApp`:** adds `BGTaskSchedulerPermittedIdentifiers` and the `processing` background mode to `Info.plist`. `iOSApp.swift` registers and schedules the task.
- **Not in scope:**
  - Per-import progress such as "340 of 2,000"; the feed keeps its count of changes.
  - Changing which failures reject a write.
  - Uploading photos, which still upload before their record is queued.
