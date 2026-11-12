# Proposal

## Why

The activity page (`ActivityDetailScreen`, the page every activity card opens) is the biggest screen iOS still draws with the shared
Compose UI, and the one athletes open most. Every piece it needs now exists natively (`RouteMapView`, `LineChartView`,
`CommentsSection`, `EffortRowView`, `MediaStrip`, `ContentStateView`, `reporting(_:)`), but most of what the page says is still chosen
inside Compose code a native page can't ask: each figure's label and value (`PageFigures.kt`), a stream's title and unit
(`ChartUnits.kt`), a dive's gases and pressures (`DiveFormat`), a sensor's name, a card's title, a lap's cells, a zone's range, and the
page's buttons and notices.

## What Changes

- **Kotlin chooses what the page says, both UIs ask** (dev/shared-display-text):
  - `FigureTexts` (`:app-common-api`, `app.detail`): a figure's label and value in the viewer's units, a stats group's and an avg/max
    row's name, a card's title, and a stream's title and unit text. `figureLabel`, `figureText`, `groupTitle`, `avgMaxLabel`,
    `sectionTitle`, `streamTitle` and `streamUnit` in `:app-common-ui` resolve them.
  - `DiveText` (`:app-common-api`) for a dive's pressures, rates, gas names and surface interval, which `DiveFormat` resolves, and
    `LabelText.sensor` for a sensor's name, which `sensorLabel` resolves.
  - `CardLines` (`:app-common-api`): a metric card's lines as `LineChart`s with their titles and details (a pace line inverted, its
    average marked), and `ZoneRows` and `LapTable` for its time in zones and laps.
  - `ActivityPageTexts` (`:feature-detail-api`): the title, like, likers, together-with, owner's and others' menus, delete and split
    confirmations, export sheet, notices, dive site line, recordings and members, combine banner and prompt.
- **A native chart can be inverted and mark its average** (ui/ios-design-system): `LineChart` gains `inverted` and `average`, which
  `LineChartView` draws (the faster pace at the top, a dashed average line). The segment profile's chart is unchanged.
- **The activity page is native on iOS** (`ActivityPage`, registered in `ScreenRegistry`), in inset grouped sections: the header (who,
  start, title, type, dive site, description, recordings and members, the combine banner, notices), the route on the native map with
  its expand to the enlarged map and the photos opening the gallery, the hero figures, likes and who liked it, Together with, the
  avg/max table, the training load card, a card per metric with its headline, native charts, time in zones (or Set thresholds) and
  rows, the laps table, segment efforts and lifts, the stats list with gear rows opening their gear, and the comments. The owner's menu
  (Edit, Combine with, Add a recording, Split, Ungroup, Hide start and end, Create segment, Create dive site, Export with its sheet and
  the share sheet, Delete with its confirmation) and Report on someone else's activity sit in the bar. A hidden activity waits behind
  its warning. From a regular width (an iPad) the page lays out as two columns: the side pane (map, header, hero, likes, group, avg/max,
  training load, stats, comments) beside the cards, as the shared page does from 840dp.
- **Samples, previews and snapshots**: new `ActivityDetailSamples.variants` (a ride with cards, charts, zones and laps for its owner, a
  run's pace card, a hidden activity, likes shown, a combine suggestion), previews, a snapshot suite light and dark at phone and iPad
  size, and the registry entry and test.

What waits (open items in the roadmap and in this change):

- Charts the native chart can't draw yet: the elevation behind the effort lines, a left/right pair's second line, several tanks on one
  chart, a dive's gas switches, warnings and deco ceiling, e-bike assist mode's steps, the power curve and the gears chart. Their cards
  still show their headline and rows natively, and each line as a plain chart.
- The legs, lengths and sets tables (multisport, pool swims, workouts), the edit-combination sheet (remove a member, data sources) and
  the chart source captions on a combined activity. Split stays in the menu.
- The media gallery, the enlarged route map, Edit, the combine picker and Add people stay the shared screens they open.

Out of scope: any change to Android, desktop or web beyond the shared rules moving; the import preview, which shows the same cards.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/activity-page-layout`: the activity page is native on iOS, with the shared page's sections, layout by width, and what it
  leaves to the shared screens it opens.
- `ui/ios-design-system`: a native line chart can be inverted and mark its average.
- `dev/shared-display-text`: a figure's label and value, a card's lines, zones and laps, and the activity page's words are chosen in
  shared code.

## Impact

- **`:app-common-api`:** `FigureTexts`, `DiveText`, `LabelText.sensor`, `CardLines`, `ZoneRows`, `LapTable`; `LineChart.inverted`
  and `average`; tests.
- **`:app-common-ui`:** `PageFigures`, `ChartUnits`, `DiveFormat`, `sensorLabel`, `DetailSectionsUi`, `LapTableUi`, the zones in
  `ActivityChartsUi` resolve them; no visible change.
- **`:feature-detail-api`, `-ui`:** `ActivityPageTexts`; the Compose page's words ask it.
- **`:app-fixtures`:** `ActivityDetailSamples.variants`.
- **`iosApp`:** `ActivityPage` and its sections, `LineChartView` (inverted, average), type aliases, samples, previews, snapshot tests,
  the registry entry.
- **Docs:** the roadmap (phase 4 entry, findings, open items); `.agents/rules/compose-screens.md` and `adaptive-ui.md`.
- **Risk carried:** the page is large, so its native layout diverges in detail from the shared one (no elevation backdrop on charts);
  snapshots compare only on iOS 26.0; the walk sees only what the local stack's activities hold.
