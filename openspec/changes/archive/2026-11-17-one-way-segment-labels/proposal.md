# Proposal

## Why

Trails are often one-way for one sport and two-way for others. In Corner Canyon, Clarks is uphill-only for bikes and open both ways on foot, and the importer already honours that through `oneway:bicycle`. The app barely says so: the ride row reads "Clarks · 1.42 mi · Ride", and the run row reads "… · Run, Hike · Both ways". The ride segment's sport and direction switcher offers a single direction with no explanation.

The app can't infer one-way from a missing direction. `publish` publishes only the candidates athletes have ridden, so a two-way trail often has just one direction published so far. Athlete-made segments come in one direction too. The rule has to come from the data the importer reads. Separately, compass names such as "(eastbound)" mean little on a trail that climbs: riders say "up Clarks", not "east on Clarks".

## What Changes

- **New `feed.segment#oneWay`** (optional boolean). The segment importer sets it on a segment whose sport group may travel the line only in this direction under the source data's rules (`oneway`, `oneway:bicycle` for rides). The importer's staged candidates (`importer.defs#candidate`) carry it, and so does its candidates file.
- **New `segmentView.oneWay`.** The AppView serves it as true only for a segment in the importer account's repo whose record says so. An athlete's segment is never one-way, whatever its record holds.
- **A shared climb rule** in `core/location`. A line climbs, descends or neither, judged from its gain, loss and length. It counts as uphill or downhill only when it clearly climbs or descends; otherwise the direction is its compass heading (or clockwise and counterclockwise for a loop). The clients judge from the elevation a segment view serves. The importer judges from terrain.
- **Rows say which sport is one-way.** A browse or Trail page row whose sport group travels its line one way only says so: "Uphill only", "Downhill only", or "Eastbound only" when the line neither clearly climbs nor descends. A row mixing one-way and two-way sport groups names each, for example "1.42 mi · Run, Hike both ways · Ride uphill only". Sport groups with no flag and only one direction listed keep today's text, with no direction.
- **The segment page's switcher explains a one-way sport.** When the shown sport group is one-way, the switcher shows its one direction as a single disabled choice ("Uphill only"). This holds even for a segment alone in its segment group, such as Clarks' ride segment, which has no switcher today. Two-way directions are named "Uphill" and "Downhill" when the line clearly climbs or descends, else by compass as today.
- **Names follow the climb.** When `extract` is given terrain (`--terrain`, the tiles `scripts/terrain-fetch.sh` writes), a whole two-way trail that clearly climbs one way is named "<trail> (uphill)" and "<trail> (downhill)" in place of a compass direction. Rows drop "(uphill)" and "(downhill)" the same way they drop compass words.
- **`refine` brings existing segments up to date.** It writes a published segment's `oneWay` when its candidate's differs, together with any rename or finer line, in one write.
- Terrain sampling (`TerrainTiles`) moves from the AppView into a small shared server module, so the importer and the AppView read tiles the same way.
- Alpine runs and lifts get no one-way label. They only ever go one way, so the label would add nothing.

Out of scope: merging lines that nearly match into one segment group (such as Clarks' 2.29 km ride line and its 2.02 km foot line).

## Capabilities

### New Capabilities
- `segments/segment-direction`: covers
  - which way a segment runs, as words: uphill, downhill, or by compass or turn;
  - the climb rule and its thresholds;
  - the `oneWay` flag: what sets it, whose segments may carry it, and how the AppView serves it.

### Modified Capabilities
- `segments/segment-groups`:
  - Rows also drop "(uphill)" and "(downhill)" from names.
  - A row says, per sport group, whether it goes both ways or one way only, and which way.
  - The switcher names directions by climb where the line clearly climbs or descends.
  - The switcher shows a one-way sport group's single direction as a disabled "… only" choice, on the shared and the native page.
- `segments/trail-candidates`:
  - A candidate holds whether it is one-way.
  - With terrain given, whole two-way trails are named by climb.
- `segments/segment-candidates`: staged candidates hold whether they are one-way, and checks return it.
- `segments/segment-import`:
  - A published segment carries its candidate's `oneWay`.
  - `refine` updates `oneWay` alongside name and line.
- `segments/path-page`: the Trail page's rows show the one-way wording, as browse rows do.

## Impact

- **Lexicons:**
  - New fields: `feed.segment#oneWay`, `feed.defs#segmentView.oneWay` and `importer.defs#candidate.oneWay`.
  - Kotlin mirror in `core/lexicon`.
- **AppView:**
  - Flyway migration: `one_way` on the staged candidates table. Staging stores the flag, and checks hand it back.
  - Segment views serve the flag, read from the stored record, only when the segment's author is the importer.
  - `TerrainTiles` moves to the new shared module.
- **Importer:**
  - `CandidateCutter` records `oneWay`.
  - New `--terrain` option on `extract`; `scripts/segment-import.sh` passes it when `build/terrain` holds tiles.
  - `publish` writes the flag. `refine` updates the flag and names.
- **Shared code:**
  - New climb rule beside `SegmentHeading` in `core/location`.
  - `SegmentGroupRow` gains per-sport directions, and `GroupSwitcher` gains a one-way state and climb-named directions.
  - New texts in `SegmentBrowseTexts` and `GroupSwitcherTexts`, with strings in `:ui-resources`.
- **UI:** the Compose rows and switcher, and the iOS views (`SegmentPage.swift`, browse and Trail page rows); regenerated iOS resources.
- **Tests:** samples in `:app-fixtures`, presenter and UI tests, Android screenshot references and iOS 26.0 snapshots.
- **Data:** existing regions get the flag and climb names only after a re-extract with `--terrain`, followed by `stage` and `refine`.
