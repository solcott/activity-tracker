# Tasks

Each feature follows batch 1. Steps:
- Create `feature/<name>/{api,presenter,ui}`, whose build scripts hold only the convention plugin and any extra dependency, and add them to the `settings.gradle.kts` list.
- Move the files with `git mv`, keeping packages.
- Point the modules that used them at the new `-api`, or for app shells at the presenter and UI (design.md, "Layer modules and shells declare the `-api` modules they use").
- Run the feature's tests.

After every group, `./gradlew checkModuleBoundaries` passes and the build compiles on JVM.

## 1. Baseline

- [x] 1.1 Record the test counts that will move, from `main`:
  - every presenter test design.md lists under "Tests move with their feature", plus `GearChoiceTest`, `ActivityTypeChoiceTest` and the `:app-presenters:jvmTest` total;
  - the seven UI tests, plus the `:composeApp:jvmTest` total.

  Write them under this task. Verify: the counts are recorded.

Baseline (task 1.1), `main` at d8061cbd, all passing:
- `:app-presenters:jvmTest` 570 in all:
  - gear: `GearPresenterTest` 11, `GearListPresenterTest` 7, `GearCollectionPresenterTest` 6, `EditGearPresenterTest` 18, `IgnoredSensorsPresenterTest` 2 (44);
  - edit: `EditActivityPresenterTest` 17;
  - segment: `SegmentPresenterTest` 38, `SegmentLeaderboardPresenterTest` 13, `SegmentMayorsPresenterTest` 6, `StarredSegmentsPresenterTest` 9, `ProposeSegmentNamePresenterTest` 3, `CreateSegmentPresenterTest` 14 (83);
  - settings: `SettingsPresenterTest` 16, `ZonesPresenterTest` 8, `SportZonesPresenterTest` 7, `TrainingZonesPresenterTest` 8, `ThresholdHistoryPresenterTest` 6, `PrivacyZonesPresenterTest` 8 (53);
  - feed: `FeedPresenterTest` 15, `FeedQueryTest` 3, `UnsentChangesPresenterTest` 5, `SegmentsPromptTest` 7 (30);
  - staying: `GearChoiceTest` 8, `ActivityTypeChoiceTest` 2, `ActivityDetailSegmentsTest` 12.

  So 227 move and 343 stay.
- `:composeApp:jvmTest` 82 in all:
  - gear: `GearUiTest` 9, `GearSensorsUiTest` 5, `GearCollectionUiTest` 3, `GearListUiTest` 2 (19);
  - segment: `SegmentCreditUiTest` 5, `SegmentNamesUiTest` 7 (12);
  - feed: `UnsentChangesUiTest` 6.

  So 37 move and 45 stay.

## 2. Shared types and fixtures

- [x] 2.1 Move `HistoryDate` out of `ThresholdHistoryScreen.kt` into its own file in `:app-common-api`, keeping the `app.settings` package. Verify: `./gradlew :app-common-ui:compileKotlinJvm :app-presenters:jvmTest --tests "*.ThresholdHistoryPresenterTest"` passes.
- [x] 2.2 Move record's `GearField.kt` to `:app-common-api` and `GearChoice.kt` to `:app-common-presenter`, keeping the `app.record` package, and make the declarations other modules now use public. Verify: `./gradlew :app-presenters:jvmTest --tests "*.GearChoiceTest" --tests "*.EditActivityPresenterTest" --tests "*.ManualActivityPresenterTest"` passes.
- [x] 2.3 Move `SessionTestFixture`, `TestZones` (made public) and `VisibilityFieldsTesting` to `:app-common-presenter-testing`, which adds `:app-common-presenter`, `:core-location` and whatever else their imports need. (Only `:app-common-presenter` was needed: `:core-data` already exports `:core-location`.)
  - Move `Repos.typeChoices` and `Repos.newDeviceChoices` out of the class into extension properties in `RecordChoices.kt` in `:app-presenters`' jvmTest.

  Verify: `./gradlew :app-presenters:jvmTest` passes with the baseline total.

## 3. gear

- [x] 3.1 Create `feature/gear/{api,presenter,ui}`. Move:
  - the six screens (`GearScreen`, `GearListScreen`, `GearCollectionScreen`, `EditGearScreen`, `EditGearCollectionScreen`, `IgnoredSensorsScreen`) to `:feature-gear-api`;
  - their six presenters and five presenter tests to `:feature-gear-presenter`;
  - the nine UI files to `:feature-gear-ui`, and gear's four UI tests to its `jvmTest`.

  Add `:feature-gear-api` to `:app-presenters` (detail opens `GearScreen`, settings `GearListScreen`) and `:app-fixtures`. Wire the app shells and screenshot tests. Verify:
  - `./gradlew :feature-gear-presenter:jvmTest :feature-gear-ui:jvmTest` passes with the baseline counts;
  - `checkModuleBoundaries` passes.

  Done: presenter 44 and UI 17. Two of `GearSensorsUiTest`'s tests render record's import screen and stay in composeApp as `ImportSensorsUiTest` (design.md), so composeApp has 65 (82 − 19 + 2). The gear UI module adds FileKit for the edit form's photo picker.

## 4. edit

- [x] 4.1 Create `feature/edit/{api,presenter,ui}`. Move:
  - `EditActivityScreen.kt` to `:feature-edit-api`;
  - `EditActivityPresenter.kt`, `EditVisibility.kt`, `LeaderboardExclusion.kt` and `EditActivityPresenterTest.kt` to `:feature-edit-presenter`;
  - `EditActivityUi.kt` to `:feature-edit-ui`.

  Add `:feature-edit-api` to `:app-presenters` (detail opens it) and `:app-fixtures`. Wire the app shells, `TopLevelDestination` and the screenshot tests. Verify:
  - `./gradlew :feature-edit-presenter:jvmTest` passes with the baseline count;
  - `checkModuleBoundaries` passes.

## 5. segment

- [x] 5.1 Create `feature/segment/{api,presenter,ui}`. Move:
  - the seven screens to `:feature-segment-api`;
  - the seven presenters, `BoardTypes.kt`, `SegmentDisplay.kt` and the six presenter tests to `:feature-segment-presenter`, which depends on `:app-screens` for `ActivityDetailScreen` and `ProfileScreen` (with a comment, as search does);
  - the nine UI files to `:feature-segment-ui`, and `SegmentCreditUiTest` and `SegmentNamesUiTest` to its `jvmTest`.

  Switch `CreateSegmentPresenterTest` to `ActivityTestBase` (design.md). Add `:feature-segment-api` to `:app-presenters` (detail, profile) and `:app-fixtures`. Wire the app shells, `TopLevelDestination`, `SessionPreviewSubUis`, `TestTagsUiTest` and the screenshot tests. Verify:
  - `./gradlew :feature-segment-presenter:jvmTest :feature-segment-ui:jvmTest` passes with the baseline counts;
  - `./gradlew :app-presenters:jvmTest --tests "*.ActivityDetailSegmentsTest"` passes;
  - `checkModuleBoundaries` passes.

  Done: presenter 83 and UI 12. `CreateSegmentPresenterTest` needed nothing from `ActivityDetailTestBase` beyond `ActivityTestBase`. `:app-presenters:jvmTest` has 426 (570 − 44 − 17 − 83), and composeApp 53.

## 6. settings

- [x] 6.1 Create `feature/settings/{api,presenter,ui}`. Move:
  - the six screens to `:feature-settings-api`;
  - the six presenters, `DefaultVisibilitySettings.kt`, `SegmentMatchingSetting.kt` and the six presenter tests to `:feature-settings-presenter`, which depends on `:feature-gear-api`, `:feature-moderation-api`, and on `:app-screens` for `BlockListScreen`;
  - the eight UI files to `:feature-settings-ui`.

  Add `:feature-settings-api` to `:app-presenters` (profile opens settings, detail training zones) and `:app-fixtures`. Wire the app shells and screenshot tests. Verify:
  - `./gradlew :feature-settings-presenter:jvmTest` passes with the baseline counts;
  - `checkModuleBoundaries` passes.

  Done: presenter 53, and `:app-presenters:jvmTest` 373. `:app-presenters` drops `:feature-moderation-api`, which only settings used.

## 7. feed

- [x] 7.1 Create `feature/feed/{api,presenter,ui}`. Move:
  - `FeedScreen.kt` and `UnsentChangesScreen.kt` to `:feature-feed-api`, which depends on `:feature-feedfilter-api`;
  - `FeedPresenter.kt`, `FeedQuery.kt`, `UnsentChangesPresenter.kt` and their four tests (`FeedPresenterTest`, `FeedQueryTest`, `UnsentChangesPresenterTest`, `SegmentsPromptTest`) to `:feature-feed-presenter`, which depends on `:app-screens` for the record screens;
  - `FeedUi.kt` and `UnsentChangesUi.kt` to `:feature-feed-ui`, and `UnsentChangesUiTest` to its `jvmTest`.

  Drop `:feature-feedfilter-api` from `:app-screens` and `:app-presenters` if nothing else there uses it. Add `:feature-feed-api` to `:app-fixtures`. Wire the app shells, `TopLevelDestination`, `AppPreviewUiTest` and the screenshot tests. Verify:
  - `./gradlew :feature-feed-presenter:jvmTest :feature-feed-ui:jvmTest` passes with the baseline counts;
  - `:composeApp:jvmTest` passes with its baseline total less the moved UI tests;
  - `checkModuleBoundaries` passes.

  Done: presenter 30 and UI 6. `:app-presenters:jvmTest` has 343, the baseline's count that stays, and composeApp 47 (the 45 that stay plus `ImportSensorsUiTest`'s 2). `:app-screens` drops `:feature-feedfilter-api`, and nothing in `:app-presenters` uses it any more. The feed screen's test renders a placeholder in the filter's place (design.md).

## 8. Docs

- [x] 8.1 Update CLAUDE.md's "Screens span three modules" paragraph and `.agents/rules/compose-screens.md` to list the twelve moved features and the shared fixtures in `:app-common-presenter-testing`. Verify: `scripts/check-agent-sync.sh` passes.

## 9. Integration

- [x] 9.1 Check that nothing was left behind or copied:
  - no files remain in the five packages under `app/screens`, `app/presenters` or `composeApp/src`, except the record producers' `RecordChoices.kt` and the tests design.md keeps in place;
  - `grep` finds no `internal` declaration in the moved files that another module reads.

  Verify: `find app/screens app/presenters composeApp/src -path "*/app/feed/*" -o -path "*/app/edit/*" -o -path "*/app/gear/*" -o -path "*/app/segment/*" -o -path "*/app/settings/*"` lists nothing.
- [x] 9.2 Run the full checks:
  - `./gradlew check`;
  - `./gradlew composeStabilityCheck`, so every new `-api` module is under strict stability;
  - `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite`, with every reference matching unchanged;
  - `scripts/check-public-safety.sh`.

  Verify: all pass.

  Done: `./gradlew check composeStabilityCheck --continue` ran 4633 tasks in 15m 1s, including `checkModuleBoundaries` in all 54 modules and `testDebugScreenshotTestDefaultTestSuite`, and everything passed except one task, `:ui-designsystem:ktfmtCheckScripts`. That task couldn't read `build/intermediates/.../stripped.dir`, which the module's Android resource packaging was rewriting at the same time: the same race batch 1 met, in a module this change doesn't touch. It passes on its own. `scripts/check-public-safety.sh` passes.
- [ ] 9.3 Measure `./gradlew check --rerun-tasks --continue` locally as in batch 1, and record wall time, task count and peak RSS here, next to batch 1's (11m 48s, 3355 tasks, 7017 MB). After the push, record the GitHub Actions `Check` time against batch 1's 14m 5s. Verify: the numbers are recorded with the percentage change, and CI is green.

  Local, after this change (same machine, `--no-daemon` as in batch 1):
  - Wall time: 15m 2s (902 s), +27% against batch 1's 708 s.
  - Tasks: 4633, +38% against 3355, from the 15 new modules' per-target tasks.
  - Peak RSS of the new JVMs together: 6193 MB, −12% against 7017 MB. That drop may be the Kotlin daemon: whether the run reuses one already running varies between runs, and its RSS isn't counted.

  The run failed in one place, `:feature-gear-presenter:ktfmtCheckScripts`, which couldn't read a temp file another task had deleted under `--rerun-tasks` (the race batch 1 met). It passes on its own.

  CI: pending the push.
- [x] 9.4 Run the apps:
  - iOS (the documented `xcodebuild`, then the simulator): the feed loads; edit an activity; open gear from an activity and add an item; open a segment, its leaderboard and its map from an activity; open Settings, zones and privacy zones;
  - web: the same;
  - desktop: launch and confirm the feed renders;
  - Android: the feed loads and Settings opens.

  Verify: each screen opens and behaves as before.

  Run on the local stack, from this change's build:
  - Web (signed in as `mod.test`):
    - the feed rendered with its filter chips;
    - Settings opened, and from it Training zones, Default zones, Privacy zones and the gear list (settings → `:feature-gear-api`);
    - Add gear saved "Test bike", and its gear page opened;
    - the feed's Add manual activity opened record's form (feed → `:app-screens`), and a 3 mi run saved and opened;
    - its menu's Edit opened the edit form, which offered the bike in the shared gear picker and saved it;
    - the feed card then showed its gear, the activity listed "Test bike", and tapping it opened the gear page with the run in its totals and list;
    - Starred segments opened from the profile (profile → `:feature-segment-api`), empty.
  - iOS (simulator, signed in as bob):
    - the feed loaded, with the segments prompt (feed's `SegmentsPrompt`, left alone);
    - the recorded run's detail opened; its menu opened Edit and Create segment (detail → edit and segment APIs);
    - publishing "Batch 2 test segment" returned to the activity with "Segment created. It will appear once it's processed.";
    - the feed's add menu listed Record, manual entry and import, and Settings opened.
  - Android (emulator, signed in as carol): the feed loaded once the emulator's lost `adb reverse` ports were restored, and Settings opened from the profile.
  - Desktop: `:desktopApp:run` launched to the login screen, since its stored session had lapsed. The user signed in as bob, and the feed rendered in the wide layout (sidebar, the feed filter chips, bob's run with its route map). No clicks were driven: the hot-reload MCP server is attached to the main checkout.
  - iOS, after the user approved turning on bob's segment matching (the feed prompt's Turn on):
    - within half a minute the run listed "Batch 2 test segment", Proposed, PR 1:01;
    - tapping it opened the segment screen (detail → `:feature-segment-api`), showing the status note and bob's effort;
    - its expand button opened the segment map (`SegmentMapScreen`).
    - There is no leaderboard to open: `SegmentUi` offers Leaderboard only for an active segment, and a new one stays proposed until enough athletes have done it.
  - Seen in passing, not from this change: on web, the You tab's back stack still held the gear page from before the run existed. Its totals had updated, but its activity list was still empty until it was reopened from the activity.

## Workflow follow-up

- Commit in small commits by concern (shared types, fixtures, then one per feature, then docs) when asked.
- Archive the change once it is verified.
