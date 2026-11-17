# Tasks

## 1. Climb rule and direction names (`core/location`)

- [x] 1.1 Add `SegmentClimb` (`Uphill`, `Downhill`, `reversed()`, `of(gainM, lossM, lengthM)`, with `CLIMB_MIN_M = 15` and `CLIMB_MIN_GRADE = 0.03`) and `SegmentDirection` (climb, else `SegmentHeading`; loops always by turn; words for name suffixes). Verify with `./gradlew :core-location:jvmTest`, with tests for the segment-direction scenarios: Clarks 199/0 over 2287 m, rolling 100/90, short 12 m, unknown elevation, loop.

## 2. Lexicon and AppView

- [x] 2.1 Add optional `oneWay` to `feed.segment`, `feed.defs#segmentView` and `importer.defs#candidate` in `lexicons/`, and mirror them in `core/lexicon` (`SegmentRecord`, `SegmentView`, `SegmentCandidate`), following the `lexicon-change` skill. Verify that `./gradlew :core-lexicon:jvmTest` passes and that the `lexicon-auditor` agent reports no mismatch.
- [x] 2.2 Add the Flyway migration `V66__segment_candidate_one_way.sql` (`segment_candidates.one_way`, boolean, default false). Make `putSegmentCandidates` store the flag and `checkSegmentCandidates` return it. Verify with an AppView test: a one-way candidate is staged and checked back as one-way, and a candidate that doesn't say is not one-way.
- [x] 2.3 Serve `segmentView.oneWay` only when the stored record says `oneWay: true` and its author is `SEGMENT_IMPORTER_DID`. Verify with AppView tests: the importer's one-way segment is served `oneWay: true`, and an athlete's record with `oneWay: true` isn't.

## 3. Shared terrain module

- [x] 3.1 Move `TerrainTiles` and the helpers it needs (`boundedCache`, the `E7` conversion it uses) into a new `server/terrain` module (`:server-terrain`), and make the AppView depend on it. Verify that `./gradlew :server-appview:test` passes, with `TerrainTilesTest` moved alongside, and that `./gradlew checkModuleBoundaries` passes.

## 4. Importer

- [x] 4.1 Make `CandidateCutter` mark candidates given in one direction only (one-way tags or `alpine`) as `oneWay`, carry the flag through the candidates file and `stage`, and have `publish` write `oneWay: true` from the staged candidate. Verify with importer tests: an `oneway:bicycle` path gives one-way ride candidates and two-way run and hike candidates, and a published one-way candidate's record holds `oneWay: true`.
- [x] 4.2 Add `extract --terrain <dir>`, which names whole two-way trails "(uphill)" and "(downhill)" by `SegmentDirection` where tiles cover the whole line, else by compass. Pass it from `scripts/segment-import.sh` when `build/terrain` holds tiles, and document it in `server/segment-importer/README.md` and the commands in `AGENTS.md`. Verify with an importer test that uses a small tile fixture (climbing line, no tiles, partial coverage).
- [x] 4.3 Make `refine` set `oneWay` to its candidate's in the same write as a rename or finer line, and report the change. Verify with importer tests: a flag added to an existing segment, flag and rename in one write, and a second run that changes nothing.

## 5. Shared rows and switcher (`feature/segment/api`, `:ui-resources`)

- [x] 5.1 Give `SegmentGroupRow` per-sport directions (`BothWays`, `OneWay(direction)`, `Unstated`; alpine never one-way), and make `displayName` drop "(uphill)" and "(downhill)". Verify that `SegmentBrowseTextsTest` covers each segment-groups naming and details scenario.
- [x] 5.2 Build the details line in `SegmentBrowseTexts`: one shared wording, or one phrase per distinct direction for mixed groups. Add the strings to `:ui-resources`. Verify that `SegmentBrowseTextsTest` checks "1.42 mi · Ride · Uphill only", "Ride · Eastbound only", "Run, Hike both ways · Ride uphill only" and the unstated case, and that `PathTexts` gives the same text.
- [x] 5.3 Make `GroupSwitcher` name directions by `SegmentDirection` from the shown segment's elevation (the other direction reversed), and add the one-way state. `offered` becomes `options.size > 1 || oneWay`, and `showsSports` is `options.size > 1`. Add `GroupSwitcherTexts.oneWay`. Update `SegmentPresenter` to pass the shown view. Verify with switcher unit tests and a `SegmentPresenterTest` case: a lone Clarks ride segment offers "Uphill only", and a two-way climbing run offers "Uphill" and "Downhill" in fixed places.
- [x] 5.4 Add samples to `:app-fixtures`: a mixed Clarks-style path with a one-way climbing ride and a two-way run and hike, a lone one-way segment page, and a two-way page named by climb. Then run `./gradlew generateIosResources`. Verify that `./gradlew :composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"` passes and that `checkIosResources` is clean.

## 6. Compose UI

- [x] 6.1 In `SegmentUi.GroupChoices`, hide the sport row unless `showsSports`, and show a one-way sport's direction as a single disabled, selected chip. Rows need no change beyond the texts. Verify with `SegmentGroupUiTest`, `SegmentBrowseUiTest` and the path UI tests on the new samples, then update the screenshot references with `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite` and review the changed images.

## 7. Native iOS

- [x] 7.1 In `SegmentPage.swift`'s `GroupChoices`, hide the sport chip unless `showsSports`, and show the one-way direction as a disabled one-segment picker. Browse and Trail page rows take the new texts. Verify with the `SegmentTextTests` and `SearchTextTests` additions, and with snapshot suites recorded on an iOS 26.0 simulator for the lone one-way page and the mixed Trail page.

## 8. Integration

- [x] 8.1 Run `./gradlew check` and the iOS build and tests (`xcodebuild … build` and `test` on iOS 26.0), and verify both pass.
- [x] 8.2 End to end on the local stack:
  - run `scripts/terrain-fetch.sh` for a box around Corner Canyon;
  - re-extract Utah with `--terrain`, then stage and refine;
  - in the app, verify that Clarks' ride row reads "… · Ride · Uphill only", its foot row is named "Clarks - Bikes Uphill Only" with "Run, Hike · Both ways", and the ride segment's page shows a disabled "Uphill only" chip.

## Workflow follow-up

- Commit on main, in small commits by concern, only when the user asks.
- Archive the change with `/opsx:archive` once it is reviewed.
