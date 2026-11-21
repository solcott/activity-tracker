# Design

## Context

See proposal.md for why. Today:

- `MapSegmentLine(uri, polyline, lift)` (`:app-common-api`) is all a map line carries. `SegmentBrowsePresenter`'s `mapSegmentLines()` and `mapSegmentLine()` build them; `PathPresenter` marks the path's own pieces muted by reusing `lift = true`.
- `SegmentsMap` (`:app-common-api`) turns lines into `MapSegment(uri, points, role)` with `MapSegmentRole` (run, lift, selected), and is what iOS's `SegmentsMapView` and the Compose canvas stand-in draw. The MapLibre map (`SegmentsMap.nonWeb.kt`) draws three GeoJSON sources: lifts (`outline`, dashed), runs (`primary` on a white casing) and highlighted (`tertiary` on a casing). Web has no map.
- `segmentView.trail` and `pathView.trail` are already served, cleaned, for importer data. `TrailTexts`' private `ScaleFilter` decides which difficulty scales fit a sport group for the Trail details rows.
- `GradeBands` (`:ui-designsystem`) plus hand-made catalog colours `GradeBand0Color`… is the precedent for a palette both UIs share.

## Goals / Non-Goals

**Goals:**
- One Kotlin rule for a level, asked by both UIs and every map; Swift never maps a scale.
- Colour plus a non-colour cue (double line for Extreme, casing everywhere), legible in light and dark.

**Non-Goals:**
- Recolouring the Segment page's `RouteMap` line or activity maps.
- Regional piste palettes, rating filters or sorting, crowd-sourced ratings.
- Any server, lexicon or index change.

## Decisions

### The rule lives in `:app-common-api` as `TrailRating`
An enum `TrailRating { EASIEST, EASY, INTERMEDIATE, DIFFICULT, EXTREME }` with `TrailRating.of(trail: TrailAttributes?, sport: String?): TrailRating?` (null is unrated) and `label: AppText` (`trail_rating_<name>`). It goes beside `MapSegmentLine` because the map types there need it and `:app-common-api` already sees the lexicon. Which scale fits which sport group moves out of `TrailTexts.ScaleFilter` into a small `TrailScales` object there too, so the Trail details rows and the rating can't disagree about, say, whether a run segment shows the SAC scale. *Alternative:* keep it in `:feature-segment-api`. Rejected: `SegmentsMap` and its legend are in `:app-common-api`, which can't see a feature.

### Lines carry the level, not a colour
`MapSegmentLine` gains `rating: TrailRating? = null`, and `MapSegment` the same, so the shared model stays free of colours (it is in an `-api`-level module Swift reads). The presenters fill it: a browse group row's shown segment from its own sport group (or the chosen sport), a path row from the path's `trail` and the chosen sport, the Trail page's segments from their sport group. Lifts and the Trail page's muted path pieces stay unrated. `SegmentsMap` keys on the rating too, so a changed sport redraws.

### Dive site outlines are not trails
Dive site browse draws its outlines through the same map, so `MapSegmentLine` also gets `trail: Boolean = true`. Dive site outlines pass `false`: they're never rated, are left out of the legend, and keep the accent colour they had, rather than turning unrated grey. *Alternative:* a separate map for dive sites. Rejected: the outlines need nothing else the trail map doesn't already do.

### Legend and swatches are Kotlin values both UIs draw
`SegmentsMap.legend: List<RatingLegendEntry>` (a class holding the `TrailRating?` and its `AppText`, since a list of enums can't reach Swift) lists the levels present in the drawn runs, ordered, Unrated last, empty when none is rated. Rows ask `BrowseRow.rating(sport)` and `SegmentGroupRow`'s shown segment through the same rule, getters in `:feature-segment-api`.

### Palette in `:ui-designsystem`, catalog colours on iOS
`TrailRatingColors` beside `GradeBands`: line and casing per level, plus unrated grey. Sign colours stay the same in both themes (white, green #2E7D32, blue #1565C0, black #111111), with dark-theme green and blue lifted a step (#43A047, #1E88E5) for the dark map; Easiest takes a dark casing (#424242), every other level a white one; unrated #757575 / #9E9E9E. iOS gets hand-made `TrailRating0Color`…`TrailRating4Color`, `TrailRatingCasing0Color` and `TrailUnratedColor` with light and dark appearances, matched by a test that reads both, as GradeBands' are kept by hand. *Alternative:* data-driven colours in the GeoJSON features. Rejected: the theme picks the shade, so it stays in the UI.

### MapLibre: one runs source with a level property
Runs become one GeoJSON source whose features carry `level` (0–4, or -1 unrated); the casing and line layers colour with a `match` on it, and an extra thin white centre-line layer filtered to Extreme draws the double line. Lifts and the highlight layers are unchanged and stay above (highlight) or below (lifts). The canvas stand-in for previews and screenshots draws the same colours, so screenshot tests show them. *Alternative:* one source per level. Rejected: five sources rebuild on every selection change for no gain.

### iOS strokes by level in `SegmentsMapView`
The `MapPolyline` loop picks the stroke by `segment.rating`, adding a second narrower white stroke for Extreme, and the `sketch` canvas does the same. The legend is a small native overlay (`RatingLegendView`) and rows a `RatingSwatch` in `Design/`.

## Risks / Trade-offs

- [Black lines vanish on a dark or satellite map] → every rated line has a contrasting casing; Difficult and Extreme use a white one.
- [Unrated grey reads as "closed"] → legend names it Unrated; most athlete-made segments are unrated, so it must stay calm rather than loud.
- [European skiers expect blue = easy, red = intermediate] → one North American ladder for now, noted as a follow-up; the legend's words make each colour's meaning explicit.
- [Map colour change breaks screenshot and snapshot references] → regenerate them with the change and review the images.

## Migration Plan

Client-only. Ships with the next app build; no data migration and nothing to roll back beyond the commit.

## Open Questions

- Should a later change add Europe's piste colours by the segment's region? It can reuse `TrailRating` and swap only the palette.
