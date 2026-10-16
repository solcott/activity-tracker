# Tasks

## 1. FIT encoding in `:core-activityfile`

- [x] 1.1 Add the internal `FitOutput`: 14-byte header (protocol 2.0, profile version, data size, `.FIT`, header CRC), definition messages per local type written before their first data message, little-endian data fields by base type with each type's invalid value for absent fields, and the trailing CRC-16; verify a `commonTest` that writes an empty-ish file and checks header bytes, data size and both CRCs on every target (`./gradlew :core-activityfile:jvmTest :core-activityfile:iosSimulatorArm64Test`)
- [x] 1.2 Add `FitSports.of(sport, subSport)` mapping each lexicon type to FIT `sport`/`sub_sport` numbers (nearest sport, else generic, for types FIT lacks); verify a jvmTest that runs every mapped pair's Java SDK names through `DecodedActivityMapper.activityType` and gets the same type back, except an explicit list of known lossy mappings
- [x] 1.3 Extend `ExportActivity` with `subSport`, elevation gain, average/max speed, average temperature (SI), `gearChanges: List<GearChange>` and `dive: ActivityDive?`, all defaulting to empty; verify `ExportWritersTest` and `ExportPointsTest` pass unchanged
- [x] 1.4 Add `FitWriter.write(ExportActivity, zone): ByteArray` for the non-dive messages: `file_id`, `file_creator`, timer start/stop events, one `record` per whole second (the first point in it) with only the channels present, gear change events (rear vs front by which gear changed), one `lap`, one `session` and the `activity` message; verify jvmTests that pass `Decode().checkFileIntegrity`, read field values through the Java SDK's `RecordMesg`/`SessionMesg`/`LapMesg`/`EventMesg`/`ActivityMesg` for a GPS ride with heart rate, cadence, power and gears, an indoor ride without positions, a trail run (sport/sub-sport), moving time ≠ elapsed, and two track points in one second
- [x] 1.5 Add the dive messages to `FitWriter`: `dive_settings`, one `dive_gas` per gas (closed-circuit diluent when `closedCircuit`), `tank_summary` per tank, and `dive_summary` referencing the session; none for other sports; verify a jvmTest decoding a two-gas closed-circuit dive with tanks through the Java SDK, and that a run has no dive messages
- [x] 1.6 Add round-trip jvmTests: export a ride (with gears) and a dive, read each back through `ActivityFileReader` with `GarminJavaFitDecoder`, and compare type, start, elapsed/moving time, distance, heart rate summary, streams to the second, gear changes, dive summary and first gas; verify `./gradlew :core-activityfile:jvmTest` passes
- [x] 1.7 Update `.agents/rules/activity-files.md`'s Export paragraph: `FitWriter` is a common encoder (not an SDK per platform, unlike decoding), one record per second, no gas switch events, and tests check it with the Java SDK; verify `scripts/check-agent-sync.sh` passes

## 2. Exporter in `core/data`

- [x] 2.1 Add `ExportFormat.FIT("fit", "application/vnd.ant.fit")` after TCX and offer it in `ActivityExporter.formats` whenever TCX is; verify the formats jvmTests: GPS track → GPX, TCX, FIT; streams only → TCX, FIT; neither → empty
- [x] 2.2 Fill the new `ExportActivity` fields in `ActivityExporter.build` from the record (sub-sport, elevation gain, speeds, temperature, dive) and the streams (gear changes), and produce bytes per format (`FitWriter` for FIT, the XML writers' text as UTF-8 otherwise); verify an `ActivityExporter` jvmTest exporting a ride with gears and a dive as FIT with the fake PDS and AppView, checking the `.fit` name, MIME type and that the bytes decode with the Java SDK (`./gradlew :core-data:jvmTest`)

## 3. Detail screen

- [x] 3.1 Add the `export_format_fit` string and its `ExportFormat.FIT` label in `ExportActivityUi.kt`; verify `ActivityDetailExportTest` covers an own GPS activity offering GPX, TCX and FIT and an indoor one offering TCX and FIT (`./gradlew :app-presenters:jvmTest :composeApp:jvmTest`), and on desktop with Hot Reload the sheet lists FIT (screenshot via the hot-reload MCP)

## 4. Integration

- [x] 4.1 With `scripts/dev.sh --seed alice`, export a GPS activity and a dive as FIT on desktop and re-import both through Import: type, distance, duration, heart rate and dive depths match within rounding
- [x] 4.2 Import an exported FIT on the iOS simulator, so `SwiftFitDecoder` reads it too, and export as FIT on Android, iOS and web, confirming the hand-off gives a `.fit` file
  - Found and fixed on iOS: the share sheet never appeared (for every format, not just FIT) because UIKit refused to present it while the closing Export sheet was still presented. `FileHandOff.ios.kt` now waits, up to a second, until nothing is presented over the key window's root.
- [x] 4.3 Upload an exported ride, indoor ride and dive to Garmin Connect and Strava and confirm each imports as an activity with its sport, time, distance and heart rate (needs the user's accounts); if manufacturer development is refused, add a creator `device_info` and record it in design.md
  - Garmin Connect accepted all three with manufacturer development, so no `device_info` was needed. The ride and the indoor ride matched: sport, distance, elapsed vs moving time, heart rate, power, NP and work. Connect recomputes ascent, average power and average cadence from the records. Strava flagged the ride as a duplicate of the original, which shows it read the file.
  - The dive came in with no dive time: Connect takes it from `dive_summary.bottom_time`, which a logbook dive doesn't store. `FitWriter` now writes the dive's elapsed time there when none is stored.
- [x] 4.4 Run `./gradlew check` and verify it passes

## Workflow follow-up

- Archive the change with `/opsx:archive` once the tasks are done, syncing the `feed/activity-export` delta into the main spec.
