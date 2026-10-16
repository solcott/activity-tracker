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
  - for a dive, the dive summary (`dive_summary`: max and average depth, bottom time, surface interval, CNS, OTU) and its gases (`dive_gas`)
- Sport and sub-sport map from the activity's `sport`/`subSport` back to FIT's enums, the reverse of the import mapping.
- The rebuilt-file note applies as for GPX and TCX: no laps beyond one, no developer fields, no running dynamics or deco state.

## Open questions

- **Encoder per platform.** Garmin's SDKs all encode: the Java SDK (`FileEncoder`) on Android, desktop and the AppView, the npm JS SDK (`Encoder`, already in `build/wasm/node_modules/@garmin/fitsdk`) on web, and the Swift SDK (`Encoder`) on iOS. A `FitEncoder` interface beside `FitDecoder`, implemented in Swift in `iosApp` the way `SwiftFitDecoder` is, keeps each platform on the official encoder. A hand-written common encoder is the alternative (one implementation, but CRC, definitions and field scaling to get right ourselves). Decide in design.
- **Round trip.** Re-importing an exported FIT should give back the same summary and streams. Decide whether a test pins that on every target or only the JVM.

## Capabilities

### New Capabilities

### Modified Capabilities
- `feed/activity-export`: adds FIT to the formats offered and what a FIT export holds. The capability is added by `export-activity-files`; archive that change first.

## Impact

- `:core-activityfile`: a `FitEncoder` interface and a `FitWriter` turning `ExportActivity` into FIT messages; implementations in `commonJvmMain` (Garmin Java SDK, already a dependency) and `wasmJsMain` (`@garmin/fitsdk` externals, extended with `Encoder`).
- `iosApp`: `SwiftFitEncoder.swift`, passed into `IosAppGraph.Factory` next to `SwiftFitDecoder`.
- `core/data`: `ExportFormat.FIT` (`application/vnd.ant.fit`, `.fit`) and the sport/sub-sport reverse mapping.
- Detail screen: FIT in the format list; no new hand-off.
- No lexicon or AppView change. Depends on `export-activity-files`.
