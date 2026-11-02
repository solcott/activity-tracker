# Tasks

## 1. Memory: no draft outlives its queueing

- [x] 1.1 In `BatchImport.kt`, make `saveBatch` keep only counts, and the queued indices for 2.2; drop `published`. Read after-save URIs and records back from what was queued. Update `ImportActivityPresenter`'s `afterSave`/`savedSuggestionsFor` callers. Verify with an `ImportBatchPresenterTest` case: saving 200 fake activities leaves no `ActivityDraft` reachable once queued, checked with a `WeakReference` per emitted draft after `System.gc()` in a retry loop.
- [x] 1.2 Drop `route` from `PrivacyZones.StaleRoute`, and have the recheck read the route back from what was queued or sent (design §4; record the choice in design.md's Open Questions). Verify with `PrivacyZonesTest`: a batch published while zones can't be read still re-trims each route once a fresh read comes.

## 2. Room: plan, log columns, local duplicate records

- [x] 2.1 Room version +1 with one migration: `import_save`, `import_save_source` and `import_save_item` (design §1), `upload_batch.total` and `upload_item.recordJson`. Add `ImportSaveDao`. Verify the regenerated schema JSON in `core/data/schemas`, and a migration test from the previous version.
- [x] 2.2 `startImport` writes `total`. `UploadLog.queued` stores `recordJson`, and the upload-queue observed query doesn't select it. Verify with `UploadLogTest` and the outbox/upload query-plan test.

## 3. Durable, idempotent saving

- [x] 3.1 Add `ActivityDraft.rkey` (used by `ActivityRepository.queue`) and optional given rkeys on `ActivityManager.combine`. Mark a plan item done as soon as it's queued; a crash in between re-queues it under the same rkey. Verify with `ActivityRepositoryTest`/`CombineTest`: a draft queues under its given rkey, queueing it twice leaves one live upload item and the same records, and a combine with given rkeys repeated writes the same combined record.
- [x] 3.2 Add `@Serializable` mirrors for `VisibilityChoice` and `OwnActivity`. In the presenter, build the plan rows from `ScannedBatch`, the selection, default titles, `BatchChoices` and `BatchPlan` (resolved title, type, visibility, zone and group, pre-assigned rkeys), and write them in one transaction. Verify with a test: a mixed batch (overrides, a narrowed export item, a zoneless file, an in-batch group, a group into a logged activity) produces the expected rows.
- [x] 3.3 Reshape `BatchDrafting` to draft from an `import_save_item` and the re-read file. Implement `ImportSaveRunner.run(batchId)`: open sources, draft and queue the not-done items by file, update counts, release, and delete the plan when none are left. Verify with `ImportSaveRunnerTest`:
  - a run stopped after 3 of 8 and run again queues only the other 5, with each rkey queued once;
  - a source that can't be opened marks its items skipped;
  - a cancelled plan stops between items.
- [x] 3.4 Add the app-scoped `ImportSaves` (per-account lock, `progress`, in-process scope, `cancel(did)` on sign-out). Verify with `ImportSavesTest`: two runners for one account run one at a time, progress reports done/skipped/total, and sign-out cancels and drops the plan.

## 4. Reopenable picked files

- [x] 4.1 Move `PickedFileSource` into `core/data` `nonWebMain` with `filekit-core` (`core/activityfile` stays free of it, since the AppView uses it). Add the platform `SourceKeeper` (keep, open, release, sweep), with a web binding that keeps nothing. Verify `./gradlew checkModuleBoundaries` and the `core/data` tests for each target.
- [x] 4.2 On Android, bookmark sources within the persisted-grant budget (existing grants counted, 64 kept spare), copy sources of 64 MB or less past it into `noBackupFilesDir/import-saves/<batchId>/`, and mark the rest `none`. Release grants and delete copies at the end, plus a startup sweep. Verify with an Android instrumented or Robolectric test of the budget split. The release is checked in 7.2.
- [x] 4.3 iOS and macOS security-scoped bookmarks resolved through FileKit's lease, refreshing stale ones; desktop path bookmarks. Verify with a JVM test that persists and reopens a temp file, and an iOS simulator test (`:core-activityfile:iosSimulatorArm64Test`) bookmarking a file in the app's container.

## 5. Running the save everywhere

- [x] 5.1 Add `ImportSaveLauncher` with platform bindings, and resume unfinished saves at session start in `App.kt`. Desktop and web run in-process; web marks a plan whose sources are all `none` as `stopped` on the next visit. Verify with a `composeApp` jvmTest: a plan left in the database is resumed when a session graph starts. (`composeApp` has no graph a JVM test can start, so `App` calls `ImportSaves.resume`, and `ImportSaveRunnerTest` checks it: a plan left in the database is handed to the launcher and saved, and nothing is launched when none is left.)
- [x] 5.2 Android `ImportSaveWorker`: unique expedited work, `setForeground` with a `dataSync` "Saving N of M" notification on the Uploads channel, the background session graph (generalize `BackgroundUploads.kt`'s graph helper), and `retry` while work remains. Add `FOREGROUND_SERVICE_DATA_SYNC` and the `SystemForegroundService` `dataSync` type to the manifest. Verify `:androidApp:assembleDebug` and Android lint, and a `WorkManagerTestInitHelper` test that the worker runs the runner and returns `retry` when stopped early. (`androidApp` `ImportSaveWorkerTest`, Robolectric at SDK 36: `retry` while saves are left, `success` when done or signed out, the notification at most once a second, and two Saves keep one unique job that runs the saves and waits to run again.)
- [x] 5.3 iOS:
  - `IosImportSaves` registers and schedules the `BGProcessingTask` `io.github.solcott.activitytracker.importsave` (no network), on Save and on going to the background with a save unfinished;
  - add the identifier to `iosApp/Info.plist`;
  - wrap in-process runs in `beginBackgroundTask`;
  - call it from `iOSApp.swift`.
  - Verify the `xcodebuild` simulator build, then in the simulator, use `e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"io.github.solcott.activitytracker.importsave"]` to see a killed save resume.

  Run 2026-11-02: the simulator refuses every request (`BGTaskSchedulerErrorDomain` error 1), so the task path needs a device. On an iPad Air (M2) with the stack in device mode (`dev.scottolcott.com`), 2026-11-02: an 80-file save went on in the background after Home until iOS suspended the app (at 17, and after a resume at 39 and 69); with the app suspended and Settings in front, `_simulateLaunchForTaskWithIdentifier` through a debugger attached to the running app (calling the class by `NSClassFromString`, since lldb can't see the BackgroundTasks module) resumed it, and it finished 80 of 80 with 80 log items under 80 rkeys. The resume came about three minutes after the call; the app's own log wasn't read, so that it was the task's launch is inferred from nothing else waking the app. Leaving the app ran the save on in its background time (59 to 62 of 150 in 18 s). Going to the background crashed the app: `IosBackgroundUploads` and `IosImportSaves` passed a Kotlin `String` to `NSLog`'s `%@`, which a C variadic receives as a C string; both now pass an `NSString`, and the refusal is logged.
- [x] 5.4 Presenter:
  - Save writes the plan and launches it;
  - the screen shows `ImportSaves.progress` and a saving state when opened mid-save;
  - Back doesn't cancel;
  - after-save steps run only while composed.
  - Add the saving-state samples and UI.
  - Verify with `ImportBatchPresenterTest`: leave mid-save (a gated fake), the save finishes, and a new presenter shows its progress, then the saved summary.
- [x] 5.5 Update `.agents/rules/data-and-auth.md` (the persisted save plan, `ImportSaves`, the launcher, source handles) and `compose-screens.md` (a write that must outlive its screen goes through a persisted plan, not `rememberSingleFlight`). Verify `scripts/check-agent-sync.sh` passes.

## 6. Failures, stopped and cancelled imports in the UI

- [x] 6.1 Add `SaveFailure` to the batch and single-file state, drawn next to Save. Keep the not-done items selected, and have Save again re-launch the same plan (updating the remaining rows if the selection changed). Verify with `ImportBatchPresenterTest`: a failure after 3 of 5 shows "3 of 5", Save again queues only the other 2, and `UploadLogTest` shows one import of 5. Also the UI tests against new samples, and `composeStabilityCheck`.
- [x] 6.2 Stopped imports (design §5) and saving imports in `UploadQueue`. The uploads screen shows "Saving N of M" and "Stopped after N of M · pick the files again". The import screen gets a dismissable stopped notice. `cancelRest` cancels the plan. Verify `UploadQueueTest`/`UploadCancelTest` (cancel rest drops unsaved items and releases sources), `UploadsUiTest`, the import UI test, `ContentLoadingConformanceTest` and the screenshot suite after reviewing the references.

## 7. Duplicate check against local data

- [x] 7.1 Split `ownActivitiesInRange` into a never-throwing local read (pending outbox, own `feed_item` views, `upload_item` records) plus the AppView read, deduped by URI. `ownActivitiesAround` keeps searching locally after an AppView failure and returns `complete = false, localOnly = true`. Verify with `OwnActivitySearchTest`: with a failing AppView, outbox, feed-cache and upload-log activities in range are still returned; with a working one, they're merged by URI.
- [x] 7.2 Add `RetryDuplicateCheck`, the "only activities on this device" text, and `Failed` in the single-file `PreviewDuplicate`. Verify with `ImportLogbookPresenterTest`/`ImportActivityPresenterTest`: a failed search flags local matches, Retry searches again and clears `Failed`, and the single-file preview shows the notice. Update the `DuplicateNotice` UI tests and screenshots.

## 8. Integration

- [x] 8.1 Run `./gradlew check` through the gradle-runner agent. Verify it passes, including `checkModuleBoundaries`, `composeStabilityCheck` and the screenshot suite.
- [x] 8.2 On the Android emulator (`scripts/dev.sh`, `:androidApp:installDebug`), with a 150-file pick and a Strava export zip, verify:
  - `adb shell dumpsys meminfo` stays flat while saving;
  - Home mid-save shows the saving notification and the save finishes;
  - `adb shell am kill` mid-save, then waiting: the save resumes without opening the app, and the queue shows one import with no duplicates;
  - after it finishes, `adb shell dumpsys activity permissions` (or `contentResolver.persistedUriPermissions` logged) shows no grants left, and no copies in `import-saves/`;
  - with the PDS stopped (`docker compose stop pds`), picking the same files flags them, with the local-only notice and Retry.

  Run 2026-11-02 on Pixel_10_Pro (8 GB, `-gpu host`) with picks of 160 and 200 one-hour GPX files; all passed. Java heap stayed at 19–49 MB through each save. Home at 12:00:29, save of 200 finished in the background at 12:01:08 with the notification. `kill -9` at 47 of 200: JobScheduler restarted the process within a second and the save finished, 200 upload items with 200 distinct rkeys, no duplicate starts on the PDS. Reopening showed "Import saved". No persisted grants and nothing in `no_backup/import-saves/` afterwards. Instead of stopping the shared PDS, the device's `adb reverse` ports were removed: all 200 repeats were flagged as exact copies, with the notice and Retry, which cleared once reconnected. The worker now updates its notification at most once a second; it was re-posting it for every activity. A Strava export zip (257 MB, 1,272 activities) kept one persisted grant, saved through Home and a `kill -9` at 255, finished with 1,272 saved and 1,273 log items under as many rkeys (the extra is a combine), released its grant, and the Java heap peaked at 63 MB.
- [x] 8.3 On the iOS simulator: swipe the app away mid-save, then trigger the importsave task or relaunch. Verify the save resumes and finishes with one import and no duplicates. On the web, reload mid-save and verify the stopped notice.

  Run 2026-11-02. Web: a reload at 26 of 80 showed "An import stopped after saving 26 of 80 activities…" with Dismiss. iOS: `simctl terminate` at 64 of 150, then relaunching, resumed and saved all 150 under 150 rkeys, but the log held 151 items: the activity queued as the app stopped had its first write sent and its item finished before the resume queued it again. `UploadDao.queued` now reopens an import's item for the rkey instead of adding one (`UploadLogTest.anImportActivityQueuedAgainKeepsItsOneItem`). Rerun with that build: four `simctl terminate`s and relaunches (at 20, 51, 80 and 111 of 150) finished with 150 saved and 150 log items under 150 rkeys.

## Workflow follow-up

- Archive the change with `/opsx:archive` after review, and sync its specs into `feed/batch-import`, `feed/import-duplicates` and `data/upload-queue`.
