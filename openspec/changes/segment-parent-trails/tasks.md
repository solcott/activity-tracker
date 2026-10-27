# Tasks

## 1. Trail kind

- [x] 1.1 Replace `Trail.fromRelation` and `Candidate.fromRelation` with a `kind` enum (`NAMED`, `RELATION`, `PARENT`) in `TrailBuilder.kt` and `CandidateCutter.kt`, updating every reader in the importer and its tests; verify `./gradlew :server-segment-importer:test` passes with `expected-candidates.ndjson` unchanged

## 2. Parent trails in TrailBuilder

- [x] 2.1 Add family grouping (the name up to the first `" - "`, plus ways named exactly the parent, per sport group, only for families with at least one sectioned way) and the directed path search over way ends, with the 1,000-step budget per family and group, as design.md describes; verify with new `TrailBuilderTest` cases built with `OsmBuilder`: a one-way chain of three named sections gives one parent trail through all three, a single-name family gives none, and a two-way pair gives one trail traversable both ways
- [x] 2.2 Keep distinct way-sets (a path and its reverse as one) up to 4 per family and group, else only the longest, and log a family that hits the search budget; verify a `TrailBuilderTest` case: a one-way diamond (Tom Sawyer → Exit Stage Left | Through the Trees → Rush Trail) gives two parents, and a family with six paths gives one, the longest
- [x] 2.3 Join only at way ends; verify a `TrailBuilderTest` case: a same-family way ending at a node in the middle of a section is on no parent trail, and the parent follows the main way end to end

## 3. Cutting, naming and dedup

- [x] 3.1 In `CandidateCutter.cut`, give a `PARENT` trail only its whole piece; `TrailBuilder` names it `<Parent>` or `<Parent> via <section>` from the kept paths, and the cutter adds the heading suffix for two-way, as today; verify `CandidateCutterTest` cases: a parent crossed by a named road gives one candidate per direction, the diamond's candidates are named "Rush Trail via Exit Stage Left Section" and "Rush Trail via Through the Trees Trail Section", and a parent over 50 km gives none
- [x] 3.2 Rank `CandidateDedup` by agency, relation, named whole, parent, stretch, then id; verify `CandidateDedupTest` cases: a parent that duplicates one of its sections' whole candidates loses to it, and a route relation over the same ways as a parent wins with no duplicate ids left
- [x] 3.3 Add a sectioned one-way family to `src/test/resources/trails.osm`: a fork that rejoins, a section shorter than the ride minimum, and a feeder ending mid-section. Rebuild `trails.osm.pbf` and regenerate `expected-candidates.ndjson` per the README's test-fixture steps, review the diff (only new parent candidates added, no existing id or name changed), and verify `ExtractTest` passes
- [x] 3.4 Document parent trails in `server/segment-importer/README.md`: which names form a family, branches and "via" names, feeders, and the whole-only rule. Verify the README's extract example still runs as written

## 4. Integration

- [x] 4.1 Re-extract Draper (`./gradlew :server-segment-importer:run --args="extract build/segment-import/draper-trails.osm.pbf --agency usfs=build/segment-import/usfs-draper.geojsonl --region us-utah-draper --out build/segment-import/us-utah-draper.ndjson"`) and verify the output has `ride` candidates "Rush Trail via Exit Stage Left Section" and "Rush Trail via Through the Trees Trail Section" running from Tom Sawyer to the end of Limelight, and that every candidate id from the previous extract is still present
- [x] 4.2 Run `./gradlew :server-segment-importer:check` (tests, ktfmt, detekt) and verify it passes

## 5. Spans to junctions

- [x] 5.1 Have `TrailBuilder` give each `PARENT` trail its family name and the ways on no other kept path of its family; verify a `TrailBuilderTest` case: in the Rush diamond, each branch's own ways are only its branch section
- [x] 5.2 In `CandidateCutter`, give a `PARENT` trail a span from its start to each junction (a node between differently named sections where a named way outside the family meets it) and from each junction to its end, each permitted direction, leaving out single-name spans, and name spans `<trail> to <J>` / `<trail> from <J>` as design.md describes; verify `CandidateCutterTest` cases: a Rush-like parent with named trails at two section boundaries gives "… to Canyon Hollow Trail", "… to Bonneville Shoreline Trail" and "Rush Trail from Canyon Hollow Trail" but no span from the last junction, a road crossing mid-section gives no span, and a two-way parent's spans read "from" in reverse
- [x] 5.3 Verify in `CandidateDedupTest` that a span shared by every branch, named after the family, leaves one candidate, and that a span loses to a named whole trail it duplicates
- [x] 5.4 Add a named trail meeting the fixture's Rush family at a section boundary in `src/test/resources/trails.osm`, rebuild the PBF and expected output per the README, review the diff (only new span candidates added), and verify `ExtractTest` passes
- [x] 5.5 Document junction spans and their names in the README's "Parent trails" section
- [x] 5.6 Re-extract and re-stage `us-utah-draper`, run `publish`, and verify that "Rush Trail via Exit Stage Left Section to Canyon Hollow Trail" is published and has an effort from each of the two Draper rides
- [x] 5.7 Run `./gradlew :server-segment-importer:check` and verify it passes

## Workflow follow-up

- Archive the change after review.
