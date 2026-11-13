# Design

## Context

The checklist names `DiveStats.kt:52-160`, `DiveSettingsStats.kt:25-66`, `DepthProfile.kt:84-96` and `LapTableUi.kt:49-57` as unused.
Checked by name across every Kotlin and Swift source set, tests, fixtures and screenshot tests: none has a caller outside those files.
Strings are looked up through `Res.string.*` in Compose and `AppText.named("…")` in shared code (`FigureTexts`' `LABELS` map among
them), and the String Catalog is generated from `strings.xml`, so a string is unused only when neither spelling appears outside the
deleted files and Swift doesn't name it.

## Decisions

- **Delete whole files, not just the listed lines.** `diveCardStats` (`DiveStats.kt:46`) and the private helpers around the listed
  ranges are unused too, and `DiveFormat` had no caller but `DiveStats.kt`. Keeping them would keep a file of dead wrappers.
- **`DepthProfile` goes whole.** Its composable has no caller: the import preview is the activity page, which draws a dive's depth
  through `CardLines`. Its chart helpers (`ChartFrame`, `ChartReadout`, `StaticChart`, …) stay: the threshold history chart and
  `TimeSeriesChart` use them.
- **`DivePreview.profile` stays.** It is the import contract's, filled by the presenter and tested there; removing it is a separate
  cleanup of the import preview's state, not text.

## Risks

- A string removed that a UI still needs would fail at build (`Res.string`) or show its key (`AppText.named`); `SharedTextNamesTest`
  and `checkIosResources` cover the shared names and the catalog.
