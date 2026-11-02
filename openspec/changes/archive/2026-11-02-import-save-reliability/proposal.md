## Why

Applying `upload-queue` on an Android emulator found three ways a batch import fails without telling the athlete:

- Saving 100 or more activities on Android runs out of memory partway, with no message.
- Android freezes the app about 12 seconds after the athlete leaves it, which stops the save. When they come back, the import screen is empty and the unsaved activities are gone. The screen promised that large imports "keep uploading in the background, even after you leave the app".
- With the PDS down, the duplicate check treats every file as new.

These hit hardest on the imports the queue was built for: full account exports, on phones. Uploading already survives the app being closed or killed; saving must too.

## What Changes

- **Memory.** A batch save holds no full activity (route, streams, original file) once it's queued, and a failed zones read no longer keeps every route.
- **Save errors.**
  - A save error is shown next to Save, with how many were saved.
  - Saving again continues the same import without queueing anything twice.
- **The save is a persisted queue.**
  - On Save, the plan is written to the device's database: each selected activity's place in its file, its resolved title, type, visibility, zone and combine group, a pre-assigned record key, and a reopenable handle to each picked file.
  - Saving works through that queue and survives leaving the screen, the app being frozen, and the app being killed. It resumes on its own until every activity is saved or skipped.
- **Reopenable files.**
  - On Android, iOS and desktop, picked files are bookmarked: a persistable URI grant on Android, a security-scoped bookmark on iOS and macOS.
  - On Android, small loose files beyond the system's grant budget are copied into app storage.
  - Grants and copies are released when the save ends.
- **Android.** The save runs as a WorkManager job that is a `dataSync` foreground service while it can be, with a "Saving N of M" notification. WorkManager runs it again after the process is killed.
- **iOS.**
  - The save asks for background time when the app leaves the foreground.
  - It schedules a `BGProcessingTask` that resumes it when the system allows, and it also resumes on the next launch.
- **Desktop.** The save resumes on the next launch.
- **Web.** The save resumes only while the tab lives, since a picked browser file can't be reopened. If the tab closes anyway, the import is reported as stopped after N of M, with the advice to pick the files again.
- **Stopped imports.**
  - An import that can't resume (a file it can no longer open, or a closed web tab) is listed as stopped after N of M, in the upload queue and on the import screen.
  - Cancelling the rest of an import also drops its unsaved activities.
- **Duplicate check.**
  - Always matches against the activities this device knows of: queued writes, the cached feed, and the last 7 days of the upload log.
  - When the log can't be read, it says only those were checked and offers Retry. Saving stays possible.
  - A single-file import also shows that the check failed.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/batch-import`: saving holds bounded memory, reports failures next to Save, continues without repeats, and resumes after the screen, the app or the process goes away, with stopped imports reported where it can't.
- `feed/import-duplicates`: the log search always includes activities on the device, says when only those were checked, offers Retry, and shows a failed check for single files too.
- `data/upload-queue`: stopped imports are shown, cancelling the rest drops unsaved activities, and saving shows progress in the background on Android and iOS.

## Impact

- **`feature/record`:**
  - presenter: `BatchImport.kt` (`saveBatch`, `BatchDrafting` reading a persisted plan), `ImportActivityPresenter.kt`, `OwnActivities.kt`;
  - api: save failure, saving and stopped states, a duplicate-check retry event;
  - ui: `ImportBatchForm.kt`, `ImportActivityUi.kt`, `DuplicateNotice.kt`.
- **`core/data` `importsave/`:** a platform `SourceKeeper` keeps picked files reopenable, and `PickedFileSource` moves here from `feature/record/ui`, with a filekit-core dependency.
- **`core/data`:**
  - Room tables `import_save`, `import_save_source`, `import_save_item`;
  - `upload_batch.total`, `upload_item.recordJson`;
  - the app-scoped `ImportSaves` (lock, progress, launch);
  - `ActivityDraft` takes a given rkey, `ActivityManager.combine` takes given rkeys;
  - `PrivacyZones` stale routes without their points;
  - `OwnActivitySearch`/`FeedRepository` local sources;
  - `UploadQueue` stopped imports and cancelling unsaved items.
- **`composeApp`:**
  - `SessionGraph` exposes the save runner;
  - `App.kt` resumes unfinished saves at session start;
  - the iOS `IosBackgroundUploads` peer `IosImportSaves`, with a new BGTask identifier in `iosApp/Info.plist`.
- **`androidApp`:** `ImportSaveWorker`, the manifest's `FOREGROUND_SERVICE_DATA_SYNC` and the `SystemForegroundService` `dataSync` type.
- **`ui/resources`:** strings. Fixtures, screenshot references and tests for the new states.
