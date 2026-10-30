# Tasks

Each feature group follows the search pilot. Steps:
- Create `feature/<name>/{api,presenter,ui}`, whose build scripts hold only the convention plugin and any extra dependency, and add them to the `settings.gradle.kts` list.
- Move the files with `git mv`, keeping packages.
- Point the modules that used them at the new `-api`, or for app shells at the presenter and UI (design.md, "Layer modules declare the `-api` modules they use").
- Run the feature's tests.

After every group, `./gradlew checkModuleBoundaries` passes and the build compiles on JVM.

## 1. Baseline

- [x] 1.1 Record the presenter test counts that will move: `LoginPresenterTest`, `RouteMapPresenterTest`, `MediaGalleryPresenterTest`, `ReportPresenterTest`, `ModerationPresenterTest`, `FeedFilterPresenterTest` and `TypeSelectionTest`, plus `SettingsPresenterTest` and `ActivityDetailRouteTest`, from `./gradlew :app-presenters:jvmTest` on `main`. Write them under this task. Verify: the counts are recorded.

Baseline (task 1.1), `main` at ade6db32, all passing: `LoginPresenterTest` 5, `RouteMapPresenterTest` 7, `MediaGalleryPresenterTest` 4, `ReportPresenterTest` 4, `ModerationPresenterTest` 4, `FeedFilterPresenterTest` 7, `TypeSelectionTest` 3, `SettingsPresenterTest` 15, `ActivityDetailRouteTest` 9.

## 2. Shared helpers and fixtures

- [x] 2.1 Move these files to `:app-common-presenter`, keeping their packages, and make the declarations other modules now use public:
  - `app/presenters/.../detail/OwnRoute.kt`;
  - `app/presenters/.../record/ActivityTypes.kt`.

  Verify: `./gradlew :app-common-presenter:compileKotlinJvm :app-presenters:jvmTest` passes.
- [x] 2.4 Split `ActivityDetailTestBase`:
  - its feature-neutral part (the served activity's mock HTTP, `Repos` and `repos()`) becomes `ActivityTestBase` in `:app-common-presenter-testing`, which adds `:core-data` and Ktor's mock engine;
  - `ActivityDetailTestBase` extends it and keeps the detail-specific helpers.

  Move `RouteMapPresenterTest.theDetailMapOpensTheEnlargedMap` into `ActivityDetailRouteTest`, and make `RouteMapPresenterTest` and `MediaGalleryPresenterTest` extend `ActivityTestBase`. Verify: `./gradlew :app-presenters:jvmTest` passes with the same total test count as before.
- [x] 2.2 Add a `TestRoutes` object to `:app-common-presenter-testing` holding `SERVED_POLYLINE` and `SERVED_ROUTE`, and make `ActivityDetailRouteTest` and `RouteMapPresenterTest` use it instead of `ActivityDetailRouteTest.Companion`. Verify: `./gradlew :app-presenters:jvmTest --tests "*.ActivityDetailRouteTest" --tests "*.RouteMapPresenterTest"` passes.
- [x] 2.3 Move `ModerationPresenterTest.settingsOffersModerationOnlyToModerators` into `SettingsPresenterTest`. Verify: `./gradlew :app-presenters:jvmTest --tests "*.SettingsPresenterTest" --tests "*.ModerationPresenterTest"` passes, and the two classes' counts add up to the baseline.

## 3. report

- [x] 3.1 Create `feature/report/{api,presenter,ui}`. Move:
  - `ReportScreen.kt` (with `Reporting`, `ReportingEvent`, `BlockOffer`) to `:feature-report-api`;
  - `ReportPresenter.kt` and `ReportPresenterTest.kt` to `:feature-report-presenter`;
  - `ReportUi.kt` to `:feature-report-ui`.

  Add `:feature-report-api` to `:app-screens` (`api`), `:app-common-presenter`, `:app-common-ui` and `:app-fixtures`. Wire the app shells and androidApp's screenshot tests. Verify:
  - `./gradlew :feature-report-presenter:jvmTest` passes with the baseline count;
  - `./gradlew :composeApp:jvmTest checkModuleBoundaries` passes.

## 4. media

- [x] 4.1 Create `feature/media/{api,presenter,ui}`. Move:
  - `MediaGalleryScreen.kt` to `:feature-media-api`, which depends on `:feature-report-api`;
  - `MediaGalleryPresenter.kt` and `MediaGalleryPresenterTest.kt` to `:feature-media-presenter`;
  - `MediaGalleryUi.kt` to `:feature-media-ui`.

  Add `:feature-media-api` to `:app-presenters` (detail opens the gallery), `:app-common-presenter` (the activity-list sink) and `:app-fixtures`. Wire the app shells and screenshot tests. Verify:
  - first move `ActivityDetailModerationTest`'s two gallery-only tests into `MediaGalleryPresenterTest` (design.md);
  - `./gradlew :feature-media-presenter:jvmTest` passes with the baseline count plus those two (6);
  - `./gradlew :app-presenters:jvmTest --tests "*.ActivityDetailModerationTest"` passes;
  - `checkModuleBoundaries` passes.

## 5. feedfilter

- [x] 5.1 Create `feature/feedfilter/{api,presenter,ui}`. Move:
  - `FeedFilterScreen.kt` to `:feature-feedfilter-api`;
  - `FeedFilterPresenter.kt`, `TypeSelection.kt`, `FeedFilterPresenterTest.kt` and `TypeSelectionTest.kt` to `:feature-feedfilter-presenter`;
  - `FeedFilterUi.kt` to `:feature-feedfilter-ui`.

  Add `:feature-feedfilter-api` to `:app-screens` (`api`, since `FeedState` holds it) and `:app-fixtures`. Wire the app shells and screenshot tests. Verify:
  - `./gradlew :feature-feedfilter-presenter:jvmTest` passes with the baseline counts;
  - `:app-presenters:jvmTest --tests "*.FeedPresenterTest" --tests "*.FeedQueryTest"` passes;
  - `checkModuleBoundaries` passes.

## 6. map

- [x] 6.1 Create `feature/map/{api,presenter,ui}`. Move:
  - `RouteMapScreen.kt` to `:feature-map-api`;
  - `RouteMapPresenter.kt` and `RouteMapPresenterTest.kt` to `:feature-map-presenter`;
  - `RouteMapUi.kt` to `:feature-map-ui`.

  Add `:feature-map-api` to `:app-presenters` and `:app-fixtures`. Wire the app shells, `TopLevelDestination` and the screenshot tests. Verify:
  - `./gradlew :feature-map-presenter:jvmTest` passes with the baseline count;
  - `:composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"` still checks `RouteMapState`;
  - `checkModuleBoundaries` passes.

## 7. moderation

- [x] 7.1 Create `feature/moderation/{api,presenter,ui}`. Move:
  - `ModerationScreen.kt` to `:feature-moderation-api`;
  - `ModerationPresenter.kt` and `ModerationPresenterTest.kt` to `:feature-moderation-presenter`;
  - `ModerationUi.kt` to `:feature-moderation-ui`.

  Add `:feature-moderation-api` to `:app-presenters` (settings opens it) and `:app-fixtures`. Wire the app shells and screenshot tests. Verify:
  - `./gradlew :feature-moderation-presenter:jvmTest` passes with the count left after 2.3;
  - `checkModuleBoundaries` passes.

## 8. login

- [x] 8.1 Create `feature/login/{api,presenter,ui}`. Move:
  - `LoginScreen.kt` to `:feature-login-api`;
  - `LoginPresenter.kt` and `LoginPresenterTest.kt` to `:feature-login-presenter`;
  - `LoginUi.kt` to `:feature-login-ui`.

  Add `:feature-login-api` to `:app-fixtures`. Wire the app shells (`App.kt` renders `LoginScreen` on `AppCircuit`) and the screenshot tests. Verify:
  - `./gradlew :feature-login-presenter:jvmTest` passes with the baseline count;
  - `:composeApp:jvmTest` passes;
  - `checkModuleBoundaries` passes.

## 9. Docs

- [x] 9.1 Update CLAUDE.md's "Screens span three modules" paragraph, which currently says only search has moved, and `.agents/rules/compose-screens.md` to list the seven moved features. Verify: `scripts/check-agent-sync.sh` passes, and `grep -n "only search" CLAUDE.md .agents/rules` finds nothing.

## 10. Integration

- [x] 10.1 Check that nothing was left behind or copied:
  - no files remain in the six packages under `app/screens`, `app/presenters` or `composeApp/src/commonMain`;
  - `grep` finds no `internal` declaration in the moved files that another module reads.

  Verify: `find app/screens app/presenters composeApp/src -path "*/app/{login,map,media,report,moderation,feedfilter}/*"` lists nothing.
- [x] 10.2 Run the full checks:
  - `./gradlew check`;
  - `./gradlew composeStabilityCheck`, so every new `-api` module is under strict stability;
  - `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite`, with every reference matching unchanged;
  - `scripts/check-public-safety.sh`.

  Verify: all pass.
- [ ] 10.3 Measure `./gradlew check --rerun-tasks --continue` locally with fresh daemons and record wall time, task count and peak RSS here, next to the foundation's numbers (10m 17s, 1843 tasks, 4936 MB). After the push, record the GitHub Actions `Check` time against the foundation's cold 25m 35s and warm 3m 15s. Verify: the numbers are recorded with the percentage change, and CI is green.

  Local, after this change (same machine). The running Gradle daemon couldn't be stopped, so this ran with `--no-daemon`, which gives the build a fresh Gradle JVM. A Kotlin daemon that was already running may have been reused, and its RSS isn't counted.
  - Wall time: 11m 48s (708 s), +15% against 618 s.
  - Tasks: 3355, +82% against 1843, from the 18 new modules' per-target tasks.
  - Peak RSS of the new JVMs together: 7017 MB, +42% against 4936 MB.

  That run failed in only two places:
  - 3 `ktfmtCheckScripts` tasks, which couldn't read temp and generated files that other tasks had deleted under `--rerun-tasks`;
  - 2 detekt findings from the moves, now fixed.

  After the fix, `./gradlew check composeStabilityCheck` passed. The GitHub Actions numbers wait for the push.
- [x] 10.4 Run the apps:
  - iOS (the documented `xcodebuild`, then the simulator): sign out and sign back in, open a route map and the photo gallery from an activity, and open the feed filter;
  - desktop and web: the same, except signing out;
  - Android: report a comment, and from Settings open moderation on a moderator account if one is available.

  Verify: each screen opens and behaves as before.

  Run on the local stack, from this change's build:
  - iOS (simulator, signed in as bob): recorded a run with a simulated GPS route, then:
    - its detail map's expand button opened the route map (route, start and end markers, recenter);
    - after two photos were added through Edit, tapping one opened the gallery at "2 / 2";
    - the feed filter's type sheet opened with its quick types.
  - iOS photos showed as placeholders, in the gallery and in the unmoved detail thumbnails alike. iOS fetches them through the tunnel's `/img/media/…`, which redirects from HTTPS to the PDS's plain `http://localhost:2583`, and the app doesn't follow it. That is the local setup, not this change.
  - Web (signed in as alice):
    - the feed filter's type sheet opened, and applying Run filtered the feed (`Clear filters` appeared);
    - bob's photo opened the gallery at "1 / 2", and its next arrow paged to "2 / 2";
    - web has no enlarged route map by design (`ActivityMap.wasmJs.kt`).
  - Desktop: launched with `hotRun` from this worktree, signed in, and the feed rendered with the feed filter chips. The hot-reload MCP server was attached to the main checkout, so no clicks were driven.
  - Android (emulator, signed in as carol):
    - no crash at start;
    - bob's profile menu → Report opened the report sheet (reasons, note, Send), cancelled without sending. There was no comment to report, and this is the same `ReportScreen` a comment opens.
    - Settings offered no moderation entry, as expected for a non-moderator.
  - Web, by the user: signed out, signed back in as `mod.test` through the login screen, and opened Moderation from Settings, which showed its subject field and Open button.
  - Not driven: clicks on desktop. It launched signed in and rendered the feed and its filter, and desktop shares these screens' code with the other targets.
  - Seen in passing, not from this change: a photo picked on iOS was stored upside down (orientation lost at upload), and shows that way on web too.

## Workflow follow-up

- Commit in small commits by concern (shared helpers, then one per feature, then docs) when asked.
- Archive the change once it is verified.
