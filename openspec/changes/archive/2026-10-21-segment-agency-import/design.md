# Design

## Context

The importer (`server/segment-importer`) turns an OSM extract into candidates in three passes: `OsmReader` → `OsmData` (ways with tag maps and node-id arrays, relations, `NodeCoords`), `TrailBuilder` (joins named ways per sport group, using `TrailTags.wayGroups`/`directions`), `CandidateCutter` (junctions are node ids shared with a named way that `TrailTags.marksJunctions`; ids hash way ids and end node ids) and `CandidateDedup` (preference order, then the AppView's duplicate test). `Extract.write` stamps every entry `source: osm`, `license: ODbL-1.0`, and `Publish.create` writes `SegmentSource.OSM`/`LICENSE_OSM` whatever the candidate says.

`stage` uploads one file as one run, and `finishSegmentCandidates` then drops every candidate of the region that wasn't in that run. Two files for one region would remove each other's candidates.

The AppView stores a segment's record as `AtJson` re-encoded from `SegmentRecord`, so a new optional field reaches `feed.getSegment` without a column. Candidates are columns (`segment_candidates`), so an attribution needs one. The client credit is `SegmentCredit.Agency(license)`, rendered as "From public agency trail data".

## Goals / Non-Goals

**Goals:**
- Agency trails go through the same joining, cutting, naming, dedup, staging and publishing as OSM trails, with the per-source differences in one place each.
- Ids stay stable across re-downloads of an agency dataset, as OSM ids do across extracts.
- No GIS library in the JVM: the operator converts agency formats with GDAL, as they shrink OSM with osmium.

**Non-Goals:**
- BLM. Its national GTLF layer mixes roads and trails, and its mode-of-transport coding needs its own mapping study; a later dataset kind adds it without changing this design.
- Letting agency closures veto OSM candidates (a wilderness trail closed to bikes still gets OSM `ride` candidates when OSM doesn't tag `bicycle=no`). Dedup compares whole lines per group, so a veto needs a different test; see Risks.
- Cutting agency trails at OSM roads or OSM trails: the two sources share no topology.
- One-way agency trails: neither dataset carries a direction-of-travel restriction.
- Fetching datasets: the operator downloads them.

## Decisions

**Input is GeoJSONSeq, one file per dataset, converted with `ogr2ogr`.** USFS publishes `TrailNFS_Publish` as shapefile, file geodatabase and GeoJSON; NPS publishes `NPS_Public_Trails` as GeoJSON and shapefile. The README gives one `ogr2ogr -f GeoJSONSeq -t_srs EPSG:4326 -spat <bbox> -spat_srs EPSG:4326` per dataset: it handles every source format and projection, and `-spat` keeps features that touch the region whole instead of clipping them. GeoJSONSeq streams one feature per line through kotlinx-serialization, so a national file never sits in memory as a tree. Checked against the current data: the Forest Service's file geodatabase (`S_USA.TrailNFS_Publish.gdb.zip`, about 110 MB, already in WGS 84) holds every feature as a MultiLineString, single-part in practice, and `ogr2ogr` reads it straight from the URL with `/vsizip//vsicurl/`, fetching only what the box needs. The Park Service publishes its trails as an ArcGIS feature service (`National_Park_Service_Trails`), which `ogr2ogr` reads directly from a `query` URL carrying the same box, so neither dataset needs a full download. *Alternatives:* GeoTools for shapefiles (a large dependency for a one-off conversion); plain GeoJSON FeatureCollections (a state of trails is a few hundred MB as a `JsonElement` tree).

**`extract` takes every source for a region at once.** New form: `extract [file.osm.pbf] [--agency usfs=<file>]... --region <id> --out <file>`, at least one input. Candidates from all sources go through one dedup and one sorted file, so a region is still one staging run and cross-source duplicates are resolved before upload. *Alternative:* a separate region id per source (`us-vermont-usfs`). Rejected: staging never sees both, so duplicates are only caught at `publish` by `checkSegment`, and whichever source is published first wins rather than the preferred one.

**Agency data is converted into the existing `OsmData` model, with per-source rules.** `AgencyReader` builds `OsmWay`s: synthetic way ids (sequential within the run), a tag map holding the normalised fields (`name`, the groups, the dataset kind and feature key) and node-id arrays from a snapper. `TrailTags`' functions that decide groups, exclusion, junction-marking and direction move behind a small `TrailRules` interface; `TrailTags` implements it for OSM and `AgencyRules` for agency ways, each `OsmWay` carrying which rules apply. `TrailBuilder`, `CandidateCutter` and `CandidateDedup` then work unchanged except for asking the way's rules. *Alternative:* translating agency attributes into fake OSM tags (`highway=path`, `bicycle=no`). Rejected: `paddle` only exists for OSM relations, and fake tags would make a later OSM tag change silently alter agency behaviour.

**Snapping makes the topology.** Agency lines have coordinates, not shared node ids. The snapper hashes every vertex into ~1 m grid cells and gives it the id of an existing point within 1 m in its own or a neighbouring cell, else a new id; features are processed in feature-key order so the result is deterministic. One snapper serves all agency datasets of the run, so a USFS trail ending on an NPS trail is a junction. The agency's other lines of a trail's own name never cut it: the Forest Service publishes many trails twice, as a summer (`TERRA`) and a winter (`SNOW`) feature over the same ground, split at different mileposts, and a first run on Vermont data named hundreds of stretches "Wilkinson Trail: Wilkinson Trail to Wilkinson Trail" before this rule. OSM node ids and agency point ids live in disjoint ranges (agency ids negative), so the cutter's junction lookup can't confuse them.

**Agency ids hash feature keys and end positions.** `CandidateCutter.idOf` gains a source-aware key: for an agency candidate, `group|sense|dataset:featureKey,...|startE5|endE5[#visit]`, where the feature key is USFS `TRAIL_CN` plus its begin milepost `BMP` (one trail is several features), or NPS `GEOMETRYID`, plus the MultiLineString part index when above zero, and ends are rounded to 1e-5° (~1 m). The OSM key is unchanged, so every OSM candidate keeps its id. *Alternative:* hashing the geometry. Rejected: any re-digitising would change every id, and published segments are keyed by id.

**Allowed-use mapping lives in one table per dataset (`AgencyRules`).**

| Dataset | Kept when | `ride` | `run`, `hike` | `ski` | `paddle` |
|---|---|---|---|---|---|
| `usfs` (`TrailNFS_Publish`) | `TRAIL_NAME` set | `BICYCLE_` | `HIKER_PEDESTRIAN_` | `XCOUNTRY_SKI_` | `NONMOTOR_WATERCRAFT_` |
| `nps` (`NPS_Public_Trails`) | `TRLNAME` set, `TRLSTATUS` Existing, `PUBLICDISPLAY` Public Map Display, `OPENTOPUBLIC` not No | `TRLUSE` names bicycle or bike | hiker, pedestrian, hike or hiking | cross-country ski(ing) or XCSki | canoe, kayak, non-motorized watercraft or paddling |

A USFS use counts when its `_MANAGED`, `_ACCPT` or `_ACCPT_DISC` field holds anything but blank or `N/A` (their values are season ranges such as `06/15-10/31`); `_DISC` and `_RESTRICTED` don't count. `_ACCPT_DISC` carries the off-season of a managed use (hikers managed `05/01-11/01`, accepted or discouraged `11/02-04/30`) and the whole year on trails where bikes are allowed but not managed for, and `_ACCPT` alone carries winter fat-bike seasons on snow trails. Field names are matched without regard to case: the published files use upper case, the agency's map service lower case.

`TRLUSE` is free text in practice: a national count found `Hiker/Pedestrian`, `Hiker / Pedestrian`, `Hike`, `Bike`, `Cross Country Skiing`, `XCSki` and `Paddling`, separated by `|`, `,` or `/`. The reader splits it on all three, keeps only the letters of each part, lower-cased, and looks each up in a fixed table (`hiker`, `pedestrian`, `hike`, `hiking`; `bicycle`, `bike`; `crosscountryski`, `crosscountryskiing`, `xcski`; `canoe`, `kayak`, `canoekayak`, `nonmotorizedwatercraft`, `paddling`). About a third of park features list only `Unknown` or `Non-Motorized` and give no trail. `TRLSTATUS` and `PUBLICDISPLAY` are compared trimmed and without regard to case; `OPENTOPUBLIC` is empty on every feature today and only a `No` excludes. Feature keys checked against the national data: `GEOMETRYID` is set and unique on every park feature; `TRAIL_CN` is set on every Forest Service feature and was unique with `BMP` in a 206-feature Green Mountain sample; `BMP` `BMP` carries floating-point noise (`0.6900000000000001`), so the key rounds it to 0.001 mile. Some park features are MultiLineStrings (about 4%).

**Agency candidates win duplicates.** `CandidateDedup`'s order becomes source (agency first), relation, whole, id. Agency data is the land manager's statement of allowed use, and public domain avoids ODbL share-alike on the segments database. A published OSM segment is never replaced: `publish`'s `checkSegment` finds it as a live duplicate of the newer agency candidate, as for any rerouted trail.

**Attribution is an optional record field.** `feed.segment#attribution` and `importer.defs#candidate#attribution`: string, `maxLength` 1000, `maxGraphemes` 100, "who to credit for the line's data". Values: "USDA Forest Service", "National Park Service". The importer writes it per dataset; the AppView validates candidates' (rejects out of bounds) and, when indexing a segment, drops an out-of-bounds or blank one from the stored record rather than refusing the segment, since it is only a credit. The presenter maps it into `SegmentCredit.Agency(license, attribution: String?)`; the UI shows "From <attribution> trail data", or the existing generic text when null. It is ignored for other sources, so an athlete can't put arbitrary text in a credit line under their handle. *Alternatives:* encoding the agency in `source` (`agency-usfs`), which breaks the open enum's meaning and every source switch; or a fixed client-side table of agencies, which can't credit a dataset added later without a client release.

**Candidates file version 2.** Entries gain optional `attribution` and `agencyFeatures` (feature keys followed, like `osmWays`); `osmWays` becomes optional. `IMPORTER_VERSION` becomes 2 and `stage` refuses version 1 files, per the existing unknown-version requirement; re-extracting is cheap.

**Migration.** `segment_candidates.attribution varchar(1000) NULL`, in the next free Flyway version (V33 unless `segment-leaderboard-brackets` lands first). H2-portable `ALTER TABLE ... ADD COLUMN`.

## Risks / Trade-offs

- [Field names or values in the agency datasets differ from the table] → Task 1.1 checks them against real downloads before any reader code; fixtures are cut from those downloads.
- [An OSM `ride` candidate survives on a trail the agency closes to bikes] → Accepted for now; such a candidate is published only once three athletes have actually ridden it. A follow-up can drop OSM candidates whose line lies within an agency trail not open to their group.
- [Snapping joins two trails that pass within 1 m without meeting, or misses a junction digitised 2 m apart] → 1 m is well under GPS error and above typical GIS snapping tolerance; a missed junction only means fewer stretches.
- [Agency and OSM give near-identical but not duplicate lines (one is a stretch of the other)] → Both are staged; at most one is published per stretch because `publish` checks live segments, and riders decide which gets ridden first.
- [An agency candidate wins dedup over an OSM candidate whose segment was already published] → `checkSegment` reports the agency candidate as a duplicate of the live OSM segment, so it isn't published and the OSM segment keeps its efforts.
- [USFS names in capitals title-cased badly ("Mckinley")] → Only all-caps names are changed; a minor cosmetic risk on imported names.

## Migration Plan

Deploy the AppView (migration, lexicon) before running the new importer; the new importer's `stage` sends `attribution`, which an old AppView would ignore under `ignoreUnknownKeys` but not store. Re-extract each region with its agency datasets and stage it; earlier OSM-only runs are replaced by the finish as usual. Rollback: run the old importer on OSM alone; agency segments already published stay, as published segments always do.
