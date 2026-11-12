# Tasks

## 1. Kotlin rules (no visible change)

- [ ] 1.1 `StreamUnits`, `DiveText`, `LabelText.sensor` and `FigureTexts` in `:app-common-api`; `PageFigures`, `ChartUnits`, `DiveFormat`, `sensorLabel`, `DetailSectionsUi`, `TrainingLoadFigures` resolve them. Verify `FigureTextsTest`, `PageFiguresTest` and `:app-common-ui:jvmTest`.
- [ ] 1.2 `LineChart.inverted` and `average`; `CardLines` (lines, detail, description); `ZoneRows`; `LapTable`; the Compose chart header, zones and lap table ask them. Verify `CardLinesTest`, `ZoneRowsTest`, `LapTableTest`, `LapTableUiTest`.
- [ ] 1.3 `ActivityPageTexts` (`:feature-detail-api`); the Compose page asks it. Verify its test and the detail UI tests.
- [ ] 1.4 `IosApp.writeExport` (`:iosShell`); `ActivityDetailSamples.variants` (owner's ride with cards, charts, zones, laps and stats; a run's pace card; hidden; likes shown; combine banner). Verify `ContentLoadingConformanceTest`.

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
