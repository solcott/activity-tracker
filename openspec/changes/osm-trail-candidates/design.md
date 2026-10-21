# Design

## Context

See proposal.md for why. `core/location` already has what a candidate needs to look like a segment: `SegmentGeometry` (precision-6 polyline encoding, `lengthMeters`, `duplicates`), `TrackMath.simplify` (Ramer–Douglas–Peucker in metres), and `SegmentSportGroup` minimums and `MAX_METERS` in `core/lexicon`. Both are KMP modules with a `jvm()` target, so a JVM module can use them unchanged. `server/appview` is the only server module; settings flatten `server/x` to `:server-x`.

OSM has no elevation, so nothing here can tell a climb from a descent or rank by grade.

## Goals / Non-Goals

**Goals:**
- A repeatable, offline `extract` that runs on one Geofabrik region (a US state or similar) on a laptop.
- Candidates that pass the AppView's segment validation as they are, so staging and publishing never reshape them.
- Ids stable enough that re-extracting a region doesn't restart ridership counts for unchanged trails.

**Non-Goals:**
- Elevation, grades and climb categories (would need a DEM such as Copernicus GLO-30; a later change if wanted).
- Road segments. Only trails.
- Staging, publishing, the service account, agency data: later changes.
- Choosing the first region: `segment-import-publishing`.

## Decisions

**A standalone JVM module, `server/segment-importer`, with a CLI.** `application` plugin with `mainClass` `…segmentimporter.MainKt`, run as `./gradlew :server-segment-importer:run --args="extract …"`, on the `activitytracker.kotlin.jvm` convention plugin like the AppView. Subcommands are parsed by hand (`extract` now, `stage` and `publish` later): three commands don't justify a CLI library. *Alternative:* an AppView background job. Rejected: the AppView stays an indexer, and extraction is a batch job that needs memory the AppView shouldn't.

**`org.openstreetmap.pbf:osmpbf` to read PBF, three passes.** It is the small protobuf parser osmosis uses, on Maven Central, with no transitive framework. PBF stores nodes, then ways, then relations, so the reader goes: (1) relations, keeping qualifying route relations and their member way ids; (2) ways, keeping qualifying trail ways, member ways of kept relations, and every named highway's node ids (for junctions); (3) nodes, keeping coordinates of only the nodes kept ways use. Node ids are held in sorted primitive `LongArray`s with binary search, and coordinates in parallel `IntArray`s at 1e-7, so a US-state extract fits in a couple of GB. *Alternatives:* osm4j (not on Maven Central), Planetiler (a tile pipeline, far more than needed), shelling out to `osmium` (an extra install for every operator; still usable to pre-filter large extracts, which the README mentions).

**The pipeline is pure over an in-memory model.** `OsmReader` produces an `OsmData` (ways with tags and node ids, relations with members, node coordinates); `TrailBuilder` → `Trail`s per sport group; `CandidateCutter` → candidates; `CandidateDedup`; `CandidateWriter`. Tests build `OsmData` with a small DSL, so tag rules and cutting are tested without binary fixtures; one test reads a committed PBF fixture (a few KB, built from a hand-written `trails.osm` with `osmium cat`, both committed, the command in the README) to cover the reader.

**Tag rules live in one table per sport group** (`TrailTags`), so the agency change and later tuning edit one place. Values follow the spec; `highway=footway` qualifies for `ride` only with `bicycle=yes|designated`.

**Joining and cutting.** Named ways join when they share the name, the sport group and an end node, and stop at a node where more than two such ways meet. Relations join members in order, reversing a way when needed; a gap starts a new trail. Junctions are nodes shared with any other named trail of any group or a named `highway` road; they are found from the node-id sets of pass 2. Stretches are cut at junctions and joined greedily from the trail's start until each reaches the group's minimum; a short final stretch joins the previous one. The whole trail is a candidate too. Both directions are emitted unless `oneway=yes|-1` (or `oneway:bicycle` for `ride`).

**Lines.** Each line is simplified with `TrackMath.simplify` at 5 m tolerance, which keeps it well inside the matcher's 30 m corridor, then encoded with `SegmentGeometry.encodePolyline`. Length is computed from the simplified line with `SegmentGeometry.lengthMeters`, as the AppView does, so a candidate never passes here and fails there. A line over the record's 100 kB limit is simplified again at a doubled tolerance until it fits (a 50 km trail is far below it in practice).

**Ids.** `sha256(sport | direction | wayId,wayId,… | firstNode | lastNode)`, base32, first 26 chars. Only the ways and end nodes count, so moving an interior node (a geometry fix) keeps the id and only the line changes; replacing a way changes it.

**Names.** As the spec says. The compass direction is the 8-way bearing from start to end, reduced to the 4 cardinal words; names are truncated to 100 graphemes at a word boundary.

**Dedup.** Within each sport group, candidates are bucketed by their end points' `SegmentCells`; pairs in a bucket are compared with `SegmentGeometry.duplicates`, keeping relation > whole trail > stretch, then the lower id.

**Output: NDJSON.** One JSON object per line, sorted by id, written with kotlinx.serialization (`@Serializable data class Candidate`), plus a header line with the region, the extract's replication timestamp and the importer version. NDJSON streams, diffs well between runs, and is what `stage` will read. *Alternative:* SQLite. Rejected: nothing queries it locally; the AppView is the staging store (`segment-candidate-ridership`).

## Risks / Trade-offs

- [Many candidates per trail (whole + stretches, × directions, × groups)] → Expected: ridership decides which become segments, and unridden candidates cost only AppView rows. The extract logs counts per group so tuning is visible.
- [OSM names and tags are uneven] → Unnamed ways only count inside named relations. Tuning the tag table is cheap.
- [Memory on large extracts] → Primitive arrays and three passes; the README recommends one region at a time and, for big ones, `osmium tags-filter` first.
- [osmpbf is LGPL-3.0] → Used unmodified as a library by a server-side tool that isn't distributed.

## Open Questions

- The exact osmpbf version (1.5.0 was current at the last check); confirm on Maven Central when adding it to the catalog.
