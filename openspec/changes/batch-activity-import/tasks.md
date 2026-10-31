# Tasks

Each group lands its own tests. Test fixtures are synthetic files written by `FitWriter`/`TcxWriter`/`GpxWriter` and zipped in the test, never real activity files (`scripts/check-public-safety.sh`).

## 1. Archives in `core/activityfile`

- [x] 1.1 Add the `ImportSource` interface (name, size, `read(offset, length)`) and an in-memory `ByteArrayImportSource` for tests and the single-file path.
  - Verify: `:core-activityfile:jvmTest` compiles and a small test reads ranges.
- [x] 1.2 Add a streaming raw-deflate inflater (`InflaterInput`, RFC 1951) in common code, reading no further than its data ends (design §2).
  - Its output is capped by `readAll(maxBytes)`.
  - Verify: `InflateJvmTest` inflates `Deflater(nowrap)` output at every level and strategy, rejects truncated and corrupt data, enforces the cap and leaves trailing bytes unread; `InflateTest` inflates each platform's own deflate, run on JVM and `:core-activityfile:iosSimulatorArm64Test`.
- [x] 1.3 Implement `ZipReader` over `ImportSource`:
  - the end-of-central-directory record and ZIP64
  - local headers
  - stored and deflate members, others reported unsupported
  - per-member, total and member-count limits (design §2)
  - Verify: jvmTests on zips built with `java.util.zip.ZipOutputStream`: stored, deflated, ZIP64 (forced), an unsupported method, a bomb past the member cap, and too many members.
- [x] 1.4 Add streaming reading of a nested zip by local headers, including deflate members with data descriptors, and skipping stored members with descriptors.
  - Verify: a jvmTest reads a zip-in-zip whose inner zip uses data descriptors.

## 2. Scan and re-read

- [x] 2.1 Add `EntryRef`, `ScanEvent` (`Progress`, `Read`, `Skipped` with an unsupported, damaged or too-large reason), `ImportedActivity.summary()` and `BatchReader.scan(): Flow<ScanEvent>`.
  - Each source and member is opened as plain, gzip or zip, recursing.
  - Each activity file is decoded with `ActivityFileReader.readFile`. `Read` hands over its full activities; the presenter keeps a summary candidate of each (it needs `core/data` to note the detail categories each holds), so peak memory is one file.
  - Members that aren't activity files are counted and skipped without a reason row.
  - Verify: jvmTests with a zip of FIT, GPX, a `.fit.gz`, a UDDF logbook, a truncated FIT and a `readme.txt` give the right reads, skipped list and counts.
- [x] 2.2 Add `BatchReader.read(ref)` and `readEach(refs)`, which reads in source order and makes one pass per nested archive.
  - Verify: re-read files are byte for byte as found (still gzipped where they were), and a test shows reading ten entries of one nested zip reads it less than twice over.
- [x] 2.3 Make the scan cancellable and report progress (files done and activities found).
  - Verify: a test cancels mid-scan and no further events arrive.

## 3. Account export metadata

- [x] 3.1 Add an RFC 4180 CSV reader (quoted fields, embedded newlines, doubled quotes, header lookup with the first column winning).
  - Verify: unit tests for each case.
- [x] 3.2 Add `ExportDetector` and the Strava reader:
  - `activities.csv` keyed by `Filename`
  - name, description and type through a `StravaTypes` table
  - privacy from a visibility or private column when present (design §4)
  - Verify: jvmTests on a synthetic Strava export zip: titles and descriptions applied, unknown type kept from the file, a private row gives Only me, a missing privacy column gives none.
- [x] 3.3 Add the Garmin reader:
  - summarized-activities JSON with a plain `Json { ignoreUnknownKeys = true }`
  - matching by `_<id>.fit` file name, else start ±60 s and duration ±5 %
  - `GarminTypes`
  - privacy
  - Verify: jvmTests on a synthetic Garmin export (outer zip, inner `UploadedFiles_*.zip`, JSON) cover the id match, the time match and no match.
- [x] 3.4 Apply the metadata to candidates during the scan (title, description, type, `exportVisibility`).
  - Verify: `ExportMetadataJvmTest` scans synthetic Strava and Garmin exports through `BatchReader` and sees each activity's export entry on its `Read` (as `AccountExportActivity`).

## 4. Matching and log search in `core/data` and the presenter

- [x] 4.1 Change `ActivityMatcher.match` to take `MatchSpan`s:
  - `ImportedActivity` and `ImportCandidate` provide them
  - `DuplicateOf.InFile` becomes `InBatch`
  - a sorted sweep only compares spans within 14 h
  - Verify: the existing `ActivityMatcher` tests pass unchanged in meaning, plus a cross-file pair and a 5,000-span performance test that finishes in well under a second on JVM.
- [x] 4.2 Turn `rememberOwnActivities` into a batch search:
  - padded spans merged into windows of at most 31 days, querying only windows that hold candidates
  - a window that hits `MAX_RANGE_PAGES` is halved and re-queried
  - a failure gives `DuplicateCheck.Failed` and keeps what was found
  - The windowing is `FeedRepository.ownActivitiesAround` in `core/data` (over `ownActivitiesInRange`, which reports truncation); `rememberOwnActivities` calls it with the batch's `MatchSpan`s.
  - Verify: `OwnActivitySearchTest` (`core/data`, over the range-search MockEngine harness): a three-year batch finds a logged activity in each year with one query each, a dense window splits, a failing window ends incomplete keeping what was found, and windows merge up to 31 days; the record presenter's 82 tests still pass.

## 5. `publishBatch` in `ActivityRepository`

- [x] 5.1 Add `publishBatch(drafts: Flow<ActivityDraft>)`:
  - one `privacy.withReadOnce` and `zones.withTrainingReadOnce` around the flow, queueing each draft as it arrives
  - drain started after the last, without waiting for it
  - `publishAll` re-expressed through it
  - Each draft is checked as it arrives (a refusal leaves earlier ones queued); `drain = false` leaves sending to the next `sync`.
  - Verify: the existing publishAll and privacy-zone batch tests pass; `publishBatchQueuesEachDraftAsItArrivesWithoutSending` sees each draft queued before the next and nothing sent until `sync`; `aBatchReadsTheTrainingOnceAcrossItsChunks` reads the training once for six drafts built as the flow goes.

## 6. Batch contracts, samples and UI

- [x] 6.1 In `feature/record/api`, rename `DiveBatch`/`DiveRow` to `ImportBatch`/`ImportRow` with the fields of design §5:
  - type, distance, file name, month, optional `DiveRowExtras`
  - skipped files, filter, counts and an originals-size total
  - `ImportItemPreview`
  - the new events (`FilesPicked(ImmutableList<ImportSource>)`, `ItemToggled`, `AllShownSelected`, `FilterChanged`, `ItemOpened`, `ItemTitleChanged`, `ItemTypeChanged`, `ZoneChanged(sourceId, zone)`, `SaveBatch`, `CancelReading`)
  - reading and saving progress
  - Verify: `./gradlew :feature-record-api:composeStabilityCheck` passes.
- [x] 6.2 Update `:app-fixtures` `ImportActivitySamples` with the batch samples: logbook, mixed files, Strava export with skipped files, all duplicates, filter on duplicates, reading progress, saving progress, and preview sheet open.
  - Verify: `ContentLoadingConformanceTest` passes.
- [x] 6.3 Build the batch form UI from `DiveBatchForm`:
  - month-grouped `ReadableList` rows with type, start, duration, distance or depth, and device
  - filter chips, select or deselect shown
  - per-source zone notices, skipped-files section, originals-size and large-original notices
  - Save with "Saving n of m"
  - Verify: a `runComposeUiTest` in `feature/record/ui/src/jvmTest` toggles a row and filters by tag.
- [x] 6.4 Build the read-only preview sheet from the single-file preview pieces (map, stats, charts) with only title and type fields.
  - Verify: a UI test shows no visibility, gear or description fields in the sheet.
- [x] 6.5 Add the `PlatformFile` → `ImportSource` adapters (expect/actual: source and skip off the web, `Blob.slice` on web), and switch the picker to `FileKitMode.Multiple()`.
  - Verify: the desktop app picks two files and a zip in a manual run (task 8.2).
- [x] 6.6 Add the screenshot files for the new batch samples and regenerate the logbook references.
  - Verify: `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite`, then review the changed references, then `:androidApp:testDebugScreenshotTestDefaultTestSuite` passes.

## 7. Batch presenter

- [x] 7.1 Generalize `DiveSelection` to `BatchSelection`:
  - deselected ids, duplicate actions, title and type overrides
  - select or deselect all applying to the shown filter only, duplicates left alone as today
  - Verify: unit tests for each rule in the `feed/batch-import` "Activities can be declined" scenarios.
- [x] 7.2 Wire the scan into `ImportActivityPresenter`:
  - `FilesPicked` launches `scan` with `launchLatest`, `CancelReading` cancels it
  - a single candidate in total falls back to the existing single-file path via `BatchReader.read`
  - two or more build `ImportBatch`
  - Verify: presenter tests cover a single file staying on the preview form, two files giving a batch, and cancel returning to picking.
- [x] 7.3 Add per-source zones: a zone change re-derives that source's candidates' starts and reruns matching.
  - Verify: a test with two zone-less dive logs moves only one log's dives.
- [x] 7.4 Add the preview sheet: `ItemOpened` re-reads the entry and builds the read-only preview, and closing it keeps the title and type overrides on the row.
  - Verify: a test changes a GPX row's type to Hike and sees the row update.
- [x] 7.5 Implement save:
  - order by combine group, then re-read in chunks of 25 in source order
  - build drafts with `importDraft` using overrides, export description and visibility clamped to the batch (`exportVisibility`)
  - `DraftSource` from one `SourceFile.prepare` per `EntryRef`
  - `publishBatch`, then `ActivityManager.combine` per group
  - skipped re-reads counted, with progress and a summary
  - Verify: presenter tests over `SessionTestFixture`:
    - eight of ten saved after declining two
    - a private Strava row saved Only me in a Public batch
    - three FIT files give three sources uploaded once each
    - a zip-member source holds the member's bytes and name
    - a watch and Edge pair combined from two files
    - a vanished source reported as skipped
- [x] 7.6 Move the existing `ImportLogbookPresenterTest` and `ImportCombinePresenterTest` cases onto the renamed contracts.
  - Verify: `./gradlew :feature-record-presenter:jvmTest` passes with no fewer cases than before.

## 8. Docs and integration

- [x] 8.1 Update `.agents/rules/activity-files.md` (archives, scan and re-read, export metadata, `publishBatch`, `InBatch`, batch search) and `AGENTS.md` if a command or module changes.
  - Verify: `scripts/check-agent-sync.sh` passes.
- [x] 8.2 Run an end-to-end check on the local stack (`scripts/dev.sh --seed alice`, `:desktopApp:hotRun`):
  - Pick two FIT files plus a synthetic Strava export zip and a Garmin export zip.
  - Check rows, skipped files, an in-batch duplicate combined, a declined item, a type override and a private item.
  - Confirm the AppView shows the saved activities with sources.
  - Re-pick the Strava export and see every activity flagged as already logged.
  - Verify: screenshots from the hot-reload MCP and the activity counts.
- [ ] 8.3 Verify the Strava and Garmin readers against a real export of each, on the developer's machine and never committed.
  - Fix the column and key names, and close design.md's open question.
  - Verify: the export's titles and privacy appear on its rows.
- [x] 8.4 Run `./gradlew check`.
  - Verify: it passes.

## Workflow follow-up

- Propose `paced-outbox` (rate-limit pacing, background drain, upload progress) as the next change.
- Archive this change after review.
