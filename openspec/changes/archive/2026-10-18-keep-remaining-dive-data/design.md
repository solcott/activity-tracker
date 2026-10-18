# Design

## Context

See proposal.md (Why) for what's still lost. What shapes the approach:

- Every format decodes to a `DecodedActivity`; `DecodedActivityMapper` and `StreamBuilder` (common) turn it into an `ImportedActivity` and its `ActivityStreamData`. The three FIT decoders (Java in `commonJvmMain`, JS in `wasmJsMain`, Swift in `iosApp/iosApp/SwiftFitDecoder.swift`) only translate SDK messages into `FitDiveMessages`; `FitDives` interprets them.
- Dive state is already a set of `DecodedSample` fields (`ppO2Bar`, `cnsPercent`, `ndlSeconds`, `nextStopDepthMeters`, `nextStopSeconds`, `ttsSeconds`) that `StreamBuilder` turns into int channels, and tank pressure is `DecodedActivity.tankUpdates` keyed by `DiveTank.sensor`. FIT and UDDF fill them; Shearwater and Subsurface don't.
- Event-like stream data (`gears`, `gasSwitches`) sits in the stream blob as a list beside the int channels, not in `INT_CHANNELS`. `ActivityMerge` handles those lists by hand (`fillMissing`, `addsChannels`, `shifted`), as does the AppView's `bounded`.
- The Java FIT SDK (21.218) names 39 `DiveAlert` values plus `INVALID`, and `DiveSettingsMesg` has `model` (`TissueModelType`, only `ZHL_16C`), `po2Warn`/`po2Critical`/`po2Deco` (bar), `safetyStopEnabled`/`safetyStopTime`, and `ccrLowSetpoint`/`ccrHighSetpoint` (bar) with their switch modes and depths. The decoder already keeps the first `dive_settings` and listens for `EventMesg` (`DIVE_GAS_SWITCHED`).
- UDDF 3.2 waypoint `alarm` text is one of `ascent`, `breath`, `deco`, `error`, `link`, `microbubbles`, `rbt`, `skincooling`, `surface`. `UddfSchemaOrderTest` already knows where `alarm` sits in a waypoint.
- Shearwater Cloud XML records (every 10 s) carry `averagePPO2` (bar), `currentNdl` and `ttsMins` (minutes), `firstStopDepth` (file units, 0 outside deco) and `tank0pressurePSI`…`tank3pressurePSI` (PSI in either unit system, or text such as `AI is off` / `N/A`). The header's `decoModel` is libdivecomputer's numbering: 0 GF, 1 VPM-B, 2 VPM-B/GFS, 3 DCIEM.
- Subsurface samples write `ndl`, `tts`, `stopdepth`, `stoptime`, `cns`, `po2` and `in_deco` only when they change, like `temp`; cylinder pressure is `pressure` (older files, the first cylinder) or `pressureN` with `sensorN` naming the cylinder.

## Goals / Non-Goals

**Goals:**
- A Shearwater or Subsurface dive charts the same deco and tank channels as a FIT or UDDF dive with the same data, through the existing `DecodedSample` and `tankUpdates` paths, with no new channel.
- Alerts and settings survive a FIT export and re-import; alerts also survive UDDF.

**Non-Goals:**
- Subsurface `event`s other than `gaschange` (its own alarm events vary by computer) and Subsurface `extradata`.
- UDDF dive settings: UDDF has no field for the deco model or ppO₂ limits we'd round-trip reliably.
- FIT `dive_settings` fields that describe the watch rather than the dive (backlight, sounds, heart rate source, apnea countdown, gas consumption display, setpoint switch modes and depths, repeat dive interval, last stop multiple, no-fly mode).
- FIT `dive_alarm` messages (the diver's configured depth/time/speed alarms): they're settings, and only the `dive_alert` events that fired say what happened.
- No AppView indexing of the new fields; no backfill.

## Decisions

### Alerts are an event list beside gas switches
`streamData.diveAlerts: [{ time, kind }]`, in time order, `kind` an open-enum string (`maxLength` 64). Like `gasSwitches`, an alert is a moment, not a per-sample value; a per-sample `alert` int channel would be mostly null and couldn't hold two alerts in one second. `StreamChannel.DIVE_ALERTS` joins `channels.knownValues`. Decoders pass `DecodedDiveAlert(timestampMillis, kind)`; `StreamBuilder` turns times into seconds since the start. Dismissals (`alert_dismissed_by_key`, `alert_dismissed_by_timeout`) are dropped in `FitDives`: they say the diver pressed a button, not that something happened.

### Alert tokens follow FIT, spelled like `LapTrigger`
The token set is FIT's `dive_alert` names in lower camel case (`ndlReached`, `approachingNdl`, `po2Warn`, `ascentCritical`, `safetyStopComplete`, …), like `LapTrigger`'s `heartRate`. FIT is the larger vocabulary and what most dives come from. UDDF alarms map onto it where the meaning matches (`ascent` → `ascentCritical`, `deco` → `decoCeilingBroken`, `surface` → `nearSurface`) and otherwise get their own tokens (`breath`, `error`, `link`, `microbubbles`, `rbt`, `skinCooling`). One table (`DiveAlerts`, common, in `:core-activityfile`) holds each token with its FIT value, its UDDF alarm (if any) and whether it's a warning; the readers and writers all go through it, and `core/lexicon` holds the token constants as an open enum like `WaterType`.

A FIT value the table lacks is stored as the SDK's name in lower camel case, or its number when the SDK doesn't name it; it's kept but neither charted nor exported. Alternative: drop unknown alerts. Rejected: the spec keeps them, and an SDK bump can name them later without a reimport.

### The chart marks warnings only
Warnings are the alerts that say something went wrong or nearly did: NDL reached or approaching, ppO₂ warn/critical high and low, CNS and OTU warning/critical, ascent critical, deco ceiling broken, safety stop broken, depth and time alerts, battery and tank battery low/critical, diluent ppO₂ low, and UDDF's own alarms. Informational ones (gas switch prompted, safety stop started/complete, deco complete, deco stop cleared, approaching first deco stop, setpoint switches, open/closed circuit switches, near surface, apnea cues) are stored and exported but not drawn: a typical dive logs several, and they'd crowd the gas switch markers. The presenter (`ChartsBuilder`) filters with the table's warning flag into an `alerts` list on `ActivityCharts`; the depth chart draws them as labelled markers in a warning colour, distinct from gas switches.

### Settings are flat fields on `activity#dive`
`decoModel` (open enum: `zhl16c`, `vpmB`, `vpmBGfs`, `dciem`), `ppO2Warn`, `ppO2Critical`, `ppO2Deco`, `lowSetpoint`, `highSetpoint` (millibar) and `safetyStopTime` (seconds). Flat, like `gradientFactorLow`/`High` and `waterType`, which they sit beside; a nested `settings` object would split related fields in two places. They're public: they describe the computer's configuration, not the diver or a place. A safety stop that was off has no `safetyStopTime` (rather than a separate boolean): the page shows nothing either way. Shearwater's `decoModel` 0 means Bühlmann ZHL-16C with gradient factors, so it maps to `zhl16c`. FIT `TissueModelType` names only `ZHL_16C`; export writes `model` only for that token.

`DecodedDive` and `ImportedDive` gain the same fields in SI units (bar); `DiveSummary.of` converts to millibar.

### Shearwater and Subsurface fill the existing sample fields
Neither parser needs new types for dive state: each fills `DecodedSample.ppO2Bar`, `ndlSeconds`, `nextStopDepthMeters`, `nextStopSeconds`, `ttsSeconds` and `cnsPercent`, and `StreamBuilder` does the rest as for UDDF.

- **Shearwater:** `firstStopDepth` of 0 is "not in deco", so the stop depth stays null; the stop time is unknown (the file has no per-stop time). `currentNdl` is stored as recorded, 99 included: the computer caps its display there, and the spec stores what the computer recorded. Depths convert with the header's units.
- **Subsurface:** values persist until the next sample that carries the attribute, so the parser carries each one forward (the way the computer meant it) and resets the stop depth and time to null at a sample with `in_deco='0'`.

Tank pressure goes through `tankUpdates` so the nearest-sample placement and series order are shared. Shearwater gets a `DiveTank` per transmitter slot with any numeric reading (sensor `"0"`…`"3"`), start and end from its first and last readings, like FIT's no-`tank_summary` path. Subsurface names each update by cylinder index, which already orders `dive.tanks`; a cylinder with readings but no start/end attributes becomes a tank from its readings.

### Merging and the AppView treat alerts like gas switches
`ActivityMerge.fillMissing` takes the other recording's `diveAlerts` when the dive has none, `shifted` moves them and drops ones before the new start, and `addsChannels` counts them. Settings ride the existing per-field dive merge. `bounded` caps `diveAlerts` at 500 and each `kind` at 64 characters, as it does for laps' tokens.

## Risks / Trade-offs

- [The JS or Swift FIT SDK may name `dive_alert` values differently, or not decode `event.data` as an enum for `dive_alert`] → each decoder passes the raw `data` number and `FitDives` resolves it through the table by FIT value, so the SDKs' spellings don't matter.
- [Shearwater's NDL cap of 99 minutes reads as a real 99-minute limit on the chart] → acceptable: it's what the computer showed; the chart already caps the NDL axis. Revisit if it looks wrong on real dives.
- [Subsurface carry-forward could stretch a stale value across a sample gap] → carry only within the dive's own samples and never past the last sample; the merge's nearest-sample rules are unaffected.
- [`DiveAlerts` table drifts from a future SDK] → unknown values are kept by name or number, so nothing is lost; a test checks every FIT `DiveAlert` the Java SDK names (except `INVALID`) is in the table.

## Migration Plan

Only new imports get the new data. The lexicon changes are optional fields, so older clients and the AppView pass them through; republish the local lexicons with the `lexicon-change` skill's steps. `keep-workout-structure` and `keep-sensor-data` are archived, so `feed/activityStreams.json` has no competing change in flight.
