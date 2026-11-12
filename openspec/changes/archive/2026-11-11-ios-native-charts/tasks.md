# Tasks

## 1. Kotlin rules (no visible change)

- [x] 1.1 `ChartAxis` and `BandStop.runs` in `:core-data` (`data.chart`); `TimeSeriesChart` asks `ChartAxis` and takes `axis`; `GradeBands` keeps colours and brush (design: generic rules). Verify `ChartAxisTest`, `BandStopTest`, `:ui-designsystem:jvmTest`.
- [x] 1.2 `LineChart`, `LinePoint`, `ChartTick` in `:app-common-api` (`app.chart`). Verify `LineChartTest` (nearest, readout, ticks; splitting at gaps is the drawing's, `ChartPieces`).
- [x] 1.3 `SegmentProfile.chart(units)` and `SegmentState.chart`/`routeMap`; `SegmentProfileChart` draws the value. Verify `SegmentProfileChartsTest` and the Android screenshot tests unchanged.
- [x] 1.4 String-array items as `AppText` in the Compose resolver; `EffortTexts` (`:app-common-api`) and `SegmentPageTexts` (`:feature-segment-api`); `EffortRow`, `SegmentUi`, `Credit`, `MayorCard` ask them. Verify their tests, `SharedTextNamesTest`, the screenshot tests unchanged.
- [x] 1.5 `SegmentSamples.variants` (an OpenStreetMap segment with Mayor and ranked, PR, not-ranked and e-bike efforts; a duplicate). Verify `ContentLoadingConformanceTest`.

## 2. The native views

- [x] 2.1 Type aliases and samples (appended blocks). Verify the app builds.
- [x] 2.2 `LineChartView` with `GradeColors`, `ChartBands` and `ChartScrub`; a chart gallery entry. Verify `ChartTests` and the design snapshots.
- [x] 2.3 `LeaderCardView` from the dive site's card; `EffortRowView`; `SegmentPage`. Verify previews, `SegmentSnapshotTests` light and dark with each image looked at, and the dive site snapshots unchanged.
- [x] 2.4 The registry entry. Verify `ScreenRegistryTests` names `SegmentScreen` as native.

## 3. The whole change together

- [x] 3.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [x] 3.2 The Swift tests on iOS 26.0 (`D6328F65`) and 18.4 (`958C0DC3`). Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [x] 3.3 The Release configuration for the simulator. Verify it builds and passes the stub check.

## 4. See it

- [x] 4.1 On the signed-in iPhone 17 Pro `E426A46D` on the local stack: a segment page with its live map, chart and scrubbing, star, boards, names and efforts, light and dark; nothing written or anything written put back. Verify by noting what was seen; record what the stack lacks under "Not verified".

## 5. Docs

- [x] 5.1 The roadmap: a Phases item 4 bullet, "Phase 4 findings (charts)", the open items (segment page done, threshold history left). Verify `openspec validate ios-native-charts --strict` passes.
- [x] 5.2 `.agents/rules/compose-screens.md` and `adaptive-ui.md` where a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 5.3 A "Not verified" section listing what wasn't seen and why.

## Walk (iPhone 17 Pro `E426A46D-6243-41B7-8FB3-1A6CF2AFB295`, iOS 26.0, signed in as alice on the local stack; this change's Debug build)

- **4.1.** Search's Segments mode (the shared browse, embedded) found "Rush - 2112" everywhere, which opened the native segment page:
  Star in the bar, the live MapKit map with the line, start and end markers and Apple's credit, "0.65 mi · Ride", "10 ft gain · 256 ft
  loss · -7.2% avg grade" and "5,358 ft high · 5,112 ft low · 19% max grade" in alice's imperial units, "By @importer.test", the native
  profile on Kotlin's 5,000–5,400 ft axis with 0.0–0.6 mi labels, tinted by grade; a sideways drag showed the readout ("0.4 mi · 5,200
  ft · -1.6% grade") following the finger and kept it after release, and a vertical drag that started on the chart scrolled the page
  (after the fix in this change; before it the page didn't move). OpenStreetMap's credit and "Data licence: ODbL-1.0", Leaderboard, the
  Mayor card (alice, 3 efforts, "You: 3 efforts") opening the native Mayors board, and "Your efforts" with PR on the fastest; an effort
  opened its activity (the shared page) and back returned with the readout kept. Dark mode turned the page, the chart's band colours and
  the readout dark. Star filled the star and a second tap unstarred it. The expand button opened the shared enlarged map. "Easy Street"
  (an alpine run with no terrain profile) showed the ski run icon, "0.28 mi · Ski run", no chart, and "You have no efforts on this
  segment yet". A drag over the map scrolled the page.
- **Put back:** the star was set and cleared again; nothing else was written. Appearance back to light; the Feed tab chosen.

## Not verified

- A segment with names to vote on, Propose, the name sheet and history on a device: the walked segments have no proposals and alice
  can't propose on them. The snapshots draw each, and the sheet was verified natively in `ios-native-report-sheet`.
- A lift on a device (Ramshead Express Quad has alice's rides but wasn't reached through the embedded search), a duplicate, a proposed
  or archived segment, an agency's credit, a rank in the top 10, a not-ranked reason and e-bike efforts: the stack's segments don't
  show them for alice. The snapshots draw them.
- A chart with gaps on a device: no segment profile has gaps; the design gallery's snapshot draws one.
- iOS 18 signed in (the roadmap's open item), an iPad, VoiceOver and the largest text sizes.

## Checks

- `./gradlew check -Pkotlin.incremental=false` passed (the Android screenshot tests unchanged); the Release simulator build passed the
  stub check; `xcodebuild test` passed 368 tests on iOS 26.0 (`D6328F65`, snapshots compared) and on iOS 18.4 (`958C0DC3`, snapshots
  skipped).
