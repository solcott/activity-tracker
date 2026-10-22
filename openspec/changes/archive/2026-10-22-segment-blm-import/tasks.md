# Tasks

## 1. Fixture

- [x] 1.1 Convert a small area of BLM's managed trails (Moab: `-109.75 38.45 -109.35 38.80`) with the design's `ogr2ogr` query and check its fields against the design's table (names, `Original_GlobalID`, mode and access values). Fix the design if anything differs. Verify: a sample feature carries every field the table names.
- [x] 1.2 Add a hand-written `blm-trails.geojsonl` under `server/segment-importer/src/test/resources` with the real field names: a shared non-motorized trail, an equestrian-and-hiking trail, a shared motorized trail, a motorcycle-only trail in capitals, an `UNK` trail with a non-mechanized planned mode, a nordic trail, a trail for permitted users only, one without a name and one without `Original_GlobalID`. Verify: every line parses with `jq -c .`.

## 2. BLM rules

- [x] 2.1 Add the `BLM` entry (`blm`, "Bureau of Land Management") to `AgencyRules`, with the design's key, name and mode table, the planned-mode fallback and the access exclusion. Verify: `AgencyRulesTest` cases for each scenario under "BLM features qualify by their allowed modes" and "BLM trail for permitted users only", plus a trailing-space code and a mixed-case planned mode.
- [x] 2.2 Read the BLM fixture through `AgencyReader`. Verify: an `AgencyReaderTest` case that the id-less feature is counted as skipped, the unnamed, motorcycle-only and restricted features give no ways, and the capitalised name is title-cased.
- [x] 2.3 Add the fixture to `ExtractTest`'s agency run and regenerate `expected-candidates-agency.ndjson` with `UPDATE_EXPECTED=1`, reviewing that only BLM lines were added and existing ids are unchanged. Verify: `./gradlew :server-segment-importer:test` passes.

## 3. Docs

- [x] 3.1 Add BLM to the README's agency data section (the `ogr2ogr` query, what counts as a use, the key) and to `Main.kt`'s `USAGE`. Verify: the README's BLM command runs as written and its output extracts with `--agency blm=`.

## 4. Integration

- [x] 4.1 Run `./gradlew check`. Verify: it passes.
- [x] 4.2 Extract the Moab box with OSM (from Overpass, as for Vermont) and BLM, then stage it on the local stack. Verify: the candidates file holds BLM candidates attributed to "Bureau of Land Management", and staging rejects none of them.

## Workflow follow-up

- Archive the change once implemented and reviewed, syncing the `segments/trail-candidates` delta.
