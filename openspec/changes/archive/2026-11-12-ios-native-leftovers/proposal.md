# Proposal

## Why

The iOS migration has four small leftovers. Threshold history is the last settings page still drawn by the shared UI, because its
charts are step lines with dots and value labels, which the native chart can't draw yet. The native feed's type sheet has no search and
no collapsed sports, though the shared sheet has both. Some iOS 26.0 snapshot suites fail only under a full `xcodebuild test` run's load,
so a full run isn't a trustworthy check. And `UploadsPresenter` calls `PersistentList.add`, which is deprecated in
kotlinx-collections-immutable 0.5.

## What Changes

- **Threshold history is native on iOS** (`ThresholdHistoryView`), registered in `ScreenRegistry`: an inset grouped list with the
  shared help, Expand all or Collapse all in the bar, and per group (the defaults, then each sport) a header row that expands or
  collapses it with its summary. An expanded group shows each threshold's value now and how it moved, its step chart with a dot and a
  value label on each dated value (filled on the current value) running on to today, the first date and "Today" under it with the
  undated count, then its values newest first (date, source, Current, a menu to remove one, progress while removing) and Show all or
  Show fewer. A collapsed group shows a tile per threshold: its value, a sparkline and its change. Removal failure and loading follow
  `ui/content-loading`.
- **The native chart draws steps and dots.** `LineChart` (`:app-common-api`) gains a step style and point dots (filled or hollow, each
  with an optional label), both additive with defaults, which `LineChartView` draws. A sparkline is a `LineChart` with no ticks.
- **Kotlin chooses what threshold history says and charts** (`ThresholdHistoryTexts` in `:feature-settings-api`): a group's heading,
  help and summary, a threshold's label and values, its change, a row's date and source, Show all, the remove label and the chart's
  description, and the step chart as a `LineChart` (`ThresholdHistoryTexts.chart(section, detailed)`). The Compose page resolves the
  same texts; its Vico chart is unchanged.
- **The native type sheet searches and collapses** like the shared one: a search field (empty each time the sheet opens) matching a
  type's label, its sport's label or either token; while searching every match shows, categories with none are hidden, the quick types
  are hidden, category headers show no mark and don't toggle, and "No sports match" shows when nothing does. Without a query, each sport
  with sub-sports under it starts collapsed and discloses them, expanding never changing the draft. The match rule moves from
  `:app-common-ui` to `:app-common-api` (`SportTypeSearch`), and the sheet's groups for a query are `TypePickerState.groups(...)`,
  which both UIs ask.
- **Full snapshot runs are reliable.** The cause of the suites that fail only under load is found and fixed in
  `iosApp/iosAppTests/Snapshots.swift` (or the suites), and a full `xcodebuild test` on iOS 26.0 passes twice in a row.
- **`UploadsPresenter` uses `adding`** in place of the deprecated `PersistentList.add`.

Out of scope: Android, desktop and web beyond the shared rules moving (no visible change there); the activity page's charts (another
session's); the record-screen type picker; Vico's removal from iOS (phase 5).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui/settings-layout`: threshold history is native on iOS, opened from the native Training zones as a settings page.
- `ui/ios-design-system`: the native line chart draws step lines and labelled dots.
- `feed/feed-filter`: the native type sheet's search and collapsed sports.
- `dev/ios-snapshot-tests`: a full test run's snapshot comparisons are as reliable as a single suite's.
- `dev/shared-display-text`: threshold history's text and chart, and the type search's match rule, are chosen in shared code.

## Impact

- **`:app-common-api`:** `LineChart.steps`, `ChartDot`; `SportTypeSearch`; tests.
- **`:app-common-ui`:** `matchesTypeQuery` asks `SportTypeSearch`; `HistoryDates` asks shared date texts.
- **`:feature-settings-api`, `-ui`:** `ThresholdHistoryTexts`; the Compose page's text helpers resolve it.
- **`:feature-feedfilter-api`, `-ui`:** `TypePickerState.groups`; the Compose sheet asks it.
- **`:feature-uploads-presenter`:** `adding`.
- **`:app-fixtures`:** threshold history variants if the native page needs states the samples lack.
- **`iosApp`:** `LineChartView` (steps, dots), `ThresholdHistoryView`, `TypePickerSheet`, type aliases, samples, previews,
  snapshots, the registry entry and its test, `Snapshots.swift`.
- **Docs:** the roadmap's phases, findings and open items; `.agents/rules/compose-screens.md` and `adaptive-ui.md`.
- **Risk carried:** the activity-page session may change `LineChartView` too (coordinated in the shared notes; this change only
  adds); a flake under load may need more than one cause fixed.
