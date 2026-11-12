# Design

## Context

`ActivityDetailUi` (`:feature-detail-ui`) draws `ActivityDetailState`, whose loaded `ActivityDetailPage` already carries every choice
the presenter makes: the page's `ActivityPageContent` (start, hero, training load, cards, stats, avg/max, the charts and the map's
lock), the route (`ShownRoute`), segments and lifts, partners and the owner's group, `manage`, `export`, `combineSuggestion`,
`reporting`, `revealed`, and `commentsState`. What the Compose page decides on its own is wording: `PageFigures.kt` (a figure's label
and value), `ChartUnits.kt` (a stream's title, unit, pace unit and readout), `DetailSectionsUi.sectionTitle`, `DiveFormat`,
`sensorLabel`, `LapTableUi` and the zone bars in `ActivityChartsUi`, and the page's own strings in `ActivityDetailUi`,
`ManageActivityUi`, `ExportActivityUi`, `TogetherWithUi`, `DiveSiteLine` and the combine pieces. `ios-native-charts` left
`LineChart` (one line, bands, readouts) and `LineChartView`; `ios-native-map` left `RouteMapView`; `ios-native-post-card` left
`CommentsSection`.

## Goals / Non-Goals

**Goals:**
- A native `ActivityPage` for `ActivityDetailScreen` that shows everything the shared page shows except the charts and tables listed
  as left in the proposal, and offers every action the shared page offers.
- Every word with a rule chosen in Kotlin once; the Compose page asks the same functions and looks the same (the Android screenshot
  references don't change).

**Non-Goals:**
- Teaching `LineChart` several lines, a backdrop, markers or steps (threshold history and dive charts want them; a change of their own).
- The import preview's native page, which reuses these texts later.

## Decisions

**Figure texts move whole into `:app-common-api`.** `FigureTexts.label(figure)` and `FigureTexts.value(value, units)` return
`AppText`, and `PageFigures.kt`'s `figureLabel`/`figureText` become one-line resolvers, as `Format.distance` is over `StatText`. Their
helpers move with them: `StreamUnits` (`title(kind)`, `text(kind, value, units)` and `convert(kind, value, units)`, the decimals and
unit names per stream), `DiveText` (pressure, pressure rate, gas rate, gas name, surface interval) under `DiveFormat`, and
`LabelText.sensor`. The deco model, water type and gear kind names already are `AppText` names (`LabelText`, `GearTexts`) or become
them. `withSmallUnits` stays in Compose (layout); Swift splits a value's number from its unit the same way with a small helper over
the resolved string, tested. Alternative: keep Compose's code and write Swift twins: rejected, the rules say never write a rule twice.

**A card's lines are `CardLine`s Kotlin builds.** `CardLines.of(card, charts, units)` returns, per series the native chart can draw,
a `CardLine(title: AppText?, detail: AppText?, chart: LineChart)`: x in seconds with the duration labels, y in the viewer's unit with
the stream's axis labels, a readout per point ("time · value"), the description the Compose chart reads, `inverted` for pace and depth,
and `average` for the dashed line. Compose's Vico chart keeps its richer drawing (backdrop, second lines, markers) but takes its
header detail and description from the same functions (`CardLines.detail`, `description`). A pace line is capped at 1.5 × its average
as Compose's is. Series a native chart can't draw yet still get a plain chart of their main line.

**`LineChart` gains two optional fields.** `inverted: Boolean = false` (Swift negates y and its ticks, so gridlines and labels stay the
shared ones) and `average: Double? = null` (a dashed `RuleMark`). Defaults keep the segment profile and threshold work unchanged. The
other session (leftovers: threshold history's step chart) may touch `LineChartView` too; I note the two fields in the shared notes
before committing them.

**Zones and laps are rows Kotlin words.** `ZoneRows.of(zones, unit)` gives each zone's label, name, range, time and fraction (the
Compose `Zones` asks it); `LapTable.of(laps, ski, units)` gives the headers and each row's cells and spoken sentence (`LapTableUi`
asks it). Swift draws zones as label/value rows with a bar, and laps as a `Grid` in the card.

**The page's own words are `ActivityPageTexts` in `:feature-detail-api`.** Like `SegmentPageTexts`: title, like label, likers toggle,
together-with lines, menu labels (a list of `ActivityMenuItem`s naming the event, so both UIs build the same menu from one rule),
delete and split alerts, export sheet lines, notices and the dive site line.

**Layout.** One inset grouped `List` on a compact width; from a regular horizontal size class with at least 840 points, an `HStack` of
a 400-point side `List` and a cards `List` capped at 1,040 points, as `PanedPage`. Sections, not cards: each metric card is a `Section`
with its title as the header (feed/activity-page-layout "Native card surfaces"). Comments are `CommentsSection` at the end of the list
(or the side pane), which asks for more as its last row shows.

**Actions.** The bar's menu is a SwiftUI `Menu`; Split and Delete use `.alert`; Export is a `.sheet` with the shared words; when
`export.file` arrives the page writes it with `IosApp.writeExport(file, path)` and shows the share sheet (`ArchiveShare`'s presenter),
then sends `ExportHandedOff`. Report is `reporting(_:)` with `Reporting.reportActivity(activity)` built in Kotlin. The combine prompt is
an `.alert` while `combineSuggestion.asPrompt`.

**Where it lives.** `Screens/ActivityPage.swift` (the page, layout, header, likes, group), `Screens/ActivityPageCards.swift` (figures,
cards, zones, laps, stats, avg/max), `Screens/ActivityPageMenu.swift` (menu, alerts, export sheet). Named `ActivityPage` because
`ActivityView` is the lexicon's class.

## Risks / Trade-offs

- [Charts lose the elevation backdrop and second lines natively] → listed as left, in the roadmap's open items and the change.
- [Moving 460 lines of Compose text could change the Android look] → `PageFiguresTest`, `LapTableUiTest` and the screenshot tests
  compare it; the functions move text choices only.
- [A readout per point is many `AppText`s] → the series are thinned for drawing already (`stream-resampling`).
- [`LineChartView` is shared with the leftovers session] → additive fields with defaults; a note in the shared folder.
