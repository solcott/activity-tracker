# Tasks

## 1. Average temperature from the samples

- [x] 1.1 Make `DecodedActivityMapper.avgTemperature` take the time-weighted mean of the records' temperatures, with each sample's weight capped by `PerSecond`'s hold limit, and fall back to the sessions' average. Verify with `DecodedActivityMapperTest` cases: a session average of 19 °C with records averaging 19.24 °C gives 192 tenths, records without temperature give the session's value, and a long pause doesn't over-weight its sample. Run `./gradlew :core-activityfile:jvmTest`.
  - Dives (FIT dive computers and Shearwater logs) keep their previous average: the computer's own, else the plain mean of the samples, which matches Shearwater's 82.5 °F for its test log.

## 2. Minimums and grade

- [x] 2.1 Add `min` to `LineSeries` (`app/screens`) and fill it in `ChartsBuilder.line`. Set the Grade line's `average` to null. Verify `./gradlew :app-screens:composeStabilityCheck` passes and that `ActivityFileDataChartsTest` asserts the Grade line has no average and the Elevation line has a min.
- [x] 2.2 Make the chart header, average line and content description skip a null average (if they don't already). Verify in the desktop app's semantic tree that the reference ride's Grade reads "Max 13.2%" with no "Avg", and its description has no average.
- [x] 2.3 Add `FigureKey.Lowest` to the Elevation card, and `FigureKey.Min` to the Temperature and Respiration headlines (order Avg, Min, Max), with their strings. Verify with `ActivityPageTest` cases and, in the desktop app, 4,557 ft lowest point, 60.8 °F min and 16.4 brpm min for the reference ride.
  - Desktop app: "Lowest point 4,557 ft", Temperature "Average 66.2 °F · Minimum 60.8 °F · Maximum 80.6 °F" (the stored average, imported before this change), Respiration "Minimum 16.4 brpm". The Avg/Max table's value columns narrow to 64dp when it has three, so "Temperature" doesn't wrap.
- [x] 2.4 Add lowest elevation, and min and max temperature, rows to the stats list's Elevation and Temperature groups, only when the stream was served. Verify with an `ActivityPageTest` case and with a viewer not served the streams, who sees none of them.

- [x] 2.5 Add `min` to `AvgMaxRow`, filled for temperature only, and give `AvgMaxTable` a Min column between Avg and Max when any row has one (empty cells elsewhere). Verify with an `ActivityPageTest` case (the temperature row has a min, the others don't, and a ride without a temperature stream has no min) and, in the desktop app at 900dp, the reference ride's table reads Temperature 66.6 / 60.8 / 80.6 °F with empty Min cells for heart rate, power, speed and cadence.

## 3. Pedal strokes and temperature precision

- [x] 3.1 Add `CycleCount.PEDAL_STROKES` for `Sport.RIDE` and `Sport.VELOMOBILE`, with a `stat_pedal_strokes` plural in the Counts group. Verify `CycleCount` tests (ride → pedal strokes, handcycle → null) and that the desktop app shows "6,676 pedal strokes" on the reference ride.
- [x] 3.2 Show temperatures to one decimal in `Format.temperature` and the temperature stream unit. Verify with `PageFiguresTest` (190 tenths → 66.2 °F, 270 → 80.6 °F, 270 → 27.0 °C) and the dive stats tests.

## 4. Checks

- [x] 4.1 Re-import the reference FIT (`~/Downloads/24785356441_ACTIVITY.fit`) on the desktop app. Verify that avg temperature reads 66.6 °F, min 60.8 °F and max 80.6 °F, matching Garmin Connect.
  - Checked without a second import, which would add a duplicate ride to the account: reading the file through `ActivityFileReader` gives 19.2426 °C (192 tenths, 66.6 °F per `PageFiguresTest`) and 6,676 cycles. The ride already on the desktop app shows min 60.8 °F and max 80.6 °F, and its stored average still reads 66.2 °F, as the spec keeps earlier imports unchanged.
- [x] 4.2 Run `./gradlew check` and verify it passes.
