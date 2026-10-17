# Tasks

## 1. Lexicons and models

- [x] 1.1 With the `lexicon-change` skill, add `#length`, `#set` and `#leg` and the `lengths`, `sets` and `legs` properties to `feed/activityStreams.json`, add `calories`, `intensity` and `trigger` to `#lap`, and add `poolLength` (centimeters) to `feed/activity.json`, in the shapes design.md gives; verify the JSON parses and the `lexicon-auditor` agent reports no mismatch once 1.2 lands.
- [x] 1.2 Mirror them in `core/lexicon`: `Length`, `WorkoutSet`, `Leg`, the new `Lap` fields, `ActivityStreamData.lengths`/`sets`/`legs` and their `StreamChannel` tokens in `channels`, `ActivityRecord.poolLength`, and the known-value objects `SwimStroke`, `LapIntensity` and `LapTrigger`; verify `ActivityStreamsSerializationTest` and `RecordSerializationTest` round-trip each new field through `AtJson` and that older JSON without them still decodes.

## 2. FIT decoding

- [x] 2.1 Check that the pinned Java, JS (npm) and Swift FIT SDKs expose `length`, `set` (with `category`, `category_subtype` and the per-category exercise-name types), `split` (`split_type`, times, distance, ascent, descent, speeds), `session.pool_length`/`pool_length_unit`, and `lap.intensity`/`lap_trigger`/`total_calories`; record any gap in a KDoc on `FitDecoder`, bumping an SDK only for a patch or minor release.
- [x] 2.2 Add `DecodedLength`, `DecodedSet` and `DecodedSplit` to `DecodedActivity`, and pool length, calories, intensity and trigger to `DecodedSummary`; fill them in `GarminJavaFitDecoder`, resolving each set's exercise name through its category; verify with new `FitFixtures` (a pool swim with an idle length, a strength workout with rests, a resort ski day, a triathlon with run laps) in `GarminJavaFitDecoderTest`.
- [x] 2.3 Fill the same in `GarminJsFitDecoder`; verify `:core-activityfile:compileKotlinWasmJs` passes (wasm tests are disabled; the web import is checked in 8.2).
- [x] 2.4 Fill the same in `iosApp/iosApp/SwiftFitDecoder.swift`; verify the iOS app builds with the `xcodebuild` command in CLAUDE.md (the iOS import is checked in 8.2).

## 3. TCX decoding

- [x] 3.1 Read each TCX lap's `Calories`, `Intensity` and `TriggerMethod` in `TcxParser`; verify in `TcxParserTest` with an interval run alternating Active and Resting laps.
- [x] 3.2 Read every `Activity` of a `MultiSportSession` in `TcxParser`, joining points and laps in time order, with a session per activity and per `NextSport` transition, as design.md describes; verify in `TcxParserTest` that a run, ride, run duathlon gives three sport sessions and two transitions with every point, and that a plain TCX file still gives no sessions.

## 4. Mapping to the import

- [x] 4.1 In `DecodedActivityMapper`, keep TCX laps as FIT laps are kept, with the new lap fields normalized to the `LapIntensity` and `LapTrigger` tokens (FIT's `position_*` and TCX `Location` to `position`); verify in `DecodedActivityMapperTest` that the TCX interval run stores eight laps with calories and intensity, and a one-lap TCX ride stores none and keeps its totals.
- [x] 4.2 Map lengths and pool length (to centimeters; a rest for an idle length) onto `ImportedActivity`; verify in `DecodedActivityMapperTest` the spec's 25 m, 25 yd, open water and no-lengths scenarios.
- [x] 4.3 Map sets (exercise tokens lowercased to snake case, weight to grams, category kept without a name); verify in `DecodedActivityMapperTest` the spec's strength, bodyweight, unknown-exercise and category-only scenarios.
- [x] 4.4 Map a multi-session file's sessions to legs (transitions marked, laps left as laps), and no legs for one session; verify in `DecodedActivityMapperTest` the FIT triathlon's five legs, its run laps within the run leg, the TCX duathlon's legs, and a single-sport run with none.
- [x] 4.5 Replace a ski or snowboard activity's laps with its run and lift splits when it has any (a lift as a rest lap); verify in `DecodedActivityMapperTest` the resort day's sixteen laps and the backcountry ski that keeps its own laps.
- [x] 4.6 Carry lengths, sets, legs and pool length through `StreamBuilder` into `ActivityStreamData` and through `ActivityDraft.fromImport` onto the record; verify in `StreamBuilderTest` and `ActivityDraftTest` that each lands in the streams and the pool length on the record.

## 5. Merging and the AppView

- [x] 5.1 In `ActivityMerge`, copy `lengths`, `sets` and `legs` when the existing data has none, shifted to its start as laps are, and fill `poolLength` when absent; verify in `ActivityMergeTest` the spec's merge scenarios and that existing lists are never replaced.
- [x] 5.2 In `ActivityStreamsViews.bounded`, cap lengths (2000), sets (500) and legs (20), clamp their numbers and token lengths, and clamp the new lap fields; verify in `ActivityStreamsTest` that an oversized or malformed blob is served bounded and a valid one unchanged.

## 6. Activity page

- [x] 6.1 Expose lengths, sets, legs and the pool length in the activity detail `State` and `ActivityDetailPresenter`, each only where the viewer may see the streams (pool length where they may see the activity); verify in `ActivityDetailPresenterTest` the spec's viewer and non-viewer scenarios for each.
- [x] 6.2 Add a lengths table (number, stroke, time, strokes, SWOLF; rests with time only) and the pool length in the swim's stats; verify with a Compose Hot Reload screenshot of an imported pool swim on desktop.
- [x] 6.3 Add a sets table with labels for common exercises and the readable fallback (`single_arm_cable_row` → "Single arm cable row"); verify the fallback in a unit test and the table with a Compose Hot Reload screenshot of an imported strength workout.
- [x] 6.4 Add a legs table (sport, time, distance, pace or speed by sport; transitions with time only); verify with a Compose Hot Reload screenshot of an imported triathlon.
- [x] 6.5 Mark lap intensity in `LapTableUi`, and for ski and snowboard activities label laps as runs and lifts with each run's descent and show the run count in the stats; verify with Compose Hot Reload screenshots of the TCX interval run and the resort ski day, and that a ride without intensity shows no marks.
- [x] 6.6 Check that activities imported before this change show as before; verify by opening an earlier imported swim, workout and ride on desktop and seeing no new tables or stats.

## 7. Export

- [x] 7.1 In `FitWriter`, write a `length` message per length, the session's pool length, unit and length count, and each lap's first length index and length count; verify in `FitWriterTest` with the Java SDK that a 25 m swim of 40 lengths decodes with them and passes the integrity check.
- [x] 7.2 In `FitWriter`, write a `set` message per set; verify in `FitWriterTest` that a three-set bench press workout decodes with its exercise, reps, weight and rests.
- [x] 7.3 In `FitWriter`, write a session per leg (transitions with FIT's transition sport, each lap with its leg's sport, `num_sessions` on the activity) and the new lap fields; verify in `FitWriterTest` that a five-leg triathlon decodes with five sessions and passes the integrity check.
- [x] 7.4 Extend `FitRoundTripTest` with the pool swim, strength workout and triathlon; verify each imports back with the same lengths, sets, legs and laps.
- [x] 7.5 In `TcxWriter`, write a `Lap` per stored lap with its points, calories, intensity (`Resting` for rest) and trigger, keeping one spanning lap when none are stored; verify in `ExportWritersTest` and a TCX round trip through `ActivityFileReader` that an eight-lap interval run comes back with the same laps and intensities.
- [x] 7.6 Update `.claude/rules/activity-files.md` for TCX laps being kept, multisport legs, lengths, sets, ski runs and the FIT and TCX export changes; verify `scripts/check-agent-sync.sh` passes.

## 8. Integration

- [x] 8.1 Run `./gradlew check`; verify it passes on every target.
- [x] 8.2 Import the pool swim, strength workout, triathlon and ski day fixtures in the web app and the iOS simulator; verify each shows the same tables as on desktop.
- [x] 8.3 Export the triathlon and the pool swim as FIT and import them into Garmin Connect by hand; verify the legs and lengths show there, or note what doesn't in design.md's risks.

## Workflow follow-up

- Archive the change with `/opsx:archive` once implemented and verified.
