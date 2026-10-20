# Proposal

## Why

The app prints whole numbers without digit grouping: "1959 ft", "5841 ft" and "6676" where Garmin Connect, and most of the platforms' own apps, show "1,959 ft". Long numbers are harder to read at a glance, and a page full of them looks unfinished next to the decimals the app already writes with the viewer's locale's decimal separator.

## What Changes

- Every whole number the app shows with four or more integer digits is grouped in thousands with the viewer's locale's grouping separator. That covers figures, stats rows, feed cards, lap and workout tables, the profile and chart axes, markers and screen-reader descriptions. In English that is "1,959 ft"; in German it is "1.959 ft".
- Decimals keep their decimal separator and gain grouping in their whole part ("1,013.9 mi").
- Durations ("1:38:05"), dates, years, rkeys and handles, and numbers inside text the athlete typed, are left as they are.

## Capabilities

### New Capabilities

- `ui/number-format`: How the app writes numbers for people to read: digit grouping and decimal separators by locale, and what is exempt.

### Modified Capabilities

(none)

## Impact

- `core/data` `units/Decimals.kt`: a new `expect fun groupingSeparator()` with JVM, iOS and wasmJs actuals beside `decimalSeparator()`, and grouping in `formatDecimal` plus a new `formatWhole`.
- `composeApp` `Format` (elevation, temperature and the other unit helpers), `PageFigures.figureText` (`Count`, `Quantity`, `Stream`), `ActivityChartsUi` axis and marker labels, `LapTableUi`, `WorkoutTablesUi` and other places that interpolate a number with `"${n}"`.
- String resources take formatted text instead of raw `Long`s where they don't already.
- No stored data, lexicon or AppView changes.
