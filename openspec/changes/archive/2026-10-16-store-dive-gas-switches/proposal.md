# Proposal

## Why

A dive keeps the gases breathed (`activity#dive.gases`, in order of first use) but not when each was switched to. Every format we import knows: FIT `dive_gas_switched` events, Subsurface `gaschange` events, UDDF `switchmix` in a waypoint, and Shearwater's per-sample `fractionO2`/`fractionHe`. All parsers keep only the order and drop the time. So the depth chart can't show where a diver went onto deco gas, and an exported file can't say it either: `export-fit-files` writes every gas but no switch events, so re-importing the FIT gives back only the first gas, and `export-dives-as-uddf` can't write `switchmix`.

Gas switches are one case of a wider loss. FIT and UDDF files hold much more that we either never read or read and then drop at publish:

- **Read, then dropped.** The import screen shows power (avg, max, NP, work, FTP, IF, TSS), cadence, descent, shift counts and mountain bike dynamics (`ImportedActivity.power`, `shifting`, `mtb`), and a dive's entry and exit positions (`ImportedDive.entry`/`exit`), but `ActivityDraft.fromImport` publishes none of them.
- **Never read from FIT.** Laps (decoded, then unused), calories, training effect, a dive's per-sample decompression state (`record` ppO₂, NDL, next stop depth and time, time to surface, CNS and N₂ load), tank pressure over time (`tank_update`), the dive summary's N₂ loading, ascent and descent rates and descent time, the water type, and per-sample respiration rate, running dynamics and left/right power balance.
- **Never read from UDDF.** Per-waypoint tank pressure, ppO₂, CNS, no-deco time, deco stops and heart rate; the dive's air temperature, visibility, altitude, no-fly and desaturation times and highest ppO₂; each tank's volume and the gas in it; and the diver's notes.

Once an import is published, all of this is gone: nothing re-reads the file.

## What Changes

**Gas switches**
- New stream channel: `activityStreams#streamData.gasSwitches`, an array of `{ time, oxygen, helium }` (seconds since `startedAt`, whole percents), like `gears`. It names the gas itself rather than an index into `dive.gases`, since the record and its streams are separate records that a merge (`ActivityMerge`) can fill from different files. The first gas is a switch at time 0.
- Import keeps each switch's time: the FIT decoders (Java, JS and Swift) pass the event's timestamp with its gas index, `FitDives` resolves it to the gas, and the Subsurface, UDDF and Shearwater parsers record the time of each change. `dive.gases` stays as it is, derived as now.
- The dive's depth chart marks each switch with the gas's name (Air, EAN50, Tx18/45).

**Dive state over time** (FIT and UDDF)
- New stream channels: ppO₂, CNS, N₂ load, no-deco limit, deco stop depth and time, time to surface, and tank pressure per tank. The dive page charts them: the deco stop as a ceiling under the depth line, ppO₂, CNS and tank pressure as their own charts.
- UDDF waypoint heart rate fills the existing heart rate channel.

**More dive summary** (public `activity#dive`)
- From FIT: start and end N₂ load, average and maximum ascent and descent rates, descent time, and water type and density.
- From UDDF: air temperature, visibility, altitude, no-fly and desaturation times, highest ppO₂.
- Each `diveTank` gains its volume and the gas it held.
- A UDDF dive's notes fill the import screen's description, for the athlete to keep or edit before publishing.
- A dive with no GPS track keeps its entry and exit positions (or a logbook site's position) as a two-point track in the athlete's `self` space, never in the public record.

**Activity data from FIT**
- New public summary fields on `feed.activity`: elevation loss, average and maximum cadence, average, maximum and normalized power, work, calories, front and rear shift counts, and jumps, grit and flow.
- Training load (FTP, intensity factor, TSS, aerobic and anaerobic training effect) reveals thresholds and fitness, so it goes on the activity's `feed.activityZones` record, which is already stored at the narrower of charts and training visibility, rather than on the public activity.
- New stream channels: respiration rate, running dynamics (vertical oscillation, stance time, step length, vertical ratio, stance time balance) and left/right power balance, charted on the detail page.
- Laps: `streamData.laps`, each lap's start, elapsed and moving time, distance, speed, heart rate, power, cadence, ascent and descent. The detail page shows a lap table. Laps share the streams' visibility.

**Export, merge and limits**
- FIT export writes everything above that FIT can hold: `dive_gas_switched` events, the new record fields, `tank_update` messages, the stored laps (one lap only when none are stored), and the new session and dive summary fields. A re-imported FIT gives back every gas, lap and channel. The `export-fit-files` spec's round-trip caveat for later gases goes away.
- UDDF export (`export-dives-as-uddf`), whichever lands first, writes `switchmix` at each switch; its open question about gas switches is answered by this.
- `ActivityMerge` fills each new summary field and stream channel, `gasSwitches` and `laps` like any other it lacks.
- `activityStreams.channels` allows up to 32 channels, up from 16.
- Only new imports get the new data; nothing is backfilled. An older dive exports as before.

**Out of scope** (in the backlog, planned in `keep-remaining-file-data`): device alerts and alarms, dive computer settings beyond water type (ppO₂ limits, setpoints, safety stop), per-sample SAC, RMV and air time remaining (derivable or device-estimated), swim lengths, HRV, the dive's rating, buddy names (other people's data, never published), TCX and GPX laps, and the extra per-sample dive state Shearwater and Subsurface files carry (only their gas switch times are in scope).

## Capabilities

### New Capabilities
- `feed/dive-gas-switches`: keeping when a dive switched gases, showing it on the depth chart, and writing it into exported files.
- `feed/dive-data`: keeping a dive's decompression state and tank pressure over time, the extra summary fields, notes and entry and exit positions from FIT and UDDF files, and showing them.
- `feed/activity-file-data`: keeping an imported FIT activity's power, cadence, calories and other summary fields, its training load, its extra stream channels and its laps, and showing them.

### Modified Capabilities
- `feed/activity-export`: a FIT export writes gas switches, laps, the new channels and summary fields, and re-imports with every gas and lap; rebuilt files no longer say they lack laps when laps are stored.
- `feed/activity-zones`: the zones record also carries the activity's training load, and is published for training load even without zone snapshots.

## Impact

- Lexicons: `feed/activityStreams.json` (new channels, `#gasSwitch`, `#tankPressure`, `#lap`, `channels.maxLength`), `feed/activity.json` (summary fields, `#dive` and `#diveTank` fields), `feed/activityZones.json` (`#trainingLoad`); `core/lexicon` models, `StreamChannel`. Use the `lexicon-change` skill. The AppView stores records and stream blobs as JSON, so it passes the fields through; check its views and the `getActivityStreams` channel list.
- `:core-activityfile`: `DecodedActivity`/`DecodedSample`/`DecodedSummary`, `FitDiveMessages`, all three FIT decoders (including `iosApp/iosApp/SwiftFitDecoder.swift`), `UddfParser`, the Subsurface and Shearwater parsers (switch times only), `StreamBuilder`, `DecodedActivityMapper`, `ExportPoints`/`ExportActivity`, `FitWriter`/`FitProfile`.
- `core/data`: `ActivityDraft.fromImport`, `DiveSummary`, `ActivityMerge`, `ActivityZonesStore` (training load), the dive track from entry/exit (`ActivityTrackStore`).
- UI: switch markers and deco ceiling on the depth chart, new dive and activity charts, ride and run summary stats on the detail page and feed card, a lap table, dive stats.
- Builds on `export-fit-files` (archived); coordinates with `export-dives-as-uddf` and `keep-original-activity-files`.
