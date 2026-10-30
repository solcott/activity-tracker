# Tasks

Each feature follows batches 1 and 2. Steps:
- Create `feature/<name>/{api,presenter,ui}` and add them to `settings.gradle.kts`.
- Move the files with `git mv`, keeping packages.
- Point the modules that used them at the new `-api`, or for app shells at the presenter and UI.
- Run the feature's tests.

After every group, `./gradlew checkModuleBoundaries` passes and the build compiles on JVM.

## 1. Baseline

- [x] 1.1 Record the test counts that will move, from `main`:
  - every class in `:app-presenters:jvmTest` and its total;
  - `StartTimeUiTest`, `RecordUiTest`, `ManualActivityUiTest`, `ImportSensorsUiTest` and the `:composeApp:jvmTest` total;
  - `:app-common-presenter:jvmTest`'s total.

  Verify: the counts are recorded under this task.

Baseline (task 1.1), `main` at 3508b04d, all passing:
- `:app-presenters:jvmTest`: 343 in all.
  - profile: `ProfilePresenterTest` 20, `EditProfilePresenterTest` 15, `FollowListPresenterTest` 8, `FollowRequestsPresenterTest` 10, `BlockListPresenterTest` 6, `ApprovesFollowersTest` 2 (61).
  - record (82):
    - `RecordPresenterTest` 6, `ManualActivityPresenterTest` 11, `ActivityTypeChoiceTest` 2, `GearChoiceTest` 8, `ActivityPagePreviewTest` 1;
    - `ImportActivityPresenterTest` 11, `ImportActivityNotesTest` 2, `ImportCombinePresenterTest` 4, `ImportFileFormatTest` 3, `ImportGearTest` 1, `ImportLocalTimeTest` 1, `ImportLogbookPresenterTest` 16, `ImportPreviewPageTest` 1, `ImportSensorGearTest` 13, `ImportSourceFileTest` 2.
  - detail (175):
    - `ActivityDetailCommentsTest` 20, `ActivityDetailExportTest` 16, `ActivityDetailLoadingTest` 14, `ActivityDetailManageTest` 8, `ActivityDetailModerationTest` 3, `ActivityDetailPresenterTest` 9, `ActivityDetailRepliesTest` 15, `ActivityDetailRouteTest` 10, `ActivityDetailSegmentsTest` 12;
    - `ActivityFileDataChartsTest` 15, `WorkoutStructureChartsTest` 8, `CombineDataSourcesTest` 7, `CombinePresentersTest` 9, `CombineSuggestionsTest` 8, `AwaitIndexedTest` 4, `GroupEffortsTest` 17.
  - to `:app-common-presenter`: `ActivityPageTest` 25.
- `:composeApp:jvmTest`: 47 in all.
  - Moving: `StartTimeUiTest` 13 (detail), and `RecordUiTest` 2, `ManualActivityUiTest` 2, `ImportSensorsUiTest` 2 (record).
  - 28 stay.
- `:app-common-presenter:jvmTest`: 4 (`SingleFlightTest`).

## 2. The activity page

- [x] 2.1 Move these to `:app-common-api`, keeping their packages:
  - `ActivityPageContent.kt` and `ActivityCharts.kt`;
  - `DetailSection`, out of `ActivityDetailScreen.kt` into its own file;
  - `GearTimePreview` and `DepthPreviewPoint`, out of `ImportActivityScreen.kt` into their own file.

  Verify: `./gradlew :app-common-ui:compileKotlinJvm :app-presenters:jvmTest` passes.
- [x] 2.2 Move `ActivityPage.kt`, `ChartsBuilder.kt`, `MetricCards.kt`, `StructureRows.kt` and `DiveFigures.kt` to `:app-common-presenter`, making public what other modules now use. Move `ActivityPageTest` to `:app-common-presenter`'s jvmTest. Verify:
  - `./gradlew :app-common-presenter:jvmTest :app-presenters:jvmTest` passes;
  - the two totals together match the baseline.

  Done: 29 + 318 = 347, the baseline's 4 + 343. `ActivityPage` also called `detailSections` and `poolLength`, which lived at the bottom of `ActivityDetailPresenter.kt` with the stream-kind sets they read. They moved too, into `PageSections.kt` (design.md). `ActivityPage`, `rememberPage` and `ChartsBuilder` became public.

## 3. profile

- [x] 3.1 Create `feature/profile/{api,presenter,ui}`. Move:
  - the five screens to `:feature-profile-api`, which `api`-depends on `:feature-report-api`;
  - the five presenters, their tests and `ApprovesFollowersTest` to `:feature-profile-presenter`;
  - the UIs and `AvatarCompression` (common, nonWeb, wasmJs) to `:feature-profile-ui`.

  Point these at `:feature-profile-api`:
  - the search, segment and settings presenters;
  - `:app-common-presenter`, `:app-presenters` and `:app-fixtures`.

  Wire the shells, `TopLevelDestination` and the screenshot tests. Verify:
  - `./gradlew :feature-profile-presenter:jvmTest` passes with the baseline counts;
  - `:feature-search-presenter:jvmTest :feature-segment-presenter:jvmTest :feature-settings-presenter:jvmTest` passes;
  - `checkModuleBoundaries` passes.

  Done: presenter 61, `:app-presenters` 257 (318 − 61), and search 7, segment 83 and settings 53 pass. The UI module adds FileKit for the avatar picker and its compression off the web. Search and settings no longer depend on `:app-screens`. Segment keeps it for `ActivityDetailScreen`, as does `:app-common-presenter`, until detail moves.

## 4. record

- [x] 4.1 Create `feature/record/{api,presenter,ui}`. Move:
  - the three screens, `NewDevicesFields.kt` and `ActivityTypeField.kt` to `:feature-record-api`;
  - the presenters and helpers to `:feature-record-presenter`, which depends on `:app-screens` for `ActivityDetailScreen` until detail moves. Their tests go with them: design.md's record list, with `RecordChoices.kt`;
  - the UIs to `:feature-record-ui`, and `RecordUiTest`, `ManualActivityUiTest` and `ImportSensorsUiTest` to its jvmTest.

  `RecordScreenRequests` stays in composeApp. Point feed's presenter, `:app-presenters` and `:app-fixtures` at `:feature-record-api`, and wire the shells, `App`, `TopLevelDestination` and the screenshot tests. Verify:
  - `./gradlew :feature-record-presenter:jvmTest :feature-record-ui:jvmTest` passes with the baseline counts;
  - `:feature-feed-presenter:jvmTest` passes;
  - `checkModuleBoundaries` passes.

  Done: presenter 82 and UI 6. `:app-presenters` has 175 and composeApp 41 (47 − 6). Feed's presenter reads the record screens from `:feature-record-api` and no longer depends on `:app-screens`. The UI module adds FileKit for the import file picker. The UI tests use `AppPreview`'s default, since no record UI embeds a sub-screen.

## 5. detail

- [x] 5.1 Create `feature/detail/{api,presenter,ui}`. Move:
  - `ActivityDetailScreen.kt`, `CombinePickerScreen.kt` and `SegmentEffortGroups.kt` to `:feature-detail-api`, with the moved features' APIs its States hold;
  - the presenters and helpers left in `app/presenters/.../detail` to `:feature-detail-presenter`. Their tests go with them: design.md's detail list, with `ActivityDetailTestBase` and `CombineTestBase`;
  - the UIs to `:feature-detail-ui`, and `StartTimeUiTest` to its jvmTest.

  Point segment's and record's presenters, `:app-common-presenter` and `:app-fixtures` at `:feature-detail-api`, dropping their `:app-screens` dependency. Wire the shells and screenshot tests. Verify:
  - `./gradlew :feature-detail-presenter:jvmTest :feature-detail-ui:jvmTest` passes with the baseline counts;
  - `app/screens` and `app/presenters` hold no source files;
  - `checkModuleBoundaries` passes.

  Done: presenter 175, UI 13 and composeApp 28. Segment 83, record 82 and profile 61 pass. Segment's and record's presenters and `:app-common-presenter` read detail's screens from `:feature-detail-api`, and none of them depends on `:app-screens` any more. `app/screens` and `app/presenters` hold no source files.

  Found while applying (design.md):
  - `:app-common-ui`'s `DataSourcesUi.kt` read detail's `DataSources`, `DataSourceRow` and `ActivityDetailEvent` through `:app-screens`. Its `DataSourcesSection`, used only by detail's combination editor, moved to `:feature-detail-ui` as `DataSourcesSection.kt`; `ChartSourceCaption` stays for the shared page cards.
  - The first name for that file, `DataSourcesUi.kt`, compiled to the same `DataSourcesUiKt` class as common-ui's file in the same package, so composeApp's tests saw only one of them. No other file name repeats across modules.
  - `StartTimeUiTest` uses `AppPreview`'s default sub-UIs: `ActivityDetailUi` embeds only the report sheet, which these tests don't open.

## 6. Delete the layer modules

- [x] 6.1 Remove `app/screens` and `app/presenters` from `settings.gradle.kts` and delete them. Drop every dependency on `:app-screens` and `:app-presenters`:
  - the shells;
  - `:app-common-presenter` and `:app-common-ui`;
  - `:app-fixtures`;
  - any feature module.

  Verify: `./gradlew :composeApp:jvmTest :desktopApp:compileKotlin :webApp:compileKotlinWasmJs checkModuleBoundaries` passes.
- [x] 6.2 Update `ModuleBoundaries.kt` and `ModuleBoundariesTest.kt` (design.md, "Deleting the layer modules"), plus the comments in `ComposeStability.kt` and `UseSingleFlight.kt`. Make `ContentLoadingConformanceTest` collect from `-api` modules and `:app-common-api` only. Verify:
  - `./gradlew -p build-logic :convention:test` passes;
  - `:composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"` checks the same States as before;
  - `grep -rn "app-screens\|app-presenters\|app/screens\|app/presenters"` outside `openspec/` finds nothing.

  Done: the convention tests pass (`ModuleBoundariesTest` 8). The two replaced cases now use `:app-common-ui` → `:app-common-presenter` (fails), and the dropped ones were already covered by `-api` → `-api` cases. `ContentLoadingConformanceTest` scans `:app-common-api` (through `UiError`) and the `-api` modules. A static count of States with a `ContentState` or `PagedState` field gives the same 28 at 3508b04d and now. After 7.1, nothing outside `openspec/` names the layer modules.

## 7. Docs

- [x] 7.1 Update the docs:
  - README's module table;
  - CLAUDE.md's "Screens span three modules" paragraph;
  - `.agents/rules/compose-screens.md` and `build-logic.md`;
  - `.agents/agents/gradle-runner.md`;
  - `.agents/skills/new-screen/SKILL.md`.

  None of them should describe the layer modules or "features not yet moved". Verify: `scripts/check-agent-sync.sh` passes.

## 8. Integration

- [x] 8.1 Run the full checks: `./gradlew check composeStabilityCheck`, the screenshot suite with every reference unchanged, and `scripts/check-public-safety.sh`. Verify: all pass.

  Done: `./gradlew check composeStabilityCheck --continue` passed in 15m 13s (5267 tasks), screenshot suite included, with no failures. `scripts/check-public-safety.sh` passes.

- [x] 8.2 Measure `./gradlew check --rerun-tasks --continue` locally as in batch 2, and record it against batch 2's numbers. After the push, record the GitHub Actions `Check` time. Verify: the numbers are recorded, and CI is green.

  Local, after this change (same machine, `--no-daemon` as before):
  - Wall time: 15m 40s (940 s), +4% against batch 2's 902 s.
  - Tasks: 5264, +14% against 4633 (9 modules added, 2 removed).
  - Peak RSS of the new JVMs together: 6529 MB, +5% against 6193 MB.

  The run failed only in the root `ktfmtCheckScripts`, which couldn't read a build file another task was rewriting under `--rerun-tasks` (the race batches 1 and 2 met). It passes on its own.

  On GitHub Actions, the run after the push (run 41132228959, at fc638973) passed:
  - Gradle reported 20m 6s for `check`, +27% against batch 2's 15m 50s. The job took 22m 4s.
  - 4237 tasks, +14% against batch 2's 3725; 117 came from the build cache.
  - Like batch 2's, the run was mostly uncached because moved files changed most tasks' inputs. Both CI runs were close to fully uncached, so that doesn't explain why CI grew more than the local run (+4%); a single run on shared runners may vary. It stays well within the 45-minute timeout.
- [x] 8.3 Run the apps:
  - iOS (the documented `xcodebuild`, then the simulator): open a profile, edit the profile, open followers; record a run with a simulated route, then save it; open the activity, its charts, comments and the combine picker; import an activity file and see the preview;
  - web: the profile, an activity's detail and an import preview;
  - desktop: the feed, a profile and an activity;
  - Android: a profile and an activity.

  Verify: each screen opens and behaves as before.

  Run on the local stack, from this change's build:
  - Web (signed in as `mod.test`):
    - the feed rendered;
    - the test run's detail showed its stats and the Device section built by the shared `ActivityPage`;
    - tapping the author opened the profile (detail → `:feature-profile-api`), and Edit profile and Followers opened from it;
    - the feed's Add manual activity opened record's form (feed → `:feature-record-api`), left unsaved.
  - iOS (simulator, signed in as bob):
    - the profile, Edit profile and Followers (listing alice) opened;
    - with a simulated GPS route, Record → Start → Pause → Finish & save recorded a 0.12 mi run and opened its detail (record → `:feature-detail-api`), with route, stats and comments. Its Pace chart appeared once the streams loaded;
    - the menu's Combine with… opened the combine picker, listing the earlier run (not combined);
    - the feed then listed the new run.
  - Android (emulator, signed in as carol): the profile; Search for "bob" → bob's profile → the new run's detail, with its Pace chart.
  - Desktop (`:desktopApp:hotRun`, signed in as bob): the feed rendered (sidebar, filter chips, bob's run with its route map); tapping the author opened bob's profile, and tapping the run there opened its detail, with route, stats, averages and its Pace chart.
  - Web import preview (the user picked a `.fit` file): the preview page showed its stats, route, Training load and the Heart rate and Power charts, built by the shared `ActivityPage` from record's import screen.
  - Not driven: the import preview on iOS and Android, which starts from the same native file picker. `ImportPreviewPageTest` and `ActivityPagePreviewTest` cover the page.

## Workflow follow-up

- Commit in small commits by concern (the shared page, then one per feature, then the deletion, then docs) when asked.
- Archive the change once it is verified.
