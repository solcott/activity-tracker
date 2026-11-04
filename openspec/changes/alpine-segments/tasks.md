# Tasks

## 1. Lexicons and the Kotlin mirror

- [ ] 1.1 Add `alpine` to the sport group `knownValues` in `feed/segment.json`, `importer/defs.json`, `feed/checkSegment.json`, `feed/searchSegments.json` and `feed/defs.json#segmentViewBasic`. Add `snowboard` to the board `knownValues` in `getSegmentLeaderboard.json` and `getSegmentMayors.json`. Verify with `lexicon-auditor` on these files.
- [ ] 1.2 Add an optional open-enum `kind` (`run` | `lift`, absent = `run`) to `feed.segment`, `importer.defs#candidate` and `feed.defs#segmentViewBasic`, with descriptions saying lifts are `alpine`-only and importer-only. Mirror it in `core/lexicon`: a `SegmentKind` constants object, nullable `kind` on `SegmentRecord`, the candidate model and `SegmentViewBasic`. Verify that an `AtJson` round-trip test keeps an absent kind absent.
- [ ] 1.3 Add `SegmentSportGroup.ALPINE` to `all`, with the 200 m minimum and a 120 km/h cap (`maxSpeedMmPerS`). Add `passTolerance(group, kind)` (40/50 m for an alpine run, 25/30 m otherwise). Map resort `ski` (not backcountry, not indoor) and `snowboard` to `alpine` in `SegmentSport.of`, update its KDoc, and add `Sport.SNOWBOARD` to `SegmentBoard.all`. Verify with `SegmentSport` tests in `:core-lexicon:jvmTest`: resort ski → alpine, backcountry → ski, indoor → none, snowboard → alpine with board `snowboard`.

## 2. AppView storage and indexing

- [ ] 2.1 Add `V49__segment_kind.sql`: `kind` not null default `'run'` on `segments` and `segment_candidates`, plus a `track` rematch enqueue like V43's. Add the columns to `Tables.kt`. Verify that `:server-appview:test` migrates on H2.
- [ ] 2.2 In `SegmentRecordIndex`: store `kind`, drop an unknown kind, drop `lift` outside `alpine` or from any account but `SEGMENT_IMPORTER_DID`, and make `duplicatesOf`/`recheckDuplicatesOf` compare within one sport group and kind. Treat a kind change like a sport-group change. Verify with indexer tests for each scenario in the `segment-records` and `segment-lifts` deltas (athlete lift dropped, lift in `hike` dropped, run beside a lift not a duplicate, record with no kind indexed as `run`).
- [ ] 2.3 In `api/SegmentCandidateViews.kt`, stage and validate the candidate `kind` (reject a non-alpine lift), and check duplicates by kind in `checkSegmentCandidates`. Make a kind change unride the candidate, and return `kind` from `getRiddenSegmentCandidates`. Verify with candidate tests for the `segment-candidates` delta scenarios.
- [ ] 2.4 Serve `kind` in every segment view (`getSegment`, search, efforts, leaderboards, Mayors, starred). Verify with a view test that an effort on a lift has `kind: lift`.

## 3. AppView matching, ranking and naming

- [ ] 3.1 In `SegmentMatcher.replaceEfforts` and `replaceCandidateRiders`, pass `SegmentSportGroup.passTolerance(group, kind)` to `SegmentGeometry.passes`. Verify with matcher tests: an alpine track 45 m off a run's line matches, the same offset from a lift doesn't, and a top-down pass of a lift doesn't. Use the resort-day shape from `FitFixtures`.
- [ ] 3.2 Exclude lifts in `rankable()`, and give lift efforts no `best`, `rank` or `notRanked` in `activitySegments`. Verify with tests that a lift's leaderboard and Mayors return no entries, that an active `alpine` run's board lists `ski` and `snowboard` types, and that a 144 km/h alpine effort is unranked.
- [ ] 3.3 Require kind `run` in `mayPropose()`, and skip lifts in `SegmentNaming`'s pass. Verify with a test that a proposal on a lift ridden on 20 dates is ignored and `canProposeName` is false.

## 4. Segment importer

- [ ] 4.1 In `TrailTags`, make `piste:type=downhill` ways an `alpine` trail (falling back to the `piste:name` name), make `route=piste` + `piste:type=downhill` relations `alpine`, and have `directions()` return forward only for `alpine`. Verify with `TrailTagsTest` cases for each `trail-candidates` delta scenario (piste name, area piste skipped, downhill route, drawn direction only).
- [ ] 4.2 Add the lift pass: qualifying named `aerialway` ways become one whole-line, forward-only `alpine` candidate of kind `lift`, with the existing id scheme. The trail joining, junction cutting and generic-word name trimming steps don't apply to them. Verify with tests covering a chairlift, a lift sharing a node with a piste, a zip line skipped, a 120 m rope tow dropped, and the name "Peak Gondola" kept.
- [ ] 4.3 Carry `kind` through `Candidate`, the candidates file (bump `IMPORTER_VERSION`), `stage`, `publish` (the duplicate check by kind, the kind written on the segment) and `refine` (keep the kind). Make de-duplication between candidates compare within one kind. Verify with `:server-segment-importer:test`: an old-version file is refused, a published lift record has `sport: alpine, kind: lift`, and a lift and a run on one line are both kept.
- [ ] 4.4 Update the importer section of `README.md` and the segment-import comments in `CLAUDE.md` to mention downhill pistes and lifts. Verify that `scripts/check-agent-sync.sh` passes.

## 5. Clients

- [ ] 5.1 In `feature/detail`, split the activity's efforts by kind. Add a lift aggregation in `-api` (one row per lift: name, ride count, total elapsed time, first-ride order) with unit tests, and a Lifts card in `ActivitySegmentsUi` with no PR or rank. Add its strings (plurals for the count) to `:ui-resources`. Verify with presenter tests and a UI test for a ski day with runs and lifts, and with a run-only day showing no Lifts card.
- [ ] 5.2 On the segment screen, hide the leaderboard, Mayor and naming sections for `kind == lift`, and label the sport group `alpine` in `SegmentEffortsUi`. Verify with a UI test that renders a lift sample.
- [ ] 5.3 Check that creation offers `alpine` for a resort ski day and writes no `kind`. Verify with a `CreateSegmentPresenter` test (resort ski day → `alpine`; treadmill run → unavailable).
- [ ] 5.4 Add samples to `:app-fixtures` (ski day with lifts, lift segment), join them to `ContentLoadingConformanceTest` where it applies, add or extend the `androidApp` screenshot files, and rerecord the references with `:androidApp:updateDebugScreenshotTestDefaultTestSuite`. Verify that `:androidApp:testDebugScreenshotTestDefaultTestSuite` passes after reviewing the images.

## 6. Integration

- [ ] 6.1 Run `./gradlew check` and verify that it passes (tests, lint, ktfmt, detekt, `composeStabilityCheck`, `checkModuleBoundaries`).
- [ ] 6.2 End to end on the local stack: run `scripts/segment-import.sh` on a small resort extract with three seeded athletes' ski days, then publish. Verify that the AppView indexes the alpine runs and lifts, the ski day shows a Lifts card and run efforts, the run has a leaderboard, and the lift has none.

## Workflow follow-up

- Archive the change once it is implemented and reviewed (`/opsx:archive`).
