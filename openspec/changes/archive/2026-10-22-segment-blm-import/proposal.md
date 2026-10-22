# Proposal

## Why

The Bureau of Land Management manages more public land in the West than the Forest Service, and much of the riding and hiking there (Moab, the Colorado Plateau, southern Oregon) is on BLM trails. `segment-agency-import` left BLM out because its national layer looked like a mixed road and trail inventory whose mode coding needed study. That study is now done: BLM's national managed-trails layer holds only trails, and every allowed-mode code names exactly which users may use the trail. So BLM can be a third agency dataset with no change to how candidates are joined, cut, deduplicated, staged or published.

## What Changes

- **A `blm` agency dataset.** `extract --agency blm=<file>` reads BLM's national public managed trails (`BLM_Natl_GTLF_Public_Managed_Trails`) as a GeoJSON text sequence, converted with `ogr2ogr` straight from BLM's feature service as the Park Service's is.
- **BLM allowed modes decide sport groups.** Each `PLAN_ALLOW_MODE_TRNSPRT` code maps to the groups its definition names (bicycles to `ride`; hiking to `run` and `hike`; nordic skiing to `ski`). A trail whose code is unknown falls back to its planned mode of transport. A code that names only motor vehicles or horses gives no candidate.
- **Restricted BLM trails are left out.** A trail whose access is restricted to administrators, permitted users or everyone gives no candidate.
- **Credit.** BLM candidates and segments carry `source: agency`, `license: CC0-1.0` and the attribution "Bureau of Land Management".
- **Docs.** The importer README covers getting BLM data. `scripts/segment-import.sh` already passes any `--agency` through.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `segments/trail-candidates`: BLM as a third agency dataset kind, how its features qualify by allowed mode, and which restricted BLM trails are left out.

## Impact

- **Importer:** a third `AgencyRules` entry and its tests and fixture; `extract`'s usage text.
- **No lexicon, AppView or client change:** the attribution field, its credit line and the `agency` source already exist (`segment-agency-import`).
- **Docs:** `server/segment-importer/README.md`.
- **Dependencies:** `segment-agency-import` (archived).
