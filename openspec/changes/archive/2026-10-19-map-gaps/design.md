# Design

## Context

`SegmentedPolyline` (polyline plus `gaps`, indices of its decoded points after which the line breaks) and `CombinedRoute.segments` (cut where positions are more than `BREAK_SECONDS` apart in time) exist for combined activities, and every map (`ActivityMap`, `RoutePreview`) already draws `polylineGaps`. A single activity's polyline comes from `RouteTrim.polylineOf` (a `String`) in `ActivityRepository`'s save and merge paths and `RouteTrimmer.retrimmed`, and untrimmed from `ActivityDraft.polylineOf` for the owner's map (`OwnRoute`) and the import preview. The lexicon caps `polylineGaps` at 10 items.

## Goals / Non-Goals

**Goals:** the same break on every map of a single activity, stored like a combined activity's.

**Non-Goals:** rewriting already-saved records (the owner chose to leave them); changing how combined activities break; capping a combined activity's breaks (it has as many as members recorded apart, well under 10 in practice).

## Decisions

### 1. Time and distance, beside `CombinedRoute`

`RouteBreaks.segments(route)` in `core/data/activity` cuts between consecutive positions whose gap in time exceeds `StreamAnalytics.gapLimit` of the track's position times (the charts' limit: `max(BREAK_SECONDS, 4 × usual interval)`) and whose distance exceeds `MIN_BREAK_METERS` (200), by `TrackMath.distanceMeters`. Time alone would break at every café stop (invisible, but it spends the 10 breaks); distance alone would break sparse logging at speed. If more than `MAX_BREAKS` (10, the lexicon's `maxLength`) qualify, the 10 with the largest distance are kept.

### 2. Trim first, then break

A combined activity trims each member's stretch on its own, since each may start or end at home. A single activity's ends are only its first and last points, and trimming each piece would hide `hideEndsMeters` around every break, so a single route is trimmed as a whole and then segmented. `RouteTrim.polylineOf` becomes `singlePolylineOf(route): SegmentedPolyline?`, returning `SegmentedPolyline.of(RouteBreaks.segments(settings.trim(route)))`; `Unknown` still returns null.

### 3. `toRecord` takes a `SegmentedPolyline`

`ActivityDraft.toRecord(createdAt, media, line: SegmentedPolyline?)` writes `polyline` and `polylineGaps` together, so no caller can write one without the other. `ActivityDraft.polylineOf` (untrimmed, for the owner's screens) becomes `segmentedPolylineOf`.

### 4. The import preview carries the breaks

`ImportPreview` gains `polylineGaps: ImmutableList<Int>?`, passed to `ActivityMap`.

## Risks / Trade-offs

- [A trimmed route has fewer points, so a gap whose far side was trimmed away disappears] → Correct: nothing is drawn there anyway.
- [Old records stay unbroken for followers] → Accepted by the owner; any retrim (a privacy-zone change) or re-save writes the breaks.
