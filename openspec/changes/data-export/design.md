# Design

## Context

See proposal.md for why. What exists today:

- **Per-activity export.** `ActivityExporter` (feed/activity-export) rebuilds one activity from an `ActivityView`: its streams come from the AppView (`feed.streams`), its full track from `ActivityTrackStore` (self space), and its original from `ActivitySourceStore`. It returns an in-memory `ExportedFile(name, mimeType, bytes)`, which `rememberFileHandOff` gives to the share sheet, a save dialog or a browser download.
- **Reading an activity from the PDS.** `ActivityPlaces.read(uri)` reads an activity from every place (repo plus each tier space) straight from the PDS. It returns a `StoredActivity` whose `full` record has every tier's details laid over it, and whose `data` merges the streams. It needs no AppView.
- **Reading the repo and spaces.** `SpaceClient` lists a space's records with paging (`listAllRecords`) and fetches space blobs (`getBlob`). The repo side has `com.atproto.repo.listRecords` and `com.atproto.sync.getBlob` descriptors (`lexicon/atproto/Repo.kt`). The space protocol can't list a space's collections, but `AthleteSpaceDeclaration` mirrors every collection the athlete space type declares, and a test pins it to `lexicons/.../space/athlete.json`.
- **Persisted background jobs.** `ImportSaves` (feed/batch-import) is the pattern for an account-scoped job:
  - Room rows record the job and its items.
  - `ImportSaveLauncher` hands the job to the platform: an Android foreground `ImportSaveWorker`, or an iOS `BGProcessingTask` plus `beginBackgroundTask`. Without a launcher it runs in an app-wide scope.
  - A per-account lock (`exclusive`) keeps a second runner out.
  - Sign-out cancels the job and drops its rows (`signedOut`).
- **Files.** FileKit 0.17 gives a kotlinx-io `sink()`, `openFileSaver` and streaming `destination write source` on Android, iOS and JVM (`nonWebMain`). It can't write user files on the web: there it offers only `FileKit.download(bytes)` and an experimental OPFS API with whole-content writes.
- **No zip library yet.**

## Goals / Non-Goals

**Goals:**
- Archive size limited by disk, not memory, on every platform, the web included.
- Resumable at item granularity after a process death, without re-reading finished items.
- Reuse the existing pieces: per-activity rebuild, `ActivityPlaces`, the stores, the `ImportSaves` launcher shape and FileKit.

**Non-Goals:**
- Restoring from an archive. The raw layout is chosen so a later change could, but nothing here reads an archive back.
- Compression tuning, or encrypting the archive.
- Exporting from another device's outbox.

## Decisions

### 1. Archive layout

```
activity-tracker-<handle>-<yyyy-mm-dd>.zip
  README.txt
  manifest.json
  records/<place>/<collection>/<rkey>.json    place = repo | followers | mutuals | self | interactions
  blobs/<place>/<cid>                         every blob a record names, except activity originals
  activities/<yyyy-mm-dd>-<title>[-n].<ext>   original file, else rebuilt FIT
```

- **Records** are written as the `value` JSON the PDS returned. Each file sits under where it was read from, so an activity split across tiers shows exactly where each part lives.
- **Blobs** are found by walking each record's JSON for `{"$type":"blob","ref":{"$link":…}}`, and are fetched from the same place (repo `sync.getBlob`, or space `getBlob`).
- **Originals** (`feed.activitySource` blobs) appear only under `activities/`, which avoids storing the largest files twice. The manifest maps each source blob's CID to its `activities/` path, so the raw layer stays complete.
- **Rejected:** putting the PDS's CAR file in the archive. It holds only the repo, carries other apps' records, and the athlete can get it from their PDS.

### 2. What is walked

- **Repo:** each `app.activitytracker` record collection, with a list derived from the lexicon record types. A jvmTest pins this list to the `"type": "record"` files under `lexicons/app/activitytracker`, the way `AthleteSpaceDeclarationTest` pins the space declaration. A new record type then fails the test instead of being silently missed.
- **Spaces:** every collection in `AthleteSpaceDeclaration`, in each of followers, mutuals, self and interactions.
- **Without spaces:** when `canUseSpaces` is false, only the repo is walked, and the manifest records why.

### 3. Activity files from the PDS, not the AppView

`ActivityExporter` gains an internal entry point that takes the `StoredActivity` from `ActivityPlaces.read` plus the track and source stores, and feeds the existing `build`.

- This reuses every FIT rule in feed/activity-export.
- It drops the AppView dependency. The one loss is the training load: that comes from `feed.activityZones` via the AppView, so the rebuilt FIT omits it.
- The `activityZones` record itself is still in `records/`.

### 4. Stage per item, zip at the end

The job writes each item (one record JSON, one blob, one activity file) as its own file in a staging directory (`<app files>/exports/<id>/staging/`), then marks it done in Room. A final local pass streams the staging tree into the zip.

- **Why not append to one zip and truncate on resume?** It avoids twice the disk use, but zip libraries keep the central directory in memory and can't reopen a half-written archive. Writing our own zip writer to get that would be the hand-rolled code the library search was meant to avoid.
- **Cost:** about twice the archive size on disk at the peak. Decision 9 covers the space check.
- **Where the final pass writes:**
  - On Android, iOS and desktop it streams into the destination the athlete picked (`openFileSaver`, or a share-sheet temp file on iOS), so the zip is built at save time and there's never a second full copy.
  - On the web it streams into an OPFS file, and the download serves that file (Decision 7).
  - If the zip pass is interrupted, it simply runs again, because staging is intact.

### 5. Zip writing with kmp-zip

`no.synth:kmp-zip` plus `kmp-zip-kotlinx`: `ZipOutputStream(Sink)`.

- It streams, writes ZIP64 records past 4 GiB or 65,535 entries, and targets JVM, iOS and wasmJs. CRC is computed for DEFLATED entries.
- JSON, README and manifest are DEFLATED. Blobs and activity files are DEFLATED at a low level, since FIT and gzip-compressed stream blobs barely shrink. On wasm, where `kmp-zlib` is pure Kotlin, they're STORED, with the CRC taken from kmp-zlib's `Crc32` while staging.
- **Rejected alternatives:**
  - ArchiveKodec: its zip writer returns a `ByteArray`, and 0.2.0 isn't on Maven Central.
  - kzip: no iOS or wasm.
  - Hand-written zip: more code to own, with no gain.
- **Android:** kmp-zip publishes no Android variant. The first task is a spike confirming that the KMP `android` target resolves its JVM variant. If it doesn't, the fallback is a thin `expect` around `java.util.zip.ZipOutputStream` on Android, with kmp-zip elsewhere.

### 6. The job: `DataExports`, shaped like `ImportSaves`

- **Room tables:**
  - `data_exports`: id, DID, state (`running` / `zipping` / `ready` / `failed` / `cancelled`), started and finished times, counts, unsent-change count, `spacesIncluded`.
  - `data_export_items`: export id, kind (record, blob, activity file), place, collection, rkey or CID, state (`pending` / `done` / `skipped`), staging path, reason.
- **Enumerate, then fetch:**
  - Listing pages stay part of the job, with a per-collection cursor stored so a resume continues the listing.
  - Each listed record is written straight away; its blobs and activity file become pending items.
  - Fetching runs with bounded concurrency (a few at a time) and a retry with backoff. After the last retry an item becomes `skipped` with its reason (Decision 8).
- **Launcher:** `DataExportLauncher` mirrors `ImportSaveLauncher`:
  - Android: a `DataExportWorker`, a foreground WorkManager job of type `dataSync` with its own progress notification. WorkManager reruns it after a process death.
  - iOS: a `BGProcessingTask` identifier added to `BGTaskSchedulerPermittedIdentifiers`, plus `beginBackgroundTask` while backgrounding. An unfinished export also resumes on next launch.
  - Desktop and web: the in-process app scope.
  - A per-account lock keeps one runner.
- **Running it:** the runner needs a session graph per DID, as `runImportSaves` does via `backgroundGraph`, so the shared helper in `BackgroundUploads.kt` is reused.
- **Rejected:** a KMP background-work library (KMP WorkManager, KMPWorker, Backgrounder). It would add a second scheduling model next to `ImportSaveLauncher`, and still wouldn't cover web or desktop.

### 7. Hand-off of a file on disk

A new `rememberArchiveHandOff(): (ExportArchive) -> Unit` sits beside `rememberFileHandOff`, so the per-activity export keeps its in-memory path. On every platform it runs the zip pass into its destination and then deletes the export.

| Platform | Behaviour |
|---|---|
| Android | `openFileSaver` (Storage Access Framework create-document) → zip pass streams into its `sink()`. |
| iOS | Zip pass into a cache file → share sheet (offers Save to Files). |
| Desktop | `openFileSaver` → zip pass into its sink. |
| Web | Zip pass into an OPFS file through our own small `RawSink` over `FileSystemWritableFileStream` → `getFile()` → `URL.createObjectURL` → anchor download. The browser keeps the `File` on disk, so the archive never sits in wasm memory. Staging on the web is OPFS too, through the same sink. |

On iOS, Android and desktop, cancelling the dialog keeps the export `ready`. On iOS the share sheet gives no reliable "saved" signal, so the export is deleted after a share completes with an activity type, and kept otherwise.

### 8. Problems are data

- **Item failures:** an item that fails after retries is `skipped` with a reason (unreadable, not found, no access, does not decode). It goes to `manifest.json` `problems[]` and is counted on the Account page.
- **Fatal failures:** the export fails only on errors it can't continue past: the PDS can't be reached for the very first listing, auth is lost, or staging can't be written for a reason other than space.
- **Unsent changes:** the count is taken from the outbox when the export starts.

### 9. Disk space

- **Before starting:** the job estimates the archive as the sum of blob sizes the records declare plus about 1 KB per record, and requires twice that free. Free space comes from the platform via an `expect fun freeBytes(dir)`; on the web it comes from `navigator.storage.estimate()`.
- **Mid-run:** a write that fails for lack of space pauses the export rather than failing it, and the Account page says how much more it needs.

### 10. Account page and sign-out

- **State:** `AccountScreen.State` gains an `export` sub-state (none / running(progress) / ready(problems) / paused(reason) / failed(reason)), from a `DataExports` flow. Events are Export, Cancel and Save.
- **Sign-out:** `AuthRepository`'s pending-work summary gains `exportPending`. `signedOut(did)` cancels the job and deletes `exports/<id>/`, like `ImportSaves.signedOut`.
- **Expiry:** a ready export older than 7 days is deleted at session start.

## Risks / Trade-offs

- [kmp-zip is young (2.1.0, ~40 stars) and has no Android variant] → An Android resolve spike comes first. The `expect`-wrapped `java.util.zip` is the fallback, and round-trip tests unzip with `java.util.zip` on the JVM.
- [Staging needs about twice the archive on disk] → A space check before and during the run, and a pause rather than a failure. On Android, iOS and desktop the zip pass streams straight to the destination.
- [The OPFS sink is our own code on an experimental browser path] → A spike task first. If OPFS can't stream from wasm, the web falls back to building in memory with a cap (for example 500 MB), and says larger exports need a desktop or phone.
- [iOS gives background processing time only opportunistically] → Resume on next launch, and keep the job resumable per item. The Account page says the export continues while the app is open.
- [Thousands of PDS reads could trip PDS rate limits] → Bounded concurrency, plus backoff on 429 that honors `ratelimit-reset`. A rate-limited item is retried, not skipped.
- [The archive holds sensitive data (unblurred tracks, privacy zones)] → It's staged only in app-private storage, deleted after saving, after 7 days or at sign-out, and the README says it holds private data.
- [A new record type isn't added to the walk] → The lexicon-pinned test from Decision 2.

## Migration Plan

- A new Room schema version adding the two tables, with an auto-migration and no data to carry.
- No lexicon, AppView or Flyway change.
- Rollback is dropping the feature. The tables are unused without it.

## Open Questions

- The exact README wording, and whether the manifest's schema gets a version number now (proposed: `"format": 1`).
