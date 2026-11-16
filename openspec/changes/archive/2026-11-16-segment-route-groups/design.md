# Design

## Context

See proposal.md for why. The facts that shape the approach:

- **The importer fans out.** It gives each trail one candidate per sport group per direction (`TrailTags.wayGroups`, `CandidateCutter.directions`). The candidate id hashes `group|sense|ways…`, and published segments are keyed by that id. Nothing in a `feed.segment` record links a trail's records, and segments from athletes never have such a link.
- **The duplicate test is the right notion of "the same path", one direction at a time.** `SegmentGeometry.duplicates(a, b)` (`core/location`) checks that the lengths are within 20%, that start meets start and end meets end within 30 m, and that the discrete Fréchet distance is within 30 m. `duplicatesOf` (`index/SegmentRecordIndex.kt`) narrows the candidates with the end cells first, then filters by sport and kind.
- **Name search pages by offset over a ranked SQL query** (`SegmentViews.searchNames`). A client cannot group rows across pages without rows moving as pages load.
- **Area browse already loads the whole area into the client** (`SegmentRepository.areaSegments`, at most 300 segments, at most 10 requests). The area list is then sorted by distance in `SegmentBrowsePresenter.areaRunsOf`.
- **The segment screen is keyed by one URI** (`SegmentScreen(uri)`). Leaderboards, Mayors, names and efforts all hang off that URI.

## Goals / Non-Goals

**Goals:**
- Derive routes on the server so every client groups the same way.
- Keep each segment's identity and data untouched.
- Keep name search's cost about what it is now.

**Non-Goals:**
- Merging leaderboards, efforts or names across a route's members.
- Changing what the importer publishes, or its duplicate rules.
- Grouping trails that only partly overlap. A stretch inside a whole trail stays its own route, since the duplicate test's length ratio keeps them apart.
- Grouping on web's activity page or anywhere besides segment browse and the segment page.

## Decisions

### 1. A stored, derived `route_uri` per segment, named by the earliest live member
**Storage.** `segments.route_uri` is nullable text, indexed. It is set inline in the indexer's transaction when a segment is new or re-lined, beside the duplicate decision, so a served segment always has a route once routes are backfilled.

**`routeOf(kind, line)`.** It takes the cells near each end, unions the two cell lookups (a reversed line swaps its ends, and the lookup is symmetric), then keeps live segments of the same kind and any sport. A segment matches when `duplicates(line, other)` holds or `duplicates(line.reversed(), other)` does. Of the matches it takes the earliest first-indexed, and returns that segment's `route_uri` (or its URI when it has none), so membership is transitive through the route's root rather than pairwise.

**Recheck.** When a route's root stops being live or is re-lined, every remaining member is re-derived earliest-first, so the next earliest member becomes the root. This mirrors `recheckDuplicatesOf`. It runs in the same transaction as the change that triggered it. Routes have few members, typically 1 to 6.

**Status changes.**
- A segment that turns `duplicate` keeps no route in search, because search never serves duplicates.
- A duplicate that becomes live again is given its route then.
- Archived segments keep their `route_uri`, but `listedTo` and `served` keep them out of every list. A recheck runs if one was a root.

**Alternatives considered:**
- *A key in the record* (for example the importer writing a trail id). This only covers imported segments, needs a lexicon field that athletes' segments can't fill honestly, and isn't trustworthy.
- *Grouping on the client by name and length.* Names drift with community renames, and an offset-paged list can't be grouped on the client.
- *Grouping by the root's line at query time.* That means O(n²) geometry per search.

**Why the root is the earliest member.** The earliest member is the most stable choice: it changes only when that segment goes, as with duplicates.

### 2. Name search groups in SQL, then fetches members
`searchNames` applies the same filters as today (served, unhidden, listed, no lifts, sport, `name like`, box prefilter).
- **Paging query.** It `GROUP BY COALESCE(route_uri, uri)` with `MIN(rank)`, `MIN(BoxDistance)` (or `MIN(name)` with no centre), and orders by those, then the route key. It is offset-paged by route. This is plain grouping, so it stays H2-portable.
- **Members query.** A second query fetches every member of the page's routes under the same filters except the name match. The box's `crosses` check is applied in Kotlin as today. Members come back in route order, then by first-indexed.

`COALESCE` lets name search work before the backfill finishes, treating a segment without a route as alone.

**Why not window functions:** `DISTINCT ON`/`ROW_NUMBER` are less portable to H2, and the project avoids them elsewhere (leaderboards use `GROUP BY … HAVING`).

**Older clients** still get a list of segment views, just with more views per page than the limit. That is harmless: they render them all.

### 3. `segmentView.route`, `getSegment.routeMembers`
**`segmentView.route`** is an optional string, served everywhere `views()` builds a view. Area search needs no server change, because the client groups the area's segments by `route`.

**`getSegment.routeMembers`** is a list of `#routeMember` (uri, sport, kind, name, line). It holds the members passing `segmentServed`, `unhidden` and `listedTo` for the viewer, excluding the segment itself. Lifts get none.

A separate `getSegmentRoute` query was rejected: the page needs the members on first draw, so one round trip is better.

### 4. Client: route rows built in the presenter; the name rule in `-api`
`SegmentBrowseScreen.kt` gains `SegmentRoute(key, members, shown)`.
- **`shown`** is the member opened and drawn: the first of the chosen sport group (or of `SegmentSportGroup.all` order when All is chosen), in the order the server or the distance sort gives.
- **Rows.** `AreaRuns.runs` and the name `results` become lists of `SegmentRoute`.
- **Area rows** are grouped after sorting by `metersAway`. A route takes its nearest member's distance.
- **Name results** are grouped per page by `route`. A route never spans pages, because the server pages by route.
- **The sport filter** is still sent to the server, so a route's members are already narrowed.
- **Selection and hover** key on the route key.
- **The map.** `SegmentsMap.of` draws one line per route (`shown`'s line), so overlapping lines don't stack and a tap selects the route.

`SegmentBrowseTexts.routeName(route)` is a pure function in the `-api` module, so both UIs and iOS (through the presenter host's state) read one rule. It is case-sensitive on the importer's English suffixes. Names in other languages keep their suffix, which is acceptable because they show unchanged. The details line gets "Both ways" and a joined sport list from new strings in `:ui-resources`, with `generateIosResources` rerun.

"Both directions" means the listed members' headings differ. A pair's lines are reverses of each other, so it is tested by whether some member's start lies within 30 m of another member's end and the reverse. This is cheap and needs no server field.

### 5. Segment page switcher shows the chosen segment in place
The segment screen state gains a `RouteSwitcher(options, current)`, built from `getSegment`'s members plus the segment itself.
- **Direction labels** come from the heading of each member's line. `heading`/`compass` move from `CandidateCutter` into `core/location` (`SegmentHeading`), so the importer and the client agree. The client maps the headings to localised labels.
- **Fixed order.** Sports follow `SegmentSportGroup.all`, and directions follow `SegmentHeading`'s order (northbound, eastbound, southbound, westbound, clockwise, counterclockwise), so choosing one never moves the chips. Which one is current is a separate flag.
- **Choosing.**
  - Choosing a sport keeps the current direction when that sport has a member that way, else takes its first.
  - Choosing a direction keeps the sport.
- **In place, not navigation.** The presenter keeps the shown segment's URI in retained state, starting at the screen's, and keys its reads on it. A switch changes that URI, so the page stays where it is and the chosen segment loads over the last one as a refresh (ui/content-loading), with no navigation and no new page. Back leaves the page as usual. Popping and pushing a new screen was tried first: it reloaded the whole page, and on iOS the segmented picker kept its old selection across the new page and stopped responding.
- **Drawing.** Compose draws it with `AppFilterChip` rows. iOS draws a segmented `Picker` for direction and a menu chip for sport, matching `SegmentBrowseView`'s sport `AppMenuChip`.

## Risks / Trade-offs

- **[Transitive chaining could merge distinct trails** (A≈B, B≈C, but A≉C)] → Membership is decided against the route's root only, never chained. A member that duplicates some other member but not the root's route forms or joins another route, which is acceptable.
- **[Recheck cost on root deletion]** → Routes are small (≤ 6 importer members, rarely more). The recheck runs only when the root changes.
- **[The backfill on a large production table]** → It runs as a resumable startup pass over segments with a null `route_uri`: batches, earliest first, each in its own transaction. Search tolerates nulls through `COALESCE` until it finishes. A `JavaMigration` was rejected because it would duplicate the geometry and cell lookups in raw JDBC, and a long migration blocks startup.
- **[Name search pages grow up to ×6 in views]** → `limit` is clamped at 100 routes. Pages from the client use 30, so the worst case is 180 views. The members query is bounded by the page's route keys.
- **[Name stripping misfires on a real name ending in "(eastbound)"]** → The rule only applies when the row lists both directions; otherwise the name is shown as is. This is a cosmetic risk only.
- **[Switching isn't in the back stack]** → An athlete can't go back from Run to Ride with Back. This is intended, since the switcher is right there.

## Migration Plan

1. Add migration `V63__segment_routes.sql`: add `route_uri` and its index. Check the next free version against `db/vendor/postgresql` and the `javaMigrations` list, which share the sequence.
2. Add the startup pass `assignMissingRoutes`, run beside the AppView's other startup passes, independent of the signing key.
3. Ship the lexicon fields as optional, so old clients and old AppViews interoperate. New clients treat a missing `route` as the segment's own URI.
4. **Rollback.** Drop the client grouping by shipping a client that ignores `route`. The column can stay.

## Open Questions

- Should the "Both ways" tag and the joined sport list be shortened on very narrow phones (for example icons in place of words)? This can be settled while doing the screenshots without changing the specs.
