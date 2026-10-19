# Tasks

## 1. Lexicon and Kotlin mirror

- [x] 1.1 Add `lexicons/app/activitytracker/feed/activityDevices.json` (record keyed by tid; `activity`, `devices[]` of `#device`, `activityProfile`, `createdAt`; `#device` fields as in design.md, with `role` and `connection` as open `knownValues`). Its description says it holds serial numbers so it lives only in `self` and the AppView ignores it. Verify: the JSON parses, and `./gradlew :core-lexicon:jvmTest` passes any lexicon-loading tests.
- [x] 1.2 Add `app.activitytracker.feed.activityDevices` to `space/athlete.json`'s collections, and add it to the description's list of `self` records the AppView ignores. Verify: `grep activityDevices lexicons/app/activitytracker/space/athlete.json` shows both.
- [x] 1.3 Mirror the record in `core/lexicon` (`ActivityDevicesRecord`, `ActivityDevice`, `Nsid.FEED_ACTIVITY_DEVICES`, role and connection token constants), following `lexicons.md` (open enums, optional fields, `$type`). Add a round-trip test through `AtJson`. Verify: `./gradlew :core-lexicon:jvmTest`.
- [x] 1.4 Check that the AppView ignores the collection in `self`: confirm `SpaceSyncer`/`Indexer` drop unindexed collections, update `Indexer`'s comment listing ignored `self` collections, and add a test syncing a `feed.activityDevices` record that indexes nothing if no test pins this. Verify: `./gradlew :server-appview:test`.

## 2. Decoding identifiers from FIT

- [x] 2.1 Extend `DecodedSensor` (`serialNumber`, `antDeviceNumber`, `antTransmissionType`, `softwareVersion`, `batteryVoltage`, `cumulativeOperatingTime`), `DecodedSource` (`serialNumber`) and `DecodedSummary` (`sportProfileName`), with KDoc on units and invalid values (serial 0 and ANT device number 0 mean none). Verify: the module compiles (`./gradlew :core-activityfile:compileKotlinJvm`).
- [x] 2.2 Extend `FitFixtures.mtbRide` to match the athlete's real Edge 840 ride. Use the real values but never the real file, whose `user_profile` holds their name:
  - `file_id` serial 3510682071, and the Edge's `device_info` at index 0 with the same serial and software version 31.33
  - the HRM 200 over Bluetooth with serial 3629252158 and battery 50%
  - the SRAM 1052 power meter with no serial in its first message and serial 3236233426 in a later one at +18 s, operating time 2,215,232 s then 2,221,408 s, battery voltage 1.59 V then 1.57 V
  - the SRAM 1060 derailleur with serial 1518497200, operating time 19,664 s then 25,872 s, battery voltage 2.56 V
  - no ANT device numbers
  - session `sport_profile_name` and `sport.name` `MOUNTAIN`

  Add a small separate fixture with one Bluetooth sensor without a serial and one ANT+ sensor with ANT device number and transmission type. Verify: existing `:core-activityfile:jvmTest` still passes.
- [x] 2.3 Read the new fields in `GarminJavaFitDecoder`, mapping invalid values to null, and assert them in its tests (`GarminFitRideTest`, `GarminJavaFitDecoderTest`). Verify: `./gradlew :core-activityfile:jvmTest`.
- [x] 2.4 Read the same fields in `GarminJsFitDecoder`, adding the externals to `GarminFitSdk.kt`. Wasm tests are disabled, so check it by decoding the fixture bytes in the web app or by a browser test harness if one exists, and say which in the task note. Verify: `./gradlew :core-activityfile:compileKotlinWasmJs`, plus that manual decode. *Done by decoding the athlete's real Edge 840 ride with the npm SDK the externals bind (`build/wasm/node_modules/@garmin/fitsdk`): every field name matched, and the SDK names `device_info` index 0 `"creator"`, which the decoder now maps to 0.*
- [ ] 2.5 Read the same fields in `iosApp/iosApp/SwiftFitDecoder.swift`. Verify: the iOS build (`xcodebuild ... build` from CLAUDE.md), and an import of the fixture ride in the simulator whose decoded devices match the JVM test's.

## 3. Interpreting devices

- [x] 3.1 Add common `FitDevices.interpret`. It takes the recorder from `file_id`, filled from the `device_index` 0 `device_info`. It groups sensors by `FitSensors`' key, and each field takes the last non-null value any of the device's messages gives (`FitSensors` keeps its last-message-wins). It leaves out local devices and shares the kind and battery tables with `FitSensors`. Voltage becomes millivolts. Add `ImportedDevices`/`ImportedDevice` and `ImportedActivity.devices` (null when the file names none), and wire them into `DecodedActivityMapper`, including `activityProfile` from the session. Add commonTests for:
  - the extended `mtbRide`: the Edge with serial, then the HRM, power meter and derailleur, each with its serial; the power meter's serial filled from its later message; the last operating time and voltage; no ANT fields; profile `MOUNTAIN`
  - the watch with only local devices
  - the strap listed twice (battery Low 20%)
  - a field only an earlier message gives
  - a Bluetooth sensor with no serial
  - an ANT+ sensor with ANT fields
  - no profile name

  Verify: `./gradlew :core-activityfile:jvmTest`.
- [x] 3.2 Read `Creator/UnitId` and `Creator/Version` in `TcxParser` into the recorder. Add a test with a Forerunner 965 TCX. A GPX import yields `devices == null` (test). Verify: `./gradlew :core-activityfile:jvmTest`.
- [x] 3.3 Read dive computer serials: UDDF `divecomputer/serialnumber`, Subsurface `divecomputer@deviceid` and Shearwater's serial field. First check the field names against real exports, or the formats' published examples, and note the source in the parser's KDoc. Test each, including a two-computer Subsurface logbook where each dive names its own computer. Verify: `./gradlew :core-activityfile:jvmTest`.
- [x] 3.4 Update `.agents/rules/activity-files.md` with a short paragraph on devices (where identifiers come from per format, recorder first, local devices left out, written to `self`). Verify: `scripts/check-agent-sync.sh` passes.

## 4. Storing and lifecycle

- [x] 4.1 Add `ActivityDraft.devices`, set by `fromImport` from `ImportedActivity.devices`. Verify: an existing `ActivityDraft` import test asserts it.
- [x] 4.2 Add `ActivityDevicesStore` (`put`, `has` for stored or queued, `delete`) writing `feed.activityDevices` to `self` through `OutboxWriter`. `put` and `delete` are skipped when `!canWriteToSpaces(FEED_ACTIVITY_DEVICES)`. Contribute it to `SessionScope`. Verify: unit tests with the fake PDS for put, has (queued and stored) and the skipped no-scope case (`./gradlew :core-data:jvmTest`).
- [x] 4.3 In `ActivityRepository`, put devices in `queue` after the source, and in `fillSelfRecords` only when the activity has none. Do not add them to `requireSelfSpaceFor`. Tests: an import queues activity, then source, then devices. An oversized file keeps devices with no source. Merging into an app recording fills them, and merging into one that has them leaves them. A grouped recording gets its own. An older session without the scope publishes with a source and no devices. Verify: `./gradlew :core-data:jvmTest`.
- [x] 4.4 In `ActivityManager.delete`, delete devices wherever sources are deleted (the activity and each recording deleted with it, not those split off). Test both delete choices. Verify: `./gradlew :core-data:jvmTest`.
- [x] 4.5 Check that visibility changes (`ActivityManager.setVisibility`, `ActivityPlaces`) never touch `feed.activityDevices`. Add a test that a Public → Only me change leaves the record in `self` unchanged. Update `data-and-auth.md`'s line on `self` records to name it. Verify: `./gradlew :core-data:jvmTest`.

## 5. Integration

- [x] 5.1 Run `./gradlew check` and `./gradlew composeStabilityCheck`. Both pass.
- [x] 5.2 Run the `lexicon-auditor` agent on `feed.activityDevices` and `space.athlete`, and fix any mismatch it reports.
- [ ] 5.3 End to end on the local stack (`scripts/dev.sh`): sign in again, import the fixture FIT ride on desktop, then read the `feed.activityDevices` record from alice's `self` space. Check it has the recorder and sensors with serials, and that the AppView's `getActivity` response carries none.

## Workflow follow-up

- Archive with `/opsx:archive` once merged, which creates `openspec/specs/feed/activity-devices/spec.md`.
- The gear change can read `feed.activityDevices`, and backfill older activities from their `feed.activitySource` if it chooses.
