# Tasks

## 1. Join trails (segments/trail-candidates)

- [x] 1.1 Group named ways by their name without its generic words, and name the trail after its longest way. Verify with a `TrailBuilderTest` case: `X Trail` + `X` + `X Trail` ways give one trail.
- [x] 1.2 Give `Placed` a node sub-range and bridge dangling chain ends across up to 200 m of a shared road or named way that is open to the group. Verify with `TrailBuilderTest` cases:
  - a trail split by a 100 m stretch of `highway=service` joins, and its edges carry the service way's id;
  - a 400 m stretch, or a road tagged `access=private`, stays split.
- [x] 1.3 Keep a relation's short road member (200 m or less, open, joining both neighbours) in its line. Verify with a `TrailBuilderTest` case.
- [x] 1.4 Trim member ways from a relation stretch's ends while they carry another trail's name and a way of the relation's own name or family remains. Verify with `TrailBuilderTest` cases:
  - "Clarks Trail" ending on an "Ann's Trail" way loses that way;
  - a relation of only differently named ways keeps all of them.
- [x] 1.5 Label a named trail after a relation trail with exactly the same ways. Verify with a `TrailBuilderTest` case: run and hike Clarks ways under a ride relation are labelled "Clarks".

## 2. Cut candidates (segments/trail-candidates)

- [x] 2.1 Split named and relation trails into direction runs. With more than one run, give each run as a non-whole piece plus its stretches, and no whole-trail piece. Verify with a `CandidateCutterTest` case: a relation that is two-way and then one-way gives no whole candidate, a both-ways run and a one-way run.
- [x] 2.2 Join stretches using the largest minimum among the groups that all of the trail's own ways qualify for. Verify with a `CandidateCutterTest` case: run and ride trails on the same ways with junctions 300 m and 700 m apart give identical stretch lines.
- [x] 2.3 Verify the dedup order with a `CandidateDedupTest` case: a whole relation is kept over another relation's run that duplicates it.

## 3. Path membership (segments/segment-paths)

- [x] 3.1 Add the importer share (0.9 of the length, no metre cap) to `PathOverlap.pathsUnder`. Verify with a `core-location` test: an importer segment 40% on a path doesn't lie on it, while an athlete's segment does.
- [x] 3.2 Use the importer share in the AppView's `PathIndex` for segments in the importer's repo. Verify with a `server-appview` test: the importer's long segment overlapping 2 km of another path isn't listed under it.
- [x] 3.3 Use the importer share in `publish-paths` for segments in its own repo. Verify with a `PublishPathsTest` case.

## 4. Docs, fixtures and checks

- [x] 4.1 Update the Clarks sample in `PathSamples.kt` to one "Clarks" group with ride one-way and run and hike both ways, then refresh the affected screenshots, iOS snapshots and text tests.
- [x] 4.2 Update `server/segment-importer/README.md` with the joining, trimming, run-cutting and stretch-minimum rules.
- [x] 4.3 Re-extract us-utah and check with `jq`:
  - Canyon Hollow's candidates follow ways 988920117 and 977484261;
  - every Canyon Hollow stretch exists for ride, run and hike with the same ways;
  - "Clarks" ride is 2022 m and one-way, with run and hike named "Clarks";
  - no BST candidate spans both Clarks and two-way ways.
- [x] 4.4 Run `./gradlew check`, and confirm `openspec validate trail-continuity` passes.
