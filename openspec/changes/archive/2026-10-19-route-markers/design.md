# Design

## Context

`ActivityMap` draws the route with MapLibre on Android, iOS and desktop, and falls back to `RoutePreview` (a Compose `Canvas`) on web. Feed cards always use `RoutePreview`. Both split the decoded polyline at `polylineGaps` with `SegmentedPolyline.pieces`.

## Goals / Non-Goals

**Goals:** start, end and break markers on every route map, with the break markers clearly different from start and end.

**Non-Goals:** tappable markers, distance or time labels, direction arrows along the line.

## Decisions

- **One helper picks the points.** `RouteMarkers.of(pieces)` returns the first point of the first piece (start), the last point of the last piece (end), and for each break the last point before it and the first after it. Both renderers use it, so the canvas and MapLibre can't disagree. It is generic over the point type so `RoutePreview` can pass indices.
- **Looks.** Start: a green dot ringed in white. End: a red dot ringed in white, drawn above the start so a loop's finish shows on top. Break ends: a smaller white dot ringed in the route's colour (the theme's primary), drawn below start and end. Colour and size together keep them apart, and the hollow ring in the line's own colour reads as part of the line rather than an event.
- **MapLibre layers.** One GeoJSON source per kind (`MultiPoint` for break ends, `Point` for start and end) and a `CircleLayer` each, above the route's line layers. No feature properties or data-driven expressions are needed.
- **Feed cards too.** The card's preview is the same route, and the start and finish help most there; the markers are scaled for its smaller height the same way the line is (fixed dp).

- **Privacy.** Viewers' polylines are already trimmed, so their markers can't fall in a zone. The owner's map draws the untrimmed track, so `OwnRoute` reads the zones (`ActivityRepository.privacySettings()`, a read on first use, then `PrivacyZones.current`, so a zone added with "hide start" moves the marker at once) and `PrivacySettings.shownEnds` trims the track the way it is published (per member on a combined activity) to find the shown ends. `ActivityMap` takes them as `RouteEnds`, each null when hidden; until the zones are read both are null, so no marker ever lands at home. Break markers on the owner's map stay at the line's own breaks: the line is drawn there anyway, and viewers' polylines only carry breaks found after trimming.

## Risks / Trade-offs

- A start and end that coincide (a loop) overlap: the end draws on top, which matches what other trackers show.
- Many breaks (up to 10, so 20 rings) on a small card could crowd it; the rings are small and the cap keeps it bounded.
