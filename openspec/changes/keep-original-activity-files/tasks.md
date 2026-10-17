# Tasks

## 1. Lexicon and local stack

- [x] 1.1 Add `lexicons/app/activitytracker/feed/activitySource.json` (`activity`, `file` gzip blob max 10 000 000, open-enum `format`, `fileName`, `size`, `compressed`, optional `entry`, `createdAt`; description says it's only in `self` and the AppView ignores it), following the `lexicon-change` skill; verify `lexicon-auditor` reports no mismatch for it
- [x] 1.2 Mirror it as `ActivitySourceRecord` (with a `FILE_FIELD` constant) in `core/lexicon` and add `Nsid.FEED_ACTIVITY_SOURCE`, kept out of `indexedCollections` and `TIER_COLLECTIONS`; verify a `core/lexicon` jvmTest round-trips a record through `AtJson` with and without `entry`
- [x] 1.3 List the collection in `space/athlete.json` and say in its description that the self tier holds each imported activity's original file, which the AppView ignores; verify `scripts/dev.sh` republishes it and a fresh sign-in's `space:` grant lists `feed.activitySource`
- [x] 1.4 Add an AppView test that drives a `self`-space `feed.activitySource` put through the `Indexer` and checks nothing is stored, next to the `feed.activityTrack` one; verify `./gradlew :server-appview:test` passes
- [x] 1.5 Set `PDS_BLOB_UPLOAD_LIMIT` in `docker/compose.yaml` to cover the 10 MB cap (check the pinned image's default first) and note why in `.agents/rules/docker.md`; verify uploading a 9.5 MB blob to the local PDS succeeds

## 2. Reading, gzipping and preparing files (`:core-activityfile`)

- [x] 2.1 Add `expect suspend fun gzip(bytes)` with JVM, iOS (zlib, gzip window bits) and web (`CompressionStream`) actuals; verify a commonTest round-trips bytes through `gzip` then `gunzip` on the jvm and iosSimulatorArm64 targets
- [x] 2.2 Add `ActivityFileFormat` (with lexicon tokens) and `ActivityFileReader.readFile` returning `ImportedFile(format, gzipped, activities)`, with `read` delegating to it; verify `ActivityFileReaderTest` checks the format and `gzipped` of a FIT, gzipped FIT, GPX, TCX, Shearwater, UDDF and Subsurface fixture
- [x] 2.3 Add `SourceFile.prepare` (gzips unless already gzipped, null over 10 MB stored, random shared key) and `SourceFile.original`; verify commonTests: a plain file comes back byte for byte, an already-gzipped file is stored and returned unchanged with `compressed = false`, and an incompressible 11 MB input gives null
- [x] 2.4 Describe `readFile`, `gzip` and `SourceFile` in `.agents/rules/activity-files.md`; verify `scripts/check-agent-sync.sh` passes

## 3. Shared outbox blobs (`core/data`)

- [x] 3.1 Add `OutboxBlobEntity`, `OutboxEntity.blobKey`, their DAO queries (insert-ignore, set ref, delete unreferenced) and `MIGRATION_2_3`, bumping `AppDatabase.version` to 3; verify the regenerated `core/data/schemas/…/3.json` is committed and a jvmTest opens a version-2 database through the migration
- [x] 3.2 Give `PendingBlob` an optional `key` and make `OutboxWriter.put` store a keyed blob once, in the same transaction as its row; verify a jvmTest queuing three puts with one key leaves one blob row and three outbox rows without inline bytes
- [x] 3.3 Teach `OutboxProcessor` to upload a keyed blob once, save its ref after the first successful put, reuse it for later rows (re-uploading once if the PDS no longer has it), and delete unreferenced blob rows; verify with `FakeAthletePds` that three rows sharing a key make one `uploadBlob` call and leave no blob rows behind
- [x] 3.4 Drop a `feed.activitySource` row whose blob the PDS refuses as too large, logging it and continuing the drain; verify a jvmTest where the fake PDS refuses the upload still writes the activity and the rows after it, and leaves no source row
- [x] 3.5 Document shared outbox blobs and the dropped-source rule in `.agents/rules/data-and-auth.md`; verify `scripts/check-agent-sync.sh` passes

## 4. Source store and lifecycle (`core/data`)

- [x] 4.1 On the local stack, put two `self`-space records referencing one uploaded blob and read the blob back through each; record the outcome in design.md and, if the second put is refused, switch 3.3 to re-upload per row
- [x] 4.2 Add `ActivitySourceStore` / `PdsActivitySourceStore` (`put`, `info`, `read`, `delete`) and `requireSourceSpace`; verify jvmTests: `info` sees a queued put and a stored record, `read` returns the original bytes, `delete` skips a session without the grant
- [x] 4.3 Add `ActivityDraft.source` and queue it in `ActivityRepository.queue` after the track and streams, with `requireSourceSpace` in `publish` and `publishAll`; verify an `ActivitySourceTest` (like `ActivityTrackTest`): a Public import's source is in `self` and nothing of it is in the repo, an offline import writes both after reconnecting, a recorded draft writes no source, and a logbook batch of three makes three records naming one blob and entries 0–2 with one upload
- [x] 4.4 Fill a missing source in `mergeInto` (stored or queued) and delete sources in `ActivityManager.queueDelete`; verify tests: merge into a sourceless dive adds one, merge into a dive with one keeps it, delete removes it, delete with recordings removes each, delete keeping recordings leaves theirs, ungroup and edit leave both, a visibility change doesn't touch it

## 5. Import screen

- [x] 5.1 Prepare the `SourceFile` after `readFile` in `ImportActivityPresenter`, set `originalTooLarge` in the state, and pass the source (with `entry` for UDDF and Subsurface dives) into every draft from `ImportDraft` and `DiveBatches`, recordings included; verify `ImportActivityPresenterTest` and `ImportLogbookPresenterTest` check a single file's draft source, the picked dives' entries, a grouped recording's source and an oversized file's notice with no source
- [x] 5.2 Show the "original file won't be kept" notice on the import screen (string in `strings.xml`); verify on the desktop app with Compose Hot Reload that picking a large GPX shows it and a FIT file doesn't

## 6. Download original

- [x] 6.1 Add `ActivityExporter.original` (not-sent, unreadable, MIME by format, original name); verify `ActivityExporterTest` covers a FIT original byte for byte, a gzipped original returned still gzipped, a queued source refused as not sent, and an unreadable blob
- [x] 6.2 Load `sources.info` for the owner's activity, add `ActivityExportState.original` and `ActivityDetailEvent.DownloadOriginal`, and show Export when there are formats or an original; verify detail presenter tests: an imported activity offers the original, a recorded one doesn't, an imported activity without track or streams still offers Export, someone else's activity offers none, and a double tap downloads once
- [x] 6.3 List "Original file" with its name first in the Export sheet, apart from the rebuilt formats and their note (strings in `strings.xml`); verify on the desktop app that downloading an imported FIT's original opens the save dialog with its original name and the saved file matches the imported one

## 7. Integration

- [x] 7.1 Run `./gradlew check` and `./gradlew composeStabilityCheck`; verify both pass
- [x] 7.2 On the local stack, import a FIT file and a three-dive Subsurface logbook on desktop, then download each original; verify the files match the imported bytes, the AppView's `getActivity` names no source, and deleting one logbook dive leaves the others downloadable

## Workflow follow-up

- Archive the change with `/opsx:archive` once implemented and reviewed, and sync `feed/activity-source-file` and `feed/activity-export` into the main specs.
