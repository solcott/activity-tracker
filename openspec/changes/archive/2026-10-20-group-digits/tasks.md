# Tasks

## 1. Shared formatter

- [x] 1.1 Add `expect fun groupingSeparator(): Char` with JVM (`DecimalFormatSymbols`), iOS (`NSLocale.groupingSeparator`) and wasmJs (`(1000).toLocaleString()`) actuals, including the fallback from design decision 1. Verify `./gradlew :core-data:jvmTest` and `:core-data:iosSimulatorArm64Test` compile and pass.
- [x] 1.2 Group the whole part in `formatDecimal` and add `formatWhole(Long)`, both taking an injectable `grouping`. Verify `DecimalsTest` cases: 999, 1000, 1959, -1234, 1013.94 to one decimal (`1,013.9`), and `.` grouping with `,` decimals (`1.013,9`).

## 2. Route every displayed number through it

- [x] 2.1 Switch `Format` (elevation, temperature, distance, speed, weight, depth), `DiveFormat` and `PageFigures.figureText` (`Count`, `Quantity`, `Stream`, `LeftRight`) to the shared formatter. Verify with new `PageFiguresTest` cases for each kind at 4+ digits, in `Locale.US`.
- [x] 2.2 Switch the chart axis labels, marker labels and content descriptions in `ActivityChartsUi`, the zone ranges in `ZoneBars`, and the values in `LapTableUi` and `WorkoutTablesUi`. Leave lap and row numbers ungrouped. Verify by searching `composeApp` for `"${` and `stringResource(Res.string.unit_` with numeric arguments: every remaining hit must be an exempt kind, and you list each exempt one in the task notes.
  - Exempt after the survey: lap, run, length and set numbers (`LapTableUi`, `WorkoutTablesUi`); power-curve duration labels (`1m`, `2h`); gear numbers on the gears chart; list keys and search keys (`SportTypeList`, `TimeZoneNotice`); text joining labels that are already formatted (`ZoneBars`, `DetailVisibilityPicker`). Stream values, chart y axes and depth labels already used `formatDecimal`, so they group without a change.
- [x] 2.3 Switch the feed card, the profile totals and the import screen's numbers. Verify with the presenter tests that render those strings, or `PageFiguresTest` where the text is built in `composeApp`.

## 3. End-to-end check

- [x] 3.1 In the desktop app (English US), open the reference ride and verify with `get_semantic_tree` that it shows "1,959 ft", "1,850 ft", "5,841 ft", "6,676" (if `stream-summary-figures` has landed) and "1,000 W" on the power-curve axis, and that "1:38:05" is unchanged.
  - Shown: "1,959 ft" (gain), "1,850 ft" (descent), "5,841 ft" (highest point), and "average 5,066 ft, maximum 5,841 ft" read to a screen reader, with "1:38:05" unchanged. "6,676" waits for `stream-summary-figures`. This ride's power never reaches 1,000 W (max 708 W), so the power-curve axis has no 4-digit label; `PageFiguresTest` covers "1,000 W".
- [x] 3.2 Run `./gradlew check` and verify it passes.
