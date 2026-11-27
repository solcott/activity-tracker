# Tasks

## 1. Trimmer core (core/data)

- [x] 1.1 Extract the 0.5 m/s moving-time rule from `RecordingState` and `DecodedActivityMapper` into one shared `MovingTime` helper both call; verify the existing recording and import tests still pass (`:core-data:jvmTest`, `:core-activityfile:jvmTest`, `:core-location:jvmTest`)
- [x] 1.2 Add `KeptSpan`, `TrimInput` and `ActivityTrimmer.trim` (re-based start and streams, dropped samples, clipped laps, dropped lengths, route filtered by time, manual entries); verify with `ActivityTrimmerTest` covering a GPS ride, an indoor ride, a pool swim and a manual entry
- [x] 1.3 Implement the totals rules (additive totals minus the measured cut, averages, maxima and zones recomputed through `StreamAnalytics`, device-only totals scaled by kept moving time); verify with tests that a one-minute trim of a ride changes distance only by the cut part, and that calories scale
- [x] 1.4 Add the distance-to-span mapping (start at the last sample ≤ d, end at the first sample ≥ d); verify with tests that cutting at a distance also drops standing time at that point
- [x] 1.5 Add `ActivityDraft.trimmed(span)` over the trimmer, leaving `source` untouched; verify that a trimmed import draft keeps its source bytes and a trimmed recording draft has re-based GPX times
- [x] 1.6 Add `TrimSuggestion.of(input)` (start standing ≥ 60 s; end standing ≥ 120 s, or a stop of ≥ 180 s followed by a fast short tail); verify with tests for the car-park and drive-home case, a clean run (no suggestion), and an activity with no speed stream (no suggestion)
- [x] 1.7 Add a `canTrim` rule (owner, not combined, not a member, no legs, no dive, no sets); verify with unit tests for each refused kind

## 2. Saved-activity rewrite (core/data)

- [x] 2.1 Extract `ActivityManager.build`'s tail into a private `rewrite` shared by combine, `setSources` and `removeMember`; verify the combine and member tests in `:core-data:jvmTest` still pass unchanged
- [x] 2.2 Add `ActivityManager.loadForTrim(activity)` (drain-or-throw `PendingWritesException`, read `StoredActivity`, the track, the zones and the segment track's marks, so the confirm needs no connection) and `ActivityManager.trim(source, span)` (trim, `rewrite` at the same rkey and visibility, keeping the marks); verify with a test using the fake PDS that the record, the details per tier, the streams, the track GPX and the segment track (with the exclusion and group-left marks kept) are all queued, and that an Only me heart rate stays in its tier
- [x] 2.3 Verify offline behaviour: a test that a trim queued offline drains all its writes on reconnect, and that a trim on an rkey with queued writes throws `PendingWritesException`
- [x] 2.4 Add an AppView test in `:server-appview:test` that a trimmed ride's rewritten segment track re-queues matching and removes an effort that now lies outside the kept range (no production code expected; fix only if it fails)

## 3. Range handles and map cut (shared UI)

- [x] 3.1 Give `AppRangeSlider` labels and value text for its two thumbs (Material's range slider already drags, moves the nearer thumb on a tap and takes arrow keys); the minimum gap is clamped by the presenter; verify with a Compose UI test of the semantics and a keyboard step
- [x] 3.2 Add `cut` to `ActivityMap` (the kept `highlight` in the route colour, the rest dashed in `outline`, markers at the kept ends) on the non-web and wasmJs actuals and `RoutePreview`; verify with an `AppPreview` screenshot test in light and dark

## 4. Trim screen (feature/trim)

- [x] 4.1 Scaffold `:feature-trim-api`, `-presenter` and `-ui` with the `new-screen` skill: `TrimScreen(uri | draftId)`, `TrimState` and `TrimEvent`, and the session-scoped `TrimDrafts` holder in `core/data` (the presenter host passes no pop result); verify that `checkModuleBoundaries` and `checkSwiftExportSources` pass
- [x] 4.2 Implement `TrimPresenter`: build the model with prefix sums, handle moves and exact values with clamping, the mode toggle (distance disabled without distance), readouts, the cut summary, before and after totals, segment-effort notes, applying and undoing the suggestion, reset, the confirm dialog, `ActivityManager.trim` on a saved target and `TrimDrafts.apply` then a pop on a draft, the still-sending error, and load errors with a retry; verify with `TrimPresenterTest` covering each event and the GPS, indoor and manual cases
- [x] 4.3 Add sample states to `:app-fixtures` (GPS loaded, trimmed, distance mode, indoor, manual, suggestion shown, confirming, still sending, load error); verify that `ContentLoadingConformanceTest` passes
- [x] 4.4 Build `TrimUi`: compact column (map, profile card with the toggle and handles, readouts, cut summary, totals) and expanded map-beside-panel, with readouts opening the exact-value sheet; verify with Android screenshot tests for each fixture (`updateDebugScreenshotTestDefaultTestSuite`, then review the references) and a desktop check through Compose Hot Reload
- [x] 4.5 Add strings to `:ui-resources` and run `generateIosResources`; verify that `checkIosResources` passes

## 5. Entry points

- [x] 5.1 Add `ActivityMenuAction.Trim` to `ownerMenu` behind `canTrim`, and its event in `ActivityDetailPresenter` opening `TrimScreen(uri)`; verify with presenter tests that the owner of a ride sees it, and that a combined activity, a member, a dive and a non-owner don't
- [x] 5.2 Add the trim offer to the recording finish form in `RecordState`, with the suggestion summary, opening `TrimScreen(draftId)` and applying the span from `TrimDrafts` on Finish; verify with a `RecordPresenter` test that a finished recording with an applied span publishes a trimmed draft
- [x] 5.3 Add the trim offer to the single-file import preview (a batch import offers none; its activities are trimmed from their pages) (summary, `TrimScreen(draftId)`, and the preview map showing the cut through `ActivityMap(cut = true)`), applying the span on import; verify with an `ImportActivityPresenter` test, and that the queued source is the whole file

## 6. iOS

- [x] 6.1 Build `TrimActivityView` (Swift Charts profile with handles, MapKit cut styling in `ActivityRouteMapView`, readouts, the exact-value sheet, totals, a confirmation dialog) over the exported presenter, and register the screen with the presenter host; verify with snapshot tests on the iOS 26.0 simulator for the GPS, manual and confirming states
- [x] 6.2 Add the Trim case to `ActivityPageMenu.swift` and the trim rows to the native record finish and import views; verify the iOS build passes `checkSwiftExportStubs` and that VoiceOver reads the handles as "Trim start" and "Trim end" with their values

## 7. Integration

- [x] 7.1 Run `./gradlew check` and the iOS `xcodebuild test`; verify both pass
- [x] 7.2 End to end on the local stack (`scripts/dev.sh`): import a FIT with a drive home, apply the suggestion in the preview, import, then trim the start from the page. Verify the PDS records, the self-space track, the segment efforts after rematch, and that downloading the original returns the untrimmed file

## Workflow follow-up

- Archive with `/opsx:archive` once the tasks are done and the specs are synced.
