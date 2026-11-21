# Proposal

## Why

Every run on the browse map and the Trail page is drawn in the same teal, so an athlete can't tell an easy green loop from a black-diamond descent without opening each one. The imported trails already carry their difficulty (`TrailAttributes`: IMBA, MTB scale, SAC scale, piste difficulty), served on every `segmentView` and `pathView`, so the maps can show it now with no new data. The Trail & Segment design canvas ("Rating colors" artboard) settled the look.

## What Changes

- One shared rule turns a segment's or trail's `TrailAttributes` into a **rating level** for a sport group: Easiest, Easy, Intermediate, Difficult, Extreme, or unrated. The sport group picks the scale (ride: IMBA, else MTB scale; run and hike: SAC; alpine: piste difficulty), as the Trail details section already filters scales. A trail with no sport chosen takes the first scale it has.
- The browse map and the Trail page map draw each run in its level's colour, as signs do: white, green, blue, black, and a double black line. Every run keeps a casing so white and black read on any map; the double line, not colour alone, marks Extreme. Unrated runs are a neutral grey; lifts stay thin, dashed and muted; the selected or hovered run keeps the amber highlight over every colour.
- Both maps show a small legend of only the levels they draw.
- Browse rows and Trail page rows show a rating swatch (with its level as its accessibility label), so the web, which has no map, shows the rating too.
- iOS draws the same levels natively from the same Kotlin rule, with the colours as catalog colours.

Not in this change: the Segment page's own map line (it uses the activity route map), region-specific piste colours (Europe's blue/red/black), a rating filter, and crowd-sourced ratings.

## Capabilities

### New Capabilities
- `segments/trail-ratings`: how a segment's or trail's difficulty becomes a rating level for a sport group, and how maps, their legends and list rows show it.

### Modified Capabilities
- `segments/path-page`: the Trail page draws its segments in their rating colours over the muted path, with a legend.
- `segments/segment-browse`: the browse map draws runs in their rating colours with a legend; lifts stay muted and distinct.

## Impact

- `:app-common-api`: `MapSegmentLine` and `MapSegment` gain a rating level; a new `TrailRating` rule (moved from `TrailTexts`' private scale filter, which then asks it).
- `:feature-segment-api`/`-presenter`: browse and Trail page lines and rows carry their level; `TrailTexts` uses the shared scale choice.
- `:ui-designsystem`: a `TrailRatingColors` palette beside `GradeBands`; `:app-common-ui`: `SegmentsMap` (MapLibre and the canvas preview) draws by level, plus a `RatingLegend` and `RatingSwatch`.
- `:ui-resources`: level names and the legend's strings, regenerated into the iOS catalog.
- iOS: `SegmentsMapView` strokes by level, catalog colours `TrailRating0Color`…`TrailRating4Color` and `TrailUnratedColor`, a native legend and row swatches; snapshots.
- Android screenshot references for browse and the Trail page; fixtures gain rated samples.
- No lexicon, AppView or migration change.
