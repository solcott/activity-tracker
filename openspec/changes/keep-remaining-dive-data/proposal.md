# Proposal

## Why

`store-dive-gas-switches` keeps most of what FIT and UDDF files hold that we used to drop, but leaves some dive data out to keep that change bounded. What it leaves out is still lost at import:

- **Device alerts.** FIT `event` messages of type `dive_alert` (no-deco limit reached, approaching NDL, gas switch prompted, ascent rate, safety stop) and UDDF waypoint `alarm` elements say when the computer warned the diver. Nothing reads them.
- **Dive computer settings.** FIT `dive_settings` holds the deco model, ppO₂ warning, critical and deco limits, closed-circuit setpoints, safety stop and repetitive dive settings. Shearwater's `decoModel` names the model. Only the gradient factors and the water type are kept.
- **Shearwater and Subsurface dive state.** Shearwater records carry `averagePPO2`, `currentNdl`, `ttsMins`, `firstStopDepth` and tank pressures (`tank0pressurePSI`…); Subsurface samples carry `ndl`, `tts`, `stopdepth`, `stoptime`, `cns`, `po2` and per-cylinder `pressure`. Only their gas switch times are kept; the deco and tank channels stay empty for these files.

Data other activity types lose is split into `keep-workout-structure` (sets, legs, laps, swim lengths, ski runs) and `keep-sensor-data` (HRV, heart rate in `hr` messages, new sensor channels, developer fields, counts, notes).

## What Changes

- **Dive alerts:** a new `streamData.diveAlerts` event channel (`{ time, kind }`, an open enum of alert kinds), read from FIT `dive_alert` events and UDDF `alarm`s, warnings marked on the depth chart, and written back as FIT `dive_alert` events and UDDF `alarm`s on export.
- **Dive settings:** new `activity#dive` fields for the deco model and the ppO₂ limits and setpoints, read from FIT `dive_settings` and Shearwater's header, shown with the dive's stats and written back to FIT `dive_settings` on export.
- **Shearwater and Subsurface dive state:** both parsers fill the ppO₂, NDL, TTS, deco stop, CNS and tank pressure channels from the fields above, so a dive from either shows the same charts as a FIT or UDDF dive.
- Only new imports get the new data; nothing is backfilled.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `feed/dive-data`: dive alerts, dive computer settings and Shearwater and Subsurface dive state.
- `feed/activity-export`: FIT dive settings, FIT and UDDF dive alerts.

## Impact

- Lexicons: `feed/activityStreams.json` (`diveAlerts`), `feed/activity.json` (dive settings fields); `core/lexicon` models. Use the `lexicon-change` skill.
- `:core-activityfile`: the three FIT decoders (including `iosApp/iosApp/SwiftFitDecoder.swift`) for `dive_alert` events and `dive_settings`; `UddfParser` alarms; `ShearwaterXmlParser` and `SubsurfaceXmlParser` dive state; `StreamBuilder`; `FitWriter`; `UddfWriter`.
- `core/data`: `ActivityDraft.fromImport`, `DiveSummary`, `ActivityMerge`.
- AppView: `ActivityStreamsViews.bounded` caps the alert list and its tokens.
- UI: alert markers on the depth chart, dive settings in the dive's stats.
- `keep-workout-structure` and `keep-sensor-data`, which also changed `feed/activityStreams.json`, have landed, so nothing else is changing it.
