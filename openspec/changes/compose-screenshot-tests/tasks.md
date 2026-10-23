# Tasks

## 1. Setup

- [ ] 1.1 Add the plugin, `screenshot-validation-api` and `ui-tooling` to the version catalog. Enable `android.experimental.enableScreenshotTest` in `gradle.properties`. Apply the plugin and the experimental property in `androidApp/build.gradle.kts`, with `:app-fixtures` on `screenshotTestImplementation`. Verify `./gradlew :androidApp:tasks` lists `updateDebugScreenshotTest` and `validateDebugScreenshotTest`.
- [ ] 1.2 Make the harness call `PreviewContextConfigurationEffect()` under inspection on Android (an `expect` with no-op actuals elsewhere). Add one `@PreviewTest` for the segment screen. Verify `updateDebugScreenshotTest` writes a reference that shows its strings, route placeholder and data credit, and that `validateDebugScreenshotTest` passes on a second run.
- [ ] 1.3 Add `@ScreenSizes`, `@PhoneSize`, `@ScreenVariants` and `@ComponentVariants`, and set the image difference threshold. Verify the segment screen's references cover all nine sizes plus dark and 1.5x, and that the 900dp one shows the expanded layout.

## 2. Screens

- [ ] 2.1 Add `XScreenshots.kt` with a loaded preview and a `@PreviewParameter` states preview for the feed, segment page, activity and profile screens. Verify the references are recorded and reviewed for a sensible look: no render errors, placeholders where expected.
- [ ] 2.2 Do the same for the form screens and the paged lists. Verify the references are recorded and reviewed.
- [ ] 2.3 Do the same for the remaining screens (settings, login, record, import, gear, zones, report and the rest). Verify that every `@CircuitInject` UI has a screenshot file. A small JVM check listing UIs without one is enough.

## 3. Components

- [ ] 3.1 Add component previews for the adaptive controls (buttons, text field, checkbox, switch, segmented control, chips, card, top bar), the loading pieces (`ContentStateLayout` states, `RefreshStatus`, paged footer) and the charts. Verify the references are recorded in light and dark at 1x and 1.5x.

## 4. Check and docs

- [ ] 4.1 Make `./gradlew check` run `:androidApp:validateDebugScreenshotTest` (only if the plugin doesn't already). Verify that a deliberate theme colour change fails `check` with a diff in `androidApp/build/reports/screenshotTest/preview/debug/index.html` and leaves the references untouched, then revert it.
- [ ] 4.2 Update `.agents/rules/compose-screens.md` (screenshot previews per screen, updating references with the UI change), the `new-screen` skill (scaffold `XScreenshots.kt`) and `AGENTS.md` (the update and validate commands, and where references live). Verify that `scripts/check-agent-sync.sh` passes.

## 5. Integration

- [ ] 5.1 Run `./gradlew check` twice with no changes and verify both runs pass. Run the screenshot tests with the network off and verify they pass.

## Workflow follow-up

- When AGP 9.5 is stable and the project is on it, migrate to AGP test suites (`testOptions { screenshotTests.create("screenshotTest") }`) following the `testing-setup` skill's migration table.
- Archive with `/opsx:archive` once implemented and reviewed, after `ui-test-harness`.
