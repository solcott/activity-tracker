# Tasks

## 1. Kotlin rules (no visible change)

- [ ] 1.1 `LineChart` gains `backdrop`, `others` (`ChartLine`), `marks` (`ChartMark`) and `fillAbove`, all defaulted, with typed getters. Verify `LineChartTest`.
- [ ] 1.2 `CardLines.of` builds every chart: elevation behind effort lines (readout with elevation), a pair's right leg, every tank on one chart, the depth with ceiling, switches and alerts, assist steps; `CardLines.powerCurve` and `CardLines.gears`. The Compose card asks their words. Verify `CardLinesTest` and `:app-common-ui:jvmTest`.
- [ ] 1.3 `StructureTableTexts` (lengths, sets, legs) as `DetailTable`s with a wide column; the Compose tables draw them. Verify a new `StructureTableTextsTest` and the Compose table tests.
- [ ] 1.4 `CombinationTexts` and the source caption; the Compose sheet, data sources and caption ask them. Verify `CombinationTextsTest` and the detail UI tests.
- [ ] 1.5 `RouteMapTexts`, `MediaGalleryTexts`, `CombinePickerTexts`; the Compose screens ask them. Verify their tests.
- [ ] 1.6 Samples: activity detail variants with a dive, a pair, tanks, a pool swim, a workout, multisport, a combined ride with captions and nested efforts; route map, gallery and picker samples. Verify `ContentLoadingConformanceTest`.

## 2. The native views

- [ ] 2.1 `LineChartView` draws the backdrop, others, marks and fill above; gallery entries. Verify the design snapshots, old references unchanged.
- [ ] 2.2 The page: every chart per card, the structure tables, source captions, Edit and `EditCombinationSheet`, guides and span bars. Verify previews and snapshots, each image looked at.
- [ ] 2.3 `ActivityRouteMapView`, `MediaGalleryView`, `CombinePickerView`; type aliases, samples, registry entries and `ScreenRegistryTests`. Verify previews and snapshots.

## 3. The whole change together

- [ ] 3.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [ ] 3.2 `xcodebuild test` on `D6328F65` (iOS 26.0, references recorded) and `958C0DC3` (iOS 18.4). Verify both pass.
- [ ] 3.3 The Release configuration for the simulator. Verify it builds and passes the stub check.

## 4. See it

- [ ] 4.1 Walk on iPhone 17 Pro `E426A46D` on the local stack: an activity's page with charts, its enlarged map, its gallery, the combine picker, nested efforts. Verify by noting what was seen; record what couldn't be under "Not verified".

## 5. Docs

- [ ] 5.1 The roadmap: a Phases item 4 bullet, findings, open items. Verify `openspec validate ios-native-activity-rest --strict`.
- [ ] 5.2 `.agents/rules/compose-screens.md` and `adaptive-ui.md` clauses. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh`.
- [ ] 5.3 A "Not verified" section.
