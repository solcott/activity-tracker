# Design

## Context

See proposal.md for why; the specs under `specs/feed/` hold the requirements.

- Per-sample channels are fields of `ActivityStreamData`, listed in `ActivityStreamData.INT_CHANNELS`. Merging (`ActivityMerge`), halving (`ActivityStreamsStore`), the AppView's `bounded` and `channels` all iterate that list, so a new int channel there is merged, bounded and served with no other change. `ChartsBuilder.lines` lists charts by hand, and `FitWriter` maps each record field by hand.
- Heart rate is already a stream at the streams' (charts) visibility. Training load (`TrainingLoad`) lives in the `feed.activityZones` record, at the narrower of charts and training visibility. The AppView rebuilds it field by field when serving (`ActivityStreamsViews.trainingLoad`).
- The AppView stores the activity record's JSON and serves it whole, so new activity fields need no Flyway migration unless something queries them. Nothing will query `cycles`, `avgStrokeDistance` or `utcOffset`.
- FIT decoding uses each platform's Garmin SDK: Java (`GarminJavaFitDecoder`), JS (`GarminJsFitDecoder`, which calls `decoder.read()` with default options) and Swift (`SwiftFitDecoder`). Each translates messages into `DecodedActivity`, and `DecodedActivityMapper` (common) does the rest. Fixture tests run on the Java decoder only. Wasm tests are disabled and iOS has no test target.
- The original imported file is kept, readable only by the athlete, in their `self` space (`feed/activity-source-file`). It still holds everything this change doesn't store, such as raw RR intervals.
- Zone-less dive logs are placed in a zone on the import screen (`ImportedActivity.inZone`). The import presenter derives the default title's part of day from that zone, or from the device's. `ActivityExporter` writes FIT, UDDF and the file name's date in `TimeZone.currentSystemDefault()`. The feed and the activity page don't show start times.

## Goals / Non-Goals

**Goals:**
- Read new raw FIT values in the decoders. Keep all interpretation (merging `hr` messages, HRV filtering, developer field matching, step doubling, UTC offset) in common code, so it's written and tested once.
- Every new per-sample value is an `INT_CHANNELS` entry, so merging, bounds and serving come for free.

**Non-Goals:**
- Writing developer fields back to FIT. Form power and leg spring stiffness stay in the app and the original file.
- A general developer field reader or settings UI. The table is code.
- Showing start times on the feed or the activity page, or time zone names. Only an offset is kept.
- Fixing running cadence's units (FIT strides per minute, stored as is). That's a separate concern.

## Decisions

### `hr` messages are anchored and merged in common code
Each decoder passes FIT `hr` messages through raw as `DecodedHrMessage(timestampMillis?, fractionalSeconds?, eventTimestamps: List<Double>, filteredBpm: List<Int>)`, with `event_timestamp_12` already expanded into `event_timestamp` by each SDK's component expansion. A common `HrMessages` ports Garmin's own algorithm (the JS SDK's `utils-hr-mesg.js`, also in the Java and Swift SDKs):
- **Anchoring:** a message with a `timestamp` anchors the device-relative `event_timestamp`s to UTC. Later readings are offsets from it, with the 2²² s rollover unwrapped.
- **Gap filling:** a gap between readings is filled with the previous reading every 250 ms, for up to 5 s.
- **Merging:** each record without its own heart rate takes the rounded average of the readings in its interval (after the previous record's time, up to its own).

`StreamBuilder` and the heart rate summary then see ordinary samples. The JS decoder passes `mergeHeartRates: false`, the only SDK that merges by default, so all three platforms take the same path.
- *Alternative:* each SDK's own merge (Java's `HrToRecordMesgBroadcastPlugin`, JS's `mergeHeartRates`, Swift's `HrMesgUtil`). Rejected: it overwrites a record's own heart rate, the three can only be tested on Java, and the Swift one runs outside Kotlin.
- *Alternative (the first sketch):* the nearest record within 2 s. Rejected for the file's own interval average, which is what the recording device reports.

### HRV: RMSSD in `TrainingLoad`
The decoders pass each `hrv` message's `time` array (seconds, invalid values dropped). The mapper computes RMSSD in whole milliseconds. Before that, it keeps intervals between 300 and 2000 ms and drops any that differ from the previous kept interval by more than 20%. It needs at least 30 intervals left, or it stores none. RMSSD goes in `TrainingLoad.hrvRmssd` (`feed/activityZones.json#trainingLoad`), so it is placed, moved and repaired with the zones by the existing rules. The AppView's `trainingLoad` clamps it to 0–500.
- *Alternative:* raw intervals with the zones. Rejected by the athlete: a 3-hour ride has about 30k values, and the original file already keeps them.

### New int channels
Units follow the lexicon's integer conventions:

| Field | FIT source | Unit |
|---|---|---|
| `torqueEffectivenessLeft` / `Right` | `left_` / `right_torque_effectiveness` | hundredths of a percent |
| `pedalSmoothnessLeft` / `Right` | `left_` / `right_pedal_smoothness` | hundredths of a percent |
| `pcoLeft` / `pcoRight` | `left_pco` / `right_pco` | millimeters (signed) |
| `coreTemperature` | `core_temperature` | hundredths of a degree Celsius |
| `smO2` | `saturated_hemoglobin_percent` | tenths of a percent |
| `totalHemoglobin` | `total_hemoglobin_conc` | hundredths of g/dL |
| `ebikeBattery` | `ebike_battery_level` | percent |
| `ebikeAssistMode` | `ebike_assist_mode` | the device's mode number |
| `ebikeAssistLevel` | `ebike_assist_level_percent` | percent |
| `formPower` | Stryd developer field | watts |
| `legSpringStiffness` | Stryd developer field | hundredths of kN/m |

Each one gets a `StreamChannel` token, an `INT_CHANNELS` entry, a `DecodedSample` field and a `StreamBuilder` channel. `ChartsBuilder` draws the left/right pairs as one chart with two series per metric (as balance is one chart today), and the rest as single lines. Assist mode is a step chart. The health channels (core temperature, SmO₂, total hemoglobin) stay with the streams, like heart rate, by the athlete's choice.

### Developer fields matched by name and units
Each decoder passes a record's developer fields as `DecodedDeveloperField(name, units, value)`, with the name and units taken from the `field_description` the SDK resolved. A common `DeveloperFields` table matches each field by its trimmed, case-insensitive name and units:
- `Power` in `Watts` → power, only where the record has no native power.
- `Form Power` in `Watts` → `formPower`.
- `Leg Spring Stiffness` in `KN/m` → `legSpringStiffness`.

Anything else is ignored.
- *Alternative:* the Connect IQ app UUID plus field number. Rejected: Stryd ships several apps, and app updates change UUIDs and field numbers.

### Counts on the activity record
`DecodedSummary` gains `totalCycles` and `avgStrokeDistanceMeters`. The mapper sums `cycles` over sessions and doubles them for on-foot sports (`SportCatalog` category Running or Walking, including hiking). `ActivityRecord` gains `cycles` (Int) and `avgStrokeDistance` (centimeters). The stats label `cycles` by sport, and nothing shows it for other sports (rides count crank revolutions). `FitWriter` halves the stored count back into strides for on-foot sports.

### Notes
`GpxParser` reads `trk/desc` (first track) and `TcxParser` reads `Activity/Notes` into `DecodedActivity.notes`, which the mapper already turns into `description` for the import screen. A batch of several files still shows no notes, as for logbooks.

### UTC offset
`ActivityRecord.utcOffset` holds minutes east of UTC at the start (−720 to 840). It's filled in three ways:
- **FIT:** each decoder passes the `activity` message's `timestamp` and `local_timestamp`. The mapper takes their difference and rounds it to 15 minutes, and stores none when `local_timestamp` is missing or the difference is out of range.
- **Zone-less dive logs:** `ImportedActivity.inZone(zone)` sets the offset that zone had at the start.
- **Recorded in the app:** `ActivityDraft.fromRecording` uses the device zone's offset at the start.

Where it's used:
- **Import screen:** the presenter's default title and existing-activity preview use a FIT file's offset, as a fixed-offset `TimeZone`, when present.
- **Export:** `ActivityExporter` builds the zone it passes to `FitWriter`, `UddfWriter` and `fileName` from the offset, falling back to the device zone.
- **Merging:** the offset fills one that's missing.

It's public with the activity: the start time already is, and an offset reveals only a band of longitude.
- *Alternative:* an IANA zone id. Rejected: FIT gives only an offset, and guessing a zone from it is wrong across daylight saving.

### Lexicon and AppView
Run the change through the `lexicon-change` skill:
- `feed/activityStreams.json`: the new channels.
- `feed/activity.json`: `cycles`, `avgStrokeDistance`, `utcOffset`.
- `feed/activityZones.json`: `trainingLoad.hrvRmssd`.

On the AppView side, only `ActivityStreamsViews.trainingLoad` changes, to clamp and pass `hrvRmssd`. `bounded` already aligns every `INT_CHANNELS` entry.

## Risks / Trade-offs

- [The JS or Swift SDK may expose `hr`, `hrv`, `activity.local_timestamp` or developer fields differently, or not at all] → check each pinned SDK first. Fixture tests cover the Java decoder. JS and Swift are checked by building them and importing the fixtures in the web and iOS apps. If one SDK lacks a message, that platform skips it (noted on `FitDecoder`).
- [Anchoring `hr` event timestamps is fiddly (rollover, a strap's clock drift)] → a port of Garmin's own algorithm, with JVM tests on a written HRM swim fixture and a synthetic rollover case. A reading that falls in no record's interval is dropped rather than guessed.
- [Whole-activity RMSSD during exercise is noisy and low] → it's labeled as the activity's RMSSD and not compared with resting HRV. The original file keeps the intervals for later analysis.
- [Stryd field names differ between app versions or locales] → the table matches case-insensitively, and an unmatched field is simply not kept. More names can be added to the table later.
- [Whether `total_strides` × 2 equals the watch's step count] → confirm against a fixture whose step count Garmin Connect shows. If the counts differ, adjust only the factor.
- [The new channels add width to stream blobs] → they're present only when the file has them. `bounded` already caps sample counts.

## Migration Plan

No data migration: only new imports and recordings carry the new fields, and older blobs, records and training loads just lack them. The AppView needs no Flyway migration. Rollback is reverting the client and AppView, and older clients ignore the unknown fields.
