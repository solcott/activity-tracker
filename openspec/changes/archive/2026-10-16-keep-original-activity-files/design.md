# Design

## Context

See proposal.md for why, and the specs for the behavior. Today:

- `ImportActivityPresenter` gets the picked file's bytes and name (`ImportActivityEvent.FilePicked`), hands the bytes to `ActivityFileReader.read`, keeps only the `ImportedActivity`s, and saves through `saveSingle` (publish, publish as a recording, or `mergeInto` + `sync`) or `saveDives` (merges, then `publishAll`, then grouped recordings). `ActivityFileReader` knows the format and whether the file was gzipped but doesn't report either. Shearwater XML holds one dive; only UDDF and Subsurface give several.
- `PdsActivityTrackStore` is the pattern to copy: a record under the activity's rkey with a blob, queued with `OutboxWriter.put(…, PendingBlob, space = self)`, plus `has` (last queued write, else `readOwn`), `read` (`spaces.getBlob`) and `delete`, with `requireTrackSpace` refusing a session without the grant before anything is queued. `ActivityRepository.queue` and `mergeInto` call it, and `ActivityManager.queueDelete` deletes it.
- The outbox holds each blob inline on its row (`OutboxEntity.blob`), and `OutboxProcessor` uploads it for every row. A 300-dive logbook would then be stored 300 times in Room and uploaded 300 times.
- `OutboxProcessor.drain` stops at the first failure, so a row the PDS always refuses blocks every write behind it.
- Export: `ActivityExporter` builds an `ExportedFile`, `rememberExport` (`app/detail/Export.kt`) holds it in `ActivityExportState`, and `rememberFileHandOff` in `composeApp` hands it to the platform. Export shows only when `formats` is non-empty.
- `gunzip` exists per platform (`Gzip.kt`, `internal`); there is no compressor.
- The local PDS sets no `PDS_BLOB_UPLOAD_LIMIT`, so it uses the image's default.

## Goals / Non-Goals

**Goals:**
- Keep each imported file once, privately, following its activity's lifecycle through the outbox.
- One Room copy and one upload per logbook file, however many dives it gives.
- An original that can't be stored never blocks other writes.

**Non-Goals:**
- Re-reading stored originals with newer parsers (a later change can use them).
- Backfilling sources for activities imported before this change, or any compatibility with sessions or records from before it: local data is wiped and athletes sign in again, as for `keep-full-track-only-me`.
- Streaming uploads from disk. The file is already held in memory for parsing; video (BACKLOG) needs file-backed blobs, not this.
- Showing whether a merge adds the original in the merge note (`MergeGain`).
- Moving the track's and streams' inline blobs to the new shared-blob table.

## Decisions

### `feed.activitySource` record in `self`
Shape: `activity` (at-uri, shares the rkey), `file` (blob, `application/gzip`, `maxSize` 10 000 000, like the track), `format` (open enum: `fit`, `gpx`, `tcx`, `shearwater`, `uddf`, `subsurface`), `fileName` (string, the name as picked), `size` (integer, bytes as imported), `compressed` (boolean: true when the app gzipped the file to store it, false when it came gzipped and is stored as is), `entry` (optional integer, the dive's index in a UDDF or Subsurface file, in file order), `createdAt`. Key `tid`.

The blob is always gzip, so one MIME type covers every format, and a file that came gzipped (a Strava bulk export) isn't compressed twice. `compressed` lets the download give back exactly the bytes picked.

Alternatives: the raw file with its own MIME type (FIT has no registered one, and GPX/TCX compress about tenfold); putting the source on `feed.activityTrack` (a recording can have a track without a file and vice versa, and their merge rules are each "fill if missing", so separate records are simpler).

### The reader reports the format
`ActivityFileReader.readFile(bytes): ImportedFile(format: ActivityFileFormat, gzipped: Boolean, activities: List<ImportedActivity>)`, and `read` becomes `readFile(bytes).activities` so existing callers and tests don't change. `ActivityFileFormat` (in `:core-activityfile`) carries each lexicon token. The format comes from the same content sniffing `decode` already does.

### Gzip at pick time
`Gzip.kt` gains `expect suspend fun gzip(bytes): ByteArray` beside `gunzip`: `GZIPOutputStream` on the JVM, zlib `deflateInit2` with gzip window bits on iOS, `CompressionStream("gzip")` on the web. A new public `SourceFile.prepare(bytes, fileName, file: ImportedFile): SourceFile?` (in `:core-activityfile`) gzips unless `gzipped`, returns null when the result is over the cap, and gives the stored bytes, `compressed`, `format`, `size`, `fileName` and a random `key` (shared by every draft from this file). `SourceFile.original(stored, compressed)` reverses it for download.

The presenter prepares it right after `readFile`, off the main thread, so the import screen can show `originalTooLarge` before saving. Gzipping once per pick also means a logbook's drafts share one byte array.

Alternative: gzip in the repository at save time. The notice would then come too late, and a batch would gzip per draft.

### `ActivityDraft.source` and the save paths
`ActivityDraft` gets `source: DraftSource?` (the `SourceFile` plus `entry`). `ImportDraft`/`DiveBatches` set it for every draft built from the file, with `entry` set to the dive's index in the file for UDDF and Subsurface. Grouped recordings are ordinary drafts, so they carry theirs. Recorded and manual drafts leave it null.

### `ActivitySourceStore` in `core/data`
A copy of the track store's shape, as an interface plus `PdsActivitySourceStore` so repository tests can fake it:
- `put(activity, source)` queues the record in `self` with a **keyed** `PendingBlob` (see below).
- `info(activity): SourceInfo?` gives `fileName`, `format` and whether it's still queued: the last queued write of the rkey if any (decoded from its JSON), else `readOwn` from `self`. The detail screen and `mergeInto` use it; `has` is `info != null`.
- `read(activity): ByteArray` reads the record, `spaces.getBlob(self, …)`, and returns `SourceFile.original(...)`. Throws on failure, unlike the track's `read`, since the download reports it.
- `delete(activity)` queues a delete in `self`, skipped for a session that can't write the collection (it never wrote one).

`requireSourceSpace()` = `requireSpacesFor(Visibility.SELF, Nsid.FEED_ACTIVITY_SOURCE)`, called next to `requireTrackSpace` in `publish`, `publishAll` and `mergeInto` when a draft has a source. This refuses rather than silently dropping the original, as for the track; after the reset every session has the grant.

### Writes
- `ActivityRepository.queue`: after the track and streams, `draft.source?.let { sources.put(uri, it) }`.
- `mergeInto`: `draft.source?.let { if (sources.info(existing.uri) == null) sources.put(existing.uri, it) }`. Like the track, the place is fixed, so this works for an activity still in the outbox.
- `ActivityManager.queueDelete`: also `sources.delete(uri)`. Delete with recordings goes through it for each; ungroup, edit and visibility changes don't touch sources.
- Deleting one dive of a logbook deletes only its record. The blob stays while any other source record refers to it; the PDS reclaims it when none does.

### One outbox blob shared by several rows
A new Room entity `OutboxBlobEntity(key PK, did, bytes, mimeType, ref: String?)` and a nullable `OutboxEntity.blobKey`. `PendingBlob` gets an optional `key`. `OutboxWriter.put` with a keyed blob inserts the blob row (`OnConflictStrategy.IGNORE`, so the second dive adds nothing) and a row with `blobKey`, `blobField` and no inline bytes, in one transaction. `MIGRATION_2_3` creates the table and adds the column; the regenerated `core/data/schemas/…/3.json` is committed.

`OutboxProcessor`, for a row with `blobKey`: use the blob row's `ref` if set, else `uploadBlob` its bytes. Once the record put succeeds, save the `ref` on the blob row (the blob is now referenced, so the PDS keeps it), delete the outbox row, then delete blob rows no pending row names. If a put that reused a saved `ref` is refused because the PDS doesn't have the blob, clear the `ref` and upload once more.

Checked on the local stack (task 4.1, Spaces alpha pinned 2026-10-01): one 9.5 MB `uploadBlob`, then two `space.putRecord`s of `feed.activitySource` in alice's `self` space naming the same blob ref, both accepted; `space.getBlob` returned the identical bytes; after deleting one record the blob was still served, and after deleting both it was gone (400). So the processor reuses the saved ref, as designed. An upload over the PDS's limit answers `413 PayloadTooLarge`.

Alternatives: keep inline blobs and re-upload per row (300× the storage and bandwidth for a logbook); compute the CID locally and upload once (needs CID/CBOR code we don't have, and still needs the bytes stored once somewhere).

### A refused original is dropped, not retried
When `uploadBlob` or the put of a `feed.activitySource` row fails because the blob is too large (the PDS's error for an over-limit upload, checked against the local stack), `OutboxProcessor` logs it, deletes the row and goes on. No other collection is treated this way. The client-side 10 MB cap catches most cases first; this handles a PDS with a lower limit. The activity then just has no source, which the detail screen shows by offering no original.

### Download in the Export sheet
`ActivityExporter.original(view): ExportedFile` throws `NotSentYetException` while the source's own row is queued, `ExportUnreadableException` when `read` fails, and otherwise returns the bytes under `fileName` with the format's MIME type (FIT `application/vnd.ant.fit`, GPX, TCX and UDDF as `ExportFormat` has them, Shearwater and Subsurface `application/xml`, a gzipped original `application/gzip`). The presenter loads `sources.info` with the owner's other detail data. `ActivityExportState` gains `original: OriginalFile?` (name, format), and the state is non-null when there are formats **or** an original. A new `ActivityDetailEvent.DownloadOriginal` runs on the export's `SingleFlight` key and lands in the same `file` slot, so `rememberFileHandOff` needs no change. The sheet lists "Original file" first with its name, then the rebuilt formats under the existing note. Strings go in `strings.xml`.

### AppView
`Nsid.FEED_ACTIVITY_SOURCE` stays out of `indexedCollections` and `TIER_COLLECTIONS`. A test drives a `self`-space `feed.activitySource` put through the `Indexer` and checks nothing is stored, as for `feed.activityTrack`. The space declaration lists the collection and its description says the AppView ignores it.

### Local stack
`docker/compose.yaml` sets the PDS's `PDS_BLOB_UPLOAD_LIMIT` high enough for the 10 MB cap (the track blob shares it). `docker.md` notes why.

## Risks / Trade-offs

- [The Spaces alpha may refuse a second space record pointing at a blob another record already took over] → Checked on the local stack before building on it (task 4.1). If it refuses, the processor uploads the shared bytes again per row (same CID, so one stored copy on the PDS), and Room still holds them once.
- [A large logbook holds its gzipped bytes in memory twice while parsing and preparing] → Bounded by the 100 MB unzipped read limit and the 10 MB stored cap. Over the cap, the prepared copy is dropped.
- [Originals hold device serial numbers and positions inside privacy zones] → Stored only in `self`, read only by the athlete, never indexed. The proposal states this.
- [A source row is dropped for size after the athlete was told nothing at import] → Only on a PDS with a limit below 10 MB. The detail screen then offers no original; a logged warning records it.
- [Deleting the last activity of a logbook while another dive's put is still queued] → Rows go in order; the blob row stays until no pending row names it, so a queued put still has its bytes.

## Migration Plan

Breaking, no migration of server data. Republish local lexicons (`docker/scripts/publish-lexicons.sh`, run by `scripts/dev.sh`), wipe the local stack and the apps' databases, and sign in again so the `space:` grant lists `feed.activitySource`. Room moves to version 3 through `MIGRATION_2_3` for anyone not wiping. Rollback is reverting the change and wiping again.
