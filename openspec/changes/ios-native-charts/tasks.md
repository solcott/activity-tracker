# Tasks

## 1. Kotlin rules (no visible change)

- [ ] 1.1 `ChartAxis` and `BandStop.runs` in `:core-data` (`data.chart`); `TimeSeriesChart` asks `ChartAxis` and takes `yAxis`; `GradeBands` keeps colours and brush (design: generic rules). Verify `ChartAxisTest`, `BandStopTest`, `:ui-designsystem:jvmTest`.
- [ ] 1.2 `LineChart`, `LinePoint`, `ChartTick` in `:app-common-api` (`app.chart`). Verify `LineChartTest` (nearest, readout, pieces, ticks).
- [ ] 1.3 `SegmentProfile.chart(units)` and `SegmentState.chart`/`routeMap`; `SegmentProfileChart` draws the value. Verify `SegmentProfileChartsTest` and the Android screenshot tests unchanged.
- [ ] 1.4 String-array items as `AppText` in the Compose resolver; `EffortTexts` (`:app-common-api`) and `SegmentPageTexts` (`:feature-segment-api`); `EffortRow`, `SegmentUi`, `Credit`, `MayorCard` ask them. Verify their tests, `SharedTextNamesTest`, the screenshot tests unchanged.
- [ ] 1.5 `SegmentSamples.variants` (an OpenStreetMap segment with Mayor and ranked, PR, not-ranked and e-bike efforts; a duplicate). Verify `ContentLoadingConformanceTest`.

## 2. The native views

- [ ] 2.1 Type aliases and samples (appended blocks). Verify the app builds.
- [ ] 2.2 `LineChartView` with `GradeColors`, `ChartBands` and `ChartScrub`; a chart gallery entry. Verify `ChartTests` and the design snapshots.
- [ ] 2.3 `LeaderCardView` from the dive site's card; `EffortRowView`; `SegmentPage`. Verify previews, `SegmentSnapshotTests` light and dark with each image looked at, and the dive site snapshots unchanged.
- [ ] 2.4 The registry entry. Verify `ScreenRegistryTests` names `SegmentScreen` as native.

## 3. The whole change together

- [ ] 3.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [ ] 3.2 The Swift tests on iOS 26.0 (`D6328F65`) and 18.4 (`958C0DC3`). Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [ ] 3.3 The Release configuration for the simulator. Verify it builds and passes the stub check.

## 4. See it

- [ ] 4.1 On the signed-in iPhone 17 Pro `E426A46D` on the local stack: a segment page with its live map, chart and scrubbing, star, boards, names and efforts, light and dark; nothing written or anything written put back. Verify by noting what was seen; record what the stack lacks under "Not verified".

## 5. Docs

- [ ] 5.1 The roadmap: a Phases item 4 bullet, "Phase 4 findings (charts)", the open items (segment page done, threshold history left). Verify `openspec validate ios-native-charts --strict` passes.
- [ ] 5.2 `.agents/rules/compose-screens.md` and `adaptive-ui.md` where a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [ ] 5.3 A "Not verified" section listing what wasn't seen and why.
