# Tasks

## 1. Simplifying to a budget (core/location)

- [x] 1.1 Add `TrackMath.simplifyToFit` (start tolerance, max characters, encoder; the tolerance doubles until the encoding fits) for one line and for several pieces sharing one tolerance and budget. Verify with new `TrackMathTest` cases: a line that fits keeps its start tolerance, an over-budget line ends within the budget at no more than twice the smallest tolerance that fits, and pieces share one tolerance.
- [x] 1.2 Add a `TrackMathTest` fixture from the Rush Trail – Tom Sawyer OSM way (229 points) and assert 2 m keeps the switchback apexes, measured as every original point within 2 m of the result. Verify with `./gradlew :core-location:jvmTest`.

## 2. Activity polylines and the owner's map (core/data, app/presenters)

- [x] 2.1 Make `SegmentedPolyline.of` simplify at 2 m with the 50,000-character precision-5 budget (a shared constant mirroring `feed.activity#polyline`'s `maxLength`), so record, details, combined, re-trim and import polylines all get it. Verify with `RouteBreaksTest`/`PrivacyZonesTest` still passing, and a new test that a track with 10 m switchbacks keeps every point within 2 m while breaks and trims are unchanged.
- [x] 2.2 Give the owner's map its own 1 m, unbudgeted line (`OwnRoute` builds its `OwnMap.line` from the track with the owner-map tolerance instead of `ActivityDraft.polylineOf`). Verify with `ActivityDetailRouteTest`/`RouteMapPresenterTest`: the owner's line keeps every track point within 1 m, and another viewer still gets the record's polyline.
- [x] 2.3 Check the import preview (`ImportActivityPresenter`) and `ActivityPage` pick up the published-polyline detail through `ActivityDraft.polylineOf`, and verify with `./gradlew :app-presenters:jvmTest`.

## 3. Rebuilding existing polylines (lexicon, RouteTrimmer)

- [x] 3.1 In `lexicons/app/activitytracker/actor/routeTrim.json`, make `revision` optional ("absent when the athlete has never saved zones") and add an optional integer `lineVersion` ("the polyline detail the polylines were rebuilt at; absent means 0"). Mirror it in `RouteTrimRecord` (nullable, null defaults). Verify with a `RecordSerializationTest` round trip, including a record with neither field, then run the `lexicon-auditor` agent on `actor.routeTrim`.
- [x] 3.2 Add a `LINE_VERSION = 1` constant beside the polyline tolerance, and make `RouteTrimmer.sweepLocked` run when the zones revision differs from the marker's **or** the marker's `lineVersion` is below `LINE_VERSION`, including for an athlete who has never saved zones (trimming by none). Write both fields when it finishes. Verify with new `PrivacyZonesTest` cases (or a `RouteTrimmerTest`): a marker at the current revision with no `lineVersion` rebuilds every tracked activity at every place; an athlete with no zones gets a marker with no `revision` and `lineVersion = 1`; a second sweep writes nothing; a manual entry isn't rewritten; segment tracks aren't rewritten by a detail-only sweep; an interrupted sweep leaves the marker unwritten.
- [x] 3.3 Update the KDoc on `RouteTrimmer` and the `routeTrim` lexicon description, and the privacy-zones section of `.agents/rules/data-and-auth.md`, to say the sweep also runs when the polyline detail changes. Verify that `scripts/check-agent-sync.sh` passes.

## 4. Finer segment candidates (segment importer)

- [x] 4.1 Replace `CandidateCutter.simplified` with `TrackMath.simplifyToFit`, using a 1 m start tolerance and the 100,000-character precision-6 budget, and bump `IMPORTER_VERSION` to 3. Verify with `CandidateCutterTest` (update the expected point counts, and add a switchback case within 1 m), and `StageTest` refusing a version-2 file.
- [x] 4.2 Update `server/segment-importer/README.md` for the 1 m tolerance and the new format version, and verify the documented `extract` command still runs as written.

## 5. Refining published segments (segment importer)

- [x] 5.1 Add a line-closeness check: the discrete Hausdorff distance between two lines resampled every 2 m, with a `REFINE_MAX_M = 6.0` threshold. Verify with unit tests: identical lines give 0; a 5 m-simplified and a 1 m-simplified copy of the same OSM way are within 6 m; a line with a 20 m reroute isn't.
- [x] 5.2 Add the `refine <candidates.ndjson>` command (`Main.kt` parsing, `Refine.kt`). It pages `listRecords` for `feed.segment` in the service account's repo, joins by rkey to the file's candidates, and `putRecord`s the candidate's line with every other field copied when the lines differ and pass the check. It reports refined, unchanged, moved and missing, and stops at the first failed call, as `publish` does. Verify with a `RefineTest` on `FakeServices`: a coarse segment is refined with only `line` changed, a moved one is left and reported, a missing candidate is left, a second run writes nothing, and nothing outside the importer's repo is touched.
- [x] 5.3 Run `refine` after `publish` in `scripts/segment-import.sh` and document it in the importer README and the commands block of `AGENTS.md`/`CLAUDE.md`. Verify with `bash -n scripts/segment-import.sh` and by reading the documented usage.

## 6. Smooth curves on maps (core/location, composeApp)

- [x] 6.1 Add `RouteCurve.smooth` in `core/location`: a centripetal Catmull-Rom curve through a piece's points in local meters, subdividing only bending stretches about every 2 m with a per-stretch cap, offsets from each chord scaled to at most 2 m. Verify with `RouteCurveTest`: every input point appears in order in the output; no output point is more than 2 m from its stretch's chord; a straight line gains no points; a hairpin (and the Tom Sawyer fixture) gains points around its turns; nothing loops back along a stretch.
- [x] 6.2 Draw the curve in `ActivityMap.nonWeb.kt` (route pieces and highlight) and `RoutePreview` (route pieces and highlight), keeping markers, breaks and bounds on the stored points. Verify with `./gradlew :composeApp:jvmTest` and the screenshot tests (update references only after looking at them), and in the desktop app on Rush Trail – Tom Sawyer.

## 7. Integration checks

- [x] 7.1 Run `./gradlew check` (formatting, detekt, screenshot tests; update screenshot references only if a fixture's line changed, after looking at them) and confirm it passes.
- [x] 7.2 On the local stack, run `extract`/`stage`/`publish`/`refine` for the region holding Rush Trail. Confirm that the Tom Sawyer segment's line in the AppView `segments` table has about 69 points, and that its map follows the switchbacks in the desktop app (Compose Hot Reload screenshot).
- [x] 7.3 Open an existing recorded activity in the desktop app after one session's sweep. Confirm that its `feed.activity` polyline was rewritten finer (more points, still trimmed), and that the owner's map and the enlarged map follow its turns.
