# Tasks

## 1. Module

- [ ] 1.1 Add `server/segment-importer` to `settings.gradle.kts` with a `build.gradle.kts` on `activitytracker.kotlin.jvm`, `application` (`mainClass` `io.github.solcott.activitytracker.segmentimporter.MainKt`) and kotlinx.serialization, depending on `projects.coreLocation` and `projects.coreLexicon`; add `osmpbf` to `gradle/libs.versions.toml` (confirm the version on Maven Central); verify `./gradlew :server-segment-importer:build` and the sorted-dependencies check pass
- [ ] 1.2 Add `Main.kt` with hand-parsed subcommands (`extract <pbf> --region <id> --out <file>`), printing usage and exiting non-zero on bad arguments; verify `./gradlew :server-segment-importer:run --args="extract"` prints the usage

## 2. Reading OSM

- [ ] 2.1 Add the `OsmData` model and a test DSL for building it; verify it compiles in `:server-segment-importer:test`
- [ ] 2.2 Implement `OsmReader` over osmpbf in three passes (relations, ways, nodes) with sorted `LongArray` id sets and `IntArray` coordinates at 1e-7; commit `src/test/resources/trails.osm` and the `trails.osm.pbf` built from it with `osmium cat`; verify an `OsmReaderTest` reads the fixture's ways, relations and coordinates

## 3. Trails

- [ ] 3.1 Implement `TrailTags` (qualifying and excluded tags per sport group, per the spec) with a test per scenario in the "Trail ways qualify", "Route relations are trails" and "Closed and unsuitable ways" requirements; verify `./gradlew :server-segment-importer:test --tests "*.TrailTagsTest"` passes
- [ ] 3.2 Implement `TrailBuilder`: named ways joined by name, group and end node (stopping where more than two meet), and route relations joined in member order with reversal and splitting at gaps; verify `TrailBuilderTest` covers the multi-way trail, the relation over unnamed paths and a relation with a gap

## 4. Candidates

- [ ] 4.1 Implement `CandidateCutter`: junctions with named trails and roads, greedy joining of short stretches, the whole trail, both directions unless one-way, length bounds from `SegmentSportGroup`; verify `CandidateCutterTest` covers the fire-road, short-stretch, unnamed-crossing, two-way and one-way scenarios
- [ ] 4.2 Add naming (`<trail>: <start> to <end>`, `(part n)`, compass direction for two-way whole trails, 100-grapheme truncation) and stable ids (sha256 of group, direction, way ids, end nodes); verify tests for each naming scenario and that re-running on a model with an unrelated edit keeps ids
- [ ] 4.3 Simplify (5 m, doubling until the line fits 100 kB), encode with `SegmentGeometry.encodePolyline`, and compute length from the simplified line; verify a test that every candidate passes the same checks as the AppView's `validLine` (copy the bounds via `SegmentSportGroup`, not the AppView code)
- [ ] 4.4 Implement `CandidateDedup` (cell-bucketed `SegmentGeometry.duplicates`, relation > whole > stretch > lower id); verify the relation-over-trail scenario test

## 5. Output and docs

- [ ] 5.1 Write NDJSON (header with region, extract timestamp and importer version; candidates sorted by id) and log per-group counts; verify an end-to-end `ExtractTest` on the PBF fixture against a committed expected file
- [ ] 5.2 Write `server/segment-importer/README.md` (what it does, running `extract`, memory notes and `osmium tags-filter` for large extracts, rebuilding the fixture) and add the extract command to AGENTS.md's Commands; verify the README's command runs on a downloaded small Geofabrik extract and writes candidates
- [ ] 5.3 Run `./gradlew check` and verify it passes
