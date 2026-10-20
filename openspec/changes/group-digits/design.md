# Design

## Context

Number text is built in two places. `core/data/units/Decimals.kt` has `formatDecimal(value, decimals, trimZeros, separator)` and an `expect fun decimalSeparator()`: `DecimalFormatSymbols` on the JVM, `NSLocale` on iOS, and a formatted 1.5 on wasmJs. Everything else is ad hoc. `Format.elevation` and `temperature` pass a `Long` into a `%1$s` string resource. `PageFigures.figureText` writes `Count` as `"${value.value}"` and passes `Quantity` straight to `stringResource`. Chart axes and markers write `"${it.roundToInt()}"`. No path groups digits. The `unit_*` resources all take `%1$s`, so they accept preformatted text without any resource changes.

## Goals / Non-Goals

**Goals:**
- One shared formatter that all number text goes through, with grouping that matches across platforms.

**Non-Goals:**
- Full ICU formatting: locale digit shapes, Indian 2-digit grouping, or minimum-grouping rules such as Spanish leaving 4-digit numbers ungrouped. We keep one simple rule everywhere.
- Changing the units, rounding or decimals shown.

## Decisions

**1. `groupingSeparator()` beside `decimalSeparator()`.** We add `expect fun groupingSeparator(): Char` to `Decimals.kt`. On the JVM it reads `DecimalFormatSymbols.groupingSeparator`. On iOS it reads `NSLocale.currentLocale.groupingSeparator`. On wasmJs it reads the character at index 1 of `(1000).toLocaleString()`. If that character is a digit (no grouping), or a platform returns no separator, we fall back to `,`, or to `.` when the decimal separator is `,`. A narrow no-break space (fr) is kept as the platform gives it.
- *Rejected: platform number formatters per call* (`NumberFormat`, `NSNumberFormatter`, `Intl`). They aren't in common code, would need a fourth actual for each kind of number, and would bring the minimum-grouping differences we're deliberately avoiding.

**2. Grouping inside `formatDecimal`, plus `formatWhole`.** `formatDecimal` groups its whole part (with a `grouping: Char = groupingSeparator()` parameter for tests). We add `fun formatWhole(value: Long, grouping: Char = groupingSeparator()): String` for integers. Durations, dates and lap numbers never call these, which is how the exemptions hold.

**3. Callers pass text, not numbers, to resources.** `Format.elevation`, `temperature`, `PageFigures.figureText` (`Count`, `Quantity`, `Stream`), the chart y labels and markers (`ActivityChartsUi`, `TimeSeriesChart` callers), `LapTableUi`, `WorkoutTablesUi`, `DiveFormat` and the import screen's counts switch to `formatWhole`/`formatDecimal`. We find them by searching for `"${` and for `stringResource(Res.string.unit_` with a numeric argument. `ZoneBars` ranges ("136–150 bpm") and the power zone bounds go through `formatWhole` as well.

**4. A detekt-free guard by test.** `PageFiguresTest` gains cases for each `FigureValue` kind at 4+ digits. There's no static rule: a bare `"${n}"` can't be told from an ID, so tests and review keep it honest.

## Risks / Trade-offs

- [A number gets missed and stays ungrouped] → Task 2's survey lists every site, and the reference ride's page is checked end to end in the desktop app.
- [Locales whose convention skips 4-digit grouping (es, pl) see "1.959"] → Accepted: it's consistent and still readable. ICU-level formatting can come later behind the same functions.
- [Tests that assert exact strings across modules break] → Tests pass an explicit `grouping` (or run in `Locale.US` on the JVM) so their output doesn't depend on the machine.
