# Tasks

## 1. Thresholds

- [ ] 1.1 On the Vermont data (OSM from Overpass and the Forest Service geodatabase for `-73.10 43.85 -72.85 44.05`, as in `segment-agency-import`), measure for each Forest Service trail with an OSM path of the same name the largest distance from the agency line to the OSM line, and the nearest distance to any differently-named OSM trail. Verify: 15 m covers same-trail pairs and stays below separate trails' distance. Otherwise update the spec's and design's numbers first.

## 2. Closures in the agency rules

- [ ] 2.1 Add `AgencyRules.closedTo` for USFS and BLM as the design lays out, empty for NPS. Verify: `AgencyRulesTest` cases for every scenario under "Agency features close trails to sport groups", plus a USFS full-year restriction with a winter accepted season (not closed) and a BLM snow code (closes nothing).
- [ ] 2.2 Make `AgencyReader` return every keyed line's open and closed groups and raw points, named or not. Verify: an `AgencyReaderTest` case on the fixtures that the wilderness trail gives a line closed to `ride` and open to `run`/`hike`, and an unnamed restricted feature still gives its closed line.

## 3. Leaving out OSM candidates

- [ ] 3.1 Add `ClosureFilter` (cell-indexed lines, 5 m sampling, 15 m and 100 m from 1.1). Verify: `ClosureFilterTest` cases for each scenario under "OSM candidates along an agency closure are left out" built from candidates and lines directly, and that agency candidates are never dropped.
- [ ] 3.2 Run the filter in `candidatesOf` before dedup and print the per-group report from `extract`. Verify: an `ExtractTest` run where the `usfs-trails` fixture's Oak Trail gains an all-year bicycle restriction leaves out the OSM Oak Trail `ride` candidates and keeps its stretches' other groups; regenerate `expected-candidates-agency.ndjson` with `UPDATE_EXPECTED=1` and review that only those candidates went.

## 4. Docs

- [ ] 4.1 Document closures in the importer README: what each dataset closes, the 15 m and 100 m rule, the report, and that every agency dataset for a region should be passed. Verify: the README's description matches the report the Vermont run prints.

## 5. Integration

- [ ] 5.1 Run `./gradlew check`. Verify: it passes.
- [ ] 5.2 Extract Vermont again with OSM and USFS, and Moab with OSM and BLM. Verify: the report's counts are plausible, and for three dropped candidates in each region the agency data shows the closure. Record what happens to Slickrock Trail's OSM ride candidates in the design's Risks.

## Workflow follow-up

- Archive the change once implemented and reviewed, syncing the `segments/trail-candidates` delta.
- Implement `segment-blm-import` first: the BLM closures read its mode table.
