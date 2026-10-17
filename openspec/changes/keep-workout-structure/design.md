# Design

## Context

See proposal.md for why; the specs under `specs/feed/` hold the requirements.

- A recording's laps live in the `feed.activityStreams` data blob (`ActivityStreamData.laps`, `#lap`), beside `gears` and `gasSwitches`. The blob isn't validated against the lexicon, so new lists cost no record-schema migration. Its place sets who may see it, and the AppView sanitizes it (`ActivityStreamsViews.bounded`: caps each list, aligns each int channel) before serving it.
- The AppView stores the activity record's JSON (`record` text column) and serves it whole, so a new activity field needs no Flyway migration unless something queries it.
- `DecodedActivityMapper` treats FIT sessions as the activity's summaries (falling back to laps, all TCX has) and keeps laps only when the file has sessions. A file whose sessions are different sports becomes one `multisport` activity; `TRANSITION` is its sentinel for FIT's transition sport. The Java decoder already collects every session.
- `TcxParser` reads the first `Activity` only and builds laps as summaries. `TcxWriter` writes one lap with `Calories` 0, `Intensity` Active and `TriggerMethod` Manual, which the TCX schema requires.
- FIT decoding uses each platform's Garmin SDK (Java, JS, Swift); `FitWriter` writes FIT in common code from `FitProfile`.
- `ActivityMerge` copies the other recording's laps, shifted to the existing start, when the existing one has none.

## Goals / Non-Goals

**Goals:**
- One shape per structure (length, set, leg, lap), shared by every format that carries it and by FIT export.
- Every new list follows the streams' visibility, merges like laps, and is bounded by the AppView like laps.

**Non-Goals:**
- GPX laps. Strava's importer ignores `trkseg` breaks and counts distance across them, and Garmin Connect has been reported to import only a file's first segment, so a segment per lap would gain nothing in Strava and risk losing data in Garmin Connect. GPX keeps one segment (see Decisions).
- A multisport TCX export (`MultiSportSession`): TCX export stays one activity with its laps.
- Editing lengths, sets or legs in the app, and planned workouts (FIT `workout` / `workout_step`).
- Exercise labels beyond a short list of common exercises.

## Decisions

### New lists in the streams blob, beside laps
`ActivityStreamData` gains `lengths`, `sets` and `legs`, each a list documented as a def in `feed/activityStreams.json` (`#length`, `#set`, `#leg`) and a `StreamChannel` token, so `channels` lists them like `laps`. They sit at the streams' visibility, as laps do.
- *Alternative:* on the activity record. Rejected: a set's weights and a swim's lengths are detail the athlete sets visibility for with the streams, and records can't hold a narrower audience than the activity.
- *Alternative:* a record of their own. Rejected: three more records per activity for data that's always read with the streams.

Shapes, in lexicon units (seconds since `startedAt`, grams, centimeters):
- `#length`: `start`, `elapsed`, `stroke?` (open: `freestyle`, `backstroke`, `breaststroke`, `butterfly`, `drill`, `mixed`, `im`), `strokes?`, `rest?` (true for an idle length).
- `#set`: `start`, `elapsed`, `rest?`, `category?`, `exercise?` (both open tokens), `reps?`, `weight?` (grams).
- `#leg`: the `#lap` fields plus `sport` and `subSport?` (the activity record's tokens) and `transition?`.

The pool length is `activity.poolLength` (centimeters), public like distance: it says nothing a swim's distance doesn't.

### Legs are their own list, not typed laps
Each session of a multisport file becomes a `#leg`; the file's laps stay `laps`, each falling within its leg's time.
- *Alternative (the proposal's first sketch):* laps with an activity type. Rejected: a triathlon's run leg has its own laps (kilometer splits), so typed laps would either drop those or mix two levels in one table, and FIT export needs sessions and laps apart anyway.

A transition is a leg with `transition: true` and the multisport activity's sport token. Legs are kept only when the file has more than one session, so a single-sport file stores none.

### Lap fields: calories, intensity, trigger
`#lap` gains `calories` (kcal) and `intensity` and `trigger`, open tokens with known values in `core/lexicon` (`LapIntensity`, `LapTrigger`):
- intensity: `active`, `rest`, `warmup`, `cooldown`, `recovery`, `interval` (FIT `intensity`; TCX `Active` → `active`, `Resting` → `rest`).
- trigger: `manual`, `time`, `distance`, `position`, `heartRate`, `sessionEnd`, `fitnessEquipment` (FIT's four `position_*` triggers → `position`; TCX `Location` → `position`, `HeartRate` → `heartRate`).

Tokens the app doesn't know are stored as given, after `camelCase` normalization in the mapper, like other open enums.

### TCX laps are stored
The mapper keeps a TCX file's laps the way it keeps FIT laps (the "single lap spanning the activity stores none" rule still applies). Summaries still come from the laps when there are no sessions, so the totals don't change.

### TCX multisport reads every activity
`TcxParser` reads every `Activity` under `MultiSportSession` (`FirstSport`, each `NextSport`), joining their points and laps in time order, and gives one `DecodedSummary` per activity as a session, with its sport and the sums of its laps. A `NextSport`'s `Transition` lap becomes a session of the transition sport. From there the mapper builds legs as for FIT. A plain TCX file still has no sessions.

### Ski runs replace laps
The FIT decoders pass `split` messages of type `ski_run_split` and `ski_lift_split` through as `DecodedSplit`s. When a ski or snowboard activity has any, the mapper stores them as its laps (a lift as `intensity: rest`) instead of the file's laps, which on resort days are usually one automatic lap per run anyway, without the lifts. Other split types (run/walk, climb) are ignored for now. The page counts the `active` laps as runs and labels the laps by activity type.
- *Alternative:* a `runs` list of its own. Rejected: runs are laps in every way the lap table, merging and FIT export already handle, and a separate list would need its own export path.

### Sets keep the FIT profile's tokens
Each decoder resolves a `set` message's first `category` and its `category_subtype` (whose meaning depends on the category, e.g. `bench_press_exercise_name`) through its SDK's profile to names, and passes them as strings; the mapper lowercases them to snake case, the FIT profile's spelling (`bench_press`, `single_arm_cable_row`). `weight` (kg) becomes grams; `set_type` gives `rest`. The UI names common exercises from a small table and otherwise shows the token with underscores as spaces and the first letter capitalized.
- *Alternative:* map to our own exercise catalog. Rejected for now: FIT has hundreds of exercise names, and keeping its tokens loses nothing while a catalog can come later.

### Merge and bounds follow laps
`ActivityMerge` copies the other recording's `lengths`, `sets` and `legs` when the existing data has none, shifted to the existing start as laps are, and fills `poolLength` on the record when absent. `ActivityStreamsViews.bounded` caps them (lengths 2000, sets 500, legs 20) and clamps their numbers and token lengths, as it does laps; the new lap fields are clamped the same way.

### Export
- `FitWriter`: a `length` message per stored length, and the session's `pool_length`, `pool_length_unit` and `num_lengths`, with each lap's `first_length_index` and `num_lengths` for the lengths in its time (Garmin Connect groups lengths by lap); a `set` message per stored set; a `session` per stored leg (each lap's sport is its leg's, `num_sessions` on the activity message); and the new lap fields on each lap.
- `TcxWriter`: a `Lap` per stored lap, its points the ones in its time, with its `Calories` (0 when not stored), `Intensity` (`Resting` for `rest`, `Active` otherwise, as TCX allows only those two) and `TriggerMethod` (mapped back; `Manual` when not stored). One spanning lap when none are stored, as now.
- `GpxWriter`: unchanged, one segment.

## Risks / Trade-offs

- [The JS and Swift SDKs may expose `split`, `set` or the exercise-name types differently from the Java SDK] → check first that each pinned SDK exposes them. The Java decoder gets fixture tests (a ski day, a strength workout, a pool swim, a triathlon); wasm tests are disabled and iOS has no test target, so the JS and Swift decoders are checked by building them and importing the fixtures in the web and iOS apps. If one SDK lacks a message, that platform skips it (noted on `FitDecoder`) and the others still keep it.
- [A large multisport FIT file can hold thousands of laps] → `bounded` already caps laps; legs are capped at 20.
- [Strava and Garmin Connect may show an exported multisport FIT differently from a watch's] → the round-trip test checks our own import; an exported file is checked once by hand in Garmin Connect before shipping.
- [TCX `Intensity` has only Active and Resting, so warm-up and cool-down are lost in a TCX export] → accepted; FIT keeps them, and the original file is offered first.
- [Ski splits replacing laps hides a manual lap the skier pressed] → accepted: the run list is what ski activities are read for, and the original file keeps the laps.
- [Exercise tokens from older FIT profiles may differ] → the tokens are open; the UI fallback shows any token readably.

## Migration Plan

No data migration: only new imports carry the new lists, and older blobs and records just lack them. The lexicon change goes through the `lexicon-change` skill; the AppView needs no Flyway migration, since it serves the record and the blob as stored. Rollback is reverting the client and AppView: older clients ignore the unknown fields.
