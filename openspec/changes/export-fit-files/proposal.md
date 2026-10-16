# Proposal

## Why

FIT is what Garmin Connect, TrainingPeaks, intervals.icu and most head units read best, and Garmin's own export offers it. `export-activity-files` ships GPX and TCX only and defers FIT. GPX and TCX drop channels FIT keeps: power, cadence and temperature only survive in vendor extensions, TCX can't hold depth, and neither has a session summary that tools read reliably.

## What Changes

- Export offers FIT next to GPX and TCX on the athlete's own activities. FIT is offered whenever TCX is (a track or streams), indoor activities included.
- The FIT file is an activity file (`file_id` type activity) rebuilt from the same joined track and streams as the other formats:
  - `record` messages with timestamp, position, altitude, distance, speed, heart rate, cadence, power, temperature and depth
  - one `lap` and one `session` spanning the activity, with sport and sub-sport, elapsed and moving time, distance and the heart-rate summary
  - `event` start/stop and an `activity` message, so Garmin Connect and Strava accept it as a completed activity
  - `event` gear-change messages from the stored gear changes, which GPX and TCX drop
  - for a dive, the dive summary (`dive_summary`: max and average depth, bottom time, surface interval, CNS, OTU), its gases (`dive_gas`), gradient factors (`dive_settings`) and tanks (`tank_summary`)
- Sport and sub-sport map from the activity's `sport`/`subSport` back to FIT's enums, the reverse of the import mapping.
- The rebuilt-file note applies as for GPX and TCX: no laps beyond one, no developer fields, no running dynamics or deco state.
- Re-importing an exported FIT gives back the activity's type, summary, streams, gear changes and dive data. A dive's later gases are written but not switched to: the app doesn't store switch times, and the file doesn't invent them.

## Resolved questions

- **Encoder.** One FIT encoder in common Kotlin, not one per platform behind a `FitEncoder` interface. The file holds a fixed handful of message types we fully control, so header, definitions, scaling and CRC are small; one implementation runs the same on Android, iOS, desktop and web, and the JVM tests check it with Garmin's Java SDK, which is the reference decoder. Per-platform encoders would mean three message translators, one of them in Swift that Gradle's tests never run. See design.md.
- **Round trip.** Pinned on the JVM only: export, then decode with `GarminJavaFitDecoder` through `ActivityFileReader` and compare. The encoder is common code, so the JVM covers every platform; a common test checks the header and CRC on every target.

## Capabilities

### New Capabilities

### Modified Capabilities
- `feed/activity-export`: adds FIT to the formats offered and what a FIT export holds.

## Impact

- `:core-activityfile`: a common `FitWriter` turning `ExportActivity` into FIT bytes (message definitions, field scaling, CRC), and `ExportActivity` gaining what FIT holds that GPX and TCX don't (sub-sport, ascent, speeds, temperature, gear changes, dive data), and the lexicon-to-FIT sport table. JVM tests decode its output with Garmin's Java SDK, already a test dependency.
- `core/data`: `ExportFormat.FIT` (`application/vnd.ant.fit`, `.fit`), offered whenever TCX is; `ActivityExporter` passing the record's dive and the streams' gear changes.
- Detail screen: FIT in the format list (a label string); no new hand-off, which already takes bytes.
- No lexicon, AppView, iOS-app or dependency change. Builds on the archived `export-activity-files`.
