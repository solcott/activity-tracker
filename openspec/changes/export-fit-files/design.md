# Design

## Context

See proposal.md for why, and `specs/feed/activity-export/spec.md` for what a FIT export holds. What exists:

- **Export pipeline.** `ActivityExporter` (`core/data`) checks the outbox, reads the track (`ActivityTrackStore`) and streams (`FeedRepository.streams`) fresh, joins them with `ExportPoints.join` into an `ExportActivity` (SI units), and hands a `String` from `GpxWriter`/`TcxWriter` to `ExportedFile(name, mimeType, bytes)`. `ExportFormat` is an enum (extension, MIME type); `ActivityExporter.formats` decides what's offered, and the detail presenter (`Export.kt`) and the format sheet (`ExportActivityUi.kt`) list it. The platform hand-off already takes bytes, so a binary format needs nothing new there.
- **What `ExportActivity` lacks for FIT.** It has the lexicon sport and TCX sport but not the sub-sport, elevation gain, average/max speed, average temperature, gear changes (`ActivityStreamData.gears`, dropped by the join) or the dive (`ActivityRecord.dive`, an `ActivityDive` in lexicon units: cm, tenths of °C, seconds, mbar, L).
- **FIT reading.** Each platform decodes with Garmin's SDK behind `FitDecoder`; `DecodedActivityMapper.activityType` maps FIT sport/sub-sport names to lexicon types by name, and `FitDives` picks the dive summary (`dive_summary`, else the session), gradient factors (`dive_settings`), tanks (`tank_summary`) and the gases breathed: the first enabled `dive_gas`, then each `dive_gas_switched` event's gas. `StreamBuilder` rounds each record to its second and reads gear change events' gear numbers.
- **Garmin's Java SDK** (`com.garmin:fit` 21.218.0) is on `:core-activityfile`'s `commonJvmMain` and `jvmTest` classpaths. Its `Profile`/`*Mesg` classes carry every message's field numbers, types and scales.

## Goals / Non-Goals

**Goals:**
- One FIT encoder in common code, identical on every platform, checked against Garmin's decoder in JVM tests.
- A file Garmin Connect, Strava and our own import read as the activity it came from.
- Keep `GpxWriter`/`TcxWriter` and their output unchanged.

**Non-Goals:**
- A general FIT encoding library: only the messages and fields listed below, little-endian, normal headers, no compressed timestamps, no developer fields.
- Pause events, laps, device info or sensors in the file: the app stores none of these in a form FIT can carry faithfully.
- Changing the import, including how it picks a dive's gases.

## Decisions

### A common FIT encoder rather than one per platform
`FitWriter.write(ExportActivity): ByteArray` in `:core-activityfile/commonMain`, over a small internal `FitOutput` that owns the byte buffer, local message definitions and CRC. Encoding the fixed set of messages we write needs: the 14-byte header (protocol 2.0, the SDK profile version we test against, data size, `.FIT`, header CRC), one definition message per message type before its first data message, little-endian data messages, invalid values for absent fields, and the trailing CRC-16 (FIT's nibble-table CRC). That's a few hundred lines with no platform code.

Alternatives: a `FitEncoder` interface beside `FitDecoder`, implemented with the Java SDK (`FileEncoder`), the JS SDK (`Encoder`, new externals) and the Swift SDK (`SwiftFitEncoder` in `iosApp`, passed into `IosAppGraph.Factory`). It would use Garmin's encoders, but needs three translators from `ExportActivity` to SDK messages, one in Swift that Gradle's tests never run, a second Swift class wired through `ContentView`, and still a common message model to keep them alike. Decoding goes per-platform because reading arbitrary devices' files (compressed timestamps, developer fields, every profile message) is large; writing our own handful is not.

### What the file holds
Field numbers, base types, scales and offsets come from Garmin's profile; tests name them through the Java SDK's `*Mesg` constants, so a wrong number fails a test rather than producing a file only our own reader accepts.

- `file_id`: type activity, manufacturer development, product 0, `time_created` = export time. `file_creator` with the app's version.
- `event` timer start at `startedAt`.
- `record` per point: timestamp, `position_lat`/`position_long` (semicircles, the inverse of `FitUnits`), `distance`, `enhanced_speed`, `enhanced_altitude`, `heart_rate`, `cadence`, `power`, `temperature` (whole °C, FIT's sint8), `depth`. One record definition per file holding only the channels some point has; a point without a channel writes that field's invalid value, which decoders read as absent.
- `event` front/rear gear change (`marker`) at each `GearChange.time`: rear when the rear gear differs from the previous change's (or the first change has one), else front; `data` packs the gear numbers as the profile's components do, with teeth left invalid since the app doesn't store them.
- Dive only: `dive_settings` (gradient factors), one `dive_gas` per `ActivityDive.gases` entry in order (`message_index` 0…, status enabled, mode closed-circuit diluent when `closedCircuit`, else open circuit), `tank_summary` per tank (mbar → bar × 100, L × 100), and a `dive_summary` referencing the session with max/average depth, bottom time (the elapsed time when none is stored, since Garmin Connect shows `bottom_time` as the dive time), surface interval, dive number, start/end CNS and OTU. No `dive_gas_switched` events: the app doesn't store switch times, so later gases re-import as configured, not breathed (the spec says so).
- `event` timer stop_all at `startedAt + elapsed`.
- One `lap` then one `session` (start time, timestamp at the end, elapsed, timer = moving or elapsed, distance, ascent, enhanced avg/max speed, avg/max heart rate, avg temperature, sport, sub-sport, `num_laps` 1), then `activity` (timestamp, total timer time, `num_sessions` 1, type manual, event activity/stop, `local_timestamp` in the device's zone, passed in as `ActivityExporter.fileName` already takes one).

Message order follows Garmin's activity file guidance (summary messages at the end), which every reader accepts.

### One record per second
FIT record times are whole seconds. `FitWriter` keeps the first point of each second, so a 1 Hz recording loses nothing and a faster one drops the extra positions rather than writing duplicate timestamps, which some readers turn into zero-length, infinite-speed segments. The join is unchanged; the filter is FIT's own.

### `ExportActivity` grows the fields FIT needs
Add `subSport`, `elevationGainMeters`, `avgSpeedMps`, `maxSpeedMps`, `avgTemperatureCelsius` (SI, like the rest), and `gearChanges: List<GearChange>` and `dive: ActivityDive?` as the lexicon types they're stored as. `FitWriter` converts those two itself, as `ExportPoints` converts streams; an SI copy of the dive would only be converted back. All new fields default to empty, so GPX/TCX callers and tests don't change.

### Sport table in `:core-activityfile`
`FitSports.of(sport, subSport): Pair<Int, Int>` maps each lexicon type to FIT's `sport`/`sub_sport` numbers: the reverse of `DecodedActivityMapper`'s name table, kept beside it. A sub-sport FIT lacks writes the sport with sub-sport generic; a sport FIT lacks writes its nearest FIT sport where one is unambiguous (`handcycle` → cycling/hand_cycling) and generic otherwise. A JVM test walks every entry: the numbers' Java SDK names (`Sport.getByValue(n).name`) run through `DecodedActivityMapper.activityType` must give back the same lexicon type, or be listed as a known lossy mapping.

### `ActivityExporter`
`ExportFormat.FIT("fit", "application/vnd.ant.fit")`, last in enum order so the sheet lists GPX, TCX, FIT. `formats` offers FIT exactly when TCX is. `build` fills the new `ExportActivity` fields from the record and streams and switches on format to bytes: the XML writers' `encodeToByteArray()`, `FitWriter.write` as is. The detail UI gets an `export_format_fit` string; nothing else in the presenter or hand-off changes.

## Risks / Trade-offs

- [Our encoder writes something Garmin's decoder accepts but Garmin Connect or Strava reject (manufacturer development, missing `device_info`)] → JVM tests run the SDK's integrity check and decode every message; a manual task uploads one ride, one indoor ride and one dive to Garmin Connect and Strava before archiving. If either refuses manufacturer development, add a creator `device_info`.
- [A field scale or invalid value is wrong for one base type] → tests compare decoded values per field through the SDK, not through our own reader alone.
- [Gas switches lost on re-import] → accepted and specified; storing switch times is a lexicon change for another day.
- [Temperature rounds to whole °C in records] → FIT's record temperature is sint8 °C; the session's average keeps what the record has.
- [Large activities] → bytes are built once on `Dispatchers.Default`, as the XML formats are; FIT is several times smaller than GPX.
