# Proposal

## Why

A batch import can queue thousands of activities that take hours or days to upload, but all the athlete sees is "Uploading 8,431 changes…". That counts writes, not activities. A write leaves the device the moment it's sent, so nothing records what has been uploaded, and the athlete can't see which activities are done, which one is uploading or how many are left. While all that uploads, the feed also shows a spinner, because its first load waits for the whole outbox to drain. The athlete can't stop an import they regret, or hold it back for a while.

## What Changes

- An **upload queue** listing every activity the athlete saves or edits, whether it's waiting, uploading, uploaded or refused:
  - An import shows as one group with its progress, and files skipped at save are listed in it.
  - An activity that is uploading shows its progress as the writes sent out of its total.
  - Uploaded items stay listed for 7 days, and can be cleared sooner.
- **Cancel** an activity that hasn't started uploading, or the rest of an import. Cancelling drops the activity's queued writes, as discarding a refused change does today.
- **Pause and resume imports.** One switch holds back every import's writes, and it lasts until the athlete resumes, even after the app restarts. The athlete's own activities, edits and interactions keep uploading. It shows apart from a pause the server asks for.
- **Time left** for imports, estimated from the PDS's stated limits and the pace seen so far.
- **The feed's upload row counts activities**, not writes. Own activities and imports are counted apart, and likes, comments and other interactions only as "other changes". Tapping the row opens the queue.
- **BREAKING (UI):** the "Couldn't be published" screen (`UnsentChangesScreen`) becomes the queue's refused section. Changes that aren't activities (likes, comments, blocks…) are still listed there, and can still be retried or discarded.
- **The feed's first load stops waiting for imports.** Before it loads, it sends only the athlete's own writes, not a whole import.
- **"View uploads"** on the screen shown after an import is saved.
- **Platforms:**
  - On Android, the background upload shows a progress notification while imports upload, when notifications are allowed.
  - On web and desktop, the app asks before closing while an import is uploading and not paused.

Out of scope, possibly later:
- byte-level progress for one large file
- a "Sending a large file…" hint
- a Wi-Fi-only setting
- pausing one import apart from the others

## Capabilities

### New Capabilities
- `data/upload-queue`: the athlete's view of their uploads: which activities and edits are waiting, uploading, uploaded, refused or skipped, grouped by import, with progress, time left, cancel, pause and resume, and how long finished items stay listed.

### Modified Capabilities
- `data/outbox`:
  - Rejected changes are seen in the upload queue instead of a screen of their own.
  - The athlete's own pause holds bulk writes alongside the server's.
  - The feed's uploading row counts activities.
  - A cancelled write is never sent.
  - Background uploads don't run for paused imports alone.
- `feed/batch-import`: after saving, the screen offers to open the upload queue, and each saved batch is listed there as one import.

## Impact

- **Room (`core/data`):**
  - new `upload_item` and `upload_batch` tables (`AppDatabase` version 8, with a migration)
  - the user pause stored in `key_value`
  - `OutboxProcessor`, `OutboxPacer`, `AccountPacing`, `OutboxWriter`, `ActivityRepository`, `ActivityManager` (edit, combine, delete) and `RejectedChanges`
- **New feature module** `feature/uploads/{api,presenter,ui}` with `UploadsScreen`, samples, tests and screenshots. It replaces `UnsentChangesScreen` in `feature/feed`.
- **Screens:**
  - `FeedPresenter` and `FeedUi`: the upload row, and the own-writes-only sync before loading.
  - `ImportActivityScreen`, its presenter and its UI: "View uploads".
- **Platforms:**
  - Android: `OutboxWorker` (progress notification, and no rescheduling while only paused imports remain).
  - iOS: `IosBackgroundUploads` (same idle rule).
  - Web: `webApp` `main.kt` (`beforeunload`).
  - Desktop: `desktopApp` `main.kt` (`onCloseRequest`).
- **Strings** in `:ui-resources`.
- **Depends on `feed-import-cards`.** An import's group is keyed by its `importBatch` TID (`BatchChoices.importBatch`), so this change lands after that one.
- No lexicon, AppView or PDS changes.
