# Tasks

## 1. Kotlin rules (no visible change)

- [x] 1.1 `StreamUnits`, `DiveText`, `LabelText.sensor` and `FigureTexts` in `:app-common-api`; `PageFigures`, `ChartUnits`, `DiveFormat`, `sensorLabel`, `DetailSectionsUi` resolve them (`TrainingLoadFigures` folded in). Verify `FigureTextsTest`, `PageFiguresTest`, `SharedTextNamesTest` and `:app-common-ui:jvmTest`.
- [x] 1.2 `LineChart.average` (an inverted line is plotted negated on `ChartAxis.of(inverted = true)`, as the Vico chart does); `CardLines` (lines, detail, description); `ZoneRows`; `LapTableTexts`; the Compose chart header, zones and lap table ask them. Verify `CardLinesTest`, `LapTableUiTest`, `SharedTextNamesTest`.
- [x] 1.3 `ActivityPageTexts` (`:feature-detail-api`) and `RecordingTexts` (`:app-common-api`, shared with import); the Compose page asks them. Verify `ActivityPageTextsTest` and the detail UI tests.
- [x] 1.4 `IosApp.writeExport` (`:iosShell`); `ActivityDetailSamples.variants` (owner's ride with cards, charts, zones, laps and stats; a run's pace card; hidden; likes shown; combine banner). Verify `ContentLoadingConformanceTest`.

## 2. The native views

- [x] 2.1 `LineChartView` draws `average` (an inverted line comes plotted negated); a gallery entry (`activityCharts`); a sideways scrub holds iOS 26's swipe back. Verify `ChartTests`, the design snapshots and the activity charts snapshot.
- [x] 2.2 Type aliases and samples (appended blocks). Verify the app builds.
- [x] 2.3 `ActivityPage`, `ActivityPageCards`, `ActivityPageMenu`. Verify previews and `ActivityPageSnapshotTests` light and dark, phone and iPad, and the laps and elevation cards on their own, each image looked at.
- [x] 2.4 The registry entry. Verify `ScreenRegistryTests` names `ActivityDetailScreen` as native.

## 3. The whole change together

- [x] 3.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [x] 3.2 The Swift tests on cloned iOS 26.0 and 18.4 simulators. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [x] 3.3 The Release configuration for the simulator. Verify it builds and passes the stub check.

## 4. See it

- [x] 4.1 On the signed-in iPad Air `3F116204` on the local stack: activities with a map, charts and comments, the side pane, the menus, light and dark; anything written put back. Verify by noting what was seen; record what the stack lacks under "Not verified".

## 5. Docs

- [x] 5.1 The roadmap: a Phases item 4 bullet, "Phase 4 findings (activity page)", the open items. Verify `openspec validate ios-native-activity-page --strict`.
- [x] 5.2 `.agents/rules/compose-screens.md` and `adaptive-ui.md`. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh`.
- [x] 5.3 A "Not verified" section listing what wasn't seen and why.

## Walk (iPad Air 11-inch `3F116204`, iOS 26.0, signed in as alice on the local stack through the named tunnel; this change's Debug build)

- **4.1.** "Mutuals export check" (the owner's run with a comment): the native header with the Mutuals badge, the combine banner
  ("Same activity on another device?", Combine and Don't combine), the hero, "0 likes", Add people, the stats list and the comment;
  the bar's menu listed Edit, Combine with…, Add a recording… and Delete with their icons. Edit opened the shared edit page, left
  without saving. bob's "Moderation smoke (Public)": Like (0), a photo tile, and the bar's menu with Report activity only, which
  opened the native report sheet ("Report bob"), cancelled. "Afternoon Mountain Bike Ride" (Nov 3): the live MapKit route with the
  expand button, the hero in imperial units, Leave group and Add people, and the nested segment efforts (1st, PR, times), indented;
  turned to landscape (1,180 points) the map, header and hero sat in the 400-point pane beside the efforts. "Evening Mountain Bike
  Ride" (combined from a Garmin Edge 840 and a Fenix 7s, the route's gap dashed): training load (stamina "100% → 91% · min 81%",
  sweat loss in fl oz), and native charts for heart rate (its recording gap broken, the average dashed), power (with normalized
  power and Best 20 min power), speed, cadence and respiration, Set thresholds under heart rate and power, the gears and legs cards'
  headlines, the laps table (7 laps in miles, mph, bpm, W and ft), the nested efforts, every stats group with sensors and gear rows,
  and the comment field. A gear row opened the native gear page. Dark mode turned the page dark.
- **Found and fixed:** a rightward drag across a chart started iOS 26's swipe back from anywhere over the page instead of the readout
  (a leftward one scrubbed). `ChartScrubGesture` now has every non-scroll pan wait for it to fail; after the fix a rightward drag
  showed "36:13 · 137 bpm" on the Oct 18 ride and a vertical drag from the chart scrolled the page. The segment page's chart shares
  the fix.
- **Put back:** nothing was written (Edit, the delete menu and the report sheet were left without saving); appearance light, the Feed
  tab chosen, upright.

## Not verified

- Delete's and Split's confirmations, the export sheet and the share sheet, Ungroup, Hide start and end, Create segment and Create
  dive site on a device: walking them would write or delete alice's data. The snapshots draw the menu, and `ActivityPageTextsTest`
  holds what each item sends.
- The combine prompt (an alert right after a save), likes shown with likers, someone else's like and its error, a hidden activity, a
  narrower category's lock, time-in-zone bars and a pace chart on a device: alice has no zones set, no likes, no runs with streams
  and no hidden or narrower activity on the stack. The snapshots draw each.
- The Nov 3 ride's charts: its streams failed to fetch on the AppView (`activity_stream_fetch_failures`), so it showed no cards, as
  the shared page would.
- iOS 18 signed in, an iPhone, VoiceOver and the largest text sizes.

## Checks

- `./gradlew check -Pkotlin.incremental=false` passed (the Android screenshot references unchanged); the Release simulator build
  passed the stub check (after importing `FeatureDetailApi` in `KotlinTypes.swift`, which Debug reached through the samples);
  `xcodebuild test` passed 387 tests on iOS 26.0 (`C3536D25`, a new iPhone 17 simulator, snapshots compared) and on iOS 18.4
  (`56DEFB84`, a new iPhone 16 Pro simulator, snapshots skipped).
