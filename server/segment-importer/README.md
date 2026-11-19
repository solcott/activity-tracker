# Segment importer

Turns public trail data (OpenStreetMap and US agency datasets) into candidate segments (`openspec/specs/segments/trail-candidates`), offline. It is a command-line tool, separate from the AppView, which stays an indexer. Each candidate is a line that could become a `feed.segment`: a whole named trail or route, a whole trail OSM maps as named sections, or a stretch of one between junctions, for one sport group, in each direction the trail may be travelled. `stage` uploads them to the AppView and `publish` turns them into segments (below).

## Importing a region

A region with a preset in `scripts/segment-regions/` imports with one command, which downloads its inputs and then extracts, stages, publishes and refines it:

```bash
scripts/segment-import.sh us-utah --all
```

A preset is a small shell file: `GEOFABRIK` (the extract's path under [Geofabrik](https://download.geofabrik.de/), without `-latest.osm.pbf`), `BBOX` (west south east north), `AGENCIES` (which of `usfs`, `nps` and `blm` cover the region) and `REGION_LANG`. Adding a region is adding one. `scripts/segment-fetch.sh <region>` does just the downloads, into `build/segment-import/<region>/`: the extract (again only when Geofabrik's is newer), cut down with osmium to `<region>-trails.osm.pbf`, and each agency's trails touching the box (the `ogr2ogr` commands below) as `<kind>.geojsonl`, re-fetched every run. Each file is moved into place only once complete, and the run prints each one's size and feature count, warning when an agency gave none: a service outage often answers with no features rather than an error. It needs `curl`, `osmium` and `ogr2ogr` (`brew install osmium-tool gdal`). The box takes in agency trails across the border whole, so a state's import holds slivers of its neighbours'.

`scripts/segment-import.sh <region> <file.osm.pbf> [--agency <kind>=<file>]... [--lang <code>]` still runs on files you give it.

## Extract

```bash
./gradlew :server-segment-importer:run --args="extract path/to/region-latest.osm.pbf --agency usfs=build/usfs-vt.geojsonl --region us-vermont --out build/us-vermont.ndjson"
```

Relative paths resolve from the repository root. The inputs are an OpenStreetMap `.osm.pbf` extract, such as one region from [Geofabrik](https://download.geofabrik.de/), and any number of agency trail datasets for the same region (below), each named with its kind; at least one input is needed. Every source for a region goes into one file, so the region is staged as one run and duplicates across sources are resolved first: an agency candidate wins over an OSM one it duplicates. The region id is lower-case letters, digits and dashes, and names the region in every candidate. `--lang` gives the region's language as a two-letter ISO 639-1 code (`en` unless given). It picks the generic words candidate names drop from the end of each source name, so "Gas Line Trail: Lower Corner Canyon Trail to Hoof N Boot Path" becomes "Gas Line: Lower Corner Canyon → Hoof N Boot". Only English has a list so far: a region in any other language keeps its names as the source has them, and the run says so. Extracting needs no network access.

The output is NDJSON:

- a header line with `region`, `extractTimestamp` (the OSM extract's replication timestamp, when its header has one) and `importerVersion` (3: lines within 1 m of the trail; version 2 files hold 5 m lines and are refused);
- one line per candidate, sorted by `id`, with `region`, `sport`, `name`, `line` (a precision-6 encoded polyline, as `feed.segment` stores it, within 1 m of the trail, coarser only as far as it needs to be to fit a record), `lengthMeters`, `source`, `license`, and then either `osmWays` (the OSM ways it follows, in its direction of travel; `source` `osm`, `license` `ODbL-1.0`) or `attribution` and `agencyFeatures` (the agency's name and the `<kind>:<feature key>`s it follows; `source` `agency`, `license` `CC0-1.0`).

An OSM candidate's id stays the same while it follows the same ways between the same end nodes, and an agency candidate's while it follows the same features between the same ends (to about a metre), so re-extracting newer data keeps the ids of unchanged trails. The run prints how many candidates each sport group got, how many OSM candidates of each group it left out along agency closures (below), and how many agency features it skipped for having no feature id or no line:

```
Wrote 2554 candidates for us-vermont to build/us-vermont.ndjson: 876 hike, 398 ride, 876 run, 404 ski
Left out 12 ride candidates along agency closures
```

## Joining and cutting trails

Some rules decide how ways become trails and how trails become candidates (segments/trail-candidates):

- **Name variants join.** Ways join when their names match once generic words are dropped (below), so a stretch tagged `Canyon Hollow` in the middle of `Canyon Hollow Trail` is part of it. The trail is named after its longest way.
- **Short shared tread joins.** A trail that meets roads or other trails, follows them for up to 200 m and leaves them, as Canyon Hollow does along the service road Lower Corner Canyon Trail, continues along them: along the shortest chain of connected ways to the next part of the trail, however many ways that takes. Its candidates follow, and their ids name, those ways too. This only happens where no way of the trail's own name continues, and never across a way that is private, closed to the sport group, steps or a motorway. A route relation likewise keeps a single road member of up to 200 m between two of its trail ways.
- **Roads that carry the trail's number join further.** When every way in the chain lists one of the trail's refs (its `ref` tag, split at `;`), and the trail's ways at both ends carry that ref, the chain may be up to 1 km long, or `--ref-bridge-m` meters (`REF_BRIDGE_M` for `scripts/segment-import.sh`). So Little Cottonwood Creek Trail (`ref=1001`) crosses Wasatch Resort on Granite Cliffs Road and Power Plant Lane, both `ref=NF 87672;1001`, 219 m in all. Only named roads are read, so a gap over an unnamed road stays a gap.
- **Relations lose another trail at their ends.** A route relation that runs onto another named trail at either end, such as OSM's "Clarks Trail" route onto Ann's Trail, loses those ways, as long as a way of its own name or family remains. A route made only of differently named ways keeps all of them.
- **A covering relation names the trail.** A named trail whose ways are exactly a route relation's goes by the relation's name: Clarks' `Clarks Trail - Bikes Uphill Only Section` ways give run and hike candidates named `Clarks`, like the route's ride candidate.
- **Trails are cut where their direction changes.** A trail whose permitted directions change along it, such as the Bonneville Shoreline route over the bike-uphill-only Clarks, gives no whole-trail candidate. Instead, each run of one direction gives its own candidate and stretches.
- **Every sport group cuts at the same places.** Stretches between junctions are joined until each reaches the largest minimum length of the sport groups all the trail's ways are open to (500 m when rides are allowed), so ride, run and hike stretches share their lines and show as one row.

## Parent trails

OSM often maps a known trail as a chain of sections with names of their own and no route relation over them, such as Corner Canyon's Rush descent: `Rush Trail - Tom Sawyer Section`, `Rush Trail - 2112 Section`, `Rush Trail - Limelight Section` and a way named just `Rush Trail`. Ways named `<Parent>` or `<Parent> - <Section>` form a family per sport group. Besides each name's own trail, every path through the family's ways joined at their ends, in a direction the ways allow and crossing at least two names, is a parent trail named `<Parent>` (its candidates' names drop their generic words, below):

- Where the family forks, each path is its own parent trail, named after its first section on no other path: `Rush › Exit Stage Left` and `Rush › Through the Trees Trail`. A path whose sections are all on other paths too, such as an entry that joins partway down, is named after its first section instead: `Rush from Mystic Rhythms`. A family keeps at most 4 parent trails per sport group, counting a path and its reverse as one, or only its longest when it has more.
- Ways join only at their ends, so a way that meets the family in the middle of a section, such as a feeder into Limelight, is on no parent trail.
- A parent trail gives its whole candidate and, where a named trail or road from outside the family meets it between two differently named sections, a span from its start to that junction and one from the junction to its end. Riders start and stop runs at those crossings: Rush is mostly ridden from Tom Sawyer to where Canyon Hollow Trail meets the top of Dreamline, which gives `Rush › Exit Stage Left → Canyon Hollow`. A span is named `<trail> → <junction>` or `<trail> from <junction>` in its direction of travel, after its own parent trail when it follows a way no other parent trail of the family does, and else after the family, so a span every branch shares, such as `Rush from Canyon Hollow`, is one candidate. A junction is named after its way's family, so `Bonneville Shoreline Trail - Draper Section` reads `Bonneville Shoreline`. A span of a single section is left out, and a parent trail gives no other stretches: those are its sections' own. A section too short for a candidate of its own still counts as part of the parent.
- When candidates duplicate each other, a route relation's wins over a parent trail's, and a single section's whole trail wins over a parent trail that adds only a short piece to it.

## Names

A candidate's name is built from the names of the ways it follows and meets, each without one generic word of the region's language at its end (`extract --lang`, `en` unless given): Trail, Path, Foot Path, Footpath and Section in English. A `<Parent> - <Section>` name loses one from each side, a word before a parenthesised qualifier goes too, and a word stays when nothing but an article would be left, so `Trail` and `The Trail` keep theirs. Road and every other word stay. A whole trail is named after its trail, with its direction when it goes both ways (`Rattler (eastbound)`); a stretch after its trail and the places at its ends, `<trail>: <start> → <end>` (`Ghost Falls: Ghost Connector → Corner Canyon Road`), keeping an end's section (`Bonneville Shoreline - Draper: Rush - Dreamline → Aqueduct`), or `<trail> (part n)` when an end has no name. A language with no word list keeps every name as its source has it.

Agency datasets don't form families: their features already join at shared ends whatever their names.

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

- **`blm`**: the Bureau of Land Management's public managed trails, from its `BLM_Natl_GTLF_Public_Managed_Trails` feature service (layer 2), queried the same way:

  ```bash
  ogr2ogr -f GeoJSONSeq -t_srs EPSG:4326 -spat -109.75 38.45 -109.35 38.80 -spat_srs EPSG:4326 build/blm-moab.geojsonl "https://services1.arcgis.com/KbxwQRRfWyEYLgp4/arcgis/rest/services/BLM_Natl_GTLF_Public_Managed_Trails/FeatureServer/2/query?where=1%3D1&outFields=*&geometry=-109.75,38.45,-109.35,38.80&geometryType=esriGeometryEnvelope&inSR=4326&outSR=4326&f=json"
  ```

  A trail counts for the users its `PLAN_ALLOW_MODE_TRNSPRT` code names, read literally: bicycles for rides, hiking for runs and hikes, nordic skiing for skis, so a code for motor vehicles or horses alone (`MTC_ONLY`, `EQU_ONLY`, …) gives nothing. An `UNK` or empty code falls back to `PLAN_MODE_TRNSPRT` (Non-Motorized for rides, runs and hikes; Non-Mechanized for runs and hikes). A trail whose `PLAN_ACCESS_RSTRCT` is `All`, `Admin Only` or `Authorized/Permitted User Only` gives nothing. Features are keyed by `Original_GlobalID`.

Agency lines are joined where their ends or vertices lie within a metre of each other, across datasets, but never to OSM ways. Names written wholly in capitals (common in Forest Service and BLM data) are title-cased. Which fields mean what, and the values seen in the national data, are in the `segment-agency-import` and `segment-blm-import` OpenSpec changes' designs.

### Closures

Agency data also says where a sport is not allowed, and OSM candidates that would invite it there are left out. A feature closes a trail to a group when:

- **`usfs`**: that use's `_RESTRICTED` field is `01/01-12/31` and none of its `_MANAGED`, `_ACCPT` or `_ACCPT_DISC` fields holds a season (a wilderness trail with bikes restricted all year closes it to rides). A seasonal restriction closes nothing.
- **`blm`**: its `PLAN_ALLOW_MODE_TRNSPRT` code is known and not a `SNOW_` code, and leaves the group out of rides, runs and hikes (`EQU_HIK_ONLY` closes rides; `MTC_ONLY` closes all three), or its `PLAN_ACCESS_RSTRCT` is `All`, `Admin Only` or `Authorized/Permitted User Only` (closed to every group). An `UNK` or empty code closes nothing.
- **`nps`**: never. Its uses are free text, often `Unknown`.

Every feature with a key counts, named or not, including those that give no candidates. An OSM candidate is left out when, for at least 100 m of its line in all (sampled every 5 m), it lies within 15 m of an agency line closed to its group and not within 15 m of one open to it. So a crossing, a junction's approach or a trail running 30 m alongside doesn't count, a winter feature open to rides keeps a summer closure's ground open, and the same path's candidates for other groups stay. Agency candidates are never left out. Since only the datasets given to `extract` close anything, pass every agency dataset that covers the region. A candidate left out on a later run leaves staging with that run, but a segment already published from it stays.

BLM codes are read literally, so a mis-coded trail closes OSM's candidates along it. Moab's Slickrock Trail is coded `MTC_ONLY` (motorcycles only), so OSM's Slickrock rides, runs and hikes are all left out. Check the report's counts after each run.

## Publish

```bash
./gradlew :server-segment-importer:run --args="publish --region us-vermont"
./gradlew :server-segment-importer:run --args="publish --region us-utah --all build/segment-import/us-utah.ndjson"
```

`publish` writes a `feed.segment` in the service account's repo, keyed by candidate id, for each of the region's ridden candidates (those `SEGMENT_CANDIDATE_RIDERS` athletes have ridden) that duplicates no live segment. With `--all` and the region's staged candidates file, it takes every candidate in the file instead, ridden or not, still leaving out duplicates and candidates the region's current run doesn't stage; a file of another region is refused. The AppView makes the importer's segments `active` as it indexes them, so they're searchable by everyone and never archived for lack of riders. It never rewrites a segment it published, and stops at the first failed call; running again carries on.

## Refine

Publishing never rewrites a segment, so segments published from older files keep their lines. `refine` takes the candidates file of a fresh `extract` and gives each segment the importer already published under one of its candidate ids that candidate's line, when the two are the same path: within 6 m of each other all along (their Hausdorff distance on points every 2 m), which a 5 m line and a 1 m line of one unchanged trail always are. It also gives each one its candidate's name when the two differ, even when the trail moved, so shorter names and trails renamed in OSM reach published segments; each rename is reported with its old and new name. Only `line` and `name` change, in one write; every other field stays as stored, and the AppView recomputes the segment's efforts. A trail that moved further keeps its line, and is reported. Running it again with the same file writes nothing. `scripts/segment-import.sh` runs it after `publish`:

```bash
./gradlew :server-segment-importer:run --args="refine build/us-vermont.ndjson"
```

## One-way and climbing trails

A candidate given in one direction only, because a trail is tagged `oneway` (or `oneway:bicycle` for rides) or is alpine, is marked `oneWay`, and `publish` writes `oneWay: true` on its segment (segments/segment-direction). The apps then say, for that sport group, "Uphill only", "Downhill only" or the compass direction. The AppView honours the flag only on this account's segments. With `--terrain <dir>` (the tiles `scripts/terrain-fetch.sh` writes, at zoom 12 unless `--terrain-zoom` says otherwise), `extract` names a whole two-way trail whose line clearly climbs "<trail> (uphill)" and "<trail> (downhill)", not by compass: a net climb of at least 15 m and 3% of its length, by terrain sampled and counted as the AppView counts it, where the tiles cover the whole line. `refine` brings published segments up to date, writing their `oneWay` and new names. With terrain, each candidate also gets a `climb`: its own line's clear climb or descent in its direction of travel, else its whole trail's, so a flat piece of a trail that descends overall reads "Downhill only" like the rest; `publish` writes it on the segment, `refine` updates it, and the AppView serves it only for this account's segments. `scripts/segment-import.sh` passes `--terrain build/terrain` when that folder holds tiles, and `scripts/segment-fetch.sh` (which the import runs for a preset region) fetches the tiles over the region's box first.

```bash
scripts/terrain-fetch.sh us-utah
./gradlew :server-segment-importer:run --args="extract utah-latest.osm.pbf --region us-utah --out build/us-utah.ndjson --terrain build/terrain"
```

## Paths

Segments are browsed by the trail or road they lie on (segments/segment-paths). With `--paths <file>`, `extract` also writes the region's paths: one per trail family (a trail's sections, such as "Rush Trail - 2112 Section", and its parent trails' branches join their family "Rush Trail", and same-named pieces that touch, such as a trail split at its forks, are one path), each way drawn once and open to its trails' sport groups (a footpath to run and hike only); and one per run of same-named OSM road ways joined end to end, for roads open to bikes or to people on foot (no motorways, nor roads closed to both), open to any sport. Roads give no candidates. A long trail stays one path, its line in up to 64 pieces that each fit a record. An agency trail that OSM also maps gives one path, OSM's: when 90% of the agency path follows one OSM trail path, by the same test segments are held to, only the OSM path is written, keeping its name, line and id, and opened to the agency path's sport groups too, so both sources' segments lie on it. A path's id hashes its kind, its name and the features at its two ends, so re-extracting keeps it.

`publish-paths` asks the AppView, a tile under 1 degree at a time, for every live segment's line and sport group (`importer.getSegmentLines`), and publishes as a `feed.path`, keyed by its id, each path in the file one of them lies on, by the AppView's own test (`PathOverlap`: each stretch counts only for the nearest paths open to its sport group, so a trail running parallel 20 m away doesn't take a neighbour's segments). So a trail gets its path once it has a segment, and a city street only once an athlete has made a segment on it. The importer's own segments are held to a stricter test: one lies on a path only when 90% of it follows the path, so it's listed on its own trail and on trails sharing its tread, not on a neighbour it overlaps for part of its length. The AppView applies the same test. A published path whose name, line or sports changed in the file is rewritten under its key, Each path record names the regions whose files list it (`regions`): a path the file lists takes the file's region, one the region listed that the file no longer holds loses it, and a path is deleted once no region lists it, so a neighbouring region's paths are never touched. A path published before records named their regions is never deleted. `scripts/segment-import.sh` runs it last, on `build/segment-import/<region>-paths.ndjson`:

```bash
./gradlew :server-segment-importer:run --args="extract vermont-latest.osm.pbf --region us-vermont --out build/us-vermont.ndjson --paths build/us-vermont-paths.ndjson"
./gradlew :server-segment-importer:run --args="publish-paths build/us-vermont-paths.ndjson"
```

## Dive sites

```bash
./gradlew :server-segment-importer:run --args="extract-sites path/to/region-latest.osm.pbf --region eg-red-sea --out build/eg-red-sea-sites.ndjson"
./gradlew :server-segment-importer:run --args="publish-sites build/eg-red-sea-sites.ndjson"
```

`extract-sites` writes a region's named OpenStreetMap dive sites (nodes, and way or relation centroids, tagged `sport=scuba_diving` or `sport=free_diving`, but no shops, amenities, clubs, buildings, healthcare facilities or names with center, centre, platform, training, school or chamber; `openspec/specs/dive-sites/site-import`) to a sites file: a header line with `region`, `extractTimestamp` and `sitesVersion`, then one line per site with its `id` (`osm-node-<id>`, `osm-way-<id>` or `osm-relation-<id>`, so a re-extract keeps it), `name`, centre (`lat`, `lon` in degrees times 1e7), a `radius` sized by what it is (a mapped outline's extent plus 50 m, 100 to 500 m; 100 m for a wreck, 200 m for a reef or wall, else 150 m), `source` `osm` and `license` `ODbL-1.0`. It needs the whole extract: the trails-only cut `segment-fetch.sh` makes drops the sites.

`publish-sites` checks the file's sites with `importer.checkDiveSites`, 500 to a call, and writes a `feed.diveSite` in the service account's repo, keyed by site id, for each one no live (proposed or active) site lies within 50 m of. A site divers already placed wins, and is reported as a duplicate. It never rewrites, moves or deletes a site it published, so running it again, or on a newer file, writes only sites that are new; it stops at the first failed call, and running again carries on. `scripts/segment-import.sh <region> --sites` runs both after the segment steps, on a preset's whole download or the extract you give it, writing `build/segment-import/<region>-sites.ndjson`.

## Handoffs

When an athlete deletes an active segment or dive site, or leaves Activity Tracker, the AppView keeps a handoff of it (`openspec/specs/segments/segment-handoffs`, `dive-sites/site-handoffs`), so the trail or site others use stays on the map:

```bash
./gradlew :server-segment-importer:run --args="handoff"
```

`handoff` lists the pending handoffs with `importer.getHandoffs` (the AppView first links any that a live segment or site now covers) and writes each as a `feed.segment` or `feed.diveSite` in the service account's repo, keyed by its handoff id, with `source` `athlete`, `license` `CC0-1.0` and no attribution. The AppView makes the copy the old one's successor: its adopted names carry over, efforts and visits come back from stored segment tracks, and the old URI serves it. A handoff already written is never written again. Handoffs belong to no region, so `scripts/segment-import.sh <region> --handoff` runs it last, for all of them.

## Memory

The importer reads the file three times (relations, ways, then only the nodes trails use) and holds ids and coordinates in primitive arrays, so one region the size of a US state fits in a couple of GB of heap. For a large extract, shrink it first with [osmium](https://osmcode.org/osmium-tool/) (`brew install osmium-tool`) to the features the importer reads:

```bash
osmium tags-filter region-latest.osm.pbf w/highway w/piste:type w/aerialway r/route -o region-trails.osm.pbf
```

The importer's JVM gets the default heap, a quarter of the machine's memory; set `SEGMENT_IMPORTER_HEAP=6g` (read by the Gradle `run` task) to give it more.

## Test fixture

`src/test/resources/trails.osm` is a hand-written extract: a mountain-bike trail crossed by a named road, an unnamed side path, a private track, a sidewalk, a one-way descent, a hiking route over unnamed paths, and a one-way family of named sections that forks and rejoins, with a section shorter than any group's minimum and a feeder ending in the middle of a section, crossed by a named trail where its branches rejoin. `trails.osm.pbf` is built from it, and `ExtractTest` checks the importer's output on it against `expected-candidates.ndjson`. After editing `trails.osm`, rebuild the PBF and the expected output, then review both diffs:

```bash
osmium cat server/segment-importer/src/test/resources/trails.osm -o server/segment-importer/src/test/resources/trails.osm.pbf -O --output-header="osmosis_replication_timestamp=2026-10-01T00:00:00Z"
```

```bash
UPDATE_EXPECTED=1 ./gradlew :server-segment-importer:test --tests "*.ExtractTest"
```
