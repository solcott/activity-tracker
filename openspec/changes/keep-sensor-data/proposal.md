# Proposal

## Why

The importers read the sample fields every sport shares, plus running dynamics, left/right balance and respiration rate. Other sensor data and some of the file's own details are lost at import:

- **HRV.** FIT `hrv` messages hold beat-to-beat (RR) intervals. Nothing reads them.
- **Heart rate in `hr` messages.** A swim recorded with a chest strap (HRM-Pro, HRM-Swim) stores its heart rate in FIT `hr` messages, which the strap uploads after the swim, not in `record`s. The decoders read only `record`s, so these swims import without heart rate.
- **Cycling dynamics.** FIT records carry left and right torque effectiveness and pedal smoothness, and platform centre offset (PCO). Only left/right balance is kept.
- **Physiological sensors and developer fields.** FIT records carry `core_temperature` (CORE sensor) and muscle oxygen (`saturated_hemoglobin_percent`, `total_hemoglobin_conc`, from Moxy). Connect IQ data fields write developer fields: Stryd's running power (where it isn't in `power`), form power and leg spring stiffness. No decoder reads either.
- **Step and stroke counts.** FIT sessions' `total_cycles` (`total_strides` on a run or walk; `total_strokes` on a row or paddle; pushes in a wheelchair) and `avg_stroke_distance`. Not read.
- **E-bike data.** FIT records' `ebike_battery_level`, `ebike_assist_mode` and `ebike_assist_level_percent`. Not read.
- **Notes.** GPX `trk/desc` and TCX `Notes` hold the athlete's description. UDDF notes seed the import screen's description; these don't.
- **Time zone.** FIT `activity.local_timestamp` gives the device's UTC offset. It's dropped, so an exported file, a dive's start in the import screen and a default title's part of day all use the viewing device's zone, not the one the activity happened in.

## What Changes

- **HRV:** the decoders read RR intervals from FIT `hrv` messages, and the import computes their RMSSD. RMSSD is stored with the training load (at the narrower of charts and training visibility) and shown only to viewers who may see it. The raw intervals aren't stored: the original file, which only the athlete can read, keeps them.
- **`hr` messages:** the three FIT decoders pass `hr` messages through, and the import fills the heart rate stream from them where records have none.
- **New int channels:** torque effectiveness and pedal smoothness (left and right), PCO (left and right), core temperature, SmO₂, total hemoglobin, e-bike battery level, assist mode and assist level, Stryd form power and leg spring stiffness. They're stream channels like heart rate, at the streams' visibility. They show as charts where present, merge like other channels and are written back to FIT where FIT has a field for them.
- **Known developer fields:** the FIT decoders read developer fields, and the import recognizes a small table matched by field name and units. Stryd's power fills `power` when the record has none, and its form power and leg spring stiffness become channels.
- **Step and stroke counts:** `activity.cycles` (steps, strokes or pushes, named by sport) and `activity.avgStrokeDistance`, public like distance. They're read from FIT sessions and shown with the activity's stats.
- **Notes:** GPX `trk/desc` and TCX `Notes` seed the import screen's description for a single file, as UDDF notes do.
- **Time zone:** `activity.utcOffset`, the UTC offset at the start. It's read from FIT, or taken from the zone the athlete placed a zone-less dive log's clock in, or from the device for an activity recorded in the app. Export and the import screen show local times in it.
- Only new imports and recordings get the new data; nothing is backfilled.

Out of scope: power phase (start, end and peak angles per leg), raw RR interval storage or a beat-to-beat chart, other Connect IQ developer fields, and showing the start time on the feed or activity page, which don't show it today.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `feed/activity-file-data`: HRV, `hr` messages, cycling dynamics, physiological and e-bike channels, developer fields, step and stroke counts, notes and the time zone.
- `feed/activity-export`: the new channels, counts and the activity's time zone in exported files.

## Impact

- Lexicons: `feed/activityStreams.json` (the new int channels), `feed/activity.json` (`cycles`, `avgStrokeDistance`, `utcOffset`), `feed/activityZones.json` (`trainingLoad.hrvRmssd`); the `core/lexicon` models and `ActivityStreamData.INT_CHANNELS`. Use the `lexicon-change` skill.
- `:core-activityfile`: the three FIT decoders (including `iosApp/iosApp/SwiftFitDecoder.swift`) for `hrv`, `hr` and `activity` messages, developer fields (`developer_data_id`, `field_description`) and the new record and session fields; `TcxParser` and `GpxParser` notes; `DecodedActivityMapper`; `StreamBuilder`; `FitWriter`; `UddfWriter`.
- `core/data`: `ActivityDraft.fromImport`, `ActivityMerge`, `ActivityExporter`, the recorder's draft.
- AppView: `ActivityStreamsViews.trainingLoad` clamps `hrvRmssd`. The new channels pass through `bounded`, which already iterates `INT_CHANNELS`, and the record fields need no migration.
- UI: the new charts (`ChartsBuilder`), counts in the activity's stats, RMSSD with the training load, and the import screen's clock and default title.
- `keep-remaining-dive-data` also changes `feed/activityStreams.json`; implement it after this change.
