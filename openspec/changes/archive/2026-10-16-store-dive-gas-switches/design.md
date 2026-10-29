# Design

## Context

See proposal.md for what's dropped today and why. The constraints that shape the approach:

- Every format decodes to a `DecodedActivity`, and `DecodedActivityMapper` (common) turns it into an `ImportedActivity`. The three FIT decoders (Java in `commonJvmMain`, JS in `wasmJsMain`, Swift in `iosApp/iosApp/SwiftFitDecoder.swift`) only translate SDK messages; anything they add must be added to all three, with the interpreting in common code (`FitDives`, the mapper, `StreamBuilder`).
- `ActivityDraft.fromImport` decides what's published. Today it drops `ImportedActivity.power`, `shifting`, `mtb`, cadence, elevation loss, and the dive's `entry`/`exit`.
- Stream data is a JSON blob (`feed.activityStreams#streamData`) whose arrays line up with `time`. Blob contents aren't lexicon-validated, but `channels` on the record is, with `maxLength: 16`; 9 channels exist.
- `feed.activityZones` already sits at the narrower of charts and training visibility, moves with both (`activity-zones` spec), and is written only when zone snapshots exist.
- The AppView keeps each record's JSON (`record` column) and stream channels as a JSON array, and `AtJson` ignores unknown keys, so new optional fields pass through. Views that rebuild a typed object from columns are the exception to check.
- `ActivityMerge` lists every channel by hand in `fillMissing`, `addsChannels` and `shifted`, and `ActivityStreamData.channels` lists them again. That grows to about 25 channels here.
- `FitWriter` writes only messages we choose, with field numbers and scales from `FitProfile`, and jvmTests check it by decoding with the Java SDK.

## Goals / Non-Goals

**Goals:**
- One place per layer that knows the channel list, so adding a channel is one entry, not four edits.
- Each new value has one unit, chosen once, and is stored at a visibility that matches what it reveals.
- Every FIT field we store survives a FIT export and re-import.

**Non-Goals:**
- No new record types; everything fits on `feed.activity`, `feed.activityStreams` and `feed.activityZones`.
- No AppView indexing of the new fields (no queries filter or sort on them).
- No re-reading of earlier imports; `keep-original-activity-files` is what makes that possible later.

## Decisions

### Gas switches name the gas, not an index
`gasSwitches: [{ time, oxygen, helium }]`. An index into `dive.gases` would break when `ActivityMerge` fills the record's gases from one file and the streams' switches from another. The FIT decoders pass each `dive_gas_switched` event's timestamp along with its `data` (the gas index), so `FitDiveMessages.gasSwitches` becomes `List<FitGasSwitch(timestampMillis, gasIndex)>`; `FitDives` resolves the index to the gas and drops switches to unknown slots. `gasesBreathed` keeps working from the same list. XML parsers record a switch when the sample's gas differs from the last one (Shearwater), at `switchmix` (UDDF) and at `gaschange` events (Subsurface). `StreamBuilder` turns `DecodedActivity.gasSwitches` into the channel, adding the first gas at time 0 when the file didn't switch to it explicitly.

### Dive state as ordinary integer channels
ppO₂ (mbar), CNS (%), N₂ load (%), NDL (s), deco stop depth (cm), deco stop time (s), TTS (s) are plain `List<Int?>` channels on `DecodedSample` and `ActivityStreamData`, like depth. Alternative: one `deco` object per sample. Rejected: every other per-sample value is a flat array, the charts and merge treat channels uniformly, and a dive without deco data simply lacks the arrays. FIT's NDL is 0 while in deco and the next stop is set; we store what the computer recorded and let the UI read "in deco" from a non-null stop depth.

Measured ppO₂ wins over calculated (UDDF has both; a rebreather's cells are the truth), averaged over cells, matching what dive software shows.

### Tank pressure as a list of series
`tankPressures: [{ pressure: [Int?] }]`, one entry per tank in the order of `dive.tanks`, each array aligned with `time`. FIT's `tank_update` messages arrive on their own timestamps, keyed by sensor; the decoders pass them as `DecodedTankUpdate(timestampMillis, sensor, pressureBar)`, and common code orders sensors as their `tank_summary` messages do and places each update on the nearest sample within `ActivityMerge.MAX_GAP_SECONDS`. UDDF's `tankpressure ref` names the `tankdata` it belongs to. Alternative: `tankPressure1..N` channels. Rejected: arbitrary tank counts, and a series list is how the chart draws them.

### Laps live in the stream blob
`laps: [{ start, elapsed, moving, distance, avgSpeed, maxSpeed, avgHeartRate, maxHeartRate, avgPower, avgCadence, ascent, descent }]` in lexicon units, beside `gears`. Alternative: a `feed.activityLaps` record. Rejected: a lap's splits reveal what the streams already reveal, so they share the streams' visibility for free, with no new record, collection, space declaration or move logic. A file whose only lap spans the session (within a second) stores none, so "has laps" means real laps.

### Public summary fields on the activity
Elevation loss (m), avg/max cadence (rpm or spm), avg/max/normalized power (W), work (kJ), calories (kcal), front and rear shifts, jumps, grit and flow (both in tenths) go on `feed.activity`, shown to anyone who sees the activity. `ActivityDraft.fromImport` maps them from `ImportedActivity` (`power`, `shifting`, `mtb`, cadence, elevation loss), and `ActivityMerge.fillGaps` fills them.

### Training load on the zones record
FTP (W), intensity factor (hundredths), TSS (tenths), aerobic and anaerobic training effect (tenths) go in a `#trainingLoad` object on `feed.activityZones`. FTP is a threshold outright, and IF and TSS give it away with normalized power, so they belong with zone bounds, which already live at the narrower of charts and training visibility and already move when either changes. Alternative: public fields on the activity. Rejected: it would publish thresholds an athlete set to Only me. Alternative: a new record. Rejected: it would duplicate the zones record's placement and move logic exactly. The zones record is now written when there are zone snapshots or a training load.

### Dive positions as the private track, without a polyline
A dive with no GPS records gets its track (`feed.activityTrack`, `self` space) built from `ImportedDive.entry` and `exit` (FIT session start and end positions), else the UDDF site's position, timed at the dive's start and end. `ActivityDraft` gets the GPX from these points but leaves `route` empty, so no polyline is made and nothing reaches the record. Export then offers GPX for such a dive, and `export-dives-as-uddf` can use the track's first position as the site's coordinates, as its proposal plans.

### One channel table per layer
`ActivityStreamData` gains a channel table (token, getter, copy-with) in `core/lexicon`, and `channels`, `ActivityMerge.fillMissing`/`addsChannels`/`shifted` and `ExportPoints` iterate it instead of listing fields. Event-like channels (`gears`, `gasSwitches`, `laps`) have their own small shift and fill rules, since they're keyed by time rather than aligned with it.

### Channel limit
`activityStreams.channels.maxLength` goes from 16 to 32. Raising a maximum loosens validation, so older records stay valid; older clients ignore tokens they don't know (`knownValues` is open). The local stack's published lexicons need republishing (`docker/scripts/publish-lexicons.sh`).

### FIT export writes it back
`FitWriter` adds, from `FitProfile`: the new `record` fields, a `dive_gas_switched` event per switch (data: the `dive_gas` message index of that gas, slots written in `dive.gases` order), `tank_update` messages per tank (a synthetic sensor id per tank, matching its `tank_summary`), stored laps instead of the single lap, the new session and lap fields, the dive summary's new fields and `dive_settings.water_type`/`water_density`. Training load goes in only because export is owner-only. The round-trip jvmTests extend to each.

### Units

| Value | Unit |
|---|---|
| ppO₂, highest ppO₂, tank pressure | millibar |
| CNS, N₂ load | percent |
| NDL, deco stop time, TTS, descent time, no-fly, desaturation | seconds |
| Deco stop depth, visibility | centimeters |
| Ascent and descent rates | millimeters per second |
| Air temperature | tenths of °C |
| Altitude | meters |
| Water density | kg/m³ |
| Tank volume | milliliters |
| Respiration rate | tenths of a breath per minute |
| Vertical oscillation, step length | millimeters |
| Stance time | milliseconds |
| Vertical ratio, stance time balance | hundredths of a percent |
| Left/right balance | percent from the right leg |

## Risks / Trade-offs

- [The Swift or JS FIT SDK pinned here lacks a newer field such as `po2` or `tank_update`] → Check each SDK's profile version first (task 2.1); a field missing on one platform is passed as null there, and the platform test notes it.
- [Stream blobs grow with ~15 more channels] → Each array is one int per sample; a 2-hour run at 1 Hz adds about 50 KB of JSON per channel, well under the 5 MB blob limit. Absent channels cost nothing.
- [Calories and power are now public on Public activities] → They're ordinary public summary figures; thresholds and load stay with the zones.
- [Two-point dive tracks make GPX export available for dives] → Intended: it's the only way the positions leave the app, and only for the owner.
- [A merge could pair a record's tanks from one file with tank pressure series from another] → `fillMissing` adds `tankPressures` only when the dive has no tanks of its own (the record then takes the other's tanks too) or the same number of tanks, else leaves them out.
- [Notes prefilling the description publish personal text] → The athlete sees and edits it on the import screen before publishing; nothing is published without that step.

## Migration Plan

No data migration and no Flyway migration: new fields are optional and earlier records stay valid. Republish the local lexicons and sign in again after the lexicon change. Older clients ignore the new fields and channels. Rollback is reverting the client; published records with the new fields stay readable.

## Open Questions

- Whether the feed card shows power or calories for rides, or only the detail page. Doesn't change what's stored.
- Chart colors and order for the new dive charts; settled when building the UI.
