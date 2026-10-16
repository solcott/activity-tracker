# Proposal

## Why

Divers keep their logbook in dive software (Subsurface, MacDive, Diving Log, Shearwater Cloud), and those read UDDF, not GPX or TCX. `export-activity-files` exports a dive as GPX (depth and water temperature in Garmin extensions) or TCX (no depth at all), which a logbook can't import as a dive. We already read UDDF (`UddfParser`), so the format is known, and the dive's summary, gases and depth profile are all stored.

## What Changes

- Export offers UDDF on the athlete's own dives, next to GPX and TCX. Activities of other sports don't offer it.
- The UDDF file is one dive in one repetition group, rebuilt from what the app stores:
  - `informationbeforedive`: date and time, dive number and surface interval
  - `samples`: a `waypoint` per stream sample with `divetime`, `depth` and `temperature`, plus `switchmix` at each stored gas switch (`streamData.gasSwitches`, kept since `store-dive-gas-switches`), and the stored ppO₂, CNS, no-deco time, deco stops, heart rate and per-tank pressure where the dive has them
  - `informationafterdive`: greatest and average depth, duration, lowest temperature
  - `gasdefinitions`: a `mix` per gas breathed (O₂ and He fractions), and the dive mode when closed circuit
  - `divesite`: the site name, with the track's first position as the site's coordinates, since only the athlete reads the file. A dive with no GPS track still has one since `store-dive-gas-switches`: its entry and exit, or the logbook site's position, kept as a two-point track in `self`
  - `diver/owner/equipment`: the dive computer named by the record's `device`
- The rebuilt-file note applies. Deco state, tank pressure over time and gas switches are stored only for dives imported since `store-dive-gas-switches` (nothing is backfilled), so an older dive's file lacks them.
- Exporting several dives into one logbook file is out of scope (one dive per export, as for other formats).

## Open questions

- **Local time.** UDDF `datetime` has no zone, and imports read it in a chosen zone (`parseLocalDateTime`, `ImportedActivity.localStart`). The record stores `startedAt` as an instant. Decide whether the export writes the dive's time in the device's current zone, or whether the dive's zone needs to be kept (which would be a lexicon change and belongs in its own change).
- **Gas switches.** Answered by `store-dive-gas-switches`: the streams keep each switch's time and gas, the first gas as a switch at 0, so the export writes a `switchmix` per stored switch. An older dive with no stored switches writes none.
- **Merged dives.** A dive merged from a logbook and a FIT import may have gases from one and samples from the other; check the export reads consistently after `ActivityMerge`.

## Capabilities

### New Capabilities

### Modified Capabilities
- `feed/activity-export`: adds UDDF for dives to the formats offered and what a UDDF export holds. The capability is added by `export-activity-files`; archive that change first.

## Impact

- `:core-activityfile`: a `UddfWriter` from `ExportActivity` plus the dive's summary and gases (`ActivityDive`), tested by reading its output back with `UddfParser` and checking the summary, gases and samples match.
- `core/data`: `ExportFormat.UDDF` (`application/xml`, `.uddf`), offered only for dives, and passing the record's `dive` into the export.
- Detail screen: UDDF in the format list for dives; no new hand-off.
- No lexicon or AppView change, unless the local-time question needs the dive's zone stored. Depends on `export-activity-files`.
