# Tasks

## 1. Bridge across a chain of ways

- [x] 1.1 Add `TrailTags.refs(tags)`: the `ref` tag's values, split at `;` and trimmed, with blanks dropped. Verify with a `TrailTags` test: `"NF 87672;1001"` gives `{"NF 87672", "1001"}`, and a way with no `ref` gives the empty set.
- [x] 1.2 Build the node → ways index of walkable highway ways (design decision 1) once per `TrailBuilder.build()`, and replace `walk()` with the bounded shortest-path search.
  - Plain chains of crossable ways are accepted up to `BRIDGE_M`.
  - The bridge is kept as one `Placed(bridge = true)` slice per way.
  - Verify with TrailBuilderTest: the existing single-road bridge tests still pass, and a new test joins halves 180 m apart over two connected roads.
- [x] 1.3 Add the ref rule: a chain up to the ref limit is accepted when every way on it carries a ref that both end ways carry.
  - Verify with TrailBuilderTest cases for:
    - Little Cottonwood Creek's layout (`ref=1001` trail, two `NF 87672;1001` roads, 219 m), which joins;
    - 1.4 km with a 1 km limit, which stays split;
    - one road in the chain without the ref, which stays split;
    - the end ways not sharing the ref, which stays split.
- [x] 1.4 Keep the existing exclusions on chains. Verify with a TrailBuilderTest where one of two roads in a 150 m chain is `access=private`: the trail isn't joined.
- [x] 1.5 Check that cutting and candidates follow the joined trail. Verify with a CandidateCutterTest:
  - the joined trail gives a whole candidate whose `osmWays` include the bridged roads;
  - its ride, run and hike stretches still end at the same nodes.

## 2. The ref bridge limit as an option

- [x] 2.1 Thread `refBridgeM` from `extract()` through `TrailBuilder.build`, `candidatesOf` and `pathsOf`, defaulting to 1,000 m. Verify that the importer compiles and the tests from group 1 pass with the default.
- [x] 2.2 Parse `--ref-bridge-m` in `Main.parseExtract` as a positive integer, and add it to the usage text. Verify with Main's parsing tests: `500` is accepted, while `-5`, `0` and `abc` return the usage.
- [x] 2.3 Pass `REF_BRIDGE_M` from `scripts/segment-import.sh` to extract when it is set, and describe it in the script's header comment. Verify with `bash -n scripts/segment-import.sh`, and by reading the extract line it builds.

## 3. One path for a trail in both datasets

- [x] 3.1 In `pathsOf`, drop an agency trail path when at least 90% of it follows one OSM trail path, and give the OSM path the union of both paths' sport groups (design decision 4). Verify with ExtractTest or PathsTest cases for:
  - an agency trail along an OSM trail of another name: only the OSM path is written, with its id unchanged;
  - an agency trail open to ride along an OSM trail open to run and hike: the OSM path is open to all three;
  - an agency trail with no OSM trail along it: its path is still written.
- [x] 3.2 Regenerate the golden `expected-*.ndjson` files with `UPDATE_EXPECTED=1`, and check that each diff is a join or a merged path. Verify with `./gradlew :server-segment-importer:test`.

## 4. Docs

- [x] 4.1 Update `server/segment-importer/README.md`: chains of shared tread, the ref rule and `--ref-bridge-m` under "Joining and cutting trails", and the agency-into-OSM path rule under paths. Verify that the documented flag matches `Main`'s usage text.

## 5. Integration

- [x] 5.1 Re-extract us-utah (`extract … --paths`), then check with `jq`:
  - Little Cottonwood Creek Trail is one trail whose candidates include ways 1070727222 and 643619048;
  - the paths file holds one "Little Cottonwood Creek Trail" path and no agency "Little Cottonwood Creek" path;
  - the extract's run time is close to before.
- [x] 5.2 Run `./gradlew check` (through the gradle-runner agent) and get BUILD SUCCESSFUL.

## Workflow follow-up

- Re-import us-utah on the local stack and look at both trail pages in the app. The agency page should be gone once `publish-paths` has run.
- Archive the change with `/opsx:archive` after review.
