# Tasks

## 1. Kotlin rules (no visible change)

- [x] 1.1 `LineChart` gains `backdrop`, `others` (`ChartLine`), `marks` (`ChartMark`) and `fillAbove`, all defaulted, with typed getters. Verify `LineChartTest`.
- [x] 1.2 `CardLines.of` builds every chart: elevation behind effort lines (readout with elevation), a pair's right leg, every tank on one chart, the depth with ceiling, switches and alerts, assist steps; `CardLines.powerCurve` and `CardLines.gears`. The Compose card asks their words. Verify `CardLinesTest` and `:app-common-ui:jvmTest`.
- [x] 1.3 `StructureTableTexts` (lengths, sets, legs) as `DetailTable`s with a wide column; the Compose tables draw them. Verify a new `StructureTableTextsTest` and the Compose table tests.
- [x] 1.4 `CombinationTexts` and the source caption; the Compose sheet, data sources and caption ask them. Verify `CombinationTextsTest` and the detail UI tests.
- [x] 1.5 `RouteMapTexts`, `MediaGalleryTexts`, `CombinePickerTexts`; the Compose screens ask them. Verify their tests.
- [x] 1.6 Samples: activity detail variants with a dive, a pair, tanks, a pool swim, a workout, multisport, a combined ride with captions and nested efforts; route map, gallery and picker samples. Verify `ContentLoadingConformanceTest`.

## 2. The native views

- [x] 2.1 `LineChartView` draws the backdrop, others, marks and fill above; gallery entries. Verify the design snapshots, old references unchanged.
- [x] 2.2 The page: every chart per card, the structure tables, source captions, Edit and `EditCombinationSheet`, guides and span bars. Verify previews and snapshots, each image looked at.
- [x] 2.3 `ActivityRouteMapView`, `MediaGalleryView`, `CombinePickerView`; type aliases, samples, registry entries and `ScreenRegistryTests`. Verify previews and snapshots.

## 3. The whole change together

- [x] 3.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [x] 3.2 `xcodebuild test` on `D6328F65` (iOS 26.0, references recorded) and `958C0DC3` (iOS 18.4). Verify both pass.
- [x] 3.3 The Release configuration for the simulator. Verify it builds and passes the stub check.

## 4. See it

- [x] 4.1 Walk on iPhone 17 Pro `E426A46D` on the local stack: an activity's page with charts, its enlarged map, its gallery, the combine picker, nested efforts. Verify by noting what was seen; record what couldn't be under "Not verified".

## 5. Docs

- [x] 5.1 The roadmap: a Phases item 4 bullet, findings, open items. Verify `openspec validate ios-native-activity-rest --strict`.
- [x] 5.2 `.agents/rules/compose-screens.md` and `adaptive-ui.md` clauses. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh`.
- [x] 5.3 A "Not verified" section.

## Walk (iPhone 17 Pro `E426A46D`, iOS 26, signed in as alice on the local stack through the named tunnel; this change's Debug build)

- **The media gallery** from bob's "Moderation smoke (Public)", from both the feed card's photo and the page's: black and full screen with
  the tabs hidden, the photo fitted, Close and the photo menu (one photo, so no counter). A tap zoomed it (the status bar hid, the photo
  alone in the zooming scroll view) and a tap left zoom; the menu's "Report photo" opened the native report sheet ("Report bob"), which
  was cancelled without sending. Close went back to the page with the tabs restored.
- **Found and fixed:** the zoomed photo showed black. The image view was sized in `updateUIView`, before the scroll view had bounds; the
  scroll view now sizes it in `layoutSubviews`. Seen working after the fix.
- **The enlarged route map** from alice's Nov 3 "Afternoon Mountain Bike Ride": titled with the ride, the route framed on the live MapKit
  map running under the home indicator, the tabs hidden, Recenter; a drag panned it.
- **Nested efforts** on the same ride: a dozen groups of Corner Canyon and Golden Spoke efforts, each nested one indented under its parent
  with its guide line, and a span bar under each effort of a group showing its share of the group's time.
- **The combine picker** from the same ride's owner menu ("Combine with…"): the explanation and "No other activities within 12 hours of
  this one." Its lines were left-aligned in a centred box; now centred (snapshot rerecorded). Nothing was picked.
- **Put back:** nothing was written (the report sheet cancelled, the picker left without picking); the Feed tab chosen, light, upright.

## Not verified

- On a device: the layered charts (the elevation behind effort lines, a pair's legs, tanks, a dive's marks and ceiling, assist steps, the
  power curve, the gears), the legs, lengths and sets tables, a combined card's caption and the Edit combination sheet. The walk's rides
  with streams didn't load in the time spent (the Nov 3 ride's streams fail to fetch on the AppView, as in `ios-native-activity-page`),
  and the combined Edge 840 + Fenix 7s ride wasn't reached through the feed's test posts. The snapshots draw each
  (`ActivityRestSnapshotTests`: `moreCharts`, `dive`, `structures`, `combined`, `layeredCharts`, `editCombination`), and
  `SampleCardChartsTest` and `CardLinesTest` hold what they chart.
- Removing a member, choosing a source or leaving a kind out in the sheet, and picking a candidate in the combine picker: each writes alice's
  data. `CombinationTextsTest` holds the events they send.
- The pinch's scale and a double tap's zoom: the stack's only photos are flat grey test images, so a zoomed photo looks unchanged. Paging
  between photos, the counter and a labelled photo's cover: no activity on the stack has two photos or a media label; the snapshots draw
  `report` (2 / 2), `blurred` and `takenDown`.
- A combine candidate's row and its spinner on a device: alice has no two activities within 12 hours; the snapshots draw `loaded`,
  `combining` and `combineFailed`.
- iOS 18 signed in, an iPad, VoiceOver and the largest text sizes.

## Checks

- `./gradlew check -Pkotlin.incremental=false` passed (detekt's return-count, parameter-count, function-count and loop findings fixed on the
  way; the Android screenshot references unchanged). The Release simulator build passed the stub check. `xcodebuild test` passed 403 tests
  on iOS 26.0 (`D6328F65`, snapshots compared, new ones recorded and looked at) and on iOS 18.4 (`958C0DC3`, snapshots skipped). An 18.4
  run needs its own build: one built for the 26.0 destination lacks `libswiftCompatibilitySpan` and stops before the first test.
