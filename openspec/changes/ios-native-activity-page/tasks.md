# Tasks

## 1. Kotlin rules (no visible change)

- [x] 1.1 `StreamUnits`, `DiveText`, `LabelText.sensor` and `FigureTexts` in `:app-common-api`; `PageFigures`, `ChartUnits`, `DiveFormat`, `sensorLabel`, `DetailSectionsUi` resolve them (`TrainingLoadFigures` folded in). Verify `FigureTextsTest`, `PageFiguresTest`, `SharedTextNamesTest` and `:app-common-ui:jvmTest`.
- [x] 1.2 `LineChart.average` (an inverted line is plotted negated on `ChartAxis.of(inverted = true)`, as the Vico chart does); `CardLines` (lines, detail, description); `ZoneRows`; `LapTableTexts`; the Compose chart header, zones and lap table ask them. Verify `CardLinesTest`, `LapTableUiTest`, `SharedTextNamesTest`.
- [x] 1.3 `ActivityPageTexts` (`:feature-detail-api`) and `RecordingTexts` (`:app-common-api`, shared with import); the Compose page asks them. Verify `ActivityPageTextsTest` and the detail UI tests.
- [x] 1.4 `IosApp.writeExport` (`:iosShell`); `ActivityDetailSamples.variants` (owner's ride with cards, charts, zones, laps and stats; a run's pace card; hidden; likes shown; combine banner). Verify `ContentLoadingConformanceTest`.

## 2. The native views

- [ ] 2.1 `LineChartView` draws `inverted` and `average`; gallery entries. Verify `ChartTests` and the design snapshots.
- [ ] 2.2 Type aliases and samples (appended blocks). Verify the app builds.
- [ ] 2.3 `ActivityPage`, `ActivityPageCards`, `ActivityPageMenu`. Verify previews and `ActivityPageSnapshotTests` light and dark, phone and iPad, each image looked at.
- [ ] 2.4 The registry entry. Verify `ScreenRegistryTests` names `ActivityDetailScreen` as native.

## 3. The whole change together

- [ ] 3.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [ ] 3.2 The Swift tests on cloned iOS 26.0 and 18.4 simulators. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [ ] 3.3 The Release configuration for the simulator. Verify it builds and passes the stub check.

## 4. See it

- [ ] 4.1 On the signed-in iPad Air `3F116204` on the local stack: activities with a map, charts and comments, the side pane, the menus, light and dark; anything written put back. Verify by noting what was seen; record what the stack lacks under "Not verified".

## 5. Docs

- [ ] 5.1 The roadmap: a Phases item 4 bullet, "Phase 4 findings (activity page)", the open items. Verify `openspec validate ios-native-activity-page --strict`.
- [ ] 5.2 `.agents/rules/compose-screens.md` and `adaptive-ui.md`. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh`.
- [ ] 5.3 A "Not verified" section listing what wasn't seen and why.
