# Tasks

## 1. Rename routes to segment groups

- [x] 1.1 Rename the route lexicon fields:
  - `feed.defs#segmentView.route` becomes `group`, and `feed.getSegment`'s `routeMembers` becomes `groupMembers`, in the JSON and in the `core/lexicon` mirror.
  - Update the descriptions and the `searchSegments` note.
  - Verify with `./gradlew :core-lexicon:jvmTest`.
- [x] 1.2 Rename the AppView side and verify `./gradlew :server-appview:test` passes, with the route tests renamed:
  - Add `V64__segment_paths.sql`, starting with the `route_uri` → `group_uri` column and index rename. The path tables join it in 2.2.
  - Rename `SegmentRoutes.kt` to `SegmentGroups.kt`, `routeKey` to `groupKey`, and `routeMatesOf` and its kin.
- [x] 1.3 Rename the client side and verify `:core-data:jvmTest`, `:feature-segment-presenter:jvmTest` and `:feature-segment-ui:jvmTest`:
  - `SegmentRoute`, `RouteSwitcher`, `wholeRoutes`, and their texts and strings to group names.
  - `:app-fixtures` samples.
  - The Swift call sites in `iosApp`.
- [x] 1.4 Move the `segments/segment-routes` wording into `segments/segment-groups` everywhere outside `openspec/`: code comments that cite the spec, and `CLAUDE.md` if it names routes. Verify that `grep -rn "segment-routes" --exclude-dir=openspec .` finds nothing.

## 2. Shared overlap test and AppView paths

- [x] 2.1 Add `SegmentGeometry.overlapMeters` and the on-path rule to `core/location`: a quarter of the length or 1 km, either direction, any piece. Verify with `:core-location:jvmTest`, covering a whole stretch, a two-trail combination, a 40 m junction clip, the reverse direction and a multi-piece path.
- [x] 2.2 Add the `feed.path` lexicon, its Kotlin mirror and the `Nsid` indexed collection.
  - The migration (`V64`) gets the `paths`, `path_cells` and `path_segments` tables.
  - Run the `lexicon-auditor` agent, and verify it reports no mismatches.
- [x] 2.3 Index paths from the importer DID only, outside spaces. Verify with AppView tests:
  - Validate the kind, the name and each piece.
  - Store the cells and work out membership from segment cells.
  - Re-lining a path replaces its membership, and deleting it removes it.
  - An athlete's path and an unknown kind aren't indexed.
- [x] 2.4 Recompute a segment's paths whenever it is indexed, re-lined, re-statused or deleted. Segments indexed before paths existed get theirs when a path is indexed over them (no startup backfill needed). Verify with AppView tests for a new road path over existing segments and a re-lined segment that moves from Ann's to Hogback.
- [x] 2.5 Add `paths` to segment views. Add `feed.getPath`, `feed.getPaths` and `pathView` (with the viewer's `sports` and `groupCount` through `listedTo`), plus their routes. Verify with AppView tests, including `NotFound`, the 25-URI cap, and a path whose only segment is someone else's proposal.
- [x] 2.6 Add `feed.searchPathNames`:
  - Path and segment-name matching, ranked and grouped into path and loose-group items, then paged by item.
  - The box and sport filters, and lifts left out.
  - Verify with AppView tests: prefix ranking, a renamed stretch finding its trail, a loose road segment, a path with no visible segments, paging with limit 2, sport, and a box that's too large.
- [x] 2.7 Add `importer.getUnpathedSegments`, gated to the importer DID and bounded to 1°. Verify with AppView tests for an unpathed athlete segment, a segment that is on a path, and a refused athlete caller.

## 3. Importer paths

- [x] 3.1 Build trail paths from `TrailBuilder` trails and verify with `:server-segment-importer:test`:
  - One path per physical trail across sport groups.
  - Ids stable across re-extracts.
  - Agency source and attribution.
  - Long trails split into pieces of at most 100,000 characters.
- [x] 3.2 Add a `RoadBuilder` that joins named OSM road ways into road paths, leaving out motorways, motorway links and ways closed to both bikes and feet, and cutting no candidates. Verify with importer tests for a joined street, a motorway, and a road with `bicycle=no` but foot allowed.
- [x] 3.3 Have `extract --paths <file>` write the paths NDJSON. Verify with an importer test on the fixture extract, and that a run on a small `.osm.pbf` writes both files.
- [x] 3.4 Add the `publish-paths` command:
  - Page tiles of `getUnpathedSegments`, test with `overlapMeters`, and create or update the matched paths through the paced writer.
  - Report published, refreshed and still unpathed counts.
  - Verify with importer tests against a mock AppView and PDS: a new trail, a road with one athlete segment, nothing new, and a refreshed line.
- [x] 3.5 Run `publish-paths` last in `scripts/segment-import.sh`, and document `extract --paths` and `publish-paths` in `server/segment-importer/README.md` and the commands list in `CLAUDE.md`. Verify with `bash -n scripts/segment-import.sh` and a local run against `scripts/dev.sh`.

## 4. Browse by trails and roads

- [x] 4.1 Add `getPath`, `getPaths` and `searchPathNames` to `SegmentRepository`, with a path view cache keyed by URI. Verify with `:core-data:jvmTest` using `MockEngine`.
- [x] 4.2 Add the browse model and verify with presenter tests for grouping, the filter, a segment on two paths, and selection by row:
  - A sealed `BrowseRow` (`PathRow` | `GroupRow`).
  - Area rows: segments grouped by path, loose groups by `group`, the sport filter applied before grouping.
  - Name rows from `searchPathNames` items.
  - Selection, hover and map lines keyed by row.
- [x] 4.3 Rename the mode to "Trails & roads" and update the row texts (kind, length, sports, "%d segments") and the zoom message in `strings.xml`. Run `generateIosResources`, and verify with `:feature-segment-ui:jvmTest`, `ContentLoadingConformanceTest` and `checkIosResources`.
- [x] 4.4 Update the Compose browse UI: path rows, the map drawing path pieces, opening a path row into `PathScreen`. Update `:app-fixtures` browse samples with a path and a loose road segment. Verify with UI tests, and refresh screenshots with `:androidApp:updateDebugScreenshotTestDefaultTestSuite`.

## 5. Trail page

- [x] 5.1 Extract browse's selection into a shared map-and-list selection helper in `:feature-segment-api` and `-presenter`, and switch browse onto it. It covers the selected key, the hovered key, map roles, tap-to-nearest-line, and the camera request for an off-screen row. Verify that the existing browse presenter and UI tests still pass unchanged.
- [x] 5.2 Add `PathScreen(uri, sport)`, its state and events, in `:feature-segment-api`, and the presenter in `-presenter`:
  - Loads `getPath` as content, with groups longest first and the sport filter with "show all".
  - Uses the shared selection: tapping a row selects it, tapping the selected row opens it, clicking opens it, and hover highlights.
  - Opening a row opens `SegmentScreen` as browse does.
  - Verify with presenter tests for selecting, opening, hover, the map moving to an off-screen row, and the empty and offline states.
- [x] 5.3 Add the Compose Trail page UI: header, credit, a muted path line with segment lines over it, the selected and hovered highlight, and the group rows with their mark and scroll-into-view. Add `:app-fixtures` samples, including a selected state. Verify with UI tests (row tap selects, line tap marks the row, pointer hover), `ContentLoadingConformanceTest`, and new screenshot references.
- [x] 5.4 Check that the desktop app works end to end against the local stack (`scripts/dev.sh`, `:desktopApp:hotRun`), after a region import with `publish-paths`. Searching "ann's" should list one Ann's row, and opening it should list its segment groups, select a stretch by row and by line, highlight it on hover, and open a segment with its switcher.

## 6. Native iOS

- [x] 6.1 Update the native browse in `SegmentBrowseView.swift` for path and group rows, the mode name, and map selection by row. Verify with `InteractiveMapsTextTests`, updated for path rows, and the browse snapshot suites on the iOS 26.0 simulator.
- [x] 6.2 Add the native Trail page (`PathView.swift`): navigation bar, native map, rows, the shared selection (a map tap selects the nearest segment and marks and scrolls its row; tapping the selected row opens it), and opening a segment on the same tab. Verify with a text test and a snapshot test, then in the simulator from Trails & roads.

## 7. Membership refinements (after the first Utah run)

- [x] 7.1 Make `PathOverlap.pathsUnder` credit each sample only to the paths within 5 m of the nearest, and drop paths not open to the segment's sport group. Verify with `:core-location:jvmTest` (parallel trail, shared tread, foot path).
- [x] 7.2 Add `sports` to `feed.path` (raise `lines` to 64 pieces) and a `V65` column, rename `importer.getUnpathedSegments` to `getSegmentLines`, and re-work out nearby segments when a path is indexed or deleted. Verify with AppView tests for a parallel trail, a foot path and a removed path.
- [x] 7.3 Group the importer's trail paths by family and touching ways, with their sports, and make `publish-paths` serve every segment and delete stale paths in its area. Verify with importer tests for Rush Trail's sections, a forked trail and stale-path deletion.
- [x] 7.4 Size the Compose segmented control's choices to their labels so "Trails & roads" isn't cut off. Verify with the Search tab screenshots.
- [x] 7.5 Re-run Utah's extract and `publish-paths` against the local stack and check Achtung Baby, Hoof N Boot, Coyote Hollow and Rush Trail.

## 8. Integration

- [x] 8.1 Run `./gradlew check` through the `gradle-runner` agent, and `xcodebuild test` for the iOS app, and verify both pass.

## Workflow follow-up

- Run `/opsx:archive trails-and-roads` once the change is reviewed, and confirm that `segments/segment-routes` is retired and `segment-groups`, `segment-paths`, `path-page` and `path-import` exist in `openspec/specs/`.
- Follow-up change: auto-created road climb segments, from OSM `route=bicycle` relations and from named roads outside built-up areas. They would be ride only, cover whole climbs, and be published only past the rider gate.
