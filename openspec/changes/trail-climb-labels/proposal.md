# Proposal

## Why

`one-way-segment-labels` made one-way rows read "Uphill only" or "Downhill only" when a line clearly climbs, but Rush, a downhill-only ride trail in Corner Canyon, still reads "Westbound only" and "Northbound only" on its Trail page, and its search row ("Trail · 4.70 mi · Ride · 21 segments") says nothing about direction. Three gaps cause it:

- The apps judge climb only from the elevation a segment view serves, and the AppView serves none where it has no terrain tiles. Region presets never fetch terrain, so an imported region has none until an operator remembers to run `terrain-fetch.sh`.
- Each segment is judged alone. A short, flat piece of a trail that descends overall, such as a 500 m section of Rush, doesn't clear the climb rule, so it reads by compass while its neighbours read "Downhill only".
- A trail's view says which sport groups it holds but not which of them go one way, so its row can't say so.

## What Changes

- **New `feed.segment#climb`** (optional string, `uphill` or `downhill`). The segment importer sets it from terrain: the candidate's own clear climb along its direction of travel, else the whole trail's clear climb in that direction. Staged candidates (`importer.defs#candidate.climb`) and the candidates file carry it.
- **New `segmentView.climb`**, served only for the importer's segments, like `oneWay`. The apps name a segment's direction from it first, then from served elevation, then by compass.
- **New `pathView.oneWays`**: for each sport group whose listed segments on the path all go one way only (and isn't alpine), that way, named as a segment's direction is (`uphill`, `downhill`, or compass or turn).
- **Trail rows and the Trail page header say which sport is one-way.** "Trail · 4.70 mi · Ride · Downhill only · 21 segments"; a mixed trail reads "Run, Hike · Ride downhill only". This holds in name search, area browse and on the Trail page.
- **`refine` writes `climb`** alongside `oneWay`, names and lines, in the same one write.
- **Region presets fetch terrain.** Fetching a preset region also downloads the terrain tiles for its box, so importing it by name extracts with terrain.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `segments/segment-direction`: a segment's direction comes from its served `climb` before its elevation; the record field, and whose segments it is served for.
- `segments/trail-candidates`: a candidate holds its climb; a flat piece of a trail that clearly climbs or descends takes the trail's climb.
- `segments/segment-candidates`: staged candidates hold their climb, and checks return it.
- `segments/segment-import`: a published segment carries its candidate's climb; `refine` updates it; presets fetch terrain.
- `segments/segment-paths`: a path's view says which sport groups go one way only, and which way.
- `segments/segment-browse`: a trail or road row says which sport groups go one way only.
- `segments/path-page`: the Trail page's header says the same.

## Impact

- **Lexicons:** `feed.segment#climb`, `feed.defs#segmentView.climb`, `feed.defs#pathView.oneWays` (with a `pathOneWay` def), `importer.defs#candidate.climb`; Kotlin mirror in `core/lexicon`.
- **AppView:** Flyway migration adding `climb` to `segment_candidates`; candidate staging and checks; segment views serve `climb` behind the importer gate; `pathViews` computes `oneWays` with the shared `core/location` rule.
- **Importer:** `CandidateCutter` judges the whole trail once and records each candidate's climb; `publish` and `refine` write it; `scripts/segment-fetch.sh` fetches terrain.
- **Clients:** `SegmentGroupRow` and `GroupSwitcher` prefer the served climb; `SegmentBrowseTexts` and `PathTexts` build trail lines with one-way wording, reusing existing strings. iOS reads the same texts.
- **Tests and fixtures:** importer, AppView, presenter and text tests; `:app-fixtures` path samples; screenshot references and iOS snapshots where a sample path gains one-way wording.
- **Data:** existing regions get `climb` after terrain is fetched and the region is extracted, staged and refined again.
