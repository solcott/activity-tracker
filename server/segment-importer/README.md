# Segment importer

Turns public trail data into candidate segments (`openspec/specs/segments/trail-candidates`), offline. It is a command-line tool, separate from the AppView, which stays an indexer. Each candidate is a line that could become a `feed.segment`: a whole named trail or route, or a stretch of one between junctions, for one sport group, in each direction the trail may be travelled. Staging candidates in the AppView and publishing the ridden ones come later (`segment-candidate-ridership`, `segment-import-publishing`).

## Extract

```bash
./gradlew :server-segment-importer:run --args="extract path/to/region-latest.osm.pbf --region us-vermont --out build/us-vermont.ndjson"
```

Relative paths resolve from the repository root. The input is an OpenStreetMap `.osm.pbf` extract, such as one region from [Geofabrik](https://download.geofabrik.de/). The region id is lower-case letters, digits and dashes, and names the region in every candidate. The importer needs no network access.

The output is NDJSON:

- a header line with `region`, `extractTimestamp` (the extract's replication timestamp, when its header has one) and `importerVersion`;
- one line per candidate, sorted by `id`, with `region`, `sport`, `name`, `line` (a precision-6 encoded polyline, as `feed.segment` stores it), `lengthMeters`, `source` (`osm`), `license` (`ODbL-1.0`) and `osmWays` (the ways it follows, in its direction of travel).

An id stays the same while the candidate follows the same ways between the same end nodes, so re-extracting a newer copy of a region keeps the ids of unchanged trails. The run prints how many candidates each sport group got.

## Memory

The importer reads the file three times (relations, ways, then only the nodes trails use) and holds ids and coordinates in primitive arrays, so one region the size of a US state fits in a couple of GB of heap. For a large extract, shrink it first with [osmium](https://osmcode.org/osmium-tool/) (`brew install osmium-tool`) to the features the importer reads:

```bash
osmium tags-filter region-latest.osm.pbf w/highway w/piste:type r/route -o region-trails.osm.pbf
```

Raise the heap with `JAVA_OPTS=-Xmx6g` if needed.

## Test fixture

`src/test/resources/trails.osm` is a hand-written extract: a mountain-bike trail crossed by a named road, an unnamed side path, a private track, a sidewalk, a one-way descent and a hiking route over unnamed paths. `trails.osm.pbf` is built from it, and `ExtractTest` checks the importer's output on it against `expected-candidates.ndjson`. After editing `trails.osm`, rebuild the PBF and the expected output, then review both diffs:

```bash
osmium cat server/segment-importer/src/test/resources/trails.osm -o server/segment-importer/src/test/resources/trails.osm.pbf -O --output-header="osmosis_replication_timestamp=2026-10-01T00:00:00Z"
```

```bash
UPDATE_EXPECTED=1 ./gradlew :server-segment-importer:test --tests "*.ExtractTest"
```
