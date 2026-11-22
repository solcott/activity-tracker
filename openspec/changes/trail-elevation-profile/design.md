# Design

## Context

**Segment profiles today (`segments/segment-elevation`).**
- The matcher's `elevation` job calls `TerrainTiles.terrainOf(line)` (`server/terrain`). That resamples the line every `SAMPLE_SPACING_M`, reads the tiles, smooths the heights, and returns gain, loss, an evenly spaced profile (`ElevationProfile.even`) and the maximum grade.
- The results go in the `segments` table's `terrain_*` columns. `SegmentJobs.enqueueElevations` and `SegmentMatcher.backfillTerrain` queue the segments the tiles cover but that have no profile yet.
- `GetSegment.Output.elevationProfile` (`#elevationProfile`) serves the profile. The segment page turns it into a `SegmentProfile` and a grade-tinted `LineChart` (`SegmentProfile.chart(units)`), which `LineChartUi` and the iOS `LineChartView` both draw.

**Paths (`segments/segment-paths`).**
- A path is `Paths` (`BoxedLineTable`), indexed by `index/PathIndex.kt` from the importer's repo.
- Its `lines` are up to 64 pieces. The importer (`Paths.kt`, `piecesOf`) writes the longest trail of the family whole first, then the other trails' stretches not drawn yet, longest first. A long line is split into pieces in order.
- So most trails are one piece, and piece order is meaningful but not one continuous route.

**Trail page.**
- `PathPresenter` and `PathState` hold `content` (getPath's output as `PathContent`) and the filter's `shown`.
- `PathUi` places `PathMap` (`SegmentsMap`) and the list, stacked on a phone and side by side when wide. iOS `PathPage` uses `SegmentsMapView`.
- `TimeSeriesChart` (`ui/designsystem`, Vico) and iOS `ChartScrub` already keep a readout that follows a drag. Neither tells its page where the readout is.

## Goals / Non-Goals

**Goals:**
- One server-side derivation shared with segments: no new sampling or smoothing code.
- The stretches and the map point are worked out once, in Kotlin. Both UIs only draw.

**Non-Goals:**
- No ordering or stitching of pieces into a route. Pieces are drawn in record order with breaks.
- No profile from athletes' efforts for paths: terrain only.
- Tapping a stretch on the chart does not select its row. That may come later.
- No profile in `getPaths` or on browse cards.

## Decisions

**Store per piece in a new `path_terrain` table** (`path_uri`, `piece`, `profile_dm` text, `length_m`, `gain_m`, `loss_m`, `max_grade`), with a primary key of (`path_uri`, `piece`). It is created by Flyway migration `V75__path_terrain.sql` in portable SQL.
- `paths.terrain_complete` says whether every piece was covered when last sampled. The start-up backfill queues paths in the tiles' boxes that aren't complete, as `backfillTerrain` does for segments without terrain, so tiles added later fill in the rest.
- *Alternative:* columns on `paths`, holding a JSON array. Rejected: per-piece rows keep each profile as the same comma-separated text segments use, and replacing them on re-index is a plain delete and insert.

**Heights per piece:** `terrainOf` gives the full-resolution smoothed heights. The piece's profile is `ElevationProfile.even` at `clamp(round(length / 50 m) + 1, 2, 512)` points. `512` is `#elevationProfile.heights.maxLength`.
- *Alternative:* a fixed 128 like segments. Rejected: too coarse for a 20 km trail.

**Jobs.**
- A `path-elevation` kind joins `segment_match_jobs`, with the path URI as subject. `PathIndex` enqueues it on create or update when tiles are configured and the box touches their coverage.
- Delete removes the path's `path_terrain` rows in the same transaction.
- `backfillTerrain` also queues unsampled covered paths.
- The job deletes and rewrites the path's rows idempotently, so a replayed job or a re-index is harmless.

**Stretches are worked out when `getPath` is read**, not stored.
- For each returned segment, decode its line's first and last points. Project each onto the covered pieces in order, with the same along-line projection `PathOverlap` and `SegmentGeometry` use. Take the first piece where both lie within 30 m, and give `(min, max)` of the two along-distances, rounded to metres.
- Segments and the set returned depend on the viewer, so storing stretches would mean invalidating them on every segment change. Projecting two points onto at most 64 pieces per segment is cheap next to the query.
- *Alternative:* projecting on the client, which has the lines. Rejected: the spec wants it served, and keeping geometry rules server-side matches `paths` and `group`.

**Lexicon** (`feed.getPath`, optional `profile: #pathProfile`):
- `pathProfile { pieces: [#pieceProfile], gain, loss, spans: [#segmentSpan] }`
- `pieceProfile { piece, heights (dm, 2–512), lengthMeters, maxGrade? }`
- `segmentSpan { segment (at-uri), piece, startMeters, endMeters }`

These are mirrored in `core/lexicon` as `@Serializable` models with `AtJson`.

**Client model: `PathProfile` in `feature/segment/api`.** It is built from the output with `PathProfile.of(output)`, and provides:
- `chart(units)`: a `LineChart` over cumulative x, each piece's points offset by the pieces before it plus a gap, with `gapBefore` on each piece's first point and the grade bands each piece's heights give. This reuses `SegmentProfile`'s grade and readout rules by building one `SegmentProfile` per piece.
- `spansOf(rows)`: the shown rows' `ChartSpan(from, to, tone, selected)`. The tone is the row's rating level, which the UI colours as the map does.
- `pointAt(x)`: the `LatLng` on the right piece's line at that distance, for the map marker. It returns null in a gap.

`PathState` carries `profile: PathProfile?`, plus getters `profileChart` and `climbText` built from `shown` and `selection`. So filters and selection flow through as they do for `mapLines`. The scrubbed x is UI state (`remember` in `PathUi`, `@State` in `PathPage`), not presenter state, as the chart readout is today.

**`LineChart` additions (`app/common/api`)**, defaulting empty so other charts are unchanged:
- Breaks need nothing new: a piece's first point carries `LinePoint.gapBefore`, which both UIs already draw as a break in the line and its fill.
- `spans: ImmutableList<ChartSpan>`, each with its rating level: drawn as a strip under the plot, with the selected one also shaded over the plot.
- Swift-readable accessors (`spanCount`, `spanAt(index)`), as `dots` has, and `withSpans(…)` so the presenter re-marks one chart as the filters and selection change.

**Drawing.**
- **Compose:** `TimeSeriesChart` gains a spans strip and shading (`SpanMarks`, a decoration) and an `onScrub: (Double?) -> Unit` callback fed from its readout. `LineChartUi` passes them through. `SegmentsMap` gains an optional marker point.
- **iOS:** `LineChartView` already splits the line at gaps; it draws spans as `RectangleMark`s, and reports the scrubbed x through a binding. `SegmentsMapView` draws an `Annotation` at the point.

**Rating colours for spans** come from the same `TrailRating` palette the map uses (`trail-rating-colors`), so a stretch and its line match.

## Risks / Trade-offs

- **A family's pieces aren't one route.** Breaks make the jumps visible, and the stretches tell the athlete which part is which. Most trails are one piece.
- **Long trails are coarse at 512 points.** The readout is still right to about 100 m at 50 km. The chart is an overview, and the segment page has the detail.
- **A segment near two pieces lands on the first.** The importer draws each way once, so overlapping pieces are rare.
- **Backfill load on first start with tiles.** Jobs run in the existing background loop, one path at a time, as segments do.

## Migration Plan

- The Flyway migration adds the table and the marker column. On start-up the backfill queues covered paths.
- Rolling back the client is safe: the field is optional.
- Rolling back the AppView leaves an unused table.
