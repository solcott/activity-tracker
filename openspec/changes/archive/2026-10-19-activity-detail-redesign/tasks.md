# Tasks

Each group leaves `./gradlew check` green and is its own small commit. Run Gradle through the `gradle-runner` agent. Check UI by hand in the desktop app (`hot-reload.md`) and, where a task says so, on the iOS simulator and Android. Hand checks use the samples in `build/workout-fixtures` (written by the command in `activity-files.md`), imported into the local stack; "the MTB sample" is its mountain bike ride with power.

## 1. Adaptive groundwork

- [x] 1.1 Add `AppCard` to `ui/adaptive` as an `expect`/`actual`, following `AppCheckbox`'s split (design.md Decision 6): an outlined Material `Surface` in `nonAppleMain`, and an inset grouped section on the grouped background in `iosMain`. Verify: `:composeApp:compileKotlinJvm`, `:webApp:compileKotlinWasmJs` and the iOS framework (`:composeApp:linkDebugFrameworkIosSimulatorArm64`) compile.
- [x] 1.2 Add a `WindowWidth` helper (compact < 600dp, medium < 840dp, expanded) read from `BoxWithConstraints` at a screen root, with a JVM unit test of the thresholds at 599, 600, 839 and 840dp. Verify: the test passes.
- [x] 1.3 Document `AppCard` and `WindowWidth` in `.agents/rules/adaptive-ui.md` (wrapper list and a line on laying screens out by width, not platform). Verify: `scripts/check-agent-sync.sh` passes.

## 2. Activity page content model

- [x] 2.1 Add the page content model to `:app-screens` (`@Immutable`): hero figures, metric cards (kind, headline figures, chart series, extra rows, zones/curve/gears, visibility mark), the training load card, the stats groups and the avg/max rows, with label keys rather than strings. Verify: `./gradlew :app-screens:composeStabilityCheck` passes in strict mode.
- [x] 2.2 Build the hero in `:app-presenters` from an activity and its charts, by sport family as in spec "Hero figures by sport" and "Hero figures for other sports", skipping figures not served and those `feed/activity-stats` leaves out. Add JVM tests for MTB with power, a ride with hidden power, run, swim, dive, ski, strength with sets, and a sport outside the families. Verify: the tests pass.
- [x] 2.3 Build the metric cards (one per `DetailSection` with data, Device excluded), the training load card, the stats groups (Timing, Distance & speed, Heart rate, Power, Cadence, Elevation, Work & energy, Temperature, Counts, Dive, Device) and the avg/max rows, each only from what the viewer was served. Mark the owner's narrower categories on their card, and Start time and Map where they show. Add JVM tests for a fully admitted ride, a viewer without heart rate or zones, the owner's marks, empty groups left out and a no-streams manual run. Verify: the tests pass.
- [x] 2.4 Build the same content from an `ActivityDraft` for the import preview: turn the draft's streams, zones and training load into charts with `ChartsBuilder` off the main thread, show every category, and mark categories by the chosen visibility. Add a JVM test with an inline FIT fixture of a ride with power (as the import tests build theirs), comparing its preview content with the content its published activity gets from the page builder. Verify: the test passes.

## 3. Shared page UI

- [x] 3.1 Add `HeroRow` and a label/value row to `composeApp`: values large with smaller units (`AnnotatedString` spans), and labels wrapping without ellipsis. Verify: in the desktop app at 360dp wide, a hero with "Normalized power" and "Elevation gain" shows every label in full.
- [x] 3.2 Add `MetricCard` on `AppCard`: title with visibility badge, headline figures, the chart with a dashed average line and the elevation series filled faintly behind it (omitted without elevation), the extra rows, then the existing zones, power curve and gears pieces from `ActivityChartsUi`. Verify: compiles on every target, and `composeStabilityCheck` passes.
- [x] 3.3 Add the training load card, `StatsList` (grouped rows) and `AvgMaxTable`. Verify: compiles on every target, and `composeStabilityCheck` passes.
- [x] 3.4 Measure the elevation backdrop on the longest sample in `build/workout-fixtures` in the desktop app and an Android release build. If a chart takes over 16ms to draw, drop the backdrop below 600dp and note it in design.md. Verify: the measurement and the decision are recorded in design.md.

## 4. Activity page

- [x] 4.1 Put the content model on `ActivityDetailState`, and remove `expanded`, `anyExpanded`, `ToggleSection`, `ToggleAll` and their presenter tests. Verify: `./gradlew :app-presenters:jvmTest` passes.
- [x] 4.2 Rebuild `ActivityDetailUi` from the shared pieces in the three width layouts of spec "Layout by window width": map, hero and avg/max table in a scrolling side pane from 840dp, with comments and likes kept. Delete `StatTiles`, `SectionsHeader`, `SectionCard` and the owner's chip row. Verify: in the desktop app at 500, 700 and 1280dp, the MTB sample ride matches each layout, and the map stays in view while the cards scroll at 1280dp.
- [x] 4.3 Verify on the iOS simulator and an Android emulator: the MTB sample ride shows inset grouped sections on iOS and outlined cards on Android, every label in full, and an owner's Only me heart rate marked on its card only.
- [x] 4.4 Verify a dive, a pool swim with lengths, a multisport with legs and a strength workout with sets each show their hero, their cards (depth/oxygen/tank pressure, lengths, legs, sets) and no empty card, in the desktop app.

## 5. Import preview

- [x] 5.1 Put the draft-built content on `ImportActivityState`, rebuilt when the type or visibility changes, and drop the old four-stat `ImportPreview` fields it replaces. Update `ImportActivityPresenterTest` for a type change that switches the hero from avg speed to avg pace. Verify: `./gradlew :app-presenters:jvmTest` passes.
- [x] 5.2 Rebuild `ImportActivityUi` as the read-only page content plus a save panel (type, title and details, visibility, threshold changes): beside the preview from 840dp, after it below. Keep the duplicate and time-zone notices above the hero and the dive batch form as it is. Remove `RideDetails` if nothing else uses it. Verify: in the desktop app at 1200 and 500dp, picking the sample MTB FIT shows its hero, cards and stats with the panel placed as specified.
- [x] 5.3 Place Save by platform below 840dp: in the navigation bar's trailing slot on iOS (`AppTopBar` action), and in a bottom action bar on Android, desktop and web. Verify: on the iOS simulator Save is in the navigation bar, and on an Android emulator it's in a bottom bar that stays visible while the preview scrolls.

## 6. Integration

- [x] 6.1 Run `./gradlew check` and the iOS `xcodebuild` from AGENTS.md, and verify both pass. Then check by hand in the web app (`:webApp:wasmJsBrowserDevelopmentRun`) at 400 and 1280px that the page and import preview follow the same layouts as desktop.

## Workflow follow-up

- Archive the change with `/opsx:archive` once it is implemented and reviewed.
