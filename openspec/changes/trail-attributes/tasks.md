# Tasks

## 1. Lexicon

- [x] 1.1 Add `trailAttributes` to `lexicons/app/activitytracker/feed/defs.json` with every field, vocabulary and limit from `segments/trail-attributes`. Add optional `trail` to `feed/segment.json`, `feed/path.json`, `importer/defs.json#candidate`, and the segment and path views. Verify: the JSON parses, and the lexicon tests pass.
- [x] 1.2 Mirror the lexicon in `core/lexicon`: a `TrailAttributes` model with open-enum word constants and a surface share type, plus `trail` on `SegmentRecord`, `PathRecord`, the candidate, `SegmentView` and `PathView`. Verify: a round-trip test through `AtJson` in `core/lexicon` commonTest, and `./gradlew :core-lexicon:jvmTest`.
- [x] 1.3 Run the `lexicon-auditor` agent on the changed NSIDs. Verify: it reports no mismatches, apart from the AppView and importer layers still pending.

## 2. Importer: per-way facts

- [x] 2.1 Add `WayFacts` and `TrailRules.facts(tags, group)`. Implement it in `TrailTags` for every OSM mapping in the spec: surface and track type, smoothness, SAC, the three MTB scales with `+`/`-`, piste difficulty and grooming, visibility, width units, lit, dog, horse, wheelchair, seasonal, simple `opening_hours`, operator, and website or `contact:website`. Verify: table-driven `TrailTagsFactsTest`, which includes unknown values and `opening_hours=Mo-Fr 08:00-18:00`.
- [x] 2.2 Keep agency attribute fields in `AgencyReader` under the `agency:` prefix, and implement `facts` for USFS, NPS and BLM with the coded-prefix stripping and value tables from the spec. This covers USFS width midpoints, grade upper bound, horse use and the open dates for the group's use. Verify: `AgencyRulesFactsTest`, using the live values listed in design.md, including `N/A`, `None` and numeric NPS codes.
- [x] 2.3 Work out, once per extract, the `dog`, `horse`, `wheelchair`, `operator` and `website` each way inherits from the named route relations it belongs to, used only where the way lacks the tag and all such relations agree. Verify: a test where the way inherits a relation's website, and one where two relations disagree and the way inherits nothing.

## 3. Importer: combining and carrying

- [x] 3.1 Write `TrailAttributeCombiner` over `(WayFacts, lengthMeters)` with each combining rule from the spec: surface shares rounded down with shares that round to 0% dropped, hardest along 50 m, median width, the lit thresholds, strict dog, open horse, wheelchair, agreeing seasons, longest operator and website, 25% coverage, and the strict exceptions. Verify: `TrailAttributeCombinerTest` covering every scenario in the spec's combining requirement.
- [x] 3.2 Have `CandidateCutter` give each `Candidate` its `trail` from the lengths of its edges along each way. Bump `IMPORTER_VERSION` to 6 and add `trail` to `CandidateEntry`. Verify: `CandidateCutterTest` gives the gravel/MTB 1 candidate and the no-data candidate, and `stage` refuses a version 5 file.
- [x] 3.3 Give each `PathEntry` its `trail`, over all its ways. When an agency path merges into an OSM path, fill in only the attributes the OSM path lacks. Bump `PATHS_VERSION` to 3. Verify: `PathsTest` checks the Long Trail SAC case and the trail class filled in from the agency path, and `publish-paths` refuses a version 2 file.
- [x] 3.4 Send `trail` in `stage`, write it on segments in `publish` (from the check's response) and on paths in `publish-paths`. Make `refine` and `publish-paths` replace or remove `trail` when it differs, comparing decoded values, and report the counts. Verify: `StageTest`, `PublishTest`, `RefineTest` (backfill, unchanged, removed) and the `PublishPaths` tests.

## 4. AppView

- [x] 4.1 Add `V74__trail_attributes.sql` with nullable `trail TEXT` on `segment_candidates` and `paths`, plus the matching `Tables.kt` columns. Verify: `./gradlew :server-appview:test` migrates on H2.
- [x] 4.2 Write `TrailAttributesSanitizer` (vocabularies, ranges, date pairing, operator graphemes, absolute http(s) website). Verify: a unit test that includes `javascript:alert(1)`, a lone `openFrom` and an `mtbScale` of 9.
- [x] 4.3 Store sanitized `trail` in `putSegmentCandidates` and return it from `checkSegmentCandidates`, replacing it on restage. Verify: `SegmentCandidatesTest` scenarios (with attributes, without attributes, restaged).
- [x] 4.4 Serve `segmentView.trail` from the record, only for the importer's segments, and keep `trail` in `PathIndex` to serve it on `pathView`, both sanitized. Verify: `SegmentsTest` (an athlete's segment carries none) and `SegmentPathsTest` (a path view with attributes, website dropped).

## 5. Apps

- [x] 5.1 Add `TrailDetail` rows and `TrailTexts` to `:feature-segment-api`, with every row's wording from the spec. Add the strings to `:ui-resources`, then run `./gradlew generateIosResources`. Verify: a `TrailTextsTest` in `feature/segment/ui` jvmTest covers each value, and `checkIosResources` passes.
- [x] 5.2 Build `trail` rows from the Segment and Trail states (`trailDetails(units)`). The Segment page filters difficulty scales by sport group, and the Trail page shows all scales. Verify: cases added to `SegmentPresenterTest` and the path presenter test (ride shows MTB but no SAC, no `trail` gives no rows).
- [x] 5.3 Draw the Trail details section in the Compose Segment and Trail UIs with `ui/adaptive` wrappers, opening the website row through `LocalUriHandler`. Add sample states to `:app-fixtures` (`SegmentSamples` and the path samples). Verify: the UI tests and `ContentLoadingConformanceTest` pass.
- [x] 5.4 Draw the section natively in `iosApp/iosApp/Screens/SegmentPage.swift` and `PathPage.swift`, opening the website with `openURL` after checking its scheme. Verify: the `xcodebuild` build with the stub check passes, and the iOS snapshot tests are updated.
- [x] 5.5 Add screenshot cases to `SegmentScreenshots.kt` (and the path screenshots) for a segment with attributes, then run `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite`. Verify: the new references look right, and `:androidApp:testDebugScreenshotTestDefaultTestSuite` passes.

## 6. End-to-end verification

- [x] 6.1 Run `./gradlew check`. Verify: it passes, including detekt, ktfmt, stability and module boundaries.
- [x] 6.2 On the local stack (`scripts/dev.sh`), import a small region with `scripts/segment-import.sh` and open a segment and its Trail page on desktop. Verify: the Trail details show surface and difficulty from the region's tags, and `refine` reports backfilled attributes on a second run with an older-published segment.

## Workflow follow-up

- After merging, re-import each published region so `refine` and `publish-paths` backfill attributes on existing records.
- Archive the change with `/opsx:archive` once deployed.
