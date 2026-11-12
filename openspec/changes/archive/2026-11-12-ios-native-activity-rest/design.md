# Design

## Context

The native activity page (`ActivityPage`, `ActivityPageCards`) draws each card's lines through `CardLines.of`, one plain `LineChart` per
series. The shared card (`ActivityChartsUi.kt`) draws more through Vico's `TimeSeriesChart`: a `backdrop` (elevation, grey, on its own
scale filling 0.6 of the height, a span of at least 20 m), `others` (unfilled lines in tertiary, secondary, error), `events` and
`warnings` (labelled vertical lines), `fillAbove` (depth), `step` (assist mode, gears), and two charts of its own building (the power
curve on an index axis with log-interpolated durations, the gears' rear cog held to the end). The power curve's, gears', depth's and
tanks' words are built in Compose (`stringResource`) and the legs, lengths and sets tables likewise (`WorkoutTablesUi.kt`). The three
screens the page opens are small: `RouteMapState` (a `ShownRoute` and a title), `MediaGalleryState` (items, page, zoomed, blur, taken
down, reporting), `CombinePickerState` (candidates, combining, error). The host already hides the tabs over the gallery and the route map
(`TopLevelDestination`).

## Goals / Non-Goals

**Goals:** every chart, table, caption and sheet the shared page shows, drawn natively from values Kotlin chose; the three screens native;
Compose's look unchanged (it keeps Vico, but asks the shared words).

**Non-Goals:** Edit, Create segment, Create dive site, Add people (other changes); replacing Vico on other platforms.

## Decisions

1. **Extend `LineChart`, don't add a chart type.** New trailing constructor parameters with defaults: `backdrop: ImmutableList<LinePoint>`,
   `others: ImmutableList<ChartLine>` (`ChartLine(points, tone)`, tone an index into the chart's other colours), `marks:
   ImmutableList<ChartMark>` (`ChartMark(x, label, warning)`) and `fillAbove: Boolean`, with `count`/`at` getters as `dots` has, since
   Swift reads erased lists through typed getters most safely. Kotlin scales the backdrop onto the chart's own axis (its low at `axis.min`,
   its low plus span / 0.6 at `axis.max`, span at least 20 m), so Swift just draws an area from `axis.min`; the shared Vico chart keeps its
   own scaling. Alternative, a `TimeChart` with several series: more Swift, a second type for every page to learn.
2. **`CardLines.of` returns every chart of the card**, in drawing order: each series (with `backdrop` for the effort kinds, the right leg as
   `others`, steps for assist mode, the depth with ceiling as `others`, switches and alerts as `marks`, `fillAbove`), the tanks merged into
   one chart, then the power curve (`CardLines.powerCurve`) and the gears (`CardLines.gears`). A readout over a backdrop reads the
   elevation there too (`chart_marker_elevation`). The Compose card keeps calling Vico per series but asks `CardLines` for the power curve's,
   gears', depth's and tanks' descriptions, labels and details, so both UIs say the same. Names a dive alert resolves are moved to Kotlin
   (`DiveText.alert`) if they live in Compose.
3. **Tables as `DetailTable`.** `StructureTableTexts.lengths`, `sets`, `legs(units)` build `DetailTable`s; `DetailTable` gains `wideColumn: Int
   = if (wideFirst) 0 else -1`, defaulted. The Compose `StructureTable` draws a `DetailTable`. A stroke's and exercise's name are
   `AppText.Named` with a readable fallback, as a lap's intensity is. The native page draws them with `LapTableView`, which honours the
   wide column.
4. **The combination sheet's open state is the view's**, as in Compose (`rememberSaveable`): Edit beside the members sets it, a split that
   leaves nothing combined closes it. Words from `CombinationTexts` (`:feature-detail-api`, beside `ActivityPageTexts`); a source's name is
   its device or "Started <time>" read in the page's clock, so it takes `now` and `zone` like other clock texts.
5. **Guides and span bars in Swift**: the row's `guides`, `depth` and `lastChild` drawn as a `Canvas` behind the row (20-point indents, the
   connector meeting the name's first line), the span bar a capsule track with the filled stretch, starting past the group's deepest indent.
6. **Screens.** `ActivityRouteMapView` follows `SegmentMapView` (`RouteMapView(height: nil, interactive: true)`, bottom safe area ignored);
   `RouteMapTexts` gives the title and the `RouteMap`. `MediaGalleryView` is a paged `TabView` bound to `page`, each photo `RemoteImage`
   fitted; zoomed, the current photo is a `UIScrollView` representable (pinch, double tap, pan) and paging is disabled; Close and the
   counter (`MediaGalleryTexts.counter`) and the report menu in an overlay bar on black, the status bar light. `CombinePickerView` is a
   plain `List` with `CombinePickerTexts.row` for each candidate's lines.
7. **Shared files get appended blocks only** (`ScreenRegistry`, `KotlinTypes`, `Samples`, `DesignGallery`, rules, roadmap), as the sibling
   forms session edits the same files.

## Risks / Trade-offs

- [Several Swift Charts series per chart cost layout time on long rides] → points are thinned already (`stream-resampling`); others and
  the backdrop reuse the same thinning.
- [Swift Charts has no per-series axis] → the backdrop is pre-scaled in Kotlin, which is also testable on the JVM.
- [A paged `TabView` with a zoomable `UIScrollView` inside can fight over pans] → paging is disabled while zoomed, as the shared pager is.
- [The stack lacks dives, swims, workouts, multisport and combined rides] → samples and snapshots draw each; the walk records the rest
  under Not verified.
