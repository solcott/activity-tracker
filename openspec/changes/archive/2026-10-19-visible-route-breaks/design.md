# Design

## Context

Breaks are already computed and stored (`RouteBreaks`, `CombinedRoute`, `feed.activity#polylineGaps`). Every map turns the polyline and its gaps into pieces (`SegmentedPolyline.pieces`), and `RouteMarkers.of(pieces)` returns the start, end, and a flat `breakEnds` list (stop, resume, stop, resume…). Two renderers draw them:

- `ActivityMap.nonWeb` (MapLibre) for the detail and enlarged maps on Android, iOS and desktop: a white casing and route `LineLayer`, then one `CircleLayer` per marker kind.
- `RoutePreview` (Canvas) for feed cards, the import preview and the web map (`ActivityMap.wasmJs` delegates to it).

Today a break end is a 5 dp white dot ringed in the route colour (`MaterialTheme.colorScheme.primary`), which disappears against the line's round cap (see proposal.md, Why).

## Goals / Non-Goals

**Goals:**
- One description of break markers and the connector in `RouteMarkers`, so both renderers draw the same thing.
- Readable on light and dark map tiles, and on the Canvas preview's plain background.

**Non-Goals:**
- Any change to where breaks are, or to stored data.
- Interaction on markers (tap labels), icons or glyphs inside markers.

## Decisions

**Breaks as pairs, not a flat list.** `RouteMarkers` gets `breaks: ImmutableList<Break<T>>` with `Break(stop: T, resume: T)` in place of `breakEnds`. Renderers then draw stops, resumes and connectors from one structure without index parity tricks. Alternative: keep `breakEnds` and derive pairs by `chunked(2)` in each renderer. That repeats the logic per renderer and makes the stop/resume distinction implicit.

**Colour: one amber for breaks.** A `BREAK_COLOR` around `#FFA000` (amber 700), used for the stop fill, the resume ring and the connector. It is far from the route's teal primary, the start green `#43A047` and the end red `#E53935` in hue, and stays legible on both the light and dark tile styles. Alternatives: a grey ("missing") reads as disabled and vanishes on dark tiles; a darker route shade still looks like part of the route.

**Shape: same size as the ends, solid vs ringed.** `BREAK_RADIUS` becomes `END_RADIUS` (7 dp). The stop is a solid amber dot with a white ring, like the start and end. The resume is a white dot with a thicker amber ring (about 3 dp), so it reads as "open again" at a glance and differs from the stop without relying on colour alone. Alternative: pause and play glyphs. MapLibre would need registered images for a `SymbolLayer`, and the Canvas would need its own glyph drawing, for little gain at 14 dp.

**Connector: a dashed amber line beneath the markers.** It is drawn after the route layers and before the marker layers, so it may cross the route but never covers a marker.
- MapLibre: a GeoJSON MultiLineString of each break's `[stop, resume]` in a `LineLayer` with a dash array, about 2.5 dp wide, with butt caps so the dashes stay crisp.
- Canvas: one `drawLine` per break with `PathEffect.dashPathEffect` at the same width and dash lengths, scaled from dp.

The dash and width constants live in `RouteMarkers` next to the colour so both renderers match. Alternative: a dotted line. It is harder to tell apart from the dashed trail lines in the base tiles. The dash length should be longer than theirs.

**Layer order.** Route casing, route, connectors, stops, resumes, start, end. The end stays on top as today, and a break next to the start (a combined activity whose first member is short) still shows the start above it.

## Risks / Trade-offs

- [The base map's own dashed paths (footpaths in the screenshot) look like the connector] → amber colour and a longer dash than the tile style's paths; check on the trail-dense test ride.
- [Zoomed-out cards: a 20 dp break's two 14 dp markers overlap] → acceptable: the amber blob still marks the place, and the enlarged map separates them.
- [A connector between pieces of a combined activity can be long, across town] → it is still the honest "not recorded" join; it is dashed and thin, and the markers carry the meaning.
- [A connector across a privacy-trimmed member could hint at hidden ground] → it joins only drawn points, both of which are already shown to that viewer. Pass-through zones are already drawn as a straight join, not a break, so nothing new is revealed.

## Migration Plan

Client-only rendering change; no migration. Activities saved before breaks were stored keep drawing unbroken for other viewers, as `map-breaks` already says.
