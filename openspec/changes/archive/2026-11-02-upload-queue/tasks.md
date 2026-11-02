# Tasks

## 0. Prerequisite

- [x] 0.1 Confirm `feed-import-cards` is merged to main and this branch is rebased on it (`BatchChoices.importBatch` and `ActivityRecord.importBatch` exist); verify `./gradlew :feature-record-presenter:compileKotlinJvm` passes on the rebased branch

## 1. Upload log (Room)

- [x] 1.1 Add `UploadBatchEntity` and `UploadItemEntity` with the `(did, rkey)` and `(did, batchId)` indexes, an `UploadDao`, and `MIGRATION_8_9` (`AppDatabase.version = 9`); verify the regenerated schema JSON in `core/data/schemas` and a migration test opening a version-8 database
- [x] 1.2 Add the derived-state query (Waiting, Uploading, Uploaded, Refused, Skipped and progress from `sentWrites` plus the remaining outbox rows per `rkey`; it joins the outbox on its `(did, rkey)` index and never selects `blob`, like `OutboxDao.pending`, with a case in `OutboxQueryPlanTest`) and `UploadLog.queued`/`finished`/`prune`; verify `UploadLogTest` covers each state, an edit refreshing a live item, an edit after upload adding a new item, and 7-day pruning with batches dropped when empty
- [x] 1.3 Add a `UploadBatch` coroutine-context element and log new activities from `ActivityRepository.publish`/`publishBatch` and combines, edits from `ActivityManager.edit`/`ungroup`, skipped ones from `saveBatch`, and remove a live item on delete; verify with `UploadLogWritersTest` (publish, batch with skips, edit while waiting, delete while waiting)

## 2. Outbox: sending, claim and pause

- [x] 2.1 In `OutboxProcessor`, increment `sentWrites` and set `finishedAt` in the same transaction as each sent row's delete; verify an activity's item goes Waiting → Uploading (2 of 5) → Uploaded in `OutboxProcessorTest`
- [x] 2.2 Add `AccountPacing.claim`/`sending`, and have the processor claim, re-check each row exists and set `sending` before sending it; verify a row deleted mid-run isn't sent
- [x] 2.3 Add `UploadQueue.cancel(rkey)` and `cancelRest(batchId)` on the claim, moving `RejectedChanges.discard` onto it; verify cancel of a waiting new activity (no write sent, gone from lists), of a waiting edit (shows the PDS's version), refusal while sending or after a write was sent, and `cancelRest` keeping started items, in `UploadCancelTest`; keep `RejectedChangesTest` passing
- [x] 2.4 Add the athlete's import pause (`key_value` `uploads.importsPaused.<did>`, `AccountPacing.userBulkPaused`, `bulkMaySend`, `PacerOutcome.Idle` when only it holds writes, pacer wakes on resume); verify own writes send while bulk ones wait, the pause survives a new session, and resume sends without waiting for a queue change, in `OutboxPacerTest`
- [x] 2.5 Add `ActivityRepository.syncOwn()` computing held `rkey`s from all pending rows; verify an edit queued behind its activity's bulk writes isn't sent by it, and own writes are
- [x] 2.6 Add `UploadQueue.summary` (own, imported, other changes, refused, both pauses) and `estimate()` (stated limits and 15-minute pace, hidden without either or while paused); verify counts and estimate cases in `UploadQueueTest`
- [x] 2.7 Update `.agents/rules/data-and-auth.md` for the upload log, claim, athlete's pause, `syncOwn` and the uploads screen replacing `UnsentChangesScreen`; verify `scripts/check-agent-sync.sh` passes

## 3. Uploads screen (`feature/uploads`)

- [x] 3.1 Create `feature/uploads/{api,presenter,ui}` and register it in `settings.gradle.kts`, `composeApp`, `webApp`, `desktopApp`, `:app-fixtures` and the screenshot suite; verify `./gradlew checkModuleBoundaries` passes
- [x] 3.2 Add `UploadsScreen`, `UploadsState` (`ContentState<UploadQueueView>`, pause, estimate, open-only flag) and `UploadsEvent`; verify `./gradlew :feature-uploads-api:composeStabilityCheck` passes
- [x] 3.3 Implement `UploadsPresenter` (queue, cancel, cancel rest, retry, pause, resume, clear, open activity, prune on open, through `rememberSingleFlight`); verify `UploadsPresenterTest` covers each event, a failing cancel surfacing a `UiError`, and a double-tapped cancel acting once
- [x] 3.4 Implement `UploadsUi`: the current item, own items, import groups (progress, refused and skipped counts, first 20 then Show all), finished items, refused other changes, pause banners, time left and the open-only note; add strings to `:ui-resources`; verify `UploadsUiTest` against the samples
- [x] 3.5 Add `UploadsSamples` (loading, failed, empty, own only, import part way, paused by athlete and server, refused, finished) to `:app-fixtures`, add `UploadsScreenshots.kt` and its references, and join `ContentLoadingConformanceTest`; verify `ContentLoadingConformanceTest`, `ScreenshotCoverageTest` and the screenshot suite pass after reviewing the references

## 4. Feed and import entry points

- [x] 4.1 Replace `FeedState.pendingWrites` with the upload summary, show "Uploading N activities and M imported · K other changes" with both pauses, and open `UploadsScreen` from it and from the refused row; verify `FeedPresenterTest` and `FeedUi` tests, and update the feed screenshots
- [x] 4.2 Switch `FeedPresenter` to `syncOwn()`; verify a `FeedPresenterTest` whose fake PDS gates bulk writes still loads the timeline
- [x] 4.3 Remove `UnsentChangesScreen`, its presenter, UI, samples, tests and screenshot references; verify `./gradlew :feature-feed-ui:jvmTest :composeApp:jvmTest` passes
- [x] 4.4 Write the import's `upload_batch` (source by recognized export, else the picked files) and add "View uploads" to the import-saved state; verify `ImportActivityPresenterTest` covers the batch row and navigation, and update import screenshots

## 5. Platforms

- [x] 5.1 Android: post and update the "Uploads" progress notification from `OutboxWorker` when notifications are enabled, and cancel it when imports finish or are paused; stop rescheduling when only paused imports remain; verify with a Robolectric or instrumented worker test, or by hand on the emulator, noting which (verified by hand on emulator-5554, API 37: progress 4 → 39 of 43 then cancelled, nothing posted or rescheduled while paused, nothing posted with notifications denied)
- [x] 5.2 iOS: confirm `IosBackgroundUploads` doesn't reschedule on `Idle` from the athlete's pause; verify `./gradlew :composeApp:iosSimulatorArm64Test` (or a build of the scheme) passes
- [x] 5.3 Web: set and clear `window.onbeforeunload` from `UploadQueue.importsActive`; verify in the web preview that closing asks during an unpaused import and not when paused
- [x] 5.4 Desktop: confirm close in `onCloseRequest` under the same condition; verify with the hot-reload MCP or a run of `:desktopApp:run` (verified by hand on a `:desktopApp:run`)

## 6. Integration

- [x] 6.1 Run `./gradlew format` then `./gradlew check`, `scripts/check-public-safety.sh` and `scripts/check-agent-sync.sh`; verify all pass
- [x] 6.2 Local stack end to end on the web app: import a large export, open the queue from "View uploads" and the feed, check progress, pause and resume, cancel one waiting activity and the rest of the import, and confirm the feed loads while the import uploads
