# Design

## Context

`scripts/segment-import.sh <region> <inputs...>` already runs `extract`, `stage`, `publish` and `refine` (`segments/segment-import`). Its inputs come from README recipes: a Geofabrik `.osm.pbf`, shrunk with `osmium tags-filter`, and `ogr2ogr` pulls of the Forest Service geodatabase and the NPS and BLM feature services, each for a hand-typed bounding box. `publish` takes candidates only from `importer.getRiddenSegmentCandidates`. The AppView sets a non-duplicate segment's status to `PROPOSED` in two places in `index/SegmentRecordIndex.kt`: on index or change (`it[status] = ... PROPOSED`), and in `recheckDuplicatesOf`. `SegmentMatcher.updateStatuses` promotes proposed segments by rider count and archives those 180 days old. The indexer already knows the importer's DID (`Indexer`'s `segmentImporter`, used to lift the daily creation limit).

The goal is all of Utah on the local stack, reachable by one command, with segments everyone can see.

## Goals / Non-Goals

**Goals:**
- One command that imports a region by id: `scripts/segment-import.sh us-utah --all`.
- New regions need only a preset file.
- The importer's segments are `active` and searchable without riders.

**Non-Goals:**
- Hosting map tiles. The basemap stays OpenFreeMap.
- Clipping agency data to the state border.
- Scheduling imports, or running against a deployed AppView (the scripts work there too, but this change targets the local stack).
- Changing what athletes' segments need to become active.

## Decisions

### Presets are sourced shell files

`scripts/segment-regions/<region>.conf` sets `GEOFABRIK` (the path under `download.geofabrik.de`, without `-latest.osm.pbf`), `BBOX` (`west south east north`), `AGENCIES` (a space-separated subset of `usfs nps blm`) and `REGION_LANG`, not `LANG`, which sourcing would turn into every tool's locale. A sourced file needs no parser and matches the scripts' style. The alternative, a JSON table read with `jq`, adds a dependency for no gain. `us-utah`: `north-america/us/utah`, `-114.06 36.99 -109.04 42.01`, `usfs nps blm`, `en`. `us-vermont`: `north-america/us/vermont`, `-73.44 42.72 -71.46 45.02`, `usfs`, `en`.

### `segment-fetch.sh` writes to `build/segment-import/<region>/`

- `curl -fL -z <file> -o <file>.part` downloads the Geofabrik extract only when it's newer (`-z` sends `If-Modified-Since`), then moves it into place. `osmium tags-filter <pbf> w/highway w/piste:type r/route -O -o <region>-trails.osm.pbf` runs only when the trails file is older than the extract.
- Each agency runs the README's `ogr2ogr -f GeoJSONSeq -t_srs EPSG:4326 -spat $BBOX -spat_srs EPSG:4326` into `<kind>.geojsonl.part`, then moves it into place. USFS reads `/vsizip//vsicurl/https://data.fs.usda.gov/.../S_USA.TrailNFS_Publish.gdb.zip TrailNFS_Publish` with `CPL_VSIL_CURL_ALLOWED_EXTENSIONS` set. NPS (layer 0) and BLM (layer 2) build their `query?...&geometry=w,s,e,n...` URLs from the box. Agency datasets are re-fetched on every run: the services give no reliable modified date, and a statewide pull takes minutes.
- It checks `curl`, `osmium` and `ogr2ogr` with `command -v` before anything else, printing `brew install osmium-tool` and `brew install gdal` hints.
- It prints each output's size, so an empty agency pull (a service outage often returns zero features rather than an error) stands out. It warns when a `.geojsonl` has zero lines.

### `segment-import.sh` keeps its interface and adds two modes

- `segment-import.sh <region>` with no inputs: if the preset exists, it runs `segment-fetch.sh <region>`, then builds the inputs (`<region>-trails.osm.pbf` plus `--agency <kind>=<kind>.geojsonl` for each preset agency) and takes `--lang` from the preset. Otherwise it exits 2 and says the region needs a preset or inputs.
- `--all` passes `--all <out>` to `publish`.
- Heap: `JAVA_OPTS` reaches only the Gradle client, not the JVM `:run` forks, so the script sets none. That JVM gets the default heap, a quarter of the machine's memory (8 GB on a 32 GB Mac), enough for a state. The `run` task reads `SEGMENT_IMPORTER_HEAP` at execution for more, and the README's old `JAVA_OPTS` advice is corrected.

### `publish --all <file>` shares the page loop

`publish` gets a candidate source: the ridden pages from the AppView (today), or pages of `SegmentCandidates.MAX_BATCH` from the file read by `readCandidates` (`Stage.kt`). The per-page body (`CheckSegmentCandidates`, then `exists`/`create` per candidate) moves into one function both sources call, so duplicates, gone and present are reported identically. The file's header region must equal `--region`, checked in `Main.kt` before signing in. Candidates come from the file, so a candidate staged under the current revision but changed since in a newer file is published with the file's line. That's the line the operator is publishing. `checkSegmentCandidates` checks the staged line, but the two only differ when the operator stages one file and publishes another.

### The importer's live status is `active`, decided where `PROPOSED` is set today

A helper `liveStatus(author: String, importer: Did?)` returns `ACTIVE` for the importer's DID and `PROPOSED` otherwise. It is used in the index/change path and in `recheckDuplicatesOf`, which gains an `importer: Did?` parameter. Its callers (indexer, `deleteSegments`, `SegmentMatcher.updateStatuses`) pass theirs, and `SegmentMatcher` gets the importer DID from `Application.kt` like `Indexer` does. `updateStatuses` first sets the importer's `PROPOSED` segments to `ACTIVE`, which covers segments indexed before this change. Archiving touches only `PROPOSED`, so importer segments are never archived.

We considered a separate per-segment flag, or making the active-athlete threshold per author. Both add schema for one account's rule. Status is already derived, so deriving it from the author needs no migration.

## Risks / Trade-offs

- [Unridden, possibly poor OSM lines get leaderboards at once] -> They are public trails that extract already filtered (closures, minimum lengths, named features). Athletes' own segments still duplicate-check against them, and `refine` improves lines on later runs.
- [Agency features that touch the box come in whole, so NV, AZ, CO and SW Wyoming slivers land under `us-utah`] -> Accepted. Clipping changes lines and candidate ids. A later preset for a neighbouring state dedups against them through the duplicate check.
- [A statewide `--all` publish writes thousands of records one at a time] -> PDS rate limits are off locally, `publish` resumes after a failure, and re-runs skip existing keys.
- [Thousands of `segment` match jobs] -> Each one scans tracks sharing the segment's cells. There are few tracks locally.
- [Geofabrik or an agency service changes its URL or schema] -> The fetch fails loudly, or warns on an empty pull. URLs live in one script.

## Migration Plan

No schema migration. On deploy, the first status pass promotes the importer's existing proposed segments. Rollback: revert the code. Promoted segments stay `active`, since active is never undone, which matches the spec for athletes' segments.

## Open Questions

None blocking. Whether to schedule imports, and which region comes next, can wait.
