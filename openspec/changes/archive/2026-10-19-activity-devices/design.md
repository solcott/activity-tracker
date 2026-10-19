# Design

## Context

- The import already decodes every FIT `device_info` into `DecodedSensor` (in `GarminJavaFitDecoder`, `GarminJsFitDecoder` and `SwiftFitDecoder.swift`). `FitSensors.interpret` then reduces them to `ImportedSensor` (kind, maker, model, battery). Only kind, maker and model reach the public `feed.activity#sensor`, whose lexicon says "never serial numbers or battery state". Battery is read but never stored.
- `DecodedSource` (FIT `file_id`, TCX `Creator`, the dive logs' computer) carries only maker and product, which become `feed.activity.device`.
- `feed.activitySource` already sets the pattern for private per-activity data: a record in `self` under the activity's rkey, queued with the activity (`ActivitySourceStore`, `ActivityRepository.queue`/`fillSelfRecords`), deleted by `ActivityManager.delete`, ignored by the AppView, and never moved by visibility changes.
- A session can write a space collection only if its `space:` grant lists it (`SessionClients.canWriteToSpaces`), and the grant is filled from `space/athlete.json`'s declaration at sign-in.

## Goals / Non-Goals

**Goals:**
- One private record per imported activity that a future gear matcher can read without re-decoding original files.
- The same identifiers from every FIT SDK, so an activity imported on web, iOS or Android stores the same thing.

**Non-Goals:**
- No AppView change beyond naming the collection as ignored.
- No UI. The import screen and activity page don't show the record.
- No matching, hashing or normalizing of identifiers across activities. That's the gear change's job.

## Decisions

### A separate `feed.activityDevices` record, not fields on `feed.activitySource`
`activitySource` is already private, per activity and in `self`, so adding a `devices` array to it would avoid a new collection and the sign-in it costs. But it requires a blob and doesn't exist for files over 10 MB. Its meaning is also "the file", which a future in-app Bluetooth recording (sensors, no file) wouldn't have. A record of its own works for both cases and keeps the source record about the file.
*Alternative:* fields on `activitySource`. Rejected for the reasons above.

### Raw identifiers, not hashed
The original file in the same `self` space already holds every serial in the clear, so hashing them here protects nothing the source doesn't already expose. It would also stop the gear change from matching against what an athlete types in ("serial on the box"). The record's lexicon description says why it lives only in `self`, as `activitySource`'s does.
*Alternative:* a salted hash per athlete. Worth revisiting only if the record ever leaves `self`.

### Record shape
```
feed.activityDevices (key: tid = the activity's rkey; stored in self)
  activity        at-uri          required
  devices[]       #device         required, maxLength 32
  activityProfile string?         FIT session sport_profile_name
  createdAt       datetime        required

#device
  role            "recorder" | "sensor"     required, open token set
  kind            #sensor kind token        sensors only
  manufacturer, model   string?
  serialNumber    string?   decimal for FIT's uint32z, as written for text formats
  antDeviceNumber int?      0..65535
  antTransmissionType int?  0..255
  connection      "antPlus" | "bluetoothLowEnergy" | "local" | other  open token set
  softwareVersion string?
  battery         token?  (SensorBattery: new/good/ok/low/critical/charging)
  batteryPercent  int?    0..100
  batteryVoltage  int?    millivolts (FIT's volts x 1000; lexicons have no floats)
  operatingTime   int?    seconds, FIT cum_operating_time: the device's lifetime total
```
- `serialNumber` is a string because the text formats give hex or alphanumeric serials (UDDF, Subsurface `deviceid`), and FIT's 32-bit unsigned value overflows lexicon integers' common signed reading.
- FIT `serial_number` 0 is the invalid value for `uint32z`, so it means no serial. ANT device number 0 is a wildcard, so it's dropped too.
- `softwareVersion` is cheap to keep and lets gear show firmware later. It's optional and costs nothing when absent.
- `operatingTime` is the device's own running total, not this activity's share: the SRAM power meter on a real Edge 840 ride reported 2,221,408 s (about 617 h) and its AXS derailleur 25,872 s. The gear change can use it for component wear and as a hint for which bike a sensor is on (the derailleur's hours rise only on its own bike). `batteryVoltage` comes with it on those parts.
- On that ride the Edge 840 wrote no ANT device number or transmission type for any sensor, but a serial for every one, the Bluetooth heart rate strap included. The ANT fields stay optional for head units that write them, and the serial is the identifier to rely on.

### Fields fill across a device's messages
Recorders write `device_info` at the start and end, and sometimes mid-ride. On the real ride, the power meter's first message (at the start) had no serial and the next, 18 s later, did. Taking only the last message would happen to work there, but a recorder that writes the end message without a serial would lose it. So the entries are merged per device key: each field takes the last non-null value any message gives it. That keeps battery and operating time from the end, and the serial from whichever message had it.
*Alternative:* the last message wins whole, as `FitSensors` does for the public list. Rejected, since it drops fields an earlier message had. `FitSensors` can keep its behavior, because the public list carries only kind, maker and model.

### Where the decoding changes go
- `DecodedSensor` gains `serialNumber`, `antDeviceNumber`, `antTransmissionType`, `softwareVersion`, `batteryVoltage` and `cumulativeOperatingTime`. `DecodedSource` gains `serialNumber`. `DecodedSummary` (sessions) gains `sportProfileName`.
- Each FIT decoder reads the same fields from its SDK (`DeviceInfoMesg`/`FileIdMesg`/`SessionMesg` in Java; the JS SDK's decoded message objects, with new externals in `GarminFitSdk.kt`; the Swift SDK's getters). `FitFixtures.mtbRide` already has the real ride's sensor set (Edge 840, HRM 200 over Bluetooth, SRAM 1052 power meter and 1060 AXS derailleur over ANT+). It's extended to match the ride: a `file_id` serial, serials on every device, the power meter's first message without its serial, operating times and battery voltages on the SRAM parts, and session and `sport` profile name `MOUNTAIN`. The athlete's own file is never committed, since its `user_profile` holds their name, weight and age. A separate small fixture gives a sensor with ANT device number and transmission type, so those fields are still tested. Each decoder's existing test asserts them, as `GarminFitRideTest` does for sensors today.
- A new common `FitDevices.interpret(fileId, sensors, sessions)` builds the list. Recorder first, from `file_id`, filled from the `device_info` whose `device_index` is 0 (the creator) when `file_id` lacks a field. Then sensors, grouped by `FitSensors`' existing key, with fields merged across messages as above. `FitSensors.interpret` keeps producing the public list. Both share the kind and battery tables.
- Text formats: `TcxParser` reads `Creator/UnitId` and `Creator/Version`. `UddfParser`'s `Computer` reads `serialnumber`. `SubsurfaceXmlParser` reads `divecomputer@deviceid`. `ShearwaterXmlParser` reads the log's serial number field. Each sets `DecodedSource.serialNumber`, and `DecodedActivityMapper` turns that into a recorder entry.
- `ImportedActivity` gains `devices: ImportedDevices?` (null when the file names none), and `ActivityDraft.fromImport` carries it as `draft.devices`.

### Writing and lifecycle
- A new `ActivityDevicesStore` in `core/data`, shaped like `ActivitySourceStore`, with `put`, `has` (stored or queued) and `delete`. It writes through `OutboxWriter` to `self`. No blob.
- `ActivityRepository.queue` puts devices after the source. `fillSelfRecords` fills them when the logged activity has none, which gives merge its fill-only behavior. A grouped recording goes through `queue`, so it gets its own.
- Unlike tracks and sources, a session without the scope doesn't refuse the import: `put` is skipped when `!clients.canWriteToSpaces(FEED_ACTIVITY_DEVICES)`. Devices are a nice-to-have, while losing an import for them would be a regression. `delete` is skipped the same way, as `ActivitySourceStore.delete` already does.
- `ActivityManager.delete` deletes devices wherever it deletes sources (each recording, not the ones split off).

### Lexicon and scope
- `space/athlete.json` adds `app.activitytracker.feed.activityDevices` to the collections and to the description's list of `self` records the AppView ignores. `Indexer`'s comment on ignored `self` collections is updated. The AppView must keep ignoring it in `self`: a task checks that `SpaceSyncer` and `Indexer` drop collections they don't index, and adds a test if none pins this.
- The record and tokens are mirrored in `core/lexicon` (`Records.kt`, `Nsid`), serialized with `AtJson`, and pass through the `lexicon-change` checklist, without AppView tables or migrations.

## Risks / Trade-offs

- [Existing sessions lack the scope, so their imports silently keep no devices until the athlete signs in again] → Accepted. It's a dev-stage app, and the original still holds the data for a later backfill. There's no prompt to sign in again just for this.
- [SDKs disagree on a field (the JS SDK's types, the Swift SDK's optionals)] → Every decoder is tested against the same fixture bytes, so a mismatch fails a test, not an athlete.
- [Shearwater's and Subsurface's serial fields vary by export version] → Read where present, and keep the dive without one. A task checks real exports before the parsers are finalized.
- [`serialNumber` as a string invites inconsistent formatting across formats (decimal vs hex)] → Each format's value is kept as that format writes it. Matching across formats isn't a goal, and one athlete's sensor is seen through one recorder's format.
- [Records add one outbox write per import] → Small, with no blob, and a logbook writes one per dive, as sources do.

## Migration Plan

No data migration. New imports write the record once the app ships, and old activities have none. Rolling back leaves orphan `feed.activityDevices` records in `self` that nothing reads. They're harmless, and deleting an activity still removes them as long as the delete path ships.
- [Undocumented `device_info` fields (on the real ride, a 6-byte field on the Bluetooth strap, likely its address)] → Not read. Only the FIT profile's named fields are kept, so an SDK bump can't change their meaning.

FIT `bike_profile` is left out: the real Edge 840 ride carried none, only the activity profile name, which this change keeps.
