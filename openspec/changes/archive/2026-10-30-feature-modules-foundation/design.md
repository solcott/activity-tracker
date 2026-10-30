# Design

## Context

See proposal.md for the motivation. Current state:
- `:app-screens` holds every Screen, State and Event. Presenters and UIs both depend on it and never on each other.
- `:app-presenters` holds the presenters.
- `composeApp` holds:
  - the UIs;
  - `ui/`, which mixes the design system (`adaptive`, `theme`, `chart`, `preview`) with domain widgets;
  - 1649 strings in `composeResources`, read through `io.github.solcott.activitytracker.composeApp.resources.Res`;
  - the Metro graphs: `di/AppGraph.kt`, `di/SessionGraph.kt`, `di/CircuitBindings.kt`.
- `:app-fixtures` holds the sample States.

Wiring:
- Circuit codegen and Metro contribute presenter factories, UI factories, sub-circuit factories and serializer registrations into the `AppScope` and `SessionScope` multibinding sets. `CircuitBindings` builds the `Circuit`s from those sets.
- A graph sees every contribution on its compile classpath, so the app module only needs each feature module as a dependency.

Cross-feature imports today fall into two groups:
- **Screen keys**: navigation targets, and sub-circuit screens embedded by key.
- **Shared building blocks**: `uiErrorOf` (22 presenter imports), `rememberRevealed`, `rememberReporting`, `rememberActivityListEventSink`, the visibility contracts, and the detail-page widgets record reuses (16 UI imports).

`ui/preview/AppPreview.kt` hard-codes the UIs of three embeddable sub-screens: `FeedFilterUi`, `ReportUi` and `ProposeSegmentNameUi`.

## Goals / Non-Goals

**Goals:**
- Put every shared building block in one shared module per layer, so a moved feature's presenter and UI depend on that feature's API and the shared modules. They depend on other features only through those features' API modules.
- Enforce the module rules in `check`, not only by convention.
- Prove the pattern end to end with athlete search, including presenter tests, UI tests, screenshot tests and both conformance tests.
- Keep every unmoved feature compiling where it is, with no copies of shared code.

**Non-Goals:**
- Moving any feature other than search.
- Splitting `:core-data` or `:app-fixtures`.
- Splitting strings per feature.
- Renaming packages. Classes keep their packages and only change module.
- Changing behavior or visuals.

## Decisions

### Module layout and names
The existing `settings.gradle.kts` loop maps `dir` to `":" + dir.replace('/', '-')`. New directories are added to that loop:
- `ui/resources`, `ui/designsystem`
- `app/common/api`, `app/common/presenter`, `app/common/ui`
- `feature/search/api`, `feature/search/presenter`, `feature/search/ui`

They map to `:ui-resources`, `:ui-designsystem`, `:app-common-api`, and so on.

The `-api`, `-presenter` and `-ui` suffixes are what the dependency check reads. Alternative considered: Gradle project properties or extensions to mark a module's kind. Rejected because the name is visible everywhere, including in `settings` and in error messages.

### Shared module dependency graph
```
:ui-resources
:ui-designsystem       --> :ui-resources, :core-data (number formatting in charts)
:app-common-api        --> :core-data
:app-common-presenter  --> :app-common-api, :app-screens (until empty)
:app-common-ui         --> :ui-designsystem, :app-common-api, :app-screens (until empty)
:feature-x-api         --> :app-common-api, other :feature-*-api
:feature-x-presenter   --> :feature-x-api, :app-common-presenter, other :feature-*-api
:feature-x-ui          --> :feature-x-api, :app-common-ui
:app-screens           --> :app-common-api            (until empty)
:app-presenters        --> :app-screens, :app-common-presenter
composeApp             --> :app-common-ui, :app-screens, :app-presenters, every :feature-*-presenter and -ui
:app-fixtures          --> :app-screens, every :feature-*-api
```
- `:ui-designsystem` depends on `:core-data` only for `UnitSystem`. Moving units into a smaller module is left for a later change.
- `-api`, `:app-common-api` and `:app-screens` expose their contracts with `api(...)`, the way `:app-screens` does today. A State's field types therefore reach both presenter and UI.
- Only self-contained shared contracts move to `:app-common-api` (`UiError`, `ShownRoute`, `RouteEnds`, `VisibilityFields`, `ActivityListEvent`, `Revealed`, `MediaItem`, `ActivityDetailsFields`). The shared presenter and UI code also uses types that belong to one feature and sit in files with that feature's screens (`FigureValue` and `GasKind` in detail, the import previews in record, `ReportScreen`, the screens the activity-list sink opens), so `:app-common-presenter` and `:app-common-ui` depend on `:app-screens` for now. That is legal under the module rules (an API module), and each such type leaves with its feature's `-api` module. Pulling them apart now would split feature contracts across modules for no gain.

### Where each `ui/` file goes
The rule is mechanical, worked out from each file's imports and the same-package declarations it references (the `ui` package needs no imports between its files):
- A file that uses no app or lexicon type, nor any file outside the set, and isn't map or photo code goes to `:ui-designsystem`: `adaptive/`, `theme/`, `chart/`, `preview/PreviewResources`, `MenuField`, `ReadOnlyTextFieldState`, `HourClock`, `TestTags`.
- Every other file goes to `:app-common-ui`, including the loading pieces (`ContentStateLayout`, `ErrorText`, `PagedList`, which render a `UiError`), the `Local*` providers in `Format.kt`, the map and route-image code (MapLibre), photo compression, and `AppPreview` (it uses `RoutePreview` and the `Local*` providers).
- The detail-page pieces record's import preview uses go to `:app-common-ui`: `StatsList`, `MetricCard`, `HeroRow`, `DetailSection`, `ITEM_GAP`, `AvgMaxTable`, `FigureValue`, `StartLine`, `TrainingLoadCard`. So do the gear labels (`gearTypeLabel`, `gearKindLabel`, `deviceName`) and the settings date and zone helpers used by segment.
- Platform source sets move with the expect or interface they implement. The new modules get the same intermediate source-set hierarchy (`nonWebMain`, `nonAppleMain` and so on) from the existing convention plugins.

A file whose move would make `:app-common-ui` import a feature UI stays in `composeApp` for now and gets a note in tasks. No such file is known apart from AppPreview's registry.

### AppPreview's sub-screen registry
`AppPreview` moves to `:app-common-ui` with a new `subUis: PreviewSubUis` parameter, which maps a sub-screen to its UI and default state and defaults to `PreviewSubUis.None`. `composeApp` holds `SessionPreviewSubUis`, which registers `FeedFilterUi`, `ReportUi` and `ProposeSegmentNameUi`. composeApp's UI tests and androidApp's `ScreenPreview`/`ComponentPreview` base pass it. Tests in `ui/` that render feature UIs (`SeamsUiTest`, `TestTagsUiTest`, `AppPreviewUiTest`) stay in composeApp's jvmTest.

This removes the shared UI's only dependency on feature UIs. Alternative considered: a Metro-style multibinding for preview UIs. Rejected because previews run without a graph (dev/ui-test-harness).

### Resources keep their `Res` package
`:ui-resources` sets `packageOfResClass = "io.github.solcott.activitytracker.composeApp.resources"` and `publicResClass = true`, overriding the convention's derived package. None of the 94 files that read strings needs an import change, and the moves stay pure relocations. Renaming the package is a single follow-up sed.

Alternative considered: per-feature resources. Rejected in exploration, because most strings are shared and the split is error-prone.

### Convention plugins
New classes in `build-logic/convention/src/main/kotlin`, registered in its `gradlePlugin` block:
- `FeatureApiConventionPlugin` (`activitytracker.feature.api`) applies:
  - `activitytracker.kmp.library`, the Compose compiler, serialization and Metro;
  - `composeStability { strictClasses = true }`;
  - the SubCircuit opt-in;
  - `api(:app-common-api)`, circuit runtime and serialization, `circuitx.subcircuit`, `compose.foundation`, `dataresult.uistate`, collections.immutable and `kotlinx.io.bytestring`.

  It mirrors `app/screens/build.gradle.kts`.
- `FeaturePresenterConventionPlugin` (`activitytracker.feature.presenter`) applies `activitytracker.kmp.library`, the Compose compiler, Metro and the SubCircuit opt-in. It adds:
  - `api` on the sibling `-api` module, resolved from its own path;
  - `implementation(:app-common-presenter)`, circuit codegen annotations, retained, runtime and presenter, compose runtime and saveable, and `dataresult.uistate.circuit`;
  - commonTest: `circuit.test`, `circuitx.subcircuit.test` and `ktor.client.mock`.

  It mirrors `app/presenters/build.gradle.kts`.
- `FeatureUiConventionPlugin` (`activitytracker.feature.ui`) applies `activitytracker.kmp.compose`, Metro and the SubCircuit opt-in, with `composeUiTests { jvm = true }`. It adds:
  - the sibling `-api` module;
  - `:app-common-ui`, `:ui-designsystem`, `:ui-resources`, circuit foundation and `circuitx.subcircuit`;
  - jvmTest: `:app-fixtures`.

The generic presenter test helpers (`PresenterTest`, `AwaitUntil`) move from `:app-presenters`' jvmTest to the `jvmMain` of a `:app-common-presenter-testing` module (KMP has no `testFixtures`), so the search presenter test can reuse them. This is one extra module. `SessionTestFixture` stays in `:app-presenters`' jvmTest: it wires record's presenter-side choices and is only used there. Alternative considered: copying the helpers. Rejected as duplication.

### Module dependency check
`ModuleBoundariesPlugin` is applied from `QualityConventionPlugin`, so every project gets it. It registers `checkModuleBoundaries`, which `check` depends on. The task reads each `project(...)` dependency of every Kotlin source set's `api` and `implementation` configurations (declared dependencies, not the resolved graph, so it's cheap and configuration-cache safe). It fails on these rules:
- A `-presenter` or `:app-common-presenter` module depends on a `-ui`, `:app-common-ui` or `:ui-*` module.
- A `-ui` or `:app-common-ui` module depends on a `-presenter` or `:app-common-presenter` module.
- A `:feature-a-*` module depends on a `:feature-b-*` module that isn't `-api`.
- A `-api` or `:app-common-api` module depends on anything other than `-api`, `:app-common-api`, `:core-*` or `:app-screens`.

`:app-screens` is allowed only while it exists. Depending on it is legal, because unmoved navigation targets live there.

Transitive violations are caught because every module along the path is itself checked. The error names both modules and the rule.

Alternative considered: a detekt rule on imports. Rejected because imports can't see the module graph, and Gradle can fail before compiling.

### Conformance tests scan module lists
- `ScreenshotCoverageTest` walks a list of source roots: `composeApp/src/commonMain/kotlin` plus every `feature/*/ui/src/commonMain/kotlin`, found by globbing the root directory.
- `ContentLoadingConformanceTest` collects class names from the code source of `:app-screens` and of each `-api` module on composeApp's jvmTest classpath.

composeApp's jvmTest adds the feature modules as dependencies. The `jvmTest` task's `inputs.dir` gains the `feature/` directory.

### Search pilot
- `AthleteSearchScreen`, `AthleteSearchState` and `AthleteSearchEvent` move to `:feature-search-api`.
- `AthleteSearchPresenter` moves to `:feature-search-presenter`. It navigates to `ProfileScreen`, so the module temporarily depends on `:app-screens`. That edge goes away when profile moves.
- `AthleteSearchUi` moves to `:feature-search-ui`.
- `AthleteSearchPresenterTest` moves to `:feature-search-presenter`'s jvmTest.
- `AthleteSearchSamples` stays in `:app-fixtures`.
- composeApp's `TopLevelDestination` references the Screen through the API module. androidApp's `AthleteSearchScreenshots` adds `:feature-search-ui`.

## Risks / Trade-offs

- **[Risk]** Each KMP module compiles for 5 targets, so CI time and memory grow. → Measure before and after on the GitHub runner, recording the `check` duration in tasks. If the full rollout looks unaffordable, fall back to api + impl per feature. The dependency check already treats presenter and UI as separate kinds, so it would need a rule change only.
- **[Risk]** Metro or Circuit contributions might be missed when they come from a transitive module. → composeApp depends on every feature presenter and UI module directly. Verify on all four platforms that search opens and navigates to a profile.
- **[Risk]** iOS framework linking with more modules. → `ComposeApp` stays the only framework and its `export`s are unchanged. Run the documented `xcodebuild`.
- **[Trade-off]** `:ui-designsystem` depends on `:core-data` for units. → Accepted for now and noted as a follow-up.
- **[Trade-off]** The `Res` package keeps the `composeApp` name inside `:ui-resources`. → This avoids touching 94 files during a move. The rename can follow in its own change.
- **[Risk]** Detekt baselines are per module, so moved findings resurface. → Regenerate the baselines for the new modules and prune the moved entries from composeApp's baseline in the same commit.

## Migration Plan

Land in small commits, each of which passes `./gradlew check`:
1. Convention plugins and the module check. The check has nothing to flag yet.
2. `:ui-resources`.
3. `:ui-designsystem`, with the AppPreview registry split.
4. `:app-common-api`.
5. `:app-common-presenter`, plus the testing module.
6. `:app-common-ui`.
7. Search pilot.
8. Conformance tests.
9. Docs.

Rollback is a revert of the commit range. Nothing is persisted or published.

## Open Questions

- The CI time budget for the full rollout, i.e. how much slower `check` may get. It is answered by the measurements this change takes, and it only affects later changes.
