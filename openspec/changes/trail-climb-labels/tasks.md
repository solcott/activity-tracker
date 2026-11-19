# Tasks

## 1. Lexicons

- [x] 1.1 Add `climb` (knownValues `uphill`, `downhill`) to `feed.segment`, `feed.defs#segmentView` and `importer.defs#candidate`; add `feed.defs#pathOneWay` and `pathView.oneWays`. Mirror them in `core/lexicon` (`Records.kt`, `Views.kt`, `Queries.kt`). Verify with `./gradlew :core-lexicon:jvmTest` and the `lexicon-auditor` agent.

## 2. Importer

- [x] 2.1 In `CandidateCutter`, judge each trail's whole line once and give each non-alpine candidate its own clear climb along travel, else its trail's, else none; add `climb` to `Candidate` and the candidates file. Verify with `CandidateCutterTest` cases: flat piece of a downhill trail → `downhill`; piece climbing against its trail → `uphill`; rolling → none; no terrain → none.
- [x] 2.2 Send `climb` when staging, write it in `publish`, and compare/write/remove it in `refine` in the same write as line, name and `oneWay`. Verify with `StageTest`, `PublishTest` and `RefineTest` (including a second run changing nothing).
- [x] 2.3 Make `scripts/segment-fetch.sh` run `scripts/terrain-fetch.sh <region>`, and document it in `server/segment-importer/README.md`. Verify with `bash -n` and by reading the preset flow.
- [x] 2.4 After cutting, give a one-way candidate still without a climb the climb of the longer one-way candidates of its sport group that follow its OSM ways in its order, when they all agree (`RouteClimbs`). Verify with `RouteClimbsTest`: agreeing routes → their climb; disagreeing → none; other sport, two-way or reversed routes don't count; own climb kept; alpine none.

## 3. AppView

- [x] 3.1 Add `V72__segment_candidate_climb.sql` (nullable `climb`), the column in `Tables.kt`, and store/return it in candidate staging and checks, dropping unknown values. Verify with the candidate tests on H2.
- [x] 3.2 Serve `segmentView.climb` only for the importer's segments with `uphill`/`downhill`. Verify with a `SegmentViews` test: importer segment served, athlete segment not.
- [x] 3.3 Compute `pathView.oneWays` in `pathViews`: per non-alpine sport group whose listed segments on the path are all served one-way, the longest one's `SegmentDirection` from record climb, else served elevation, else heading. Verify with path view tests: all-one-way downhill, one-way ride with two-way run/hike, no climb data → compass, alpine none.

## 4. Apps

- [x] 4.1 Add the `SegmentView` climb helper (served `climb`, else elevation) and use it in `SegmentGroupRow.wayOf` and `GroupSwitcher.of`. Verify with row and switcher tests where served climb wins over flat elevation.
- [x] 4.2 Factor the per-sport one-way wording in `SegmentBrowseTexts` and use it for path rows (from `path.oneWays`, narrowed to the chosen sport) and `PathTexts.summary`. Verify with `SegmentBrowseTextsTest`: "Trail · 4.70 mi · Ride · Downhill only · 21 segments", "Trail · 1.42 mi · Run, Hike · Ride uphill only · 3 segments", unchanged two-way row; and a `PathTexts` summary test.
- [x] 4.3 Add a one-way path sample to `:app-fixtures` for browse and the Trail page; check `iosApp/iosApp/Screens/PathPage.swift` and the browse row draw the shared texts. Update screenshot references (`:androidApp:updateDebugScreenshotTestDefaultTestSuite`) and iOS snapshots where they change.

## 5. Verification

- [x] 5.1 `./gradlew check` passes (via the gradle-runner agent).
- [x] 5.2 Local data: fetch Utah terrain, restart the AppView, re-import `us-utah` (confirm with the user before the long download and the republish). Then in the desktop app, searching "rush" shows "Trail · 4.70 mi · Ride · Downhill only · 21 segments", and the Rush Trail page lists no "Westbound only" or "Northbound only" rows.

## Workflow follow-up

- Sync the delta specs and archive the change once applied (`/opsx:archive`).
