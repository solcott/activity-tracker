# Tasks

## 1. AppView: the importer's segments start active

- [x] 1.1 Add `liveStatus(author, importer)` in `index/SegmentRecordIndex.kt` and use it where a non-duplicate segment gets `PROPOSED` on index/change; verify with a new test in `SegmentRecordsTest` (or `SegmentTestBase`'s suite) that an importer segment indexes `active` and an athlete's `proposed`
- [x] 1.2 Give `recheckDuplicatesOf` an `importer: Did?` parameter and pass it from every caller (indexer, `deleteSegments`, `SegmentMatcher`); verify with a test that deleting the original of an importer duplicate makes it `active`
- [x] 1.3 Pass the importer DID to `SegmentMatcher` from `Application.kt`, and have `updateStatuses` set the importer's `PROPOSED` segments to `ACTIVE` before archiving; verify with `SegmentMatcherTest` cases for a pre-existing proposed importer segment (promoted) and a 181-day-old importer segment (stays `active`)
- [x] 1.4 Verify that an importer segment duplicating an athlete's active segment is still `duplicate`, and that with no importer configured everything starts `proposed`; run `./gradlew :server-appview:test`
- [x] 1.5 Update `.agents/rules/appview.md`'s status sentence and AGENTS.md's segment summary to say the importer's segments start `active`; verify with `scripts/check-agent-sync.sh`

## 2. Importer: `publish --all`

- [x] 2.1 Move `publish`'s per-page body in `Publish.kt` into one function over a page of `SegmentCandidate`s, with ridden pages as one source; verify the existing `PublishTest` still passes
- [x] 2.2 Add the file source: pages of `SegmentCandidates.MAX_BATCH` from `readCandidates`; verify with `PublishTest` cases where an unridden candidate is published, a duplicate is reported, and an unstaged id is reported gone
- [x] 2.3 Parse `publish --region <id> [--all <candidates.ndjson>]` in `Main.kt`, refusing a file whose region differs before signing in, and update `USAGE`; verify with a test for the mismatch and with `./gradlew :server-segment-importer:test`
- [x] 2.4 Document `--all` in `server/segment-importer/README.md`

## 3. Scripts: region presets and fetch

- [x] 3.1 Add `scripts/segment-regions/us-utah.conf` and `us-vermont.conf` (`GEOFABRIK`, `BBOX`, `AGENCIES`, `LANG`); verify each sources cleanly with `bash -n` and prints its values
- [x] 3.2 Write `scripts/segment-fetch.sh <region>`: tool checks, Geofabrik download with `curl -z` to `.part` then move, `osmium tags-filter` when stale, one `ogr2ogr` per agency to `.part` then move, sizes printed and a warning on empty pulls; verify by running it for `us-vermont` twice (the second run skips the extract download) and by `shellcheck`, if installed
- [x] 3.3 Extend `scripts/segment-import.sh`: region-only mode via the preset (error exit 2 without one), `--all` passed to publish, and a `SEGMENT_IMPORTER_HEAP` override read by the `run` task; verify the explicit-input form still works and `scripts/segment-import.sh us-nowhere` exits 2 with the message
- [x] 3.4 Document presets, fetching and `--all` in `server/segment-importer/README.md`, the root AGENTS.md command list and the local-stack skill's importing paragraph; run `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh`

## 4. Import Utah and check the whole

- [x] 4.1 Run `./gradlew check`; verify it passes
- [x] 4.2 With `scripts/dev.sh` up and the importer account in `docker/.env`, run `scripts/segment-import.sh us-utah --all`; verify extract's per-group counts, `Staged N`, and `Published N` with duplicates reported, and review the closure counts for BLM surprises
- [x] 4.3 Verify `searchSegments` anonymously over `-111.9 40.4 -111.7 40.6` (Corner Canyon) returns `active` importer segments, and that the desktop app opens an importer segment (reached from an activity's efforts once segment matching is on) and draws its line on the segment map; the app has no area map of segments
- [x] 4.4 Re-run `scripts/segment-import.sh us-utah --all`; verify it publishes nothing new and refine writes nothing

## Workflow follow-up

- Archive the change with `/opsx:archive` after review.
