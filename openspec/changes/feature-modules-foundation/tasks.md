# Tasks

## 1. Baseline

- [x] 1.1 Record a baseline for `./gradlew check` on `main` before any move: wall time and peak Gradle daemon memory, locally and from the latest GitHub Actions run. Write the numbers into this file under task 10.2. Verify: the numbers are recorded.

Baseline (task 1.1), `main` at 8eb2658d:
- Local (10-core Apple silicon, 32 GB), `./gradlew check --rerun-tasks --continue` with fresh daemons: 15m 17s (921 s wall), 1006 tasks, peak RSS of the new Gradle, Kotlin and worker JVMs together 6435 MB.
- GitHub Actions (`ubuntu-26.04`, 2 workers, 4 GB heap): the `Check` step took 23m 27s cold (run 40958572200, after the iOS klib change emptied the cache) and 1m 13s / 2m 52s warm (runs 40962485842, 40965571694). The runner reports no memory figure.

After this change (task 10.2), same machine and command: 10m 17s (618 s wall), 1843 tasks (+83%, from the 9 new modules' per-target tasks), peak RSS 4936 MB. The wall time is below the baseline's, which ran in a brand-new checkout, so treat it as "no measurable slowdown locally" rather than a speed-up. CI not measured yet: it needs this branch pushed.

## 2. Convention plugins and module check

- [x] 2.1 Add `FeatureApiConventionPlugin` (`activitytracker.feature.api`) to `build-logic/convention`, mirroring `app/screens/build.gradle.kts`: strict stability, SubCircuit opt-in, `api(:app-common-api)` and the contract libraries. Register it in `gradlePlugin`. Verify: a TestKit test in `build-logic/convention/src/test/kotlin` applies it to a fixture project and compiles a `Screen`.
- [x] 2.2 Add `FeaturePresenterConventionPlugin` (`activitytracker.feature.presenter`), mirroring `app/presenters/build.gradle.kts`. It adds the sibling `-api` module (resolved from its own path), `:app-common-presenter`, `:app-common-presenter-testing` for jvmTest, and the circuit test libraries. Verify: a TestKit test checks the sibling `-api` dependency for `:feature-x-presenter`.
- [x] 2.3 Add `FeatureUiConventionPlugin` (`activitytracker.feature.ui`) on top of `activitytracker.kmp.compose`. It sets `composeUiTests { jvm = true }` and adds the sibling `-api`, `:app-common-ui`, `:ui-designsystem`, `:ui-resources`, circuit foundation and subcircuit, plus `:app-fixtures` for jvmTest. Verify: a TestKit test checks the dependencies and the jvmTest UI test library.
- [x] 2.4 Add `ModuleBoundariesPlugin`, applied from `QualityConventionPlugin`. It registers `checkModuleBoundaries`, which `check` depends on, and enforces the four rules in design.md over declared `project(...)` dependencies of every Kotlin source set. Errors name both modules and the rule. Verify: TestKit tests fail for each forbidden edge (presenter→ui, ui→presenter, feature→other feature's non-api, api→presenter), pass for allowed edges, and run with the configuration cache on (`./gradlew -p build-logic :convention:test`).
- [x] 2.5 Document the three plugins and the check in `.agents/rules/build-logic.md`. Verify: `scripts/check-agent-sync.sh` passes.

## 3. `:ui-resources`

- [x] 3.1 Create `ui/resources` (`:ui-resources`, `activitytracker.kmp.compose`) and add it to the `settings.gradle.kts` list. Move `composeApp/src/commonMain/composeResources` into it with `publicResClass = true` and `packageOfResClass = "io.github.solcott.activitytracker.composeApp.resources"`, so no imports change. Point `composeApp`, `desktopApp` (window title) and `androidApp` at it. Verify: `./gradlew :composeApp:jvmTest :desktopApp:compileKotlinJvm` passes and `:androidApp:testDebugScreenshotTestDefaultTestSuite` matches the references unchanged.

## 4. `:ui-designsystem`

- [x] 4.1 Create `ui/designsystem` (`:ui-designsystem`, kmp.compose, depending on `:ui-resources` and `:core-data`). Move the `ui/` files that use no app or lexicon type and no file outside the set (design.md, "Where each `ui/` file goes"): `adaptive/`, `theme/`, `chart/`, `preview/PreviewResources`, `MenuField`, `ReadOnlyTextFieldState`, `HourClock`, `TestTags`. Move each file's platform source-set counterparts with it, and move the Calf, Haze and cupertino-icons dependencies. Verify: `./gradlew :ui-designsystem:compileKotlinJvm :ui-designsystem:compileKotlinIosSimulatorArm64 :ui-designsystem:compileKotlinWasmJs` passes.
- [x] 4.2 Give `AppPreview` (which moves to `:app-common-ui` in 7.1) a `subUis: PreviewSubUis` parameter that defaults to none. Add a `SessionPreviewSubUis` registry in composeApp holding `FeedFilterUi`, `ReportUi` and `ProposeSegmentNameUi`, and pass it from composeApp's jvmTest and androidApp's screenshot base wherever those embeds render. Verify: `./gradlew :composeApp:jvmTest` passes and the screenshot tests match unchanged.
- [x] 4.3 Move the design-system tests (`ui/adaptive`, `ui/chart`) with their sources into `:ui-designsystem`, and give the module a detekt baseline if it has findings (none turned up, so no baseline was needed). Verify: `./gradlew :ui-designsystem:jvmTest :ui-designsystem:detekt :composeApp:detekt` passes.
- [x] 4.4 Update `.agents/rules/adaptive-ui.md` and `hot-reload.md` wherever they name paths in `composeApp/.../ui`. Verify: no stale path matches `grep -rn "composeApp/src/commonMain/kotlin/.*/ui/adaptive" .agents`.

## 5. `:app-common-api`

- [x] 5.1 Create `app/common/api` (`:app-common-api`, strict stability, `api(:core-data)`). Move into it:
  - `UiError`, `ShownRoute` and `RouteEnds` from `:app-screens`;
  - the `visibility` contracts;
  - `Revealed`;
  - `ActivityListEvent`, `MediaItem` and the details-form contract types (`ActivityDetailsFields`, `DetailsEvent`, `EditMedia`, `PickedPhoto`).

  `Reporting`/`ReportingEvent` stay with `ReportScreen` in `:app-screens`, and the gear choices are presenter code in `:app-presenters`: both are feature-owned (design.md).

  `:app-screens` adds `api(:app-common-api)`. Verify: `./gradlew :app-screens:compileKotlinJvm :composeApp:compileKotlinJvm composeStabilityCheck` passes.

## 6. `:app-common-presenter`

- [x] 6.1 Create `app/common/presenter` (`:app-common-presenter`). Move into it `SingleFlight`, `ContentStates`, `FormSeed`, `uiErrorOf` and `NewActivityVisibility`, `RememberRevealed`, `Reporting`, `ActivityListEvents`, `DetailsForm` and `ApprovesFollowers`. `:app-presenters` depends on it. Verify: `./gradlew :app-presenters:jvmTest` passes.
- [x] 6.2 Create `app/common/presenter-testing` (`:app-common-presenter-testing`, jvm source set only). Move into it the generic presenter test helpers (`PresenterTest`, `AwaitUntil`); the domain fixtures (`SessionTestFixture`, `CombineTestBase`, `TestSegments`, `TestZones`, `VisibilityFieldsTesting`) stay in `:app-presenters`' jvmTest, the only tests that use them (design.md). `:app-presenters`' jvmTest uses it. Move `SingleFlightTest` into `:app-common-presenter`. Verify: `./gradlew :app-presenters:jvmTest :app-common-presenter:jvmTest` passes with the same test count as before.

## 7. `:app-common-ui`

- [x] 7.1 Create `app/common/ui` (`:app-common-ui`, depending on `:ui-designsystem`, `:app-common-api` and, for feature-owned contract types, `:app-screens`). Move into it:
  - every remaining file in `ui/`, `AppPreview` and the loading pieces included, for example `ActivityCard`, `ActivityMap`, `RoutePreview`, `ActivityStats`, `Format`, `VisibilityPicker`, `DetailVisibilityPicker`, `Avatar`, `MediaRow`, `SportIcons`, `DiveStats`, `Moderation`, the photo and route-image pieces, and so on, with their platform counterparts;
  - the MapLibre, Coil and FileKit dependencies (zoomimage stays with the gallery in composeApp).

  Verify: `./gradlew :app-common-ui:compileKotlinJvm :app-common-ui:compileKotlinIosSimulatorArm64 :app-common-ui:compileKotlinWasmJs` passes.
- [x] 7.2 Move the cross-feature UI pieces into `:app-common-ui`:
  - the activity-page rendering record's import preview shares with detail: `PageUi`, `PageCardsUi`, `DetailSectionsUi`, `StartTimeText`, `PageFigures`, `ActivityChartsUi`, `ChartUnits`, `DataSourcesUi`, `LapTableUi`, `RecordingsUi`, `TrainingLoadFigures`, `WorkoutTablesUi`, and the combine labels (`combineCueLabel`, `suggestedActivityLabel`);
  - the gear labels (`GearLabels.kt`) and `GearPicker`, which edit uses;
  - `rememberLocationPermissionRequest` with its platform actuals, which settings uses;
  - the settings helpers detail and segment use (`zoneName`, `longDate`, `shortDate`, `monthName`);
  - `SegmentEffortsUi` (`EffortRow` and its labels) and `ReportingHost`.

  (`FigureValue`, `MetricCard` and `HistoryDate` are contract types in `:app-screens`, so they need no move.)

  Verify: `grep` finds no import of those symbols' old packages from another feature package in `composeApp`, and `./gradlew :composeApp:jvmTest` passes.
- [x] 7.3 Move the shared UI tests (`ActivityStatsTest`, `PhotoCompressionTest`, `RouteCameraTest`, `RouteImageStoreTest`, `RouteSnapshotKeyerTest`, …) into `:app-common-ui`. Keep the tests that render feature UIs (`SeamsUiTest`, `TestTagsUiTest`, `AppPreviewUiTest`) in composeApp. Set up detekt baselines where there are findings. Verify: `./gradlew :app-common-ui:jvmTest :app-common-ui:detekt :composeApp:detekt` passes.
- [x] 7.4 Verify the app is unchanged so far: `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite` matches every reference without updates.

## 8. Search pilot

- [x] 8.1 Create `feature/search/{api,presenter,ui}` with the three convention plugins and add them to `settings.gradle.kts`. Each build script holds only its plugin and any extra dependency, such as `:app-screens` in the presenter for `ProfileScreen`, with a comment that it goes once profile moves. Verify: `./gradlew :feature-search-api:compileKotlinJvm` passes.
- [x] 8.2 Move `AthleteSearchScreen.kt` to `:feature-search-api`. Move `AthleteSearchPresenter.kt` and `AthleteSearchPresenterTest.kt` to `:feature-search-presenter`, with the test reusing `:app-common-presenter-testing`. Move `AthleteSearchUi.kt` to `:feature-search-ui`. Verify: `./gradlew :feature-search-presenter:jvmTest :feature-search-ui:jvmTest` passes, and the presenter test count matches the old `AthleteSearchPresenterTest`.
- [x] 8.3 Wire the apps:
  - composeApp, webApp and desktopApp depend on `:feature-search-presenter` and `:feature-search-ui`, with a comment that this is for Metro aggregation;
  - `:app-fixtures` adds `:feature-search-api`;
  - androidApp's screenshot tests add `:feature-search-ui`.

  Verify: `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite` matches `AthleteSearchScreenshots` unchanged, and `TopLevelDestinationTest` passes.
- [x] 8.4 Run detekt on the three search modules (baselines only if they have findings) and run `checkModuleBoundaries`. Verify: `./gradlew checkModuleBoundaries` passes. A temporary `implementation(projects.featureSearchUi)` in the presenter makes it fail naming both modules; revert it afterwards.

## 9. Conformance tests across modules

- [x] 9.1 Make `ScreenshotCoverageTest` walk `composeApp/src/commonMain/kotlin` plus every `feature/*/ui/src/commonMain/kotlin`, and add `feature/` to composeApp's `jvmTest` `inputs.dir`. Verify: the test passes, and deleting `AthleteSearchScreenshots.kt` locally makes it fail naming `AthleteSearchUi` (restore the file afterwards).
- [x] 9.2 Make `ContentLoadingConformanceTest` collect classes from `:app-screens` and every `-api` module on composeApp's jvmTest classpath. Verify: `./gradlew :composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"` passes, and with search's row removed from the table it fails naming `AthleteSearchState`.

## 10. Docs, scaffolding and integration

- [x] 10.1 Update CLAUDE.md, `.agents/rules/compose-screens.md` and the `new-screen` skill:
  - CLAUDE.md's "Screens span three modules" paragraph describes the shared modules, the feature `api`/`presenter`/`ui` modules, the module check and the transition (unmoved features stay in `:app-screens`, `:app-presenters` and `composeApp`). Its Commands section lists `./gradlew checkModuleBoundaries`.
  - `compose-screens.md` gets the same layout.
  - The `new-screen` skill scaffolds a new feature into `feature/<name>/{api,presenter,ui}`.

  Verify: `scripts/check-agent-sync.sh` passes.
- [ ] 10.2 Measure `./gradlew check` again, locally and on the GitHub runner, and record both next to the task 1.1 baseline. Verify: the numbers are recorded with the percentage change.
- [x] 10.3 Run the full checks: `./gradlew check`, `./gradlew composeStabilityCheck`, `./gradlew -p build-logic :convention:test`, `scripts/check-public-safety.sh`. Verify: all pass.
- [x] 10.4 Run the app on each platform:
  - iOS: the documented `xcodebuild`, then launch in the simulator;
  - `./gradlew :desktopApp:run`;
  - `./gradlew :webApp:wasmJsBrowserDevelopmentRun`;
  - `./gradlew :androidApp:installDebug`.

  Verify: on each one, the search tab opens, a query lists athletes, and tapping one opens their profile.
