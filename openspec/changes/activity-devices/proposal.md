# Proposal

## Why

Gear is coming later, and we want it to assign bikes and shoes on its own. The best signals for that are which recording device and which sensors were on an activity, told apart from one another across activities. Today the import reads every FIT `device_info` but keeps only make and model on the public record and drops each device's serial number and ANT ID. The original file in `self` still holds them, but reading them back out of every stored file later is slow, and it misses files over 10 MB. Recording these identifiers privately from now on means the matcher will have a history ready when gear arrives.

A power meter in pedals moves between bikes, so the matcher must weigh each sensor by how often it moves and use the ride's context (indoor or outdoor, sub-sport, the device's activity profile). This change records the identifiers and the context the file gives that the activity record doesn't already hold. Gear and the matcher itself are out of scope.

## What Changes

- A new private record, `feed.activityDevices`, stored in the athlete's `self` space under the activity's rkey. It lists the recording device and each paired sensor the file names, each with its role, kind, maker, model, and the identifiers the file gives: serial number, ANT device number and transmission type, and connection type. It also holds the sensor's battery (status, percent and voltage) and its total operating time as the activity ended, and the activity profile the device recorded under (FIT `sport_profile_name`). A real Edge 840 ride showed every sensor with a serial, SRAM AXS parts reporting their operating hours, and no ANT IDs.
- A sensor's fields are filled in from every `device_info` message the file has for it, because a sensor can send its serial only after the first message.
- Importing a file writes this record with the activity, through the outbox, like its original file. It works for every format that names a device: FIT, TCX (`Creator/UnitId`), UDDF, Subsurface and Shearwater. GPX names no device, so a GPX import writes no record. App recordings and manual entries write none either.
- The record follows the activity's lifecycle the way its original file does. It isn't moved by visibility changes. Merging fills it only when the logged activity has none. A grouped recording keeps its own. Deleting the activity deletes it.
- The three FIT decoders (Java, JS, Swift) and the TCX and dive-log parsers start reading the identifiers they now drop.
- The `space.athlete` declaration adds `feed.activityDevices` to the collections the `self` tier holds. A session signed in before this can't write it, and its imports then skip the record instead of failing.
- The public `feed.activity` record doesn't change: it keeps make and model only, never identifiers.
- Activities imported before this change aren't backfilled. Their originals still hold the identifiers, for the gear change to read if it chooses.

Out of scope: gear records, assigning gear, any UI showing devices, Bluetooth sensors in app recordings, and FIT `bike_profile`. The Edge 840 writes no `bike_profile` into activity files; its activity profile name is the bike hint it gives.

## Capabilities

### New Capabilities
- `feed/activity-devices`: keeps, privately and per activity, the recording device and the sensors an imported file names, with the identifiers that tell one device from another across activities. Only the athlete reads it.

### Modified Capabilities
<!-- None: the public activity record, the original file and the import flow keep their requirements. -->

## Impact

- **Lexicons**: a new `lexicons/app/activitytracker/feed/activityDevices.json`; `space/athlete.json` gains the collection and its description says the AppView ignores it.
- **core/lexicon**: the `ActivityDevicesRecord` model, an `Nsid.FEED_ACTIVITY_DEVICES` constant, and its tokens (role, connection).
- **core/activityfile**: `DecodedSensor` and `DecodedSource` gain serial and ANT fields; `GarminJavaFitDecoder`, `GarminJsFitDecoder` (plus `GarminFitSdk` externals), `iosApp/iosApp/SwiftFitDecoder.swift`, `TcxParser`, `UddfParser`, `SubsurfaceXmlParser` and `ShearwaterXmlParser` read them; `ImportedActivity` carries the devices.
- **core/data**: a new `ActivityDevicesStore` modeled on `ActivitySourceStore`; `ActivityRepository` publish, merge and group paths, and `ActivityManager.delete`.
- **server/appview**: no indexing. `Indexer`'s list of ignored `self` collections names the new one.
- **Sign-in scope**: sessions need to sign in again before their imports keep devices. Imports still work without it.
- **Privacy**: serial numbers are stable identifiers. They stay in `self`, as the original file already does, and never reach the repo or the AppView.
