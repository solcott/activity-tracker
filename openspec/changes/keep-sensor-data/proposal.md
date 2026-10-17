# Proposal

## Why

The importers read the sample fields every sport shares, plus running dynamics, left/right balance and respiration rate. Other sensor data and some of the files' own details are lost at import:

- **HRV.** FIT `hrv` messages hold beat-to-beat (RR) intervals. Nothing reads them.
- **Heart rate in `hr` messages.** A swim with a chest strap (HRM-Pro, HRM-Swim) stores heart rate in FIT `hr` messages, which the strap uploads after the swim, not in `record`s. The decoders read only `record`s, so these swims import without heart rate.
- **Cycling dynamics.** FIT records carry left and right torque effectiveness and pedal smoothness, platform centre offset (PCO) and power phase. Only left/right balance is kept.
- **Physiological sensors and developer fields.** FIT records carry `core_temperature` (CORE sensor) and muscle oxygen (`saturated_hemoglobin_percent`, `total_hemoglobin_conc`, from Moxy). Connect IQ data fields write developer fields: Stryd's running power (where it isn't in `power`), form power and leg spring stiffness. No decoder reads either.
- **Step and stroke counts.** FIT sessions' `total_cycles` (`total_strides` on a run or walk: steps; on a row or paddle: strokes; in a wheelchair: pushes) and `avg_stroke_distance`. Not read.
- **E-bike data.** FIT records' `ebike_battery_level`, `ebike_assist_mode` and `ebike_assist_level_percent`. Not read.
- **Notes.** GPX `trk/desc` and TCX `Notes` hold the athlete's description. UDDF notes seed the import screen's description; these don't.

## What Changes

- **HRV:** RR intervals as a stream at the athlete's training visibility (see Open questions), read from FIT `hrv` messages, with RMSSD shown to viewers who may see it.
- **`hr` messages:** the three FIT decoders merge FIT `hr` messages into the heart rate stream by timestamp.
- **New int channels:** cycling dynamics (torque effectiveness and pedal smoothness, left and right; PCO, left and right), core temperature, SmO₂, total hemoglobin, and e-bike battery level and assist mode. They join `ActivityStreamData.INT_CHANNELS`, show as charts where present and are written back to FIT.
- **Known developer fields:** the FIT decoders read a small table of developer fields, matched by Connect IQ app and field name: Stryd's power fills `power` when the record has none, and its form power and leg spring stiffness become channels.
- **Step and stroke counts:** `activity.cycles` (steps, strokes or pushes, named by sport) and `activity.avgStrokeDistance`, public like distance, read from FIT sessions and shown with the activity's stats.
- **Notes:** GPX `trk/desc` and TCX `Notes` seed the import screen's description for a single file, as UDDF notes do.
- Only new imports get the new data; nothing is backfilled.

## Open questions

- **HRV's place.** RR intervals are health data. Decide whether they're stored like training load (at the narrower of charts and training visibility, which would need them outside the streams blob) or always Only me, and whether to keep the raw intervals or only a summary.
- **Health channels.** Core temperature, SmO₂ and total hemoglobin are health data like HRV; decide whether they follow HRV or the other streams.
- **Developer fields.** Settle which Connect IQ fields the table recognises and how they're matched (developer data id, app UUID or field name), since apps rename fields between versions.
- **Time zone.** FIT `activity.local_timestamp` gives the device's UTC offset; decide whether to keep it as the activity's zone, which would also show a dive log's start in its own zone.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `feed/activity-file-data`: HRV, `hr` messages, cycling dynamics, physiological and e-bike channels, developer fields, step and stroke counts and notes.
- `feed/activity-export`: the new channels in FIT.

## Impact

- Lexicons: `feed/activityStreams.json` (HRV, the new int channels), `feed/activity.json` (`cycles`, `avgStrokeDistance`); `core/lexicon` models and `ActivityStreamData.INT_CHANNELS`. Use the `lexicon-change` skill.
- `:core-activityfile`: the three FIT decoders (including `iosApp/iosApp/SwiftFitDecoder.swift`) for `hrv` and `hr` messages, developer fields (`field_description`, `developer_data_id`) and the new record and session fields; `TcxParser` and `GpxParser` notes; `DecodedActivityMapper`; `StreamBuilder`; `FitWriter`.
- `core/data`: `ActivityDraft.fromImport`, `ActivityMerge`.
- AppView: the new channels in `bounded`, and `cycles` and `avgStrokeDistance` in the activity index and views.
- UI: cycling dynamics and sensor charts, counts in the activity's stats, HRV.
- `keep-remaining-dive-data` and `keep-workout-structure` also change `feed/activityStreams.json`; implement the three one at a time.
