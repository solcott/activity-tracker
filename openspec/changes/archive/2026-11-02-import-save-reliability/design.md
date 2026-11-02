## Context

See proposal.md (Why). The current state:

- **The save loop.** `ImportActivityPresenter`'s `SaveBatch` runs `saveBatch` (`feature/record/presenter/.../BatchImport.kt`) in `rememberSingleFlight()`, a composition scope.
  - Its inputs are live objects:
    - `BatchReader` over the picked `ImportSource`s;
    - `ScannedBatch`, `BatchSelection` and `defaultTitles`;
    - `BatchChoices`, with a `zoneOf` lambda over `fileZones`;
    - `BatchPlan` (`alone` indices; `groups` with an optional logged `into: OwnActivity`).
  - None of them is `@Serializable`.
  - After queueing, the presenter runs `newDevices.afterSave(linked, uris)` and, for 50 or fewer, `savedSuggestionsFor(...)`.
- **Picked files.**
  - The picker (`ImportActivityUi.kt:88`, FileKit `OpenMultipleDocuments` on Android, `UIDocumentPicker` on iOS) yields `PlatformFile`s. `PlatformFile.toImportSource()` wraps them right away (`feature/record/ui/.../PickedFiles.*.kt`), and the `PlatformFile` is dropped.
  - A file inside an archive is addressed by `EntryRef(source: Int, path: List<String>)` (`core/activityfile/.../BatchReader.kt:15`), which is plain data.
  - FileKit 0.17 has `bookmarkData()`, `fromBookmarkData`/`resolveBookmarkData` and `releaseBookmark()` on Android, Apple and JVM, but not on wasmJs.
    - Android's bookmark is `takePersistableUriPermission`.
    - iOS's is a security-scoped bookmark, resolved through an access lease.
  - The app uses none of these today.
- **Session graphs.** `App.kt` creates the UI session graph in `remember`. Background upload passes build their own (`background/BackgroundUploads.kt`), and Android's `OutboxWorker` and iOS's `IosBackgroundUploads` (`BGProcessingTask` `io.github.solcott.activitytracker.outbox`) call into it. Both graphs share app-scoped per-account state (`AccountPacing`).
- **Record keys.**
  - An activity's rkey is a client TID chosen when queued.
  - `ActivityManager.combine(into, drafts)` drains the outbox and needs `into` sent.
  - `combine(drafts)` makes a new combined activity.
  - Every write is an idempotent `putRecord`.
- **What holds memory:**
  - `saveBatch`'s `published` list of full drafts;
  - `PrivacyZones.stale` routes, kept when the zones can't be read;
  - errors drawn only in `PickSection`;
  - the duplicate search, which stops at the first AppView failure. `upload_item` has no record to match against.

## Goals / Non-Goals

**Goals:**
- A save is durable from the moment the athlete taps Save: what to save lives in Room, and any session graph in any process can finish it.
- Resuming never queues an activity twice.
- A save's memory is bounded by one file's activities.
- What can't be resumed is told, never silent.
- The duplicate check never knows less than the device does.

**Non-Goals:**
- Resuming on the web after the tab closes. The athlete chose not to persist browser files; the import is reported as stopped instead.
- Re-running the duplicate check at resume. The plan is fixed at Save, as the athlete reviewed it.
- Changing how uploads run in the background (data/outbox, upload-queue design §10).

## Decisions

### 1. Save writes a persisted plan; a runner works through it

At Save, the presenter resolves everything per activity and writes it to Room in one transaction. From then on the screen holds nothing the save needs.

```
import_save         batchId PK, did, source (UploadSource JSON), total, createdAt,
                    choicesJson (training, gear, linked new gear for the after-save offer),
                    state (running|stopped|cancelled), handles (persisted|none)
import_save_source  (batchId, idx) PK, name, size, kind (bookmark|copy|none), handle BLOB?, copyPath?
import_save_item    (batchId, position) PK, did, sourceIdx, pathJson, entry, rkey,
                    title?, typeJson, visibilityJson, zoneId?, originalTooLarge, logbook,
                    groupKey?, intoJson? (OwnActivity), combinedRkey?, done INT (0|1), skipped INT (0|1)
                    index (batchId, done)
```

- **The rows.**
  - `position` follows today's order: groups first, then the rest oldest first.
  - Each item's title is the override, else the export's, else the summary's, else the UI's default title.
  - Its type is the override, else the export's, else the summary's.
  - Its visibility is narrowed as `BatchItem.narrowedTo`.
  - Its zone is the file's chosen zone when the file is zoneless.
  - `VisibilityChoice` and `OwnActivity` get `@Serializable` mirrors in `core/data` for this.
- **Pre-assigned rkeys.**
  - `rkey` is a TID assigned at Save. Combine groups also get `combinedRkey`.
  - `ActivityDraft` gains `rkey: String?`, used by `ActivityRepository.queue` instead of a new TID.
  - `ActivityManager.combine` gains optional given rkeys.
  - A resumed write of the same activity is the same `putRecord`, and `UploadLog.queued` already keeps one live item per `(did, rkey)`, so a repeat can't duplicate an activity or a queue item.
- **Marking done.** A plan item is marked `done` as soon as `publishBatch` reports it queued. Queueing reads the zones and training from the PDS, so it can't share a Room transaction with the mark. A crash in between queues the item again under the same rkey: the same records are rewritten, and `UploadLog.queued` keeps one live item.
  - A group is marked done after `combine` returns. A crash in between re-runs the group with the same rkeys, which rewrites the same records.
- **The runner.** `ImportSaveRunner` (`SessionScope`, implemented in `feature/record/presenter` next to `BatchDrafting`, interface in `core/data`) does `run(batchId)`:
  - opens the sources (§2);
  - builds a `BatchReader` over them;
  - reads the not-done items grouped by file, in one pass per nested archive as today;
  - drafts each from its row, not from `ScannedBatch`;
  - queues and marks it done;
  - updates `upload_batch` counts, then releases the sources (§2) and deletes the plan rows when none are left.
  - `BatchDrafting` is reshaped to draft from an `import_save_item` plus the re-read `ImportedFile`.
- **`ImportSaves`** (`core/data`, `@SingleIn(AppScope::class)`) is shared by every graph in the process:
  - a per-account `Mutex`, so one runner runs a save at a time;
  - `running` (the batch ids a runner is on) and `failures` (why a save's last run stopped short); progress itself is read from Room (`ImportSaveDao.counts`, through `ImportSaveStore.counts`), so every graph and process sees it;
  - a scope for in-process runs;
  - `cancel(did)`, called on sign-out.
  - A runner that finds the lock held returns at once. The holder will finish.

Alternatives:
- An in-memory `ImportSaves` holding the presenter's lambda. That was the first design. It can't survive process death, which the specs now require.
- Serializing `ScannedBatch` and the selection whole. Rejected: it's large (every summary) and couples the database to presenter types. The per-item rows hold only what a draft needs.

### 2. Picked files get reopenable handles, released when the save ends

- **`SourceKeeper`** (`core/data` `importsave/`, one per platform in `AppScope`): `keep(saveId, sources)`, `open(kept): ReopenedSource?` (a stale Apple bookmark comes back refreshed; `close()` ends its security scope), `release(saveId, kept)` and `sweep(live)`.
  - `PickedFileSource` moves from `feature/record/ui` into `core/data`'s `nonWebMain` with `filekit-core` and implements `PlatformBacked`, so the runner can reopen files without a UI module. `core/activityfile` stays free of FileKit, because the AppView uses it.
- **Android.**
  - `bookmarkData()`, which takes a persistable URI grant.
  - The system caps persisted grants per app (128 before Android 11, 512 after), counting `contentResolver.persistedUriPermissions`. Within that, a save's sources are granted while the budget allows, keeping 64 spare.
  - Past the budget, sources of 64 MB or less are copied into `noBackupFilesDir/import-saves/<batchId>/`.
  - Anything else gets `kind = none`.
  - In practice, an export is one large zip (one grant), and many loose files are small FIT files (copied).
- **iOS and macOS.** A security-scoped bookmark, resolved through FileKit's lease. A stale bookmark is refreshed on resolve.
- **Desktop.** A plain path bookmark.
- **Web.** `persist()` returns null: `kind = none`.
- **When a handle can't be resolved:**
  - A source with `kind = none`, or whose handle can't be opened when a later process resumes, has its items marked skipped if the file is gone (feed/batch-import "Source vanished before save").
  - For a `none` source, the save is marked `stopped` (§5), since the file may still exist but can't be reached.
- **Release.** When a save ends (finished, cancelled, signed out), each grant is released (`releaseBookmark`), and its copies directory is deleted. A startup sweep also releases grants and copies of plans that no longer exist.

### 3. Where the runner runs, per platform

`ImportSaveLauncher` (a platform binding, `AppScope`) has `launch(did)`. It's called after the plan is written, and at session start in `App.kt` when an unfinished save exists for the account.

- **Android.**
  - It enqueues unique work `import-save-<did>` (`ExistingWorkPolicy.KEEP`, expedited with `RUN_AS_NON_EXPEDITED_WORK_REQUEST`, no constraints).
  - `ImportSaveWorker` (androidApp, next to `OutboxWorker`) calls `setForeground` first with a `dataSync` notification, "Saving N of M" on the Uploads channel, updated from `ImportSaves.progress`. Then it runs the runner through the background session graph (`BackgroundUploads.kt`'s graph helper, generalized).
  - Enqueued from the Save tap, the work starts while the app is in the foreground, so the foreground service is allowed.
  - If the process dies, WorkManager runs the work again. If that run starts in the background and `setForeground` throws `ForegroundServiceStartNotAllowedException`, the worker goes on without it until stopped, and returns `retry`. Because every item is durable, partial runs are fine.
  - The manifest adds `FOREGROUND_SERVICE_DATA_SYNC` and declares WorkManager's `SystemForegroundService` with `foregroundServiceType="dataSync"` (`tools:node="merge"`).
  - It's the same ≤6 h/day `dataSync` budget as before; saving takes minutes.
- **iOS.**
  - The run happens in-process in `ImportSaves`' scope through the UI graph, wrapped in `beginBackgroundTask` so leaving the app buys the system's background time.
  - A new `BGProcessingTask` `io.github.solcott.activitytracker.importsave` (no network required) is added to `BGTaskSchedulerPermittedIdentifiers` and registered by `IosImportSaves` beside `IosBackgroundUploads`. It's submitted on Save and when the scene goes to the background with a save unfinished.
  - The task runs the runner through the background graph, cancels on expiration, and resubmits while work remains.
  - The next launch also resumes it at session start.
- **Desktop.** In-process. The next launch resumes it at session start (bookmarks).
- **Web.** In-process. On the next visit, a plan whose sources are all `none` is marked `stopped`.
- **The presenter.**
  - It collects `ImportSaves.progress` for its account.
  - Opening import while a save runs shows a saving state (progress, the import's name, Cancel rest).
  - When the save it started ends while it's still composed, it runs today's after-save steps from the plan's `choicesJson` and done rkeys: the gear offer, small-batch sync and suggestions.
  - If the athlete left first, those optional steps are skipped. The saved import is in the upload queue.

Alternatives:
- A separate `ImportSaveService`. Rejected: WorkManager's foreground worker gives the same protection and is restarted after process death for free.
- iOS `BGContinuedProcessingTask` (iOS 26, shows system progress). Worth adopting when the deployment target allows. Until then, `BGProcessingTask` plus launch-time resume is the baseline.

### 4. No draft outlives its queueing

- **`saveBatch`/runner.** Keeps only counts. URIs and records for the after-save steps are read back from the plan's done rows (records from `upload_item.recordJson`, §6), and only for at most `MAX_SUGGESTED` items.
- **`PrivacyZones.StaleRoute`.**
  - It drops its `route` and keeps the URI, visibility, map tier and the trim.
  - The recheck reads the route back from what was queued or sent: the activity's queued track (GPX) while it waits, else its sent track in `self`.
  - Fallback if that's impractical: past a cap of stale routes, the runner waits for a fresh zones read before queueing more. That's safe now that a pause costs nothing durable.

### 5. Failures, retries, stopped and cancelled imports

- **A failure.**
  - A queueing failure (other than cancellation) ends the run with the plan still there.
  - In-process, the presenter shows `SaveFailure` (`UiError`, done, total) above Save. The batch form keeps the not-done items selected.
  - Save again re-launches the same plan, so it's one import, with no repeats. Changes the athlete makes to the selection first update the plan's remaining rows before it's re-launched.
  - Background runs retry with WorkManager's or BGTask's backoff.
- **Stopped.**
  - `upload_batch.total` is written by `startImport`.
  - A batch is stopped when its plan state is `stopped`, or when no plan remains and `saved + skipped < total`. The second case covers a database restored without the plan.
  - `UploadQueue` exposes stopped on each import group.
  - The import screen shows a dismissable notice from `stoppedImports` (7 days). Dismissing it sets `total = saved + skipped` and deletes the plan.
- **Cancel rest** (`UploadQueue.cancelRest(batchId)`) also sets the plan `cancelled`. The runner checks it between items, then releases the sources and deletes the plan.

### 6. Local activities are always in the duplicate search

- **`ownActivitiesInRange` split.**
  - A never-throwing local read of pending outbox activities, own `ActivityView`s in `feed_item`, and `upload_item` rows with a record.
  - Plus the AppView read, deduped by URI, preferring the AppView's.
- **`upload_item.recordJson TEXT NULL`.** `UploadLog.queued` stores the activity record JSON already built for the outbox: no streams or blobs, a few KB, pruned with the item after 7 days.
  - The upload-queue's observed query doesn't select it.
  - This also serves §4's after-save records.
- **`ownActivitiesAround`.** Keeps searching locally after an AppView failure, and returns `complete = false` with `localOnly = true`.
- **The screen.**
  - `DuplicateCheck.Failed` says only activities on this device were checked.
  - `RetryDuplicateCheck` re-keys `rememberOwnActivities`.
  - `PreviewDuplicate` shows `Failed` for single files.
  - Save stays enabled.

## Risks / Trade-offs

- **[Risk] Android's persisted-grant cap**, shared with any grants the app holds already. → The budget counts existing grants and keeps a margin. Past it, small files are copied and the rest stop with a clear notice. Grants are released at the end, and a startup sweep releases any left by a crash.
- **[Risk] A resumed run in the background on Android gets no foreground service** and is stopped after the job window. → Each run makes durable progress and returns `retry`. Opening the app resumes it in the foreground.
- **[Risk] iOS background time isn't guaranteed.** → Resume at launch is the floor. The notice and the upload queue show it as saving, with its count.
- **[Risk] Copies use app storage.** → Only small loose files past the grant budget are copied. They're deleted with the plan and swept at startup.
- **[Risk] The plan is fixed at Save:** an activity the athlete logged elsewhere between Save and resume isn't re-checked. → Accepted (non-goal). Re-picking after a stop still re-checks.
- **[Trade-off] `upload_item` rows are larger.** → Bounded by the 7-day prune, and never selected by observed queries.
- **[Risk] A large plan table.** → One row per activity (no summaries), deleted when the save ends, and indexed on `(batchId, done)`.

## Migration Plan

- Room version +1, one migration:
  - creates `import_save`, `import_save_source` and `import_save_item`;
  - adds `upload_batch.total INTEGER NOT NULL DEFAULT 0` and `upload_item.recordJson TEXT`.
  - Existing batches have `total = 0` and are never reported as stopped.
- `iosApp/Info.plist` adds the BGTask identifier. The Android manifest adds the permission and the `SystemForegroundService` type. There's no server change.
- Rollback: the tables are additive. A downgrade isn't supported, as for every Room bump.

## Open Questions

- Settled: the stale-route recheck reads the route back from the activity's track: the queued `feed.activityTrack` outbox row's GPX while it waits, else the sent track in `self` (`ActivityTrackStore.readOrThrow`), the same source `RouteTrimmer.sweep` uses. One that can't be read is put back for the next recheck; no cap is needed.
- Whether to adopt `BGContinuedProcessingTask` when the iOS deployment target reaches 26. A follow-up, not this change.
