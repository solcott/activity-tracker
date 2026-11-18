# Design

## Context

See proposal.md (Why) for the motivation.

**How bridging works today.** `TrailBuilder.namedTrails` groups ways by sport group and shortened name, then chains each group. `bridged()` joins two chains when one dangling end reaches another along a single way through it (`walk()`), within `BRIDGE_M` (200 m).

- The ways it may walk are indexed only at chain end nodes (`through`).
- A way is walkable when it is `crossable` for the group and not of the trail's own name.
- Route relations keep a single road member of at most 200 m as a bridge (`sharedRoad`).

**How paths are built.** `Extract.pathsOf` builds paths from `TrailBuilder.build`, through `TrailPaths.of`, grouped by family and source. So an OSM trail and an agency trail of one family give two paths.

**How published paths go away.** `publish-paths` already deletes a held path once no region's file lists it (path-import, "A published path can be refreshed").

**What `OsmReader` keeps.** Trail ways, relation members, lifts, and every *named* highway, each with node coordinates. Unnamed roads aren't read, so they can't be bridged.

## Goals / Non-Goals

**Goals:**
- Join a trail across a chain of several ways.
- Join it across a longer chain whose ways all carry the trail's ref, up to a limit the operator can set.
- Write one path per trail when OSM and an agency both map it.

**Non-Goals:**
- Route relations' shared roads stay as they are: one road member of at most 200 m. Relations list their own members, so they rarely need a search.
- Unnamed roads stay unread. Keeping them would grow `OsmReader`'s output a lot, for gaps that named roads and refs cover.
- Agency trails are not bridged. Agency lines carry no OSM refs, and on the trail page the OSM path replaces them.
- No change to candidate dedup between sources, or to the AppView's 90% rule for segments.

## Decisions

### 1. A shortest-path search over a node index

The `walk()` along one way becomes a bounded Dijkstra search from each dangling end. The graph is the walkable ways' nodes:
- each edge is one step along a way, weighted by its length;
- a way's nodes join it to every other walkable way through them.

The search stops at the first node that is another chain's dangling end. The bridge is the resulting run of way slices, each a `Placed(bridge = true)` with its `from` and `to`. So candidates' `osmWays`, `Trail.bridged`, the stretch minimum and paths all treat it as they treat today's single-way bridge.

The search is bounded by the larger of the two limits. A route is accepted when either:
- its length is at most 200 m; or
- every way on it carries a ref that the trail ways on both sides also carry, and its length is at most the ref limit.

To find the shortest route of each kind, the search runs twice:
- over every walkable way, bounded at 200 m;
- over only the walkable ways that carry one of the end way's refs, bounded at the ref limit.

The shorter accepted route wins, and ties go to the plain one.

**The index.** A node → ways index over every walkable highway way (TrailTags rules, has `highway`). It is built once per `build()` as two parallel sorted `LongArray`s of (node, way index), with binary search, like `NodeCoords`. A `HashMap` over every node of every named road in a state would cost several hundred MB; the arrays cost about 16 bytes a node.

The index is filtered per group and per trail name when walked (`crossable`, and not the trail's own shortened name), as `open` is today.

*Alternatives considered:*
- **Extend `walk()` to step onto the next way at its end node.** This misses chains that branch, and gives no shortest route.
- **Bridge only on the ref rule.** This misses short untagged connections, which the 200 m rule already covers for one way.

### 2. A trail's refs are its end way's

The refs that may carry a long bridge are those of the way at the chain end the search starts from. That way's `ref` tag is split at `;` and trimmed (`TrailTags.refs(tags)`). The way at the other chain's end must carry the same ref.

Using the end ways, not every way of the trail, keeps a trail whose refs change along it (an `old_ref`, or a renumbered section) from bridging on a number its ends don't share. `old_ref` is ignored: the numbers a mapper still signs are in `ref`.

### 3. The limit is a build parameter

`TrailBuilder.build(data, words, refBridgeM)` takes the limit, defaulting to `DEFAULT_REF_BRIDGE_M = 1000.0`.
- `extract()` passes it to both `candidatesOf` and `pathsOf`, so candidates and paths agree.
- `Main.parseExtract` reads `--ref-bridge-m`, a positive integer of metres; any other value returns null, which prints the usage.
- `scripts/segment-import.sh` passes `--ref-bridge-m "$REF_BRIDGE_M"` when that environment variable is set, as it does with `TERRAIN_TILES_ZOOM`.

### 4. The agency path folds into the OSM path

After `TrailPaths.of`, `pathsOf` checks each agency trail path against the OSM trail paths whose bounding boxes come within the corridor:
- It sums `PathOverlap.overlapMeters` over the agency path's pieces against each OSM path's pieces.
- When the best OSM path covers at least `PathOverlap.IMPORTED_SHARE` (90%) of the agency path's length, the agency path is dropped.
- The OSM path's `sports` becomes the union of both, in `SegmentSportGroup.all` order.
- The OSM path's id, name, line, source, licence and attribution are unchanged. Its id depends only on its kind, name and end ways, so it stays stable.

The 90% share reuses the AppView's rule for importer segments, so "mostly follows" means the same thing in both places.

*Alternatives considered:*
- **Keep the agency path.** The user chose OSM: its line is finer, and bridging joins it across shared tread.
- **Merge both lines into one path.** This would draw the same trail twice, a few metres apart, in one record.

## Risks / Trade-offs

- **[Two different trails of one name join across a town.]** → Untagged chains stay at 200 m. Longer chains need a ref that both trails' end ways share, and unrelated trails rarely share both a name and a ref.
- **[The search is slow on a large state.]** → It runs only from dangling ends of names that have at least two chains, and stops at the limit. Re-extract us-utah and compare the run time with today's (about 2 minutes for the extract step).
- **[An agency path is dropped where OSM's trail is shorter.]** → A drop needs 90% of the *agency* path to follow the OSM path. A longer agency path keeps its own page.
- **[Already published segments for the old pieces stay.]** → Published segments are never changed or deleted (segment-import). The joined trail adds its whole and new stretch candidates. Matching and leaderboards treat the old pieces as ordinary segments.

## Migration Plan

Re-import a region (`scripts/segment-import.sh <region>`):
- `extract` writes the joined trails and the merged paths.
- `publish` adds the new candidates.
- `publish-paths` refreshes the OSM path's sports and deletes agency paths that no region lists any more.

To roll back, re-import with the previous importer. The agency paths are written and published again, and the extra segments stay.
