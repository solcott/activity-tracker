# Proposal

## Why

Importing a region's trails is one script today (`scripts/segment-import.sh`), but its inputs are not: the operator downloads a Geofabrik extract, shrinks it with osmium, and pulls each agency's trails with `ogr2ogr` against a bounding box they look up themselves. Utah, the next region, is mostly federal land, so it needs all three agency datasets. Even once staged, a fresh region publishes almost nothing, since only candidates `SEGMENT_CANDIDATE_RIDERS` athletes have ridden are published, and what is published starts `proposed`, which search shows only to the importer and riders. We want the whole of Utah's trails on the map now, and a repeatable one-command import for the next region.

## What Changes

- Region presets: one small file per region (`scripts/segment-regions/<region>.conf`) naming its Geofabrik extract, its bounding box, the agency datasets that cover it and its language. The first presets are `us-utah` and `us-vermont`.
- `scripts/segment-fetch.sh <region>` downloads a preset's inputs into `build/segment-import/<region>/`: the Geofabrik extract (only when newer), filtered to the features the importer reads, and each agency's trails for the box as GeoJSON text sequences.
- `scripts/segment-import.sh <region>` with no input files fetches the preset's inputs first, then extracts, stages, publishes and refines as before. Explicit inputs still work. `--all` publishes every staged candidate.
- The importer's `publish --region <id> --all <candidates.ndjson>` publishes every candidate of the file that is still staged and duplicates no live segment, whether or not it has been ridden. Without `--all`, `publish` behaves as today.
- The AppView gives the segment importer's segments the status `active` instead of `proposed` when they duplicate nothing, so they appear in search for everyone, have leaderboards at once and are never archived for lack of riders. The importer's segments already indexed as `proposed` become `active` at the next status pass. Athletes' segments are unchanged.
- Import Utah on the local stack with the new script.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `segments/segment-import`: publishing may skip the ridership gate when the operator asks (`--all`), and region presets fetch an import's inputs.
- `segments/segment-records`: the segment importer's non-duplicate segments start `active`.

## Impact

- `scripts/segment-import.sh`, new `scripts/segment-fetch.sh` and `scripts/segment-regions/*.conf`. The scripts need `curl`, `osmium` and `ogr2ogr` (GDAL) to fetch.
- `server/segment-importer`: `Publish.kt`, `Main.kt`, their tests and README.
- `server/appview`: `index/SegmentRecordIndex.kt` (status on indexing and on recheck), `segments/SegmentMatcher.kt` (status pass promotes the importer's proposed segments), and their tests.
- Docs: the importer README, the local-stack skill and AGENTS.md's segment summary.
- No lexicon, migration or client change. Matching load: each published segment queues a `segment` job, so a Utah-sized `--all` run queues thousands; locally there are few tracks, so this is cheap.
