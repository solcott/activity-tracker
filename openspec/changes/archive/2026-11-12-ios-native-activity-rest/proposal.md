# Proposal

## Why

`ios-native-activity-page` made the activity page native on iOS but left part of it out: the charts the native line chart can't draw
(the elevation behind a line, a left/right pair's second line, several tanks, a dive's gas switches, warnings and ceiling, e-bike assist
steps, the power curve and the gears chart), the legs, lengths and sets tables, the edit-combination sheet, the chart source captions on
a combined activity, and the nested efforts' guide lines and span bars. Three screens the page opens still fall back to the shared
Compose UI: the enlarged route map, the media gallery and the combine picker. Every native piece they need exists (`RouteMapView`
interactive, `LineChartView`, `RemoteImage`, `ContentStateView`, the report sheet), so these are the last of the page.

## What Changes

- **The native chart draws what the shared chart draws** (ui/ios-design-system): `LineChart` gains, all defaulted so the Compose page and
  the existing native charts are unchanged, a `backdrop` (the elevation as a faint area on its own scale behind the line), `others` (more
  unfilled lines on the same scale in their own colours: a pair's right leg, other tanks, a dive's ceiling), `marks` (a labelled vertical
  line at a moment: a gas switch, or a warning in the error colour) and `fillAbove` (a dive's depth, filled up to the surface).
  `LineChartView` draws each.
- **A card's charts are complete** (dev/shared-display-text): `CardLines` builds every chart the shared page draws: effort lines over the
  elevation (their readout naming the elevation there), a pair's two legs, a chart of every tank, the depth with its switches, warnings
  and ceiling, assist mode in steps, the power curve (durations evenly spaced) and the gears in steps over the elevation, with their
  titles, details and descriptions. The Compose charts ask the same descriptions and labels; Vico still draws them there.
- **The tables are native**: the legs, lengths and sets tables are chosen in Kotlin (`StructureTableTexts`, as `DetailTable`s), which the
  Compose tables now draw, and the native page draws them as it draws the laps.
- **A combined activity's cards say where their data came from** ("From Edge 840, then Phone"), and the owner's page offers Edit beside
  the members, opening a native sheet: the members, each removable after a confirmation, and the data sources, each kind with its
  switch and its source menu. Their words are Kotlin's (`CombinationTexts`), which the Compose sheet asks too.
- **Nested efforts draw their guides and span bars**: each nested effort joined to its parent by guide lines in its indent, and a bar
  under each effort of a group showing its span within the group, as the shared card draws them.
- **The screens the page opens are native**: the enlarged route map (`RouteMapScreen`, on the interactive `RouteMapView` filling the page,
  titled with the activity), the media gallery (`MediaGalleryScreen`: black, full screen, swiped a photo at a time, a tap zooming the
  photo for pinch, double tap and pan, the counter, Close, Report on someone else's photo, blurred photos until revealed, the owner's
  taken-down placeholders), and the combine picker (`CombinePickerScreen`: the explanation, the candidates with their type, title, start,
  length and device, a spinner on the one combining, the error and the empty state). Each is registered in `ScreenRegistry`.
- **Samples, previews and snapshots** for each new piece: chart samples in the design gallery, activity samples with a dive, a pair, tanks,
  a pool swim, a workout, a multisport activity and a combined ride, and the three screens' samples.

What still waits: Edit, Create segment, Create dive site and Add people stay the shared screens (the sibling forms change and later ones
own them).

Out of scope: any visible change on Android, desktop or web; the import preview's cards.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/activity-page-layout`: the native page draws every chart and table the shared page does, captions a combined activity's cards,
  edits a combination and nests efforts with guides and span bars; it no longer leaves them to later.
- `feed/route-map-viewer`: the enlarged route map is native on iOS.
- `feed/combined-activities`: the combine picker is native on iOS.
- `ui/ios-design-system`: a native chart draws a backdrop, more lines, marks and a fill above its line; the media gallery is native.
- `dev/shared-display-text`: the structure tables, the power curve and gears charts, a dive's chart marks, the combination sheet and the
  source captions are chosen in shared code.

## Impact

- **`:app-common-api`:** `LineChart` (`backdrop`, `others`, `marks`, `fillAbove`; `ChartLine`, `ChartMark`), `CardLines` (every chart),
  `StructureTableTexts`, `DetailTable` gains a wide column, `CombinationTexts`; tests.
- **`:app-common-ui`, `:feature-detail-ui`:** the Compose tables, captions, combination sheet and chart descriptions ask them; no visible
  change.
- **`:feature-map-api`, `:feature-media-api`, `:feature-detail-api`:** texts and helpers for the three screens.
- **`:app-fixtures`:** samples for the new cards and the three screens.
- **`iosApp`:** `LineChartView`, `ActivityPageCards`, `ActivityPage`, new `ActivityRouteMapView`, `MediaGalleryView`, `CombinePickerView`,
  `EditCombinationSheet`; type aliases, samples, previews, snapshot tests, registry entries.
- **Docs:** the roadmap (phase 4 entry, findings, open items); `.agents/rules/compose-screens.md` and `adaptive-ui.md` clauses.
- **Risk:** the local stack's activities hold few dives, swims, workouts or combined rides, so the walk sees only some; snapshots cover
  the rest and compare only on iOS 26.0.
