# Proposal

## Why

`store-dive-gas-switches` keeps most of what FIT and UDDF files hold that we used to drop, but leaves some data out to keep that change bounded. What it leaves out is still lost at import:

- **Device alerts.** FIT `event` messages of type `dive_alert` (no-deco limit reached, approaching NDL, gas switch prompted, ascent rate, safety stop) and UDDF waypoint `alarm` elements say when the computer warned the diver. Nothing reads them.
- **Dive computer settings.** FIT `dive_settings` holds the deco model, ppO₂ warning, critical and deco limits, closed-circuit setpoints, safety stop and repetitive dive settings. Shearwater's `decoModel` names the model. Only the gradient factors and, after `store-dive-gas-switches`, the water type are kept.
- **Swim lengths.** A pool swim's FIT `length` messages carry each length's stroke type, stroke count and time, and its session the pool length. We keep only the session summary.
- **HRV.** FIT `hrv` messages hold beat-to-beat (RR) intervals. Nothing reads them.
- **Laps in TCX and GPX exports.** `store-dive-gas-switches` stores laps and writes them to FIT, but TCX still writes one lap spanning the activity, and GPX one track segment.
- **Shearwater and Subsurface dive state.** Shearwater records carry `averagePPO2`, `currentNdl`, `ttsMins`, `firstStopDepth` and tank pressures (`tank0pressurePSI`…); Subsurface samples carry `ndl`, `tts`, `stopdepth`, `stoptime`, `cns`, `po2` and per-cylinder `pressure`. Only their gas switch times are kept; the deco and tank channels `store-dive-gas-switches` adds stay empty for these files.

## What Changes

- **Dive alerts:** a new `streamData.diveAlerts` event channel (`{ time, kind }`, an open enum of alert kinds), read from FIT `dive_alert` events and UDDF `alarm`s, and marked on the depth chart.
- **Dive settings:** new `activity#dive` fields for the deco model and the ppO₂ limits and setpoints, read from FIT `dive_settings` and Shearwater's header, shown with the dive's stats and written back to FIT `dive_settings` on export.
- **Swim lengths:** pool length on the activity (public, like distance) and a `streamData.lengths` list (`{ start, elapsed, stroke, strokes }`) at the streams' visibility, read from FIT `length` messages, shown as a lengths table with SWOLF on the swim's page, and written back to FIT.
- **HRV:** RR intervals as a stream at the athlete's training visibility (see Open questions), read from FIT `hrv` messages, with RMSSD shown to viewers who may see it.
- **TCX and GPX laps:** TCX writes a `Lap` per stored lap with its own time, distance and heart rate summary; GPX writes a `trkseg` per lap. Round-trip tests read them back.
- **Shearwater and Subsurface dive state:** both parsers fill the ppO₂, NDL, TTS, deco stop, CNS and tank pressure channels from the fields above, so a dive from either shows the same charts as a FIT or UDDF dive.
- Only new imports get the new data; nothing is backfilled.

## Open questions

- **HRV's place.** RR intervals are health data. Decide whether they're stored like training load (at the narrower of charts and training visibility, which would need them outside the streams blob) or always Only me, and whether to keep the raw intervals or only a summary.
- **Alert kinds.** FIT's `dive_alert` enum and UDDF's alarm types differ; settle the open enum's tokens and which alerts the chart shows (all, or only warnings).
- **GPX laps.** GPX has no lap element; check that Strava and Garmin Connect read a `trkseg` per lap as laps before writing them, or leave GPX at one segment.

## Capabilities

### New Capabilities
- `feed/swim-lengths`: keeping a pool swim's lengths and pool length, and showing them.

### Modified Capabilities
- `feed/dive-data` (added by `store-dive-gas-switches`): dive alerts, dive computer settings and Shearwater and Subsurface dive state.
- `feed/activity-file-data` (added by `store-dive-gas-switches`): HRV.
- `feed/activity-export`: TCX and GPX laps, FIT swim lengths and dive settings.

## Impact

- Lexicons: `feed/activityStreams.json` (`diveAlerts`, `lengths`, HRV), `feed/activity.json` (pool length, dive settings fields); `core/lexicon` models. Use the `lexicon-change` skill.
- `:core-activityfile`: the three FIT decoders (including `iosApp/iosApp/SwiftFitDecoder.swift`) for `dive_alert` events, `dive_settings`, `length` and `hrv`; `UddfParser` alarms; `ShearwaterXmlParser` and `SubsurfaceXmlParser` dive state; `StreamBuilder`; `TcxWriter`, `GpxWriter` and `FitWriter`.
- `core/data`: `ActivityDraft.fromImport`, `DiveSummary`, `ActivityMerge`.
- UI: alert markers on the depth chart, dive settings in the dive's stats, a lengths table, HRV.
- Depends on `store-dive-gas-switches`; implement after it is archived.
