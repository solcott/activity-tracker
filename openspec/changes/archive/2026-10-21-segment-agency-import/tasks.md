# Tasks

## 1. Agency data and fixtures

- [x] 1.1 Download current `TrailNFS_Publish` (USFS) and `NPS_Public_Trails` (NPS), convert a small area of each with the README's `ogr2ogr -f GeoJSONSeq` command, and check the design's field table (names, use values, status and display values, feature keys) against them; fix the table in design.md if anything differs. Verify: the table matches a sample feature from each file.
- [x] 1.2 Add hand-written fixtures `usfs-trails.geojsonl` and `nps-trails.geojsonl` under `server/segment-importer/src/test/resources`, using the real field names: a Forest Service trail split into milepost features, a wilderness trail without bikes, a capitalised name, a feature without `TRAIL_CN`, a MultiLineString, a park trail not for public display, a planned one, a ski trail, and one trail that duplicates a way in `trails.osm`. Verify: every line parses as a GeoJSON Feature (`jq -c . < file`).

## 2. Lexicons and AppView

- [x] 2.1 Add optional `attribution` (maxLength 1000, maxGraphemes 100) to `lexicons/.../feed/segment.json` and `importer/defs.json#candidate`, and to `SegmentRecord` and `SegmentCandidate` in `core/lexicon`. Verify: `./gradlew :core-lexicon:jvmTest` passes with a round-trip test of each with and without it.
- [x] 2.2 Add `segment_candidates.attribution` in the next free Flyway migration (V33 unless taken), store it in `putSegmentCandidates` with validation (blank kept as none, over 100 graphemes rejected) and return it from `getRiddenSegmentCandidates`. Verify: new `:server-appview:test` cases for an agency candidate staged and listed with its attribution, and one rejected for a 150-grapheme attribution.
- [x] 2.3 When indexing a `feed.segment`, drop a blank or over-long attribution from the stored record instead of refusing the segment. Verify: `:server-appview:test` cases that `getSegment` serves a valid attribution and omits an over-long one while still indexing the segment.
- [x] 2.4 Run the `lexicon-auditor` agent over the two changed lexicons. Verify: it reports no mismatches.

## 3. Client credit

- [x] 3.1 Give `SegmentCredit.Agency` an `attribution: String?`, set it in `SegmentPresenter` only for `source` `agency`. Verify: `SegmentPresenterTest` covers agency with and without an attribution, and an athlete segment carrying one.
- [x] 3.2 Show "From <attribution> trail data" in `SegmentUi` (a new string resource with an argument), keeping the generic text when null. Verify: `SegmentCreditUiTest` cases for both texts pass on `:composeApp:jvmTest`.
- [x] 3.3 Make `docker/scripts/seed-segment.sh --source agency` write the attribution "USDA Forest Service". Verify: seed a segment on the local stack and see "From USDA Forest Service trail data" on the desktop app's segment screen.

## 4. Importer: rules and reading

- [x] 4.1 Move `TrailTags`' group, exclusion, junction and direction decisions behind a `TrailRules` interface that each `OsmWay` names, with `TrailTags` as the OSM implementation; `TrailBuilder` and `CandidateCutter` ask the way's rules. Verify: `ExtractTest` still matches `expected-candidates.ndjson` byte for byte, and existing importer tests pass.
- [x] 4.2 Add `AgencyRules` with the design's USFS and NPS tables, title-casing all-caps names. Verify: an `AgencyRulesTest` covering each spec scenario under the Forest Service and Park Service qualifying requirements, "Closed and hidden agency trails are left out" and "Agency names in capitals are title-cased".
- [x] 4.3 Add the 1 m snapper and `AgencyReader`, streaming GeoJSONSeq into `OsmWay`s with negative point ids, splitting MultiLineStrings and counting skipped features. Verify: an `AgencyReaderTest` on the fixtures checks milepost features join into one trail, a USFS trail ending on an NPS trail is a junction, the MultiLineString gives two lines, and the id-less feature is counted as skipped.

## 5. Importer: candidates, file and commands

- [x] 5.1 Make `idOf` source-aware: the agency key from dataset feature keys and 1e-5° ends, the OSM key unchanged. Verify: `CandidateCutterTest` cases that moving an interior vertex keeps an agency id, moving an end changes it, and the two directions of an agency loop differ; `ExtractTest`'s OSM ids are unchanged.
- [x] 5.2 Put agency candidates first in `CandidateDedup`'s order. Verify: `CandidateDedupTest` cases for "Agency trail over the same OSM trail" and "Different groups kept apart".
- [x] 5.3 Bump the candidates file to version 2 with optional `attribution` and `agencyFeatures`, write each candidate's own source, licence and attribution, and read them in `stage`. Verify: `StageTest` refuses a version 1 file and uploads an agency candidate's attribution.
- [x] 5.4 Change `extract`'s arguments to an optional PBF plus repeatable `--agency usfs=<file>|nps=<file>`, needing at least one input, and report skipped agency features. Verify: `parse` tests for each form, an unknown kind and no input; an `ExtractTest` on `trails.osm.pbf` with both agency fixtures matching a new expected file (written with `UPDATE_EXPECTED=1` and reviewed).
- [x] 5.5 Make `publish` write the candidate's source, licence and attribution instead of OSM's. Verify: `PublishTest` publishes an agency candidate as a record with `source` `agency`, `license` `CC0-1.0` and its attribution.
- [x] 5.6 Document getting and converting agency data in `server/segment-importer/README.md`, the new `extract` usage in `Main.kt`'s `USAGE` and AGENTS.md's command list, and let `scripts/segment-import.sh` pass agency files through. Verify: the README's `ogr2ogr` and `extract` commands run as written on the downloads from 1.1.

## 6. Integration

- [x] 6.1 Run `./gradlew check` and `./gradlew :server-appview:test`. Verify: both pass.
- [x] 6.2 On the local stack, extract a small real area with OSM and a USFS download, stage it, seed three riders over an agency candidate, publish, and open the segment. Verify: the segment screen credits the USDA Forest Service and the record has `source` `agency`.

## Workflow follow-up

- Archive the change once implemented and reviewed, syncing the four segment spec deltas.
- Propose a follow-up for BLM trails and for agency closures dropping OSM candidates (design Non-Goals).
