# Tasks

## 1. Setup

- [x] 1.1 Add the engine version, `screenshot-validation-api` and `ui-tooling` to the version catalog. Enable `android.experimental.enableScreenshotTest` and `android.experimental.testSuiteSupport` in `gradle.properties`. Declare the `screenshotTest` suite in `androidApp/build.gradle.kts` (debug target, its dependencies including `:app-fixtures`, Android resources in unit tests, release unit tests enabled for AGP's preview tasks). Verify `./gradlew :androidApp:tasks --all` succeeds and lists `updateDebugScreenshotTestDefaultTestSuite` and `testDebugScreenshotTestDefaultTestSuite`.
- [x] 1.2 Make the harness call `PreviewContextConfigurationEffect()` under inspection on Android (an `expect` with no-op actuals elsewhere). Add one `@PreviewTest` for the segment screen. Verify `updateDebugScreenshotTestDefaultTestSuite` writes a reference that shows its strings and route placeholder, and that `testDebugScreenshotTestDefaultTestSuite` passes on a second run.
- [x] 1.3 Add `@PreviewScreenSizes`, `@PreviewPhoneSize`, `@PreviewScreenVariants` and `@PreviewComponentVariants`, and set the suite's `imageDifferenceThreshold`. Verify the segment screen's references cover all nine sizes plus dark and 1.5x, and that the 900dp one shows the expanded layout.

## 2. Screens

- [x] 2.1 Add `XScreenshots.kt` with a loaded preview and a `@PreviewParameter` states preview for the feed, segment page, activity and profile screens. Verify the references are recorded and reviewed for a sensible look: no render errors, placeholders where expected.
- [x] 2.2 Do the same for the form screens and the paged lists. Verify the references are recorded and reviewed.
- [x] 2.3 Do the same for the remaining screens (settings, login, record, import, gear, zones, report and the rest). Verify that every `@CircuitInject` UI has a screenshot file. A small JVM check listing UIs without one is enough.

## 3. Components

- [x] 3.1 Add component previews for the adaptive controls (buttons, text field, checkbox, switch, segmented control, chips, card, top bar), the loading pieces (`ContentStateLayout` states, `RefreshStatus`, paged footer) and the charts. Verify the references are recorded in light and dark at 1x and 1.5x.

## 4. Check and docs

- [x] 4.1 Make `./gradlew check` run `:androidApp:testDebugScreenshotTestDefaultTestSuite` (AGP doesn't). Verify that a deliberate theme colour change fails `check` with a diff in `androidApp/build/reports/tests/testDebugScreenshotTestDefaultTestSuite/index.html` and leaves the references untouched, then revert it.
- [x] 4.2 Update `.agents/rules/compose-screens.md` (screenshot previews per screen, updating references with the UI change), the `new-screen` skill (scaffold `XScreenshots.kt`) and `AGENTS.md` (the update and test commands, and where references live). Verify that `scripts/check-agent-sync.sh` passes.

## 5. Integration

- [x] 5.1 Run `./gradlew check` twice with no changes and verify both runs pass. Run the screenshot tests with the network off and verify they pass.

## Workflow follow-up

- When AGP 9.5 goes stable, bump it and recheck the suite's task names, reference path and the release-variant workaround.
- Archive with `/opsx:archive` once implemented and reviewed, after `ui-test-harness`.
