# Proposal

## Why

Imported files already say which gear was used. The power-meter crank, the derailleur and the head unit each report a serial number, and `feed.activityDevices` keeps those privately for this purpose (`feed/activity-devices`). A crank lives on one bike, so seeing it on an activity should put that bike on the activity without the athlete lifting a finger. The athlete has to tell the app once which sensor is which gear, so the import should ask the first time it sees a sensor. It should offer to keep the name the file gives, set a type, mount it on a bike, and add it to collections.

## What Changes

- **Sensor links.** A sensor or recording device can be linked to one of the athlete's items, matched by maker and serial number. Without a serial, it's matched by maker, ANT device number and transmission type, and without those by maker and model, which the app marks as a weak match. Links are stored only in the athlete's `self` space, in `gear.autoAssign`, so serial numbers never reach the repo or the AppView.
- **Activity profile links.** A device's activity profile name (FIT `sport_profile_name`, e.g. `MOUNTAIN`) can be linked to an item, for head units with one profile per bike.
- **Automatic gear on import.** Each imported activity starts with gear from, in order of priority for each gear type: its linked sensors and recorder, then its linked activity profile, then the defaults for its activity type. A linked item mounted on another item at the activity's start brings the top-level item it's mounted on (crank to Tarmac), and that item's components count through their mounts. Retired items are skipped, and collections are never applied automatically. When sensors point at two items of one type (two bikes), both are filled in and the preview flags the conflict.
- **A prompt for each new sensor.** The import preview lists each device the file names that isn't linked or ignored. For each, the athlete can add it as gear (keeping the file's name or renaming it, with a type suggested from the sensor kind, optionally mounted on one of their items and added to collections), mark it as not gear (never asked again), or leave it for later (asked again next import). A sensor shows once per batch.
- **Applying a new link to past imports.** After linking, the app offers to add the item, or the item it's mounted on, to earlier imports whose devices include that sensor and that list no item of that type.
- **Managing links**: an item's gear page lists its linked sensors and profiles, with Unlink. The gear list has an "Ignored sensors" screen to un-ignore.

Out of scope: Bluetooth or ANT+ sensors in in-app recordings (none exist), backfilling devices for imports made before `activity-devices`, and using operating time or battery for wear or reminders.

## Capabilities

### New Capabilities
- `gear/gear-sensors`: linking sensors, recording devices and activity profiles to gear, assigning gear automatically on import, and prompting for new sensors.

### Modified Capabilities
None. `feed/activity-devices` already lets only the athlete read their devices. This change adds the client's read path for matching past imports, and changes none of its requirements.

## Impact

- **Lexicons:** `gear/autoAssign.json` gains `sensors` (`#sensorLink`: gear plus the match key fields), `profiles` (`#profileLink`: name plus gear) and `ignored` (`#sensorKey`). Its description states that serials live only in `self`.
- **core/lexicon:** `SensorKey` (how a key is built from an `ActivityDevice` or an `ImportedDevice`, and how strong it is), the link models, and the sensor-kind to gear-type suggestion.
- **core/data:** `GearAutoAssign` (resolving links, profiles and defaults per gear type, and the top-level parent through mounts at the activity's start). `ActivityDevicesStore` gains `list()` for the athlete's own devices records. A past-imports pass edits activities through `ActivityRepository`.
- **Screens:** `ImportActivityScreen` gains new-sensor cards, a profile-link offer and a conflict flag. A new `IgnoredSensorsScreen`. `GearScreen` gains Linked sensors and profiles.
- **server/appview:** none. `gear.autoAssign` stays unindexed.
- **Dependencies:** `gear-items`, `activity-gear`, `gear-components`, `gear-collections` (all in flight).
