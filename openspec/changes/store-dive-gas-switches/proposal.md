# Proposal

## Why

A dive keeps the gases breathed (`activity#dive.gases`, in order of first use) but not when each was switched to. Every format we import knows: FIT `dive_gas_switched` events, Subsurface `gaschange` events, UDDF `switchmix` in a waypoint, and Shearwater's per-sample `fractionO2`/`fractionHe`. All parsers keep only the order and drop the time. So the depth chart can't show where a diver went onto deco gas, and an exported file can't say it either: `export-fit-files` writes every gas but no switch events, so re-importing the FIT gives back only the first gas, and `export-dives-as-uddf` can't write `switchmix`.

## What Changes

- New stream channel: `activityStreams#streamData.gasSwitches`, an array of `{ time, oxygen, helium }` (seconds since `startedAt`, whole percents), like `gears`. It names the gas itself rather than an index into `dive.gases`, since the record and its streams are separate records that a merge (`ActivityMerge`) can fill from different files. The first gas is a switch at time 0. A `gasSwitches` channel joins `StreamChannel`.
- Import keeps each switch's time: the FIT decoders (Java, JS and Swift) pass the event's timestamp with its gas index, `FitDives` resolves it to the gas, and the Subsurface, UDDF and Shearwater parsers record the time of each change. `StreamBuilder` turns them into `gasSwitches`. `dive.gases` stays as it is, derived as now.
- The dive's depth chart marks each switch with the gas's name (Air, EAN50, Tx18/45).
- FIT export writes a `dive_gas_switched` event at each switch, pointing at that gas's `dive_gas` slot, so a re-imported FIT gives back every gas. The `export-fit-files` spec's round-trip caveat for later gases goes away.
- UDDF export (`export-dives-as-uddf`), if it lands first or after, writes `switchmix` at each switch; its open question about gas switches is answered by this.
- `ActivityMerge` fills `gasSwitches` like any other channel it lacks.
- Only new imports get switch times; nothing is backfilled. An older dive exports as before, with later gases written but not switched to.

## Capabilities

### New Capabilities
- `feed/dive-gas-switches`: keeping when a dive switched gases, showing it on the depth chart, and writing it into exported files.

### Modified Capabilities
- `feed/activity-export`: a FIT export of a dive with stored switches writes them, and re-imports with every gas. Depends on `export-fit-files` being archived.

## Impact

- Lexicons: `feed/activityStreams.json` (`streamData.gasSwitches`, a `#gasSwitch` def), `core/lexicon` (`ActivityStreamData`, `GasSwitch`, `StreamChannel`), AppView stream indexing and `getActivityStreams` passing the channel through. Use the `lexicon-change` skill.
- `:core-activityfile`: `DecodedActivity`/`FitDiveMessages` switch times, all three FIT decoders (including `iosApp/iosApp/SwiftFitDecoder.swift`), the dive-log parsers, `StreamBuilder`, `ExportPoints`/`ExportActivity`, `FitWriter`.
- `core/data`: `ActivityMerge` channel fill.
- UI: switch markers on the dive's depth chart.
- Builds on `export-fit-files`; implement after it.
