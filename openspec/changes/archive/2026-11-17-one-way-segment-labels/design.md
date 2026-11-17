# Design

## Context

Directions today:

- **Importer.** `CandidateCutter` works out `forward` and `backward` for each piece of a trail from `TrailTags`, where `oneway:bicycle` overrides `oneway` for rides. It uses them only to emit one or two candidates and to add a compass word to two-way whole-trail names (`SegmentHeading`).
- **Clients.** They see a sport's direction only from which members a segment group lists:
  - `SegmentGroupRow.bothWays` is true when any member, of any sport, runs opposite the shown one.
  - `GroupSwitcher.directions` lists the shown sport's directions by compass.
  - `GroupSwitcher.offered` is false for a segment alone in its group. That is the case for Clarks' ride segment: its 2287 m line doesn't match the 2022 m foot lines.

Elevation:

- Every `SegmentView` carries `elevationGain` and `elevationLoss` from `servedElevation`: terrain where tiles cover the line, else effort elevation once enough athletes have efforts.
- `getSegment`'s group members carry no elevation.
- The importer reads no elevation. `TerrainTiles` lives in the AppView and uses AppView helpers (`boundedCache`, `E7`).

On the local stack, Clarks has no terrain, and its ride effort elevation (199 m up, 0 m down) comes from one athlete, so it isn't served. Checking the end-to-end result there needs `scripts/terrain-fetch.sh` for Corner Canyon and a recomputed elevation job.

## Goals / Non-Goals

**Goals:**
- One rule for "clearly climbs", used by the AppView-served data on clients and by the importer's names, so a row and a name never disagree about which way is up.
- A one-way claim only where the source data makes it.

**Non-Goals:**
- No new AppView judgement or column for climb. Clients judge from served elevation.
- No change to how segment groups form, and no merging of near-identical lines.
- No `oneWay` for athletes' segments, and no UI for setting it.
- OSM `incline` tags aren't read. Terrain is the only elevation source.

## Decisions

### 1. One-wayness is a record flag set by the importer, served only for the importer's repo
`feed.segment#oneWay` is optional and defaults to false. The view field `segmentView.oneWay` is computed when the view is built, from the stored record and `author_did == segmentImporter`.

- **Why a record flag:** inferring from a missing direction is wrong for ridden-only imports and for athlete segments (see the proposal).
- **Why not a column:** views already read the stored record, so the flag needs no column on `segments`.
- **Why gate on the author:** the record lives in a repo anyone can write, and a gate keeps a stranger from labelling their own segment "Uphill only". This follows the path indexer's importer-only rule.
- **Alternative considered:** dropping the field at indexing when the author isn't the importer. Rejected, because the raw record would still show it.

`getSegment` group members need no flag. The switcher judges only the shown segment's one-wayness: a one-way sport has no member the other way, so the shown segment is its only one.

### 2. Staged candidates carry the flag
`publish` builds each record from what `importer.checkSegmentCandidates` returns, never from the file (segment-import). So `importer.defs#candidate` gains `oneWay`, and `segment_candidates` gains a `one_way` boolean column (Flyway `V66__segment_candidate_one_way.sql`, default false, H2-portable). `refine` reads the extract file directly and takes the flag from there.

### 3. The climb rule is a pure function in `core/location`
`SegmentClimb.of(gainM, lossM, lengthM): SegmentClimb?` returns `Uphill`, `Downhill` or null (neither), and `reversed()` swaps the two. It sits beside `SegmentHeading` and `ElevationGain`, is common code, and is shared by the clients and the importer.

Thresholds: the net change must be at least `CLIMB_MIN_M = 15` and at least `CLIMB_MIN_GRADE = 0.03` of the length.

- **Why 3% net:** on a bike or on foot, 3% sustained is clearly felt. Rolling trails whose ups and downs cancel stay compass-named.
- **Why 15 m minimum:** 15 m is five times `ElevationGain`'s 3 m hysteresis. Short segments then can't earn "uphill" from terrain noise.
- **Why net, not gain alone:** gain minus loss is the net change to within the hysteresis, and it is what "this way is up" means.
- Clarks' ride effort data is 199 m net over 2287 m, about 8.7%, well clear of both thresholds.

**Alternative considered:** judging in the AppView and serving a `climb` field. Rejected: clients already have the inputs, a lexicon field would only duplicate them, and the importer needs the rule offline anyway.

A `SegmentDirection` type (climb, else heading) gives the one name the UI and the importer use. `SegmentHeading` keeps compass and loop names. A loop always uses its turn: its net change is about zero, and a lap has no "up".

### 4. Rows say direction per sport group
`SegmentGroupRow` keeps `bothWays`, which name stripping uses, and gains `directions: ImmutableList<SportDirection>`, one per sport, in app order. A sport's direction is one of:

- `BothWays`: its members run both ways (`sameWay` against that sport's first member).
- `OneWay(direction)`: one way only, and every member is `oneWay` and not alpine. The direction is `SegmentDirection` of that member's line and served elevation.
- `Unstated`: anything else.

`SegmentBrowseTexts.details` then builds the line:

- When every sport has the same direction, it names the sports and then the wording once ("Ride · Uphill only", "Ride, Run, Hike · Both ways", "Run").
- Otherwise it gives one phrase per distinct direction, in this order: both ways, one-way (by direction), unstated last. Each phrase is the sports joined, then the lower-case wording ("Run, Hike both ways · Ride uphill only").

New strings in `:ui-resources`:

- `segment_browse_one_way` ("%1$s only"), with direction words in the form a chip uses.
- `segment_browse_sports_both_ways` ("%1$s both ways") and `segment_browse_sports_one_way` ("%1$s %2$s only"), so word order stays translatable.
- Lower-case direction words, so the mixed form doesn't lower-case at run time.

`PathTexts.details` already delegates to browse, so the Trail page follows with no code change.

`displayName`'s suffix check accepts `SegmentDirection` words: the compass and turn words plus `uphill` and `downhill`.

### 5. The switcher names directions by climb and shows a one-way sport
`GroupSwitcher.of` takes the shown `SegmentView`, which brings its elevation and `oneWay`.

- **Direction names:** the shown direction's climb comes from its served elevation, and the other direction is `reversed()`, since group members share one line. Each `GroupDirection` holds a `SegmentDirection`. Order stays fixed: by heading as today, so the chips keep their places whether they read by climb or by compass.
- **One-way state:** `oneWay` is true when the shown segment is one-way, its sport has no other direction, and it isn't alpine.
- **When the switcher shows:** `offered` becomes `options.size > 1 || oneWay`.
- **Showing sports:** a new `showsSports` is true when `options.size > 1`. A lone one-way segment then shows only its direction.
- **Text:** `GroupSwitcherTexts.direction` returns "Uphill", "Downhill" or the compass word, and a new `GroupSwitcherTexts.oneWay` returns "Uphill only".
- **Compose:** shows a single disabled, selected `AppFilterChip`.
- **iOS:** shows a disabled one-segment `Picker` in `SegmentPage.swift`, so it matches the two-way control in the system's own style.

**Alternative considered:** a caption line under the header. Rejected, because a switcher-shaped choice tells the athlete there's no other way to pick.

### 6. Terrain moves to a shared JVM module
`server/terrain` (`:server-terrain`, JVM, depends on `:core-location`) holds `TerrainTiles` and the two helpers it uses. The AppView and the importer depend on it.

`extract --terrain <dir>` samples each two-way whole-trail candidate's line, counts gain and loss with `ElevationGain` after the AppView's smoothing, and names the line with `SegmentDirection`. With no tiles, or partial coverage, it uses the compass. `scripts/segment-import.sh` passes `--terrain build/terrain` when that directory holds tiles.

**Alternative considered:** copying `TerrainTiles` into the importer. Rejected: two copies would drift, and the name and the row could then disagree.

## Risks / Trade-offs

- **[Terrain and effort elevation disagree on a borderline line]** A name made from terrain at publish time could say "(uphill)" while the row, from effort elevation, says compass. → Terrain wins in `servedElevation` wherever it covers the line, and the importer names only from terrain with full coverage, so both read the same data in that case. Effort-only lines are never named by climb.
- **[Existing segments keep compass names and no flag until refined]** → The migration plan re-extracts with terrain, then stages and refines. `refine` is already idempotent.
- **[An athlete adopts a name proposal]** The current name no longer ends in a direction word, so rows show it as it is, as today.
- **[OSM one-way tags are wrong or missing]** The label is as good as the map. → Fix it in OSM, then extract, stage and refine again.
- **[3% / 15 m feel wrong on real trails]** → The constants live in one place, with tests on the Clarks figures. Tuning them changes both names (on the next refine) and labels.

## Migration Plan

1. Deploy the AppView. The migration adds the `segment_candidates.one_way` column, and views serve `oneWay` once records carry it.
2. For each imported region, in order:
   - `scripts/terrain-fetch.sh <region>`;
   - `extract --terrain build/terrain`;
   - `stage`;
   - `refine <file>`, which writes `oneWay` and the climb names in one write per segment.
3. Ship the clients. Old clients ignore the new fields.

**Rollback:** the fields are optional, so older AppViews and clients ignore them.
