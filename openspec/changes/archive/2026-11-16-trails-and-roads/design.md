# Design

## Context

- **Segment groups exist, under another name.**
  - Search and browse already fold one line's sport groups and directions into a "route": `segments.route_uri`, `SegmentRoutes.kt`, `SegmentViews.routeMatesOf`, the client's `SegmentRoute`, `RouteSwitcher` and `wholeRoutes`.
  - This change renames that concept and builds on it.
- **The importer knows trails, but publishes none of it.**
  - It builds a `Trail` per sport group (`TrailBuilder`) and cuts it into candidates (`CandidateCutter`).
  - It reads named highways only to name junctions: `TrailTags.excluded` keeps roads out of every trail.
  - A published `feed.segment` holds no trail identity.
- **Fetching already keeps roads.** The preset fetch cuts the extract with `osmium tags-filter w/highway …`, so every road is already in the trails-only extract.
- **The AppView already has the geometry tools:**
  - a cell index of segment lines (`segment_cells`, `SegmentCells`);
  - a 30 m corridor (`SegmentGeometry.CORRIDOR_M`);
  - a duplicate test that follows one line along another.
- **Client area browse:**
  - It loads every segment crossing the view with `feed.searchSegments`, capped at 300 (`SegmentRepository.areaSegments`), and groups them locally.
  - Name search pages on the server (`feed.searchSegmentNames`).

## Goals / Non-Goals

**Goals:**
- Paths are pure grouping data. Segments, efforts and leaderboards don't change.
- One membership test, shared by the AppView (which serves membership) and the importer (which decides what to publish).
- Area browse keeps its current shape: one area load, grouped on the client.

**Non-Goals:**
- Road segments of any kind. Popular road climbs are a follow-up change.
- Athlete-created paths, and editing a path in the app.
- Deleting paths. A path with no visible segments simply isn't listed.
- Changing `feed.searchSegments` or `feed.searchSegmentNames` results, beyond the rename. Older clients keep working.

## Decisions

### `feed.path`, one record for trails and roads
- **Record:**
  - `kind` (`trail` | `road`, open enum), `name`, and `lines`: 1–32 encoded polylines at precision 6, each at most 100,000 characters, as `feed.segment#line`.
  - Plus `source`, `license`, `attribution` and `createdAt`.
  - The record key is the importer's path id.
- **Why "path":** a neutral word users never see. "Route" stays free for planned routes. "Trail" can't be the record name because roads are paths too.
- **Why line pieces:**
  - A single line field can't hold a 400 km trail within the record's limits.
  - A route relation with gaps is also naturally several pieces.
- **Alternatives considered:**
  - A `trail` reference on each segment. That needs every imported segment republished, and can't express a segment that spans two trails or that an athlete made.
  - Deriving paths in the AppView from the longest containing segment. That fails for trails over 50 km, whose whole-trail segment is never published, and has no name for roads.

### Membership is worked out from the lines, in a shared test

Refined after the first Utah run, where parallel trails 20 m apart claimed each other's segments, ride segments leaked onto foot paths, and a sectioned trail came out as ten paths:
- **Nearest path wins.** `PathOverlap.pathsUnder(segment, candidates)` samples the segment every 10 m and credits each sample only to the candidate paths within 5 m of the nearest one (inside the corridor), so a parallel trail loses to the one a segment is drawn on, while trails sharing tread both win. The same function runs in the AppView and the importer.
- **Paths carry their sports.** `feed.path#sports`, from the trails' sport groups (absent for roads), drops a segment of another sport group before the comparison.
- **One path per trail family.** The importer groups trails by family (a parent trail's family, else the name before " - ") and by touching ways, and writes each family's ways once as pieces (up to 64).
- **Every segment, and stale paths.** `importer.getSegmentLines` serves every live segment's line and sport group, so `publish-paths` publishes what any segment needs, and it deletes the importer's paths inside the file's area that the file no longer holds.
- **Upkeep.** Indexing or deleting a path re-works out every segment sharing its cells, since a new or removed neighbour can change which path is nearest.
- **The test:** `SegmentGeometry.overlapMeters(segment, pathPieces)` walks the segment's line and adds up the length of the stretches lying within `CORRIDOR_M` of any piece, in either direction.
  - A segment is on a path when that overlap is at least `min(0.25 × length, 1000 m)`.
  - Lifts are never on a path.
- **Where it lives:** in `core/location`, so the AppView and the importer can't disagree.
- **Why this threshold:** a quarter of the length or 1 km means a junction clip never counts, but a 2 km stretch of Ann's inside a 20 km combined ride does. The constants are tunable without a spec change only if the spec's scenarios still hold, so they're stated in the spec.

### AppView storage and upkeep
- **Migration `V64__segment_paths.sql`:**
  - `paths`: uri, did, rkey, kind, name, lines text, length_m, source, license, attribution, indexed_at.
  - `path_cells`: cell, path_uri.
  - `path_segments`: path_uri, segment_uri, primary key on both, plus an index on segment_uri.
  - Renames `segments.route_uri` to `group_uri`, and its index.
- **Indexing a path** (only when the repo is `SEGMENT_IMPORTER_DID` and the record isn't in a space): validate it, store its cells, then find candidate segments through `segment_cells` that share cells and run the test. Re-lining replaces the cells and membership; deleting drops both.
- **Indexing a segment** (new, re-lined, re-statused, deleted): look up candidate paths through `path_cells` and run the test, beside the existing `reroute`.
- **Backfill:** none is needed. Membership is stored whatever a segment's status (queries keep only live ones), and indexing a path works out which already-indexed segments lie on it, so segments indexed before paths existed get theirs when the first paths are published.
- **Long lines:** a 400 km path covers many cells. Cells are stored per piece at the segment cell size. The candidate lookup is bounded by the cell index, and the test runs only on segments that share a cell.

### Queries
- **`segmentView`:** gains `paths: [at-uri]`. `route` is renamed `group`.
- **`feed.getPath`:** `{ path: pathView, segments: [segmentView] }`.
- **`feed.getPaths`:** takes `uris` (max 25) and returns `{ paths: [pathView] }`.
- **`pathView`:**
  - uri, kind, name, lines, lengthMeters, source, license, attribution;
  - plus, computed for the viewer through `listedTo`: `sports` (app order) and `groupCount`.
- **`feed.searchPathNames`:**
  - Params: q, bbox?, sport?, centerLat/Lng?, limit ≤ 50, cursor.
  - It returns `results`, each an object holding either `path` (a `pathView`) or `group` (the group's `segmentView`s). An object with two optional fields rather than a union: the Kotlin mirror stays a plain data class.
  - SQL:
    1. Match segments by current name (the existing `NameMatch` ranks) and paths by name.
    2. Map each matching segment to its paths, or to its group when it has none.
    3. `GROUP BY` item key with the minimum rank and minimum distance.
    4. Order, then page by offset, as name search pages routes today.
  - A path counts only when it has a segment listed to the viewer that passes the sport and box filters.
- **`importer.getUnpathedSegments`:**
  - Params: bbox (≤ 1°) and cursor. It returns `{ segments: [{uri, line}], cursor }`.
  - Live, run kind, `NOT EXISTS path_segments`.
  - Gated like the other `importer.*` calls.
  - Segments are public records, so this reveals nothing new.

**Why area browse stays client-grouped.**
- The client already holds every area segment, and now each one names its paths.
- It asks `feed.getPaths` only for the path URIs it hasn't cached. A row's `sports`/`groupCount` come from the path view, so they describe the whole path, not just the visible part.
- Alternatives considered:
  - A server-side `searchPaths` area query. That means a second area protocol, and losing lifts, which come from the same segment load.
  - Embedding path views in every segment view. That repeats long lines.

### Client
- **Rename the route types:**
  - `SegmentRoute` → `SegmentGroupRow`;
  - `RouteSwitcher` → `GroupSwitcher`;
  - `routeKey` → `groupKey`, `wholeRoutes` → `wholeGroups`;
  - `routeMembers` → `groupMembers`.
  - Text helpers and strings are renamed to match.
- **Browse model:** `BrowseRow` is a sealed type, `PathRow(path, segments)` or `GroupRow(group)`.
  - **Area:** group the area segments by path, putting a segment on each of its paths. Segments with no path become `GroupRow`s by `group`.
  - **Names:** map the `searchPathNames` items directly.
  - The sport filter is applied to segments before grouping. A path row survives only with a segment left.
  - Selection, hover and the map key on the row key: the path URI, or the group key.
- **Map:** a path row draws its line pieces. A group row draws its representative line, as today. Lifts are unchanged.
- **`PathScreen(uri, sport)`:** in `:feature-segment-api`, with a presenter in `-presenter` and a UI in `-ui`.
  - It loads `getPath` through the content-loading helpers and groups the segments into `GroupRow`s, longest first, using the same row texts as browse.
  - It reuses the segment map composables for the line and members.
  - Selection, hover and "move the map to a selected row" behave exactly as in browse.
    - The browse pieces that do this (the selected key, the hovered key, `SegmentsMap` roles, tap-to-nearest-line, and the camera request for an off-screen row) move into a shared selectable map-and-list helper that both screens use.
    - Alternative considered: copying that logic into the Trail page. Rejected, because the two would drift apart.
  - Opening a row goes to `SegmentScreen` as browse does today, so the group switcher keeps working. The page is kept on the back stack, so its selection survives.
- **iOS:** a native `PathView.swift`, plus updated browse rows in `SegmentBrowseView.swift`, all over the same `-api` state, so Swift export carries it. The new strings go through `generateIosResources`.
- **Strings:**
  - `search_mode_trails_roads` = "Trails & roads";
  - path kind labels "Trail" and "Road";
  - "%d segments";
  - "No segments here yet";
  - "Zoom in to see trails and roads".

### Importer
- **Paths file:** `extract --paths <file>` writes NDJSON with id, kind, name, lines, source, license, attribution and length.
  - **Trail paths** come from `TrailBuilder.build`. Trails of different sport groups that follow the same ways are merged into one path, keyed by kind, the trail's name and its end features, as candidate ids are (so the hash covers no sport group or direction). Pieces are split at ≤ 100,000 encoded characters after the usual simplification.
  - **Road paths** come from a new `RoadBuilder`. It joins named highway ways end to end by name, the way `TrailBuilder` joins named trail ways, keeping road classes open to bikes or feet and leaving out `motorway`, `motorway_link`, and ways tagged `bicycle=no` + `foot=no` or closed `access`. It cuts no candidates.
- **`publish-paths <paths file>`** (the file's header names the region):
  1. Covers the paths file's box in ≤ 1° tiles and pages `importer.getUnpathedSegments` for each.
  2. Tests each segment against the region's paths with `overlapMeters` through a local cell index.
  3. Writes every matched path with `applyWrites` under the paced writer: `create` for a new key, `update` when its name or lines changed.
  4. Reports how many were published, refreshed, and still unpathed.
- **`segment-import.sh`:** passes `--paths build/segment-import/<region>/paths.ndjson` to extract and runs `publish-paths` after `refine`.

## Risks / Trade-offs

- [Road paths for a city are large: thousands of named streets in the paths file] → Only matched paths are published. The file is local and NDJSON-streamed, and `publish-paths` loads it through a cell index.
- [The membership threshold misplaces an edge case, such as a short segment along two parallel trails 20 m apart] → It may then lie on both, which is harmless (it's listed twice). The constants live in one shared place.
- [A long path makes `getPath`/`getPaths` payloads heavy] → `getPaths` is capped at 25. Pieces are already simplified. If needed, a later change can add a simplified `previewLines` without changing the specs.
- [The cached `path_segments` drifts from the lines] → Every path and segment write recomputes its side. The startup backfill catches anything indexed before the migration.
- [The lexicon rename (`route` → `group`, `routeMembers` → `groupMembers`) breaks clients built before it] → The app ships client and AppView together and there are no third-party consumers. The old `route` field isn't kept as an alias.
- [A segment on no path stays a row of its own until `publish-paths` runs for its region] → Expected. Running the import regularly picks up new athlete segments.

## Migration Plan

1. Deploy the AppView with V64 and the path indexer. Segment views now carry `group` and empty `paths`.
2. Ship clients. Every row is a group row until paths exist, so the behaviour matches today's grouping.
3. Run `scripts/segment-import.sh <region>` per region. It ends with `publish-paths`, after which rows fold into trails and roads.
4. **Rollback:** an older client fails on the renamed fields, so roll back the AppView and the client together. Path records left in the importer repo are harmless, since an older AppView doesn't index them.

## Open Questions

- Whether `pathView` should carry a box or a simplified preview line for map drawing, instead of the full pieces. This can be decided by measuring payloads on a real region. It doesn't change the specs.
