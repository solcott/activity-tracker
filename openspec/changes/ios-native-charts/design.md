# Design

## Context

Charts are Vico composables in `:ui-designsystem` (`ui/chart`): `TimeSeriesChart` works out its own y axis (`YAxis.around`, a round 1,
2 or 5 times a power of ten with at most four gaps, and a floor at zero for values that start there), places x labels (`FixedXLabels`)
and reads out the nearest point (`ChartReadout`, `nearestIndex`). The segment page's chart (`SegmentProfileChart`, `:feature-segment-ui`)
converts the profile to the viewer's units, picks the distance labels (`distanceLabels`, `distanceDecimals`), formats every label and the
readout with string resources, and tints the line by grade (`GradeBands.stops`, the run rule, and `GradeBands.brush`). None of that is
reachable from Swift. `:ui-designsystem` holds no app types (`compose-screens.md`) and can't depend on `:app-common-api`, but it does
depend on `:core-data`.

The segment page (`SegmentUi`) is a list: the map (`ActivityMap` with `onExpand`), the about block (original name, kind icon with
length · type, `Elevation`, `Extremes`, the chart, `CreatedBy`, `Credit`, `PlaceStatus`, the Leaderboard button, `MayorCard`), the names
(`PlaceNames`, not for a lift), the efforts' heading and `EffortRow`s (`:app-common-ui`, shared with the activity page). Their texts are
built in composables: the elevation and extremes lines, the credit, the Mayor card's plural, the star's labels, the headings, and an
effort row's ordinal (a `string-array`), PR, reasons and e-bike.

## Goals / Non-Goals

**Goals:**
- One native chart view whose input is a Kotlin value and a height, so the activity page's line charts reuse it.
- Every rule behind the chart (axis, labels, readout, description, band runs) lives once; the Compose chart asks the same.
- The segment page native, with nothing it says written again in Swift.

**Non-Goals:**
- Step charts, point labels, a backdrop, several lines, events and warnings, an inverted axis (the activity page's and threshold
  history's needs): the value has room for them, their pages bring them.
- Threshold history (see below), the enlarged map, segment browse.

## Decisions

### The generic rules go to `:core-data`, the chart value to `:app-common-api`
`ChartAxis(min, max, step)` with `around(low, high)` and `aroundTime(low, high)` and `of(values, labelled, inverted, timeValues)`
(`yAxisOf`) moves to `:core-data` (`data.chart`), and `BandStop(at, band)` with `BandStop.runs(xFractions, bands, minPoints)` (the old
`GradeBands.stops`) beside it. `:ui-designsystem` already depends on `:core-data`, so `TimeSeriesChart` asks `ChartAxis.of` instead of its
private `YAxis`, takes an optional `axis: ChartAxis?` that overrides it, and `GradeBands.brush` takes `core-data`'s `BandStop`; the
colours and brush stay Compose's. Alternatives: `:ui-designsystem` depending on `:app-common-api` (breaks "no app types"), or a copy of
the axis rule in `:app-common-api` (two homes).

`LineChart` (`:app-common-api`, `app.chart`) is what a chart shows, non-generic for Swift export: `points` (`LinePoint(x, y, gapBefore)`,
already in display units), `xMin`/`xMax`, `axis` (`ChartAxis`), `yTicks` and `xTicks` (`ChartTick(value, label: AppText)`; the y ticks
every axis step), `readouts` (one `AppText` per point), `description` (`AppText`), `bands` (`BandStop`s, empty when untinted). Methods:
`nearest(x)` (the old `nearestIndex`, earlier on a tie), `readout(x)`, `labelAt(value, ticks)` for a UI asked about one tick,
`bandCount`. Splitting the points at gaps is the drawing's (`ChartPieces` in Swift, Vico's connector in Compose). A `LineChart.of(...)` builder computes the axis and y ticks
from a label function. `BandStop` gets a Swift type through `LineChart`'s signatures (`bandAt(index)`), as the zones' `Zone` did.

### The segment profile as a chart
`SegmentProfile.chart(units): LineChart` (`:feature-segment-api`, `SegmentProfileCharts.kt`) holds what `SegmentProfileChart` worked out:
the points in the viewer's distance and elevation units, `distanceLabels` and their decimals, the labels (`unit_distance_km`/`_mi`,
`unit_elevation_m`/`_ft` with `formatDecimal`ed values), each point's readout (`segment_profile_readout` with distance, elevation and the
grade at that point's index), the description (`segment_profile_description` with `StatText`'s distance and elevations) and the band
stops (`BandStop.runs` over `gradeBands`). `SegmentState.chart(units)` is the getter both UIs ask. The Compose chart becomes an adapter:
it passes the points as `SeriesPoint`s, the axis, the x tick values and a lookup for labels, the readout and the brushes from the stops.

### `LineChartView`: Swift Charts over the value
`LineChartView(chart:height:tint:)` in `iosApp/iosApp/Design/` (it names only `:app-common-api` and `:core-data` types, like the other
design pieces naming `AppText`). Swift Charts, the system's own: per piece an `AreaMark(x:yStart: axis.min, yEnd:, series:)` and a
`LineMark(x:y:series:)` 1.5 wide, round caps; `chartXScale(domain: xMin...xMax)`, `chartYScale(domain: axis.min...axis.max)`; y
`AxisMarks(values: yTicks)` with gridlines and `AppStrings` labels at the leading edge, x `AxisMarks(values: xTicks)` with labels and a
baseline, no gridlines. Untinted it uses the accent colour (area at 0.18). Tinted, the line and area take a `LinearGradient` across the
plot, each band's colour repeated at its stop so it switches sharply (`ChartBands.gradientStops`, a small type with a Swift test), the
area at 0.45; the colours are `GradeColors` (`Design/`), the five band colours of `GradeBands` for light and dark as dynamic colours.

Readout: `chartOverlay` with a clear layer taking a tap and a UIKit pan (`UIGestureRecognizerRepresentable`, iOS 18) that begins
only when the drag is mostly sideways (`ChartScrub`); a SwiftUI `DragGesture` there, even simultaneous, took the list's scroll (found on
the simulator). The proxy turns the finger's x into a value, Kotlin's `nearest` picks the point, a `RuleMark` and a `PointMark` mark it and
the readout label sits over the point, fitted inside the chart, on the page's background with a hairline (a material there rendered
garbled). It stays after the finger lifts, as the shared one does, and a vertical drag hides it. Half a label of room at each end
(`chartXScale(range: .plotDimension(...))`) keeps the end labels, which Swift Charts otherwise drops. Accessibility: one element,
`accessibilityLabel` the description; the marks' own elements are hidden.

### The segment page
`SegmentPage(state:)` (named so because `SegmentView` is the lexicon's alias), registered for `SegmentScreen`, a `ContentStateView` over
the typed `segment` getter holding an inset grouped `List`: `RefreshStatusView`, the map (`RouteMapView(map: state.routeMap, expand:)`,
sending `OpenMap`; `SegmentState.routeMap` from the polyline), the about section (original name, kind icon with the length · type line,
the elevation and extremes lines, the chart row, created by, credit with OSM's link, the status with Open the original), the boards
section for an active run (Leaderboard and the Mayor card), `PlaceNamesSection` (not for a lift), and the efforts section headed "Your
efforts" or "Your rides" with `EffortRowView`s (a button row each, sending `OpenActivity`) or the empty message. Star is a `pageTitle`
action (`AppIconButton(filled:)`) when `state.starred` isn't null. `.placeNames(state.names)` shows the sheet.

The dive site page's `RegularsCard` becomes `LeaderCardView` in `Screens/RankBoards.swift` (title, leader avatar and name, value, none,
own), which both pages place; the dive site's snapshots must not change.

### Texts
- `EffortTexts` (`:app-common-api`, `app.segment`): `date(effort)` (`DateText.isoDate`), `time(effort)` (`StatText.duration`), `rank(effort)`
  (the ordinal array's item, null past 10), `pr`, `notRanked(effort)`, `eBike`. The shared `EffortRow` asks them.
- A `string-array` item is `AppText.named("<array>_<index>")`, the name the iOS catalog already gives it; the Compose resolver gains the
  same lookup (`Res.allStringArrayResources`), and `SharedTextNamesTest` checks the ordinals.
- `SegmentPageTexts` (`:feature-segment-api`): `star(starred)`, `summary(segment, units)`, `elevation(segment, units)` (null without gain),
  `extremes(profile, units)`, `credit(credit)` (OSM's link text, or the agency line) and `creditLicense(credit)`, `mayorTitle`,
  `mayorNone`, `mayorValue(entry)`, `mayorYours(entry)`, `leaderboard`, `efforts(lift)`, `noEfforts`, and the status helps' names for
  `PlaceTexts.statusHelp`. `SegmentUi`, `Credit` and `MayorCard` ask them.

### Threshold history is left
Its page is a step chart per threshold with filled and hollow dots and a value label over each point, run on to today, a sparkline
overview per collapsed group, expand and collapse, show all, and removal with a failure; the chart value has no step style, dots or
point labels yet, and the page's 423 lines of UI and its texts are a change of their own. It is recorded in the roadmap's open items.

## Risks / Trade-offs

- [Swift Charts lays out its own axes] → the ticks and labels are Kotlin's; only spacing and fonts are the system's. Snapshots show it.
- [A gradient's coordinates in Swift Charts span the marks' bounds] → the line runs from `xMin` to `xMax`, so the plot and the marks'
  bounds are the same; a snapshot of a tinted profile checks the bands' places.
- [A drag gesture in a `List` row fights the scroll] → simultaneous, and a mostly vertical drag gives up the readout; seen on a device.
- [Moving `YAxis` changes nothing on Android] → the same code moved; the Android screenshot tests must stay unchanged.
- [The local stack may have no segment with a profile or efforts] → the walk creates nothing; what it can't show goes under "Not
  verified".
