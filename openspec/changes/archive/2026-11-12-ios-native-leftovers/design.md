# Design

## Context

- Threshold history's contract (`ThresholdHistoryScreen.kt`, `:feature-settings-api`) already carries everything the page shows:
  groups, sections with `shownRows`/`canShowAll`, rows, `ThresholdChart` (points placed 0–1, the first date, lowest and highest) and
  `ThresholdTrend`. What it *says* is still written in Compose (`ThresholdHistoryText.kt`, `ThresholdHistoryUi.kt`), and its chart is a
  Vico step chart with dots and labels (`ThresholdHistoryChart.kt`). Swift already reads these classes (`ZonesContractsTests`).
- `LineChart` (`:app-common-api`, `app.chart`) and `LineChartView` (Swift Charts) draw a filled line, no steps, no dots. The activity-page
  session may extend them too; the shared notes file `ios-native-leftovers-findings.md` says this change only adds.
- The native type sheet (`TypePickerSheet`, `FeedFilterBar.swift`) lists flat rows. The gear editor's `TypeChoiceList` already discloses
  `SportTypeGroup.rows` with `DisclosureGroup`. The search rule (`matchesTypeQuery`) is in `:app-common-ui`, out of Swift's reach, and
  takes resolved labels. Swift's `CatalogText` is a Kotlin `TextSource`, so Kotlin can resolve labels with it.
- `assertAppSnapshot` hosts a `UIHostingController`, sets `layer.speed = 0`, and snapshots at once with `drawHierarchyInKeyWindow`.

## Goals / Non-Goals

**Goals:** the four leftovers done, each rule written once in Kotlin, the native pages laid out as the system's lists are.

**Non-Goals:** redrawing the Compose threshold chart from `LineChart` (it keeps Vico and `ThresholdChart`; only its text moves); the
record screen's type picker; changing any snapshot reference that isn't this change's.

## Decisions

- **`LineChart` grows two additive fields.** `steps: Boolean = false` and `dots: ImmutableList<ChartDot> = persistentListOf()`, with
  `ChartDot(index, filled, label: AppText?)`. `LineChartView` uses `.interpolationMethod(.stepEnd)` for both marks when `steps`, and a
  `PointMark` per dot with a custom filled or hollow circle and the label as a top annotation fitted to the chart. Defaults keep every
  existing chart and snapshot unchanged. Not chosen: a separate `StepChartView`, which would copy the axis and scrub code.
- **The threshold chart as a `LineChart`, chosen in Kotlin.** `ThresholdHistoryTexts.chart(section, detailed)` maps `ThresholdChart`'s
  0–1 points onto x 0…1, adds the run-on point at x = 1 (no dot) when the last value is before today, and sets the y axis around the
  0–1 placements with the same insets the Vico chart uses (more room above for labels when detailed). Detailed: y ticks at the lowest and
  highest placements labelled with their values (as the Compose panel's side labels), x ticks at 0 (the first date) and 1 (Today),
  dots labelled with each value. A sparkline: no ticks, unlabelled dots none. Readouts are each point's value and date.
- **Texts.** `ThresholdHistoryTexts` (object, `:feature-settings-api`) returns `AppText`s for every string the page shows. Dates move to
  `AppText` too: the long date is `DateText.date`; the short date is `zones_history_date_short` over `month_short_<n>`, so the Compose
  `shortDate` and `monthName` resolve the same choice. The Compose helpers in `ThresholdHistoryText.kt` become thin resolvers.
- **The native page** (`ThresholdHistoryView`, `Screens/`) is an inset grouped `List` under `pageTitle(.zonesHistory, back:, actions:)`
  with Expand all/Collapse all as the action; each group is a `Section` whose first row is its header button. A threshold in full is a
  few rows (headline, chart, value rows, Show all) so the list keeps its separators; a value's remove is a trailing `Menu` with a
  destructive Remove, as the Compose confirm menu, replaced by `AppProgress` while removing. Collapsed tiles sit in one row as a
  two-column `LazyVGrid` on a phone, more by width.
- **Type search.** `SportTypeSearch.matches(type, query, typeLabel, sportLabel)` in `:app-common-api` (`app.sport`) holds the rule;
  `matchesTypeQuery` (Compose) calls it. `TypePickerState.groups(query, source: TextSource)` returns the categories' matching types as
  `SportTypeGroup`s (all when blank), which Swift asks with `CatalogText.shared`; Compose keeps its matcher over Compose-resolved labels
  through `TypePickerState.groups(matches)`. The query stays UI state (`@State` in Swift, `rememberTextFieldState` in Compose), as the
  rule for picker searches says. The native sheet uses `.searchable` in its own `NavigationStack` and `DisclosureGroup`s over
  `SportTypeGroup.rows`, expanded state reset per open; while searching, rows render flat with nested rows shown.
- **Flaky snapshots.** First reproduce under load (a full run, and with parallel load) and diff the failing images to tell timing
  (a view not settled: a sheet's or list's first layout, `onAppear`/`task` state, a deferred toolbar) from precision (antialiasing
  differences). The expected fix is in `assertAppSnapshot`: put the controller in the key window and let the run loop settle (lay out
  and spin until two renders agree, bounded) before the comparison; and only where differences are rendering noise, a tolerance
  justified by the diff. The design records which it was.
- **Flaky snapshots, found.** Timing, not precision. A full run failed `GearEditorsSnapshotTests` `nothingToAdd.dark` on 44,085 of
  1,485,540 pixels, at most 9 levels off, all under the sheet's bar: the soft scroll edge's blur, drawn by the render server from a
  backdrop it captures on its own time. Twelve busy CPU cores didn't reproduce it, and the suites passed alone. With the settling draw
  (`Snapshotting.settledImage`: the library's own key-window draw, repeated until two agree, at most six, 50 ms apart, synchronously),
  two suites' references turned out to hold a half-done first draw: athlete search's field without its clear button, and the quote
  sheet's text wrapped at a narrower width. Those were re-recorded settled; everything else compared unchanged. The same full run threw
  `count` unrecognized from a test casting an erased `content.data` to a Swift array; those tests read the typed getter now.
- **Deprecation.** `expanded.adding(event.batchId)`.

## Risks / Trade-offs

- Swift Charts' steps and label placement look close to, not equal to, Vico's; snapshots are the reference for the native look.
- A label near the top of the chart could clip: the y axis keeps headroom for labels and annotations fit to the chart.
- A settling loop in snapshots adds time per test; it is bounded and only spins while renders differ.
- Concurrent changes to `LineChartView` by the activity-page session: additive edits, coordinated in the shared notes.
