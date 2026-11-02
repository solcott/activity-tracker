# Design

## Context

Today's outbox (`core/data`, see the `data/outbox` spec and `data-and-auth.md`):

- **Rows.** `OutboxEntity` rows are keyed by `id`, grouped by `rkey`, and flagged `bulk` under `BulkWrites`.
  - An activity's details, streams, track, source, devices, zones and segment track all share its `rkey`.
  - `OutboxProcessor.run` deletes a row as soon as it's sent, and marks a refused one `rejectedAt`.
- **Pacing.** `AccountPacing` (app-wide, per account) holds the drain lock, the PDS's stated `rateLimits`, and two pauses: `pausedUntil` (a 429) and `bulkPausedUntil` (less than a tenth of the limit left).
- **Draining.**
  - `OutboxPacer.run` drains whenever the queue changes or a pause ends.
  - `runUntilIdleOrPaused` is the background pass: Android's `OutboxWorker`, iOS's `IosBackgroundUploads`.
- **What the athlete sees.**
  - The feed shows `pendingCount` (writes) and the pause.
  - `UnsentChangesScreen` (`feature/feed`) lists `RejectedChanges`, with retry and discard (`deleteGroup` plus showing what the PDS holds).
- **Feed load.** `FeedPresenter` awaits `activities.sync()` before its first load, which drains everything, imports included.
- **Writes that never enter the outbox.**
  - Visibility changes (`ActivityManager.setVisibility`) and photo blob uploads are direct calls, so they never become queue items.
  - Edits (`ActivityManager.edit`, `ungroup`), new activities (`ActivityRepository.publish`, `publishBatch`), combines and deletes do go through the outbox.
- **Batch identifier.** `feed-import-cards` adds `BatchChoices.importBatch`, a TID shared by every activity of one save. This change keys an import's group by it and lands after that change.

## Goals / Non-Goals

**Goals:**
- The outbox stays the one engine: the queue is a log beside it, never a second sender.
- What's waiting, uploading or refused is derived from the outbox itself, so the log can't disagree with what will actually be sent. The log stores only what the outbox forgets: an item exists, which import it belongs to, how many writes are sent, and when it finished.
- Cancel can never leave an activity half on the PDS.

**Non-Goals:**
- Byte-level progress, a Wi-Fi-only setting, or pausing one import apart from others.
- Listing visibility changes or photo uploads. They are direct calls with their own progress and error handling.
- Syncing the queue across devices. It describes this device's outbox.

## Decisions

### 1. A log beside the outbox: `upload_item` and `upload_batch`

New Room tables (`AppDatabase` version 8 to 9, with a plain `CREATE TABLE` migration):

```
upload_batch  id TEXT PK (importBatch TID), did, source TEXT, savedAt, saved INT, skipped INT
upload_item   id INTEGER PK auto, did, rkey TEXT?, batchId TEXT?, kind (New|Edit),
              title, sport, subSport, startedAt, queuedAt, sentWrites INT,
              finishedAt INTEGER?, skipped INT (0|1)
              index (did, rkey), index (did, batchId)
```

- **Live items.** An item is live while `finishedAt` is null. At most one live item exists per `(did, rkey)`; the DAO's upsert enforces it.
- **Skipped imports.** These are rows with `rkey = null`, `skipped = 1` and `finishedAt` set at save, so they age out with the rest.
- **State is derived** in one DAO query joining the outbox per `rkey`, on its `(did, rkey)` index and without selecting `blob` (Room re-runs observed queries after every outbox write, so reading blob bytes would stall a large drain):
  - Refused: a row with `rejectedAt`.
  - Uploading: `sentWrites > 0`, or the `rkey` is `AccountPacing.sending`.
  - Waiting: anything else that's live.
  - Uploaded: `finishedAt` set and not skipped.
  - Skipped: `skipped = 1`.
- **Progress** is `sentWrites` of (`sentWrites` + remaining rows for the `rkey`), so writes queued by a later edit simply extend the total.

**Alternative: keep sent outbox rows with a `sentAt`.** Rejected: rows carry blobs and record JSON, and keeping them a week would hold large track files for nothing. The log row is a few hundred bytes.

**Alternative: write the whole state into the log.** Rejected: two sources of truth for "is this queued?" drift after a crash between a send and the log update. Deriving from the outbox can't drift.

### 2. Who writes the log

- **New activities.** `UploadLog.queued(rkey, Kind.New, record, batchId)` is called next to the activity's first outbox write:
  - `ActivityRepository.publish` and `publishBatch`.
  - Combines that create a new activity.
- **Edits.** `ActivityManager.edit` and `ungroup` call it with `Kind.Edit`. If a live item exists for the `rkey`, only its title and type are refreshed (spec: an edit while waiting doesn't add an item).
- **Batch identifier.** It's the draft's own `ActivityDraft.importBatch`, which `feed-import-cards` already sets on every draft of a save, combines taking their drafts'. `saveBatch` writes the batch row as the save starts (`ActivityRepository.startImport`, with an `UploadSource`: the exports recognized, else the files), each file it can't read again (`skippedImport`) and the counts at the end (`finishImport`).
- **Deletes.** Deleting an activity removes its live item. The delete write is counted with other changes.
- **Sending.** `OutboxProcessor` increments `sentWrites` after each successful send of a row whose `rkey` has a live item. When no row for that `rkey` remains, it sets `finishedAt`. Both happen in the same Room transaction as the row's delete.

A coroutine-context element for the batch, beside `BulkWrites`, was planned; the draft's `importBatch` turned out to reach every place that logs (publish, `publishBatch`, combines), so none was needed.

### 3. Cancel = claim-checked discard

Spec: cancelled writes are never sent, and a started activity can't be cancelled.

**The race.** The processor reads the pending list at the start of a run and holds `AccountPacing.lock` for the whole drain, which can last hours. So cancel can't take that lock.

**The fix: a short claim.**
- **Processor.** Before sending a row, it takes `AccountPacing.claim` (a small `Mutex`), checks that the row still exists, sets `sending = rkey`, and releases the claim. If the row is gone, it skips it.
- **Cancel.** Under the same claim, it refuses when `sending == rkey`, or when `sentWrites > 0` unless one of its writes is refused (a refused write holds back the rest, so nothing more of it can go; this is today's discard). Otherwise it runs `RejectedChanges`' discard path: `deleteGroup` plus the local overlays (`LocalDeletes` for a new activity, so it leaves lists; show the PDS's version for an edit). Then it deletes the live item.

`RejectedChanges.discard` becomes the shared implementation, `UploadQueue.cancel(rkey)`. Refused items are cancelled the same way, which is today's discard.

**Cancelling the rest of an import** applies this to each live, unstarted item of the batch, in one transaction per item, skipping any the claim refuses.

### 4. The athlete's pause of imports

- **Storage.** A `key_value` entry `uploads.importsPaused.<did>`.
- **Pacing.** `AccountPacing.userBulkPaused: StateFlow<Boolean>` is loaded from it when the session starts, and written through on change.
- **Processor.** `bulkMaySend()` returns false while it's set. Writes held behind a bulk write stay held, as for the server's bulk pause, so one record's order never changes.
- **`PacerOutcome`.**
  - A pass that stops only because of the athlete's pause returns `Idle`. So `OutboxWorker` and the iOS task don't reschedule (spec: no background run for paused imports alone).
  - `OutboxPacer.run`'s wait also wakes when `userBulkPaused` changes, so Resume starts sending at once.
- **What's shown.** The athlete's pause is shown separately from `shownPause`. Resuming doesn't clear the server's pauses.
- **Outliving the screen.** Pausing, resuming, cancelling and clearing run under `NonCancellable` in `UploadQueue`, setting the pacing state before the write, so leaving the screen right after a tap can't drop it. The end-to-end run found a Resume lost this way on the web, where the database is slow while an import uploads. Retry stays cancellable, since it drains.

The alternative was a third `Instant` pause. Rejected: the athlete's pause has no end time, and the background-job rule differs (a server pause schedules a run at its end; the athlete's never does).

### 5. The feed loads without the import

- **New method.** `ActivityRepository.syncOwn()` drains only rows that `sendOrder` would send before any bulk write: not `bulk`, and whose `rkey` has no bulk row queued.
- **Ordering.** The held-back `rkey` set is computed from all pending rows, not the filtered selection. `run(only)` today computes it from the selection, which would let an edit jump ahead of its activity's queued import writes.
- **Feed.** `FeedPresenter` calls `syncOwn()` instead of `sync()`. The pacer keeps draining the rest.

### 6. Feed row counts

`UploadQueue.summary: Flow<UploadSummary>` holds:
- own live items
- imported live items
- other pending writes: rows whose `rkey` has no live item and that aren't rejected
- refused items
- the athlete's pause
- the server's pause

`FeedState` replaces `pendingWrites: Int` with this summary. The row reads like "Uploading 1 activity and 950 imported · 3 other changes". Tapping it opens `UploadsScreen`.

### 7. Time left

This is computed only for imports, by `UploadQueue.estimate()`:

- **Remaining writes.** Bulk rows not rejected or held by a refused write.
- **Rate.**
  - Each limit the PDS stated, at 90% per its policy's window (`w=`; the tenth kept back for the athlete), about 2 points a write.
  - A limit stated on an `uploadBlob` response (`RateLimit.method`, which `XrpcClient` now fills in) bounds only the bulk writes that carry a blob (streams, track, source, segment track), counted through a new outbox index `(did, bulk, rejectedAt, rkey, collection)` so the count reads no rows.
- **Pace.** The writes sent per minute over the last 15 minutes of sending, kept in memory in `AccountPacing`.
- **Estimate.** The later of the limit bound and the pace bound, rounded to "about N min / N h / N days".
- **When hidden.** When neither is known, or while the athlete's pause is on.

The PDS's limits count points rather than writes (a create costs more than a delete), so this is only an estimate. The spec says "estimate", and the UI says "about".

### 8. One screen: `feature/uploads`

**Contract.**
- A new feature with three modules, `UploadsScreen` (`SessionScope`).
- `UploadsState` holds a `ContentState<UploadQueueView>`: the current item, own items, import groups with their items, finished items and the refused other changes. It also holds the pause, the estimate, an `isOpenOnly` flag (desktop and web) and the `eventSink`.
- Events: `Back`, `Cancel(rkey)`, `CancelRest(batchId)`, `Retry(rkey)`, `PauseImports`, `ResumeImports`, `ClearFinished`, `OpenActivity(uri)`.

**Presenter.**
- It collects `UploadQueue.queue()` and keeps writes in `rememberSingleFlight`.
- An import's items are shown collapsed past the first 20, with a "Show all" that pages from Room (the log is local, so a full list is a plain `Flow`, not a cursor).

**Replacing the old screen.**
- `UnsentChangesScreen` and its presenter, UI, samples, tests and screenshots are removed.
- `FeedEvent.OpenUnsentChanges` becomes `OpenUploads`.
- `RejectedChanges` stays in `core/data` as the source of the refused section.

**Navigation.**
- `feature/feed` and `feature/record` presenters depend on `:feature-uploads-api`.
- `composeApp`, `webApp`, `desktopApp` and the screenshot suite add `-presenter` and `-ui`.

### 9. Retention

`UploadLog.prune(now)` deletes finished items older than 7 days, then batches with no items. It runs at session start (in `App`, next to the pacer) and when the queue screen opens. "Clear" deletes all finished items, and batches left empty.

### 10. Platforms

- **Android.**
  - `OutboxWorker` posts a low-importance, ongoing notification on its own channel ("Uploads"), showing the imports' uploaded and saved counts. It updates as `finishedAt` changes and is cancelled when the pass ends with no import writes, or with them paused.
  - An import's saved count is the larger of the total `finishImport` records and its activities logged so far, so the notification and the queue count right while a save runs, and after one stopped short (the emulator run found "0 of 0 uploaded").
  - It's posted only when `NotificationManagerCompat.areNotificationsEnabled()`. This change doesn't ask for `POST_NOTIFICATIONS`.
  - It isn't a foreground service, which avoids Android 15's six-hour cap on `dataSync` foreground services and the extra manifest permission. The cost is that WorkManager's normal limits still apply.
- **iOS.** Nothing visible in the background. The queue catches up from Room on open. The pause rule follows from `PacerOutcome.Idle`.
- **Web.** `webApp` `main.kt` sets `window.onbeforeunload` while `UploadQueue.importsActive` (live import items, imports not paused by the athlete) is true for the signed-in session, which `App` reports through `onImportsActive`.
- **Desktop.** `main.kt`'s `onCloseRequest` asks with a confirm dialog (`JOptionPane`) under the same condition.
- **Open only.** The screen says uploads go on only while open where GPS recording isn't supported (`LocationTracker.isSupported`), the same line between phones and the rest.

## Risks / Trade-offs

- **Log and outbox in different transactions.** A crash could leave a stale `sentWrites`. → Each increment happens in the same transaction as the row's delete (Decision 2), and state is derived from the outbox (Decision 1), so a live item with no rows left is finished on the next prune.
- **`sending` held across a slow blob upload** makes that activity uncancellable for its duration. → Intended: it has started.
- **Cancel during a long drain** relies on the processor re-checking each row under the claim, an extra Room read per row. → It's a single-row primary-key lookup, small next to the network call.
- **Estimates can be badly off** when the PDS changes limits or the connection drops. → Shown as "about", hidden when there's no basis, and recomputed live.
- **Deriving state per open of a 1,362-item import** is a join over the outbox. → Indexed by `(did, rkey)`. The screen shows groups collapsed and loads all items only on "Show all".
- **Removing `UnsentChangesScreen`** breaks any navigation into it. → Only the feed opens it today. Its route is replaced in the same change. Saved navigation state doesn't survive an app update, so no saved back stack can name the removed screen.
- **Depends on `feed-import-cards`.** → It must land first. Without it there is no batch identifier, and imports would show as ungrouped items.

## Migration Plan

- Room 8 to 9 creates the two tables. Writes queued before the upgrade have no items: they count as "other changes" until sent, and appear in the refused section if refused. No backfill is needed, since items exist only to describe new saves.
- Rollback: the tables are additive. An older build with a destructive-migration fallback isn't configured, so a downgrade isn't supported, as for every Room bump.

## Open Questions

- The exact notification copy and channel name on Android can be settled during implementation.
- Whether "Show all" for a large import needs paging if Room's `Flow` of 1,000+ rows proves slow on the web worker. For now the queue reads every item in one observed query and the screen shows the first 20 of an import until Show all; it's measured in the end-to-end test, and doesn't change the specs.
