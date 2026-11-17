# Proposal

## Why

Segment browse already folds a trail's sport groups and directions into one row, yet a trail still lists many rows. The importer cuts every trail into its whole line and its stretches between junctions, and athletes add their own segments on top, so "Ann's" shows as the whole trail plus each "A → B" stretch, and a city street with five athlete segments shows five rows. Athletes look for a place to ride or run: a trail or a road. They pick its segments from there.

## What Changes

- The Search tab's **Segments** mode becomes **Trails & roads**. Its map and list show trails and roads, one row each. A segment on no trail or road is still shown as a row of its own.
- **New `feed.path` record** (kind `trail` or `road`; name, line pieces, source, licence, attribution), published only by the segment importer. It isn't bound by the segment length limits, so a 400 km trail is one path.
- **The AppView works out which segments lie on each path** from their lines, many to many:
  - A segment counts as on a path when a meaningful share of its line follows the path. A clip at a junction doesn't count.
  - A segment that combines two trails, or a trail and a road, is on both.
  - Nothing is added to the segment record. Segment views carry the paths they're on.
- **New queries:**
  - `feed.getPath`: a path and every segment on it that the viewer may see.
  - `feed.getPaths`: views of several paths.
  - `feed.searchPathNames`: name search over paths and over segments on no path.
  - **Importer-only:** `importer.getUnpathedSegments`, the live public segments in a box that lie on no path.
- **New Trail page:** the path's name, kind, length and credit, a map of its line with its segments, and its segments listed one row per segment group. A row opens the segment page with its sport and direction switcher.
- **Importer:**
  - `extract` also writes the region's paths: its trails, merged across sport groups, and its named roads.
  - A new `publish-paths` step publishes only the paths that an unpathed live segment lies on. So every trail with segments gets a path, and a road gets one only once an athlete has made a segment on it.
  - No road segments are created.
- **BREAKING (lexicon):** the internal "route" (one line's segments across sport groups and directions) is renamed **segment group**, so "route" stays free for planned routes later:
  - `segmentView.route` becomes `segmentView.group`.
  - `getSegment`'s `routeMembers` becomes `groupMembers`.
  - The `route_uri` column becomes `group_uri`.
  - The `segments/segment-routes` capability is retired into `segments/segment-groups`.

Out of scope, for a follow-up change: auto-created road segments for popular climbs. These would come from OSM `route=bicycle` relations and named roads outside built-up areas, published for ride only, whole climbs only, and only past the rider gate.

## Capabilities

### New Capabilities
- `segments/segment-paths`: covers
  - the `feed.path` record and how the AppView indexes it;
  - which segments lie on a path;
  - the path views and queries, including name search over paths and over segments on no path.
- `segments/path-page`: the Trail page, shared and native on iOS: its header, its map and its list of segment groups.
- `segments/path-import`: how the importer extracts trail and road paths and publishes the ones that unpathed segments need.
- `segments/segment-groups`: replaces `segment-routes` with the same behaviour under the new name. Lines shared across sport groups and directions form one group, carried on segment views. The segment page switches within its group.

### Modified Capabilities
- `segments/segment-browse`: the mode becomes Trails & roads. Its map, list, selection, hover, name search and sport filter work on paths, plus groups that are on no path. Opening a path's row opens the Trail page. The same changes apply to the native iOS screens.
- `segments/segment-records`: name results are ranked by segment group, not route.
- `segments/segment-routes`: retired. Every requirement moves to `segments/segment-groups`.

## Impact

- **Lexicons:**
  - New: `feed.path`, `feed.getPath`, `feed.getPaths`, `feed.searchPathNames`, `importer.getUnpathedSegments`.
  - Changed: `feed.defs#segmentView` (`route` → `group`, new `paths`), `feed.getSegment` (`routeMembers` → `groupMembers`), and the `Nsid` indexed collections.
  - Kotlin mirror in `core/lexicon`.
- **AppView:**
  - Flyway migration: `paths`, `path_cells` and `path_segments` tables; rename `route_uri`.
  - A path indexer limited to the importer's DID.
  - Membership kept current from both sides, when a segment or a path changes.
  - Routes renamed in `SegmentRoutes.kt` and `SegmentViews.kt`.
  - New views and routes.
- **core/location:** a shared path overlap test, used by both the AppView and the importer.
- **Importer:**
  - `TrailBuilder` paths across sport groups.
  - Named road joining.
  - A paths file from `extract`.
  - The `publish-paths` command.
  - `scripts/segment-import.sh` runs it last.
- **Clients:**
  - `:feature-segment-*`: browse grouped by path; a new `PathScreen` with its presenter and UI; the renamed group types.
  - `:app-fixtures` samples, screenshots, strings.
  - Native iOS browse rows and a native Trail page.
- **Specs:**
  - New: `segment-paths`, `path-page`, `path-import`, `segment-groups`.
  - Modified: `segment-browse`, `segment-records`.
  - `segment-routes` retired.
