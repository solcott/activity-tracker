# Proposal

## Why

The importers keep an activity's totals, its sample streams and, for FIT files, its laps. The structure inside a workout is otherwise lost at import, and some of what we do store doesn't make it back out on export:

- **Swim lengths.** A pool swim's FIT `length` messages carry each length's stroke type, stroke count and time, and its session the pool length. We keep only the session summary.
- **Strength sets.** Strength, HIIT and cardio activities log each set as a FIT `set` message (exercise category and name, reps, weight, duration, active or rest). Nothing reads them, so a strength workout imports as a time, heart rate and calories.
- **Multisport legs.** A triathlon's FIT file has a session per leg (its sport, time, distance and heart rate) and per transition. They become one multisport activity's totals; the legs aren't kept. A TCX `MultiSportSession` reads only its first `Activity`.
- **TCX laps on import, and lap details.** TCX laps are read only as the activity's summaries; unlike FIT laps they aren't stored. No format's lap calories, intensity (TCX `Intensity`, FIT `intensity`: active, rest, warm-up, cool-down) or trigger (TCX `TriggerMethod`, FIT `lap_trigger`: manual, distance, time, position) are read.
- **Laps in TCX exports.** Stored laps are written to FIT, but TCX still writes one lap spanning the activity.
- **Ski runs.** Garmin's ski and snowboard activities log each run and lift ride as FIT `split` messages (`ski_run_split`, `ski_lift_split`) with a `split_summary`. We read laps only, so the run count and each run's descent are lost.

## What Changes

- **Swim lengths:** pool length on the activity (public, like distance) and a `streamData.lengths` list (`{ start, elapsed, stroke, strokes, rest }`) at the streams' visibility, read from FIT `length` messages, shown as a lengths table with SWOLF on the swim's page, and written back to FIT.
- **Strength sets:** a `streamData.sets` list (`{ start, elapsed, rest, category, exercise, reps, weight }`: `category` and `exercise` open enums holding the FIT profile's tokens, weight in grams) at the streams' visibility, read from FIT `set` messages, shown as a sets table on the activity's page, and written back to FIT.
- **Multisport legs:** each session of a multisport file kept as a leg (`streamData.legs`: a lap's fields plus its activity type), transitions included, its laps kept as laps within it, shown as the activity's legs and written back to FIT as sessions. TCX reads every `Activity` of a `MultiSportSession`.
- **Laps from every format:** TCX laps are stored like FIT laps. `streamData.lap` gains `calories`, `intensity` and `trigger` (both open enums), read from TCX and FIT laps and written back to both.
- **TCX laps:** TCX writes a `Lap` per stored lap with its own time, distance, heart rate, calories, intensity and trigger; a round-trip test reads them back. GPX stays one track segment: Strava ignores `trkseg` breaks and Garmin Connect has been reported to import only the first segment.
- **Ski runs:** FIT ski run and lift splits kept as laps in place of the file's own (a lift as a rest lap), with the run count and each run's descent on the activity's page.
- Only new imports get the new data; nothing is backfilled.

## Decided

- **Exercise names.** The FIT profile's tokens are kept as open enums; the UI names common exercises and shows any other token readably. Our own exercise catalog can come later.
- **Ski splits.** Runs and lifts are laps, a lift a rest lap.
- **GPX laps.** Not written; see TCX laps above.

## Capabilities

### New Capabilities
- `feed/swim-lengths`: keeping a pool swim's lengths and pool length, and showing them.
- `feed/strength-sets`: keeping a workout's sets, and showing them.
- `feed/multisport-legs`: keeping a multisport activity's legs and transitions, and showing them.

### Modified Capabilities
- `feed/activity-file-data`: TCX laps and lap details, ski runs.
- `feed/activity-export`: TCX laps; FIT swim lengths, sets, legs and lap details.

## Impact

- Lexicons: `feed/activityStreams.json` (`lengths`, `sets`, `legs`, `lap` calories, intensity and trigger), `feed/activity.json` (pool length); `core/lexicon` models. Use the `lexicon-change` skill.
- `:core-activityfile`: the three FIT decoders (including `iosApp/iosApp/SwiftFitDecoder.swift`) for `length`, `set` and `split` messages, the new lap fields and every session of a multisport file; `TcxParser` laps and multisport; `DecodedActivityMapper`; `StreamBuilder`; `TcxWriter` and `FitWriter`.
- `core/data`: `ActivityDraft.fromImport`, `ActivityMerge`.
- AppView: `bounded` caps the new lists. Pool length needs no migration, since the activity record is served as stored.
- UI: lengths, sets and legs tables, ski runs.
- `keep-remaining-dive-data` and `keep-sensor-data` also change `feed/activityStreams.json`; implement the three one at a time.
