# Tasks

## 1. Zones crossed between samples (P8)

- [x] 1.1 In `RouteTrim.kt`, make `trimPieces` find, for each line between consecutive samples, the stretches inside each circle neither end is inside:
  - Use the closest approach in a local flat projection, confirmed with `PrivacyCircle.contains`, then bisect for the entry and exit with `crossing`'s predicate.
  - Merge those stretches with the line's own hidden ends.
  - Emit the shown parts, starting a new piece after each hidden stretch.
  - Update the KDoc of `trim` and `trimPieces`.

  Add `RouteTrimTest` cases:
  - "Sparse samples across a zone": two pieces, whose cut points lie on the circle (within a metre of the radius).
  - A line passing just outside a circle stays one piece.
  - One line crossing two zones gives three pieces.
  - A line from inside one zone crossing a second gets cut at both.
  - `trim` still equals `trimPieces(...).flatten()`.

  Run `./gradlew :core-location:jvmTest --tests "*.RouteTrimTest"`.
- [x] 1.2 Add a `PrivacySettings.pieces` case to the existing privacy-settings tests in `core/data`: a sparse route whose line crosses a zone gives separate pieces. Check that the existing privacy-zone and segment-track tests still pass with `./gradlew :core-data:jvmTest`.

## 2. Streams fetches (E2)

- [x] 2.1 Add `V40__activity_stream_fetch_failures.sql` and its `Tables.kt` object:
  - Columns: `blob_cid`, `author_did`, `origin`, `kind`, `failures`, and `retry_at` (nullable `BIGINT`).
  - Primary key: `(blob_cid, author_did, origin)`.
  - Extend `StreamsIndex`'s cleanup to delete failure rows for CIDs no streams row names.

  Check that `MigrationVersionsTest` and the existing `ActivityStreamsTest` pass.
- [x] 2.2 Make `StreamSource.fetch` stop dropping blobs that are too large (null stays missing, a throw unreachable), in `PdsBlobStreamSource` and the space path, so `ActivityStreamsViews` classifies the bytes. In `ActivityStreamsViews.streams()`:
  - Read the cached and failure rows for all parts in one transaction.
  - Skip a part with a `content` row for its CID, or an `unavailable` row before its `retry_at`.
  - Record each failure as the design describes: content for undecodable or too large, unavailable with a doubling pause from 1 min to 1 h.
  - Delete a CID's failure rows when it's stored.

  Add `ActivityStreamsTest` cases for "PDS down" (repeated views make one fetch; a fetch again after the clock passes `retry_at`), "PDS back", and "Undecodable data" (never fetched again). Use a fake source that counts calls. Run `./gradlew :server-appview:test --tests "*.ActivityStreamsTest"`.
- [x] 2.3 Fetch the remaining parts concurrently, keeping widest-first order. Share an in-flight fetch per `(cid, author, origin)` through a process-wide map of `Deferred`s, removed in `finally`. Add `ActivityStreamsTest` cases with a gated fake:
  - "Two tiers fetched together": both fetches start before either answers.
  - Two concurrent `getActivityStreams` requests for one uncached part make one fetch.
  - A viewer not admitted to a tier still gets nothing of it while a fetch for that part is in flight for another viewer.

  Run the same test command.
- [x] 2.4 Update the streams sentence of `.agents/rules/appview.md`: the data blob is cached by CID, failures are remembered per source with a pause, bad content is never fetched again, and parts are fetched at once. Verify with `scripts/check-agent-sync.sh`.

## 3. Checking candidates in one call (E4)

- [x] 3.1 Add the lexicon `lexicons/app/activitytracker/importer/checkSegmentCandidates.json`:
  - A procedure with input `{ region, ids }` (at most 500 ids) and output `{ candidates: [{ id, duplicates }] }`.
  - Add its `Nsid` constant and a `CheckSegmentCandidates` object in `core/lexicon` `Queries.kt`, following the `lexicon-change` skill.
  - Leave it out of `indexedCollections`: it's a procedure.

  Run the `lexicon-auditor` agent on the new NSID, and `./gradlew :core-lexicon:jvmTest`.
- [x] 3.2 Add `SegmentCandidateViews.check(caller, input)` and its route in `segmentCandidateRoutes`, as the design describes:
  - It refuses anyone but the importer with `NotImporter`, and an invalid region or more than 500 ids with `InvalidRequest`.
  - It answers in one transaction, with `duplicatesOf` and the importer's `segmentServed` filter.
  - It leaves out ids not staged in the region.

  Add `SegmentCandidatesTest` cases:
  - A candidate covering an active segment lists its URI.
  - One covering nothing lists none.
  - An unknown id is left out.
  - "Check by another account" is refused.
  - 501 ids are refused.

  Run `./gradlew :server-appview:test --tests "*.SegmentCandidatesTest"`.
- [x] 3.3 In `Publish.kt`:
  - Check each page with one `checkSegmentCandidates` call, then walk the page in order.
  - Duplicates go to `duplicates`, as now.
  - A candidate the check didn't return goes to a new `gone` list, which `publishRegion` counts in its summary.
  - The others go through `exists` and `create`, as now.

  Teach `FakeServices` the procedure, with a call counter. Add `PublishTest` cases:
  - "Many known duplicates": 300 duplicates make one check call and write nothing.
  - "Duplicate no longer live": publishes once the fake drops the live segment.
  - A candidate missing from the check's answer isn't published and is reported as gone.

  Run `./gradlew :server-segment-importer:test`.
- [x] 3.4 Update `.agents/rules/appview.md` (the candidates bullet and the Routes bullet's list of importer procedures, which write nothing) and the importer sentence of `AGENTS.md`'s architecture section to name `importer.checkSegmentCandidates`. Verify with `scripts/check-agent-sync.sh`.

## 4. Integration

- [x] 4.1 Run `./gradlew check` and confirm it passes. If the full run runs out of memory on `:webApp:compileDevelopmentExecutableKotlinWasmJs`, rerun that task on its own and record that it passes.

## Workflow follow-up

- Mark P8, E2 and E4 fixed in `docs/reviews/2026-10-23-codebase-review.md` with the commit ids.
- Archive the change with `/opsx:archive` once it's reviewed.
