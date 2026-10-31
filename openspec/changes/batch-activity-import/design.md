# Design

## Context

Today the import screen (`feature/record`) works one file at a time:
- **Picking and reading:** one picked file is read whole (`PlatformFile.readBytes()`) and sent as `FilePicked(name, ByteString)`. `ActivityFileReader.readFile` then decodes it in memory, gunzipping first when needed.
- **Logbooks:** a logbook with more than one activity becomes a `DiveBatch`. `DiveSelection`, `diveImport`, `rootOf` and `saveDives` select the dives, apply duplicate actions, group in-file copies into `CombineGroup`s and publish through `ActivityRepository.publishAll`.
- **`publishAll`** reads privacy zones and training thresholds once (`privacy.withReadOnce`, `zones.withTrainingReadOnce`), queues every draft, then drains the outbox before returning.
- **Duplicate search:** `rememberOwnActivities` searches the log over the file's span ±14 h with `FeedRepository.ownActivitiesStartedBetween`. That pages up to `MAX_RANGE_PAGES` and then stops silently.
- **Matching:** `ActivityMatcher.match` takes `List<ImportedActivity>` and reports `DuplicateOf.InFile(index)`.
- **Originals:** `SourceFile.prepare` makes one stored source per file. Each draft names its `entry`.
- **FileKit 0.17:**
  - Off the web it exposes `PlatformFile.source(): RawSource` and `size()`.
  - On the web, `PlatformFile` wraps a browser `File` (a `Blob`), readable by `slice`.
  - `rememberFilePickerLauncher` takes `FileKitMode.Multiple()`.

See proposal.md for why, and `specs/feed/batch-import` for the behaviour.

## Goals / Non-Goals

**Goals:**
- Peak memory is bounded by the largest single activity file, not by the batch.
- One batch path for every format. A logbook is just a source with many entries.
- Reuse the existing selection, duplicate, combine and publish code, renamed, not rewritten.

**Non-Goals:**
- Rate-limit pacing and background draining of the outbox. That is the `paced-outbox` follow-up.
- Surviving process death mid-review. The batch is retained across configuration changes only, as today.
- Matching export gear names to the athlete's gear items.
- Archive formats other than zip and gzip (tar, 7z, rar).

## Decisions

### 1. `ImportSource`: random access behind one interface
`core/activityfile` defines:

```kotlin
interface ImportSource {
    val name: String
    val size: Long
    suspend fun read(offset: Long, length: Int): ByteArray
}
```

- **Off the web**, the UI module adapts `PlatformFile`: it opens `source()`, skips to the offset, and reads.
- **On the web**, it adapts the browser `File` with `slice(offset, offset + length).arrayBuffer()`.
- **Event:** the picker sends `FilesPicked(ImmutableList<ImportSource>)`, and the presenter keeps the list. `-api` gains a dependency on `:core-activityfile` for the interface.
- **Alternative rejected:** read every file with `readBytes()`. That is simple, but a 3 GB export can't be held in a phone's or browser's memory.
- **Alternative rejected:** copy picks into app storage first. That doubles disk use and is slow for gigabytes.

### 2. Zip reading in common code, raw inflate per platform
`ZipReader` (common):
- **Outer archive:** reads the end-of-central-directory record (and ZIP64 when present) through `ImportSource.read`, lists the members, and reads each member's local header to find its data.
- **Methods:** stored (0) and deflate (8). Any other method is skipped as unsupported.
- **A streaming raw-deflate inflater (`InflaterInput`) in common code**, not expect/actual. A nested zip has to be inflated a piece at a time, and the browser's `DecompressionStream` can't say when its output for a given input is complete. One common implementation behaves the same on every platform, and its tests run on the JVM against `java.util.zip`. It reads no further than its data ends, pushing back peeked bytes, so a streamed zip finds its next header. Gzip keeps the platforms' zlib (`Gzip.kt`).
- **Limits:** the existing `MAX_UNZIPPED_BYTES` applies per member. New caps are 10 GB total uncompressed and 100,000 members per archive. Data past them is skipped with a `TooLarge` reason.
- **Nested zips** (Garmin's `UploadedFiles_*.zip`) are **streamed**, not random-accessed:
  - The member is inflated as a stream, and its local file headers are walked in order.
  - Deflate data ends itself, so a data descriptor never stops the walk.
  - A *stored* entry with a data descriptor (unknown size) inside a nested zip is skipped as unsupported.
  - This avoids spilling inner zips to temp files, which the web lacks.
- **Gzip** detection is unchanged. A `.gz` member is gunzipped after it is read out of the zip.
- **Alternative rejected:** a zip library such as okio-zip or kotlinx-io. None covers wasmJs with random access, and the repo already wraps each platform's zlib.

### 3. Entries are addressed, not held
A scan walks every source and yields `ScanEvent`s (`Progress`, `Read(candidate)`, `Skipped(name, reason)`) as a `Flow` on `Dispatchers.Default`.

**Addressing:**
- Each activity file is an `EntryRef`: the source index, the path through any nested archives, and the file's name.
- Each activity is an `ImportCandidate`, built by the presenter from the scan's `Read` (it notes the detail categories the activity holds, which needs `core/data`). It holds:
  - its `EntryRef`
  - its index within that file (a logbook's dive number in file order)
  - `type`, `startedAt`, `elapsed`, `localStart`, `distanceMeters`, `dive?.maxDepth`, `device` and `title`
  - the file's format and its stored-size estimate, for the 10 MB notice

**Re-reading:**
- `ActivityFileReader.readFile` still decodes each file in full. The scan maps each activity to a candidate and drops it, so peak memory is one file's activities.
- The preview sheet and the save path re-read an entry with `BatchReader.read(ref)`.
- Re-reads during save go in source and archive order, so one pass over a nested zip serves every entry it holds in the chunk.

**Alternative rejected:** a summary-only decode mode in each parser and FIT SDK. It's faster, but it touches six decoders on three SDKs. Full decode per file is already fast enough for a scan in the background.

### 4. Export metadata
`ExportDetector` looks at a top-level archive's member names first:
- **Strava:** `activities.csv` plus an `activities/` prefix.
- **Garmin:** `DI_CONNECT/DI-Connect-Fitness/*summarizedActivities*.json` plus `DI_CONNECT/DI-Connect-Uploaded-Files/*.zip`.

It reads the metadata file before the scan. The result is an `ExportMetadata` with a lookup by `EntryRef`, applied to each candidate as it's read.

**Strava:**
- A small RFC 4180 CSV parser handles quoted fields, embedded newlines and doubled quotes. Columns are found by header name, and the first one wins where Strava repeats a header.
- **Keyed by** the `Filename` column (`activities/123.fit.gz`).
- **Fields:** `Activity Name` becomes the title, `Activity Description` the description, and `Activity Type` maps through a `StravaTypes` table.
- **Privacy** comes from a visibility column when the export has one (`everyone`, `followers_only`, `only_me`). Otherwise it comes from the boolean private column. With neither, the batch's visibility applies.

**Garmin:**
- **Parsing:** the JSON is parsed with a plain `kotlinx.serialization` `Json { ignoreUnknownKeys = true }`. This is not `AtJson`, which is only for lexicon data.
- **Matching:** by `activityId` when the FIT file's name ends `_<id>.fit`. Otherwise by start time (±60 s) and duration (±5 %).
- **Fields:** `name`, `activityType.typeKey` (mapped through a `GarminTypes` table) and `privacy.typeKey` (`private`, `subscribers`/`followers`, `public`).

**Applying it:**
- Metadata sets the candidate's title, description and type, plus an `exportVisibility`.
- At save, the activity's visibility is the narrower of `exportVisibility` and the batch's. Categories clamp to it as `DetailCategory` already does.

**Unknown columns or keys:** the metadata gives nothing for that field, and the activity's file decides.

### 5. One batch state for every format
- **Contract renames:**
  - `DiveBatch` becomes `ImportBatch`, and `DiveRow` becomes `ImportRow`.
  - The new fields are `type`, `distance`, `fileName`, `month`, and an optional `dive: DiveRowExtras(maxDepthMeters, diveNumber)`.
  - Events become `ItemToggled(id)`, `AllShownSelected(Boolean)`, `FilterChanged(BatchFilter)`, `ItemOpened(id)`, `ItemTitleChanged`/`ItemTypeChanged`, `ZoneChanged(sourceId, zone)` and `SaveBatch`.
- **Item ids:** each row's id is its position in the batch's oldest-first order.
- **Selection state:** `DiveSelection` becomes `BatchSelection`: the deselected ids, per-id `DuplicateAction`, per-id title and type overrides.
- **Zones:** each zone-less source's chosen zone is kept separately. A zone change re-derives only that source's candidates' starts, and reruns matching.
- **Lazy list:** rows are an `ImmutableList` in a `ReadableList` with month headers. Filters and counts are derived getters on `ImportBatch`. For thousands of rows, the presenter keeps the full list and the UI lays out lazily. No paging is needed, because each candidate is small.
- **Preview sheet:** an `ImportItemPreview` in state, which is null until a row is opened.
  - The presenter re-reads the entry, builds the same `ImportPreview` the single-file form uses, and renders it read-only in a sheet.
  - It adds a title `TextFieldState` and an `ActivityTypeField`.
  - Closing the sheet copies the overrides into `BatchSelection`.
- **A single activity** (one candidate in total) still takes the existing single-file path, unchanged.

### 6. In-batch duplicates and a complete log search
- **Matcher input:** `ActivityMatcher.match` takes a `List<MatchSpan>` (start, elapsed, `localStart`) instead of `ImportedActivity`.
  - `ImportCandidate` and `ImportedActivity` both provide one.
  - `DuplicateOf.InFile` becomes `InBatch(index)`, and the notice names the earlier item's file.
  - Matching sorts by start and only compares spans within 14 h of each other. That makes it O(n log n + k) instead of O(n²) over thousands.
- **Log search:** `rememberOwnActivities` becomes a batch search.
  - It groups the candidates' padded spans (±14 h) into windows of at most 31 days, merging overlapping ones. Only windows that hold candidates are queried.
  - Each window calls `ownActivitiesStartedBetween`. When a window returns `MAX_RANGE_PAGES` full pages, it's halved and re-queried, so nothing is cut off silently.
  - Any window failure ends with `DuplicateCheck.Failed`, which the screen already words, and keeps what was found.
- **Alternative rejected:** a new AppView query for "my activities overlapping these spans". It's cleaner, but it adds a lexicon and AppView change for a client-side need the existing query covers.

### 7. Chunked save with one publish session
- **Queue-only variant:** `ActivityRepository` gains `publishBatch(drafts: Flow<ActivityDraft>): List<AtUri>`.
  - It runs `privacy.withReadOnce` and `zones.withTrainingReadOnce` once around the whole flow, queueing each draft as it arrives.
  - Each draft is checked against the session's scopes as it arrives; a refusal stops the batch with those before it queued.
  - With `drain = false` it leaves sending to the next `sync` (the feed the screen returns to runs one), so saving finishes when everything is queued, not uploaded. The repository has no scope of its own to drain in; background draining is `paced-outbox`'s.
  - `publishAll` becomes `publishBatch(drafts.asFlow())`. This keeps the `profile/privacy-zones` batch requirements, with one read per batch.
- **Building drafts:**
  - The presenter builds the flow by re-reading entries in source order, in chunks of 25 activities, mapping each through `importDraft`.
  - Each draft gets `DraftSource(sourceFor(ref), entry)`. `sourceFor` calls `SourceFile.prepare` once per `EntryRef` and caches the key, so a file shared by several activities is stored once.
- **Combine groups** are formed before saving from the matcher's results, as `rootOf` does now. They're saved first, through `ActivityManager.combine` per group (chunked by `CombinedActivity.MAX_MEMBERS`), because combining drains the outbox: done after the bulk, it would wait on the whole batch's upload. The rest then go through `publishBatch`.
- **Progress and failures:** the screen shows "Saving n of m". An entry that fails to re-read is skipped and counted, and the rest continue.
- **Alternative rejected:** build every draft first and then `publishAll`. All drafts with their samples, tracks and source bytes would be in memory at once.

### 8. Where code lives
- **`:core-activityfile`:** `ImportSource`, `ZipArchive`/`ZipStream`, `InflaterInput`, `BatchReader` (scan and re-read), `ExportDetector`, the Strava CSV and Garmin JSON readers, `StravaTypes` and `GarminTypes`.
- **`:core-data`:** `ActivityMatcher` (`MatchSpan`, `InBatch`) and `ActivityRepository.publishBatch`.
- **`feature/record`:**
  - **`-ui`:** the `PlatformFile` → `ImportSource` adapters, as `expect`/`actual` for web versus the rest.
  - **`-presenter`:** `BatchSelection`, the batch search and the save flow.
  - **`-api`:** the contracts.
- **`:app-fixtures`:** `ImportBatchSamples` for small, mixed, export, all-duplicates, reading and saving.

## Risks / Trade-offs

- **[Outbox stores every original's bytes in Room until uploaded]**
  - At roughly 200 KB per `.fit.gz`, 2,000 activities is about 400 MB, and GPX-heavy exports more.
  - → The batch form shows the total size of the originals before saving.
  - → `paced-outbox` will move blobs to files or read them lazily.
  - → The 10 MB per-file rule still drops outliers.
- **[Upload of a full export runs into PDS rate limits]**
  - Each activity is 5–7 writes plus blobs. Today's retry on 429 keeps them queued, but uploads only while the app is open.
  - → The save summary says uploads continue while the app is open.
  - → `paced-outbox` is the planned follow-up.
- **[Strava and Garmin export layouts change, or differ by locale]**
  - Columns and keys are found by name, and missing ones are ignored, so the worst case is file-derived titles.
  - → Tests use synthetic exports built from the documented layouts.
  - → A task verifies the readers against a real export of each before archive.
- **[Leaving the screen mid-save]**
  - Drafts queued so far stay queued, and the rest aren't saved.
  - → Re-picking the export flags the saved ones as duplicates (`feed/import-duplicates`), so the athlete can finish without doubles.
- **[A picked file becomes unreadable between scan and save]** (deleted, or URI permission lost)
  - → Its entries are skipped and reported, per the spec.
- **[Large CSV/JSON metadata]**
  - Thousands of rows, a few MB, are held in memory.
  - → This is acceptable, and far smaller than the activity data.

## Migration Plan

- No lexicon, AppView or stored-data changes.
- Renaming the `DiveBatch` contracts is internal to `feature/record` and `:app-fixtures`. Screenshot references for the logbook states are regenerated.
- Rollback is a revert.

## Open Questions

- **Exact Strava privacy column name(s) and values, and Garmin's privacy key, in current exports.** These can be settled when verifying against real exports. The readers already treat them as optional.
