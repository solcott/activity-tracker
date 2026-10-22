# Segment importer

Turns public trail data (OpenStreetMap and US agency datasets) into candidate segments (`openspec/specs/segments/trail-candidates`), offline. It is a command-line tool, separate from the AppView, which stays an indexer. Each candidate is a line that could become a `feed.segment`: a whole named trail or route, or a stretch of one between junctions, for one sport group, in each direction the trail may be travelled. Staging candidates in the AppView and publishing the ridden ones come later (`segment-candidate-ridership`, `segment-import-publishing`).

## Extract

```bash
./gradlew :server-segment-importer:run --args="extract path/to/region-latest.osm.pbf --agency usfs=build/usfs-vt.geojsonl --region us-vermont --out build/us-vermont.ndjson"
```

Relative paths resolve from the repository root. The inputs are an OpenStreetMap `.osm.pbf` extract, such as one region from [Geofabrik](https://download.geofabrik.de/), and any number of agency trail datasets for the same region (below), each named with its kind; at least one input is needed. Every source for a region goes into one file, so the region is staged as one run and duplicates across sources are resolved first: an agency candidate wins over an OSM one it duplicates. The region id is lower-case letters, digits and dashes, and names the region in every candidate. Extracting needs no network access.

The output is NDJSON:

- a header line with `region`, `extractTimestamp` (the OSM extract's replication timestamp, when its header has one) and `importerVersion` (2);
- one line per candidate, sorted by `id`, with `region`, `sport`, `name`, `line` (a precision-6 encoded polyline, as `feed.segment` stores it), `lengthMeters`, `source`, `license`, and then either `osmWays` (the OSM ways it follows, in its direction of travel; `source` `osm`, `license` `ODbL-1.0`) or `attribution` and `agencyFeatures` (the agency's name and the `<kind>:<feature key>`s it follows; `source` `agency`, `license` `CC0-1.0`).

An OSM candidate's id stays the same while it follows the same ways between the same end nodes, and an agency candidate's while it follows the same features between the same ends (to about a metre), so re-extracting newer data keeps the ids of unchanged trails. The run prints how many candidates each sport group got, and how many agency features it skipped for having no feature id or no line.

## Agency trail data

US land agencies publish their trails in the public domain. The importer reads each dataset as a GeoJSON text sequence in WGS 84 (one feature per line), which [GDAL](https://gdal.org/)'s `ogr2ogr` makes from whatever the agency publishes (`brew install gdal`). Give `ogr2ogr` the region's bounding box (west south east north) with `-spat`, which keeps every feature that touches the box whole:

- **`usfs`**: the Forest Service's National Forest System trails, `TrailNFS_Publish`, from the [FSGeodata Clearinghouse](https://data.fs.usda.gov/geodata/edw/datasets.php) as a file geodatabase. `ogr2ogr` reads it straight from its URL, fetching only what the box needs:

  ```bash
  CPL_VSIL_CURL_ALLOWED_EXTENSIONS=.zip,.gdb,.gdbtable,.gdbtablx,.gdbindexes,.spx,.atx,.freelist ogr2ogr -f GeoJSONSeq -t_srs EPSG:4326 -spat -73.10 43.85 -72.85 44.05 -spat_srs EPSG:4326 build/usfs-vt.geojsonl /vsizip//vsicurl/https://data.fs.usda.gov/geodata/edw/edw_resources/fc/S_USA.TrailNFS_Publish.gdb.zip TrailNFS_Publish
  ```

  A downloaded copy works the same: pass the `.gdb.zip` (or the shapefile) instead of the URL. A use counts when its `_MANAGED`, `_ACCPT` or `_ACCPT_DISC` field holds a season; the features of one trail, split at mileposts, are keyed by `TRAIL_CN` and `BMP`.

- **`nps`**: the Park Service's public trails, from its `National_Park_Service_Trails` feature service. `ogr2ogr` pages through a query for the same box:

  ```bash
  ogr2ogr -f GeoJSONSeq -t_srs EPSG:4326 -spat -81.65 41.15 -81.50 41.30 -spat_srs EPSG:4326 build/nps-cuva.geojsonl "https://services2.arcgis.com/FiaPA4ga0iQKduv3/arcgis/rest/services/National_Park_Service_Trails/FeatureServer/0/query?where=1%3D1&outFields=*&geometry=-81.65,41.15,-81.50,41.30&geometryType=esriGeometryEnvelope&inSR=4326&outSR=4326&f=json"
  ```

  Its warnings about date fields are harmless: the importer reads no dates. A trail counts only while `TRLSTATUS` is `Existing` and `PUBLICDISPLAY` is `Public Map Display`, for the uses `TRLUSE` lists; features are keyed by `GEOMETRYID`.

Agency lines are joined where their ends or vertices lie within a metre of each other, across datasets, but never to OSM ways. Names written wholly in capitals (common in Forest Service data) are title-cased. Which fields mean what, and the values seen in the national data, are in the `segment-agency-import` OpenSpec change's design.

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
