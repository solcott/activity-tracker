# Proposal

## Why

Nothing catches a visual regression today. A theme tweak, a wrapper change in `ui/adaptive` or a layout change at one window width shows up only when someone happens to look. `ui-test-harness` makes every screen renderable from sample states without a session or the network. Screenshot tests on top of it turn those samples into reference images that `check` compares. The same preview functions also let developers browse every screen and state in Android Studio without the Docker stack or signing in.

## What Changes

- **Reference screenshots of every screen.** Each screen's loaded sample at nine window sizes:
  - widths: compact, medium and expanded (400, 610 and 900dp)
  - heights: 400, 500 and 1000dp

  Each of its other samples (loading, empty, failed, refreshing, refresh failed, invalid input) at phone size.
- **Variants.** Each screen in dark theme and at 1.5x font scale at phone size. Shared components (the adaptive wrappers, loading and error pieces, charts) in light and dark theme at both font scales.
- **Host-side and offline.** Screenshots render on the developer's machine with Android's renderer. They need no device, emulator, network, account or local stack.
- **Checked on every `./gradlew check`.** A difference from the references fails the check with a visual diff report. References change only through an explicit update command, and are reviewed in the commit.
- **Previews double as the tests.** Each screenshot is an annotated preview, so the same functions render in the IDE.
- **The rules and the `new-screen` skill** require a new screen to ship its screenshot previews.

Out of scope:
- iOS-look screenshots (no iOS simulator tests)
- desktop- and web-look screenshots
- device-based screenshots of system UI (edge-to-edge, notifications)

## Capabilities

### New Capabilities
- `dev/screenshot-tests`: which reference screenshots exist, how they are rendered and compared, and how they are updated.

### Modified Capabilities
None.

## Impact

- **`:androidApp`:**
  - Declares Google's Compose Preview Screenshot Testing as an AGP test suite (`android.testOptions.screenshotTests.create("screenshotTest")`, engine 0.0.1-alpha16, on AGP 9.5.0-rc01) targeting the debug variant.
  - Its `src/screenshotTest` source set holds the preview tests, which use `:app-fixtures` and `AppPreview`.
  - References go in `androidApp/src/screenshotTestDefaultDebug/reference`.
- **Build:**
  - `gradle.properties` enables `android.experimental.enableScreenshotTest` and `android.experimental.testSuiteSupport`.
  - The version catalog gains the engine version, `screenshot-validation-api` and `ui-tooling`.
  - `check` depends on `:androidApp:testDebugScreenshotTestDefaultTestSuite`.
- **Docs:** `.agents/rules/compose-screens.md`, the `new-screen` skill, and `AGENTS.md` (the commands and where references live).
- **Repository size:** a few hundred PNGs, kept small by the phone-size default for non-primary samples.
- **Dependencies:** requires `ui-test-harness` (harness, fixtures, inspection-mode map and image placeholders) and AGP 9.5 (test suites).
