# Tasks

## 1. Clock-time best power

- [x] 1.1 Add `StreamAnalytics.clockSeconds(time, values)` in `core/data`: one value per second from the first non-null sample to the last. Each sample holds across gaps within `PerSecond`'s hold limit, and a longer gap fills with 0 for its real length. Verify with new `StreamAnalyticsTest` cases: 1 s recording equals `perSecond`, a 600 s gap adds 600 zeros, and smart recording (1–8 s gaps) holds with no zeros. Run `./gradlew :core-data:jvmTest --tests "*.StreamAnalyticsTest"`.
- [x] 1.2 Replace `CURVE_SECONDS` with 1, 2, 5, 10, 20, 30, 60, 120, 300, 600, 1200, 1800, 3600, 7200, 10800, 18000. Update the `meanMax` test and its KDoc. Verify `StreamAnalyticsTest` passes.
- [x] 1.3 In `ChartsBuilder.build`, feed `meanMax` from `clockSeconds`, leaving `normalizedPower` on `perSecond`. Add an `ActivityPageTest` (or `ActivityFileDataChartsTest`) case: power of 300 W for 10 min, a 10-minute gap, then 300 W for 10 min. Its 20 min point and Best 20 min power must be 150 W, not 300 W, while normalized power stays 300 W. Run `./gradlew :app-presenters:jvmTest`.
- [x] 1.4 Add a regression test that decodes a trimmed copy of the reference ride's power stream, or a synthetic stream with the same gap pattern, and asserts the 20 min point equals the value Garmin's method gives (179 W for the full reference ride). If the full file is too large to commit, keep a small fixture that reproduces the stitched-versus-clock difference. Verify the test fails on the old `perSecond` input.

## 2. Garmin-style curve chart

- [x] 2.1 In `ActivityChartsUi.PowerCurve`, plot each point at its index, put axis labels at every index using the existing duration labels (`1s`, `2s` … `1h`, `2h`), and convert a marker's fractional x back to seconds by log interpolation between its neighbouring durations. Verify on the desktop app (`get_semantic_tree` / screenshot) that 1 s through 2 h sit equally apart on the reference ride.
- [x] 2.2 Remove the curve's `pointLabels`. Keep `CURVE_STATS` for the content description only. Verify the semantic tree still reads "5s, … ; 20m, 179 W; 1h, …" for the reference ride.
- [x] 2.3 Thin the axis labels below 600dp to every other label, keeping the first and last, so they never overlap. Verify on a 375dp-wide desktop window (`resize_window`) that no labels collide.
- [x] 2.4 Check that `MetricCards`' Best 20 min power still selects `seconds in 60..1200`. Update its KDoc to say the curve is clock-time. Verify that the reference ride's power card shows 179 W in the running desktop app.

## 3. Spec housekeeping and checks

- [ ] 3.1 When archiving, update `openspec/specs/feed/stream-resampling/spec.md`'s Purpose so it no longer lists the power curve among the figures resampled by recorded time. Verify with `openspec validate --specs`.
- [x] 3.2 Run `./gradlew check` and verify it passes (detekt, ktfmt, stability and all targets).
