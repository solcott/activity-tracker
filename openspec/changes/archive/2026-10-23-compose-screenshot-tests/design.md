# Design

## Context

- **What `ui-test-harness` provides:**
  - `AppPreview` (`composeApp` `ui/preview/`), which can fill the preview's own size.
  - `:app-fixtures` with `XSamples` (each with an `all` map).
  - Inspection-mode placeholders for maps (`RoutePreview`) and Coil images.
- **Module layout.** `composeApp` is a KMP library (`com.android.kotlin.multiplatform.library`). `:androidApp` is the only plain Android application module (`activitytracker.android.application`), and it depends on `:composeApp`.
- **Versions.** AGP 9.5.0-rc01, Kotlin 2.4.21, Compose Multiplatform 1.12.2. Strings come from Compose Multiplatform resources (`Res`).
- **No CI yet.** References are recorded and checked on developers' machines (macOS).

## Goals / Non-Goals

**Goals:**
- Reference images for the spec's screen and component matrix, compared on every `check`.
- Little code per screen, by driving previews from the samples.
- The same functions render in Android Studio's preview pane.

**Non-Goals:**
- iOS, desktop or web looks.
- Device screenshots: no Dropshots, and nothing involving system UI.

## Decisions

**Compose Preview Screenshot Testing (CPST) as an AGP test suite, engine 0.0.1-alpha16.**
- It renders with layoutlib on the host. Each `@PreviewTest`-annotated `@Preview` is a screenshot, and it's integrated into Android Studio. The project is on AGP 9.5.0-rc01, so it uses test suites (`testOptions.screenshotTests`) rather than the deprecated standalone plugin `com.android.compose.screenshot`.
- Alternatives considered:
  - Roborazzi on the JVM desktop target: renders the desktop look, has no IDE preview integration, and its rasterisation varies by OS.
  - Roborazzi with Robolectric: slower, and needs an Android unit-test setup that `composeApp` doesn't have.
  - Paparazzi: also layoutlib, but needs an Android library module and JUnit rules, and has no preview reuse.

**Host module: `:androidApp`.** CPST doesn't support KMP modules (its known issues), and `composeApp`'s Android target comes from the KMP library plugin.
- `gradle.properties` sets `android.experimental.enableScreenshotTest` and `android.experimental.testSuiteSupport`.
- `:androidApp` declares `testOptions.screenshotTests.create("screenshotTest")` with `engineVersion`, `targetVariants.add("debug")`, `imageDifferenceThreshold`, and the suite's own `dependencies` (`:app-fixtures`, `screenshot-validation-api`, `ui-tooling`). Its sources live in `androidApp/src/screenshotTest/kotlin`.
- The preview renderer needs `unitTests.isIncludeAndroidResources = true`. AGP 9.5.0-rc01 also registers Compose Preview tasks for every variant, and the release one fails to configure (breaking `./gradlew tasks`) without a unit-test component, so `:androidApp` enables release unit tests in `beforeVariants`. It has none, so nothing runs.
- The tests call `composeApp`'s public UIs. Any `internal` UI a test needs is made public, or reached through its screen's public `XUi`.

**Shared multipreview annotations** (`androidApp/src/screenshotTest/.../Previews.kt`):
- `@PreviewScreenSizes`: nine `@Preview`s, widths 400/610/900 by heights 400/500/1000.
- `@PreviewPhoneSize`: 400x500.
- `@PreviewScreenVariants`: 400x500 with `uiMode = UI_MODE_NIGHT_YES`, and 400x500 at `fontScale = 1.5f`.
- `@PreviewComponentVariants`: light and dark at font scales 1 and 1.5, at 400x480dp. Layoutlib renders a preview without `heightDp` at the device's height, so components can't wrap.

Inside, previews call `ScreenPreview`, which is `AppPreview(width = null, darkTheme = isSystemInDarkTheme())`, so the preview's size and `uiMode` drive the harness. `ComponentPreview` stacks components on the theme background. `AppPreview` fills only the width, so a component keeps its own height; screens fill the height themselves. The names carry the `Preview` prefix and the preview functions are private, as the Compose detekt rules require, and CPST runs private previews.

**Sample-driven previews with `@PreviewParameter`.** Per screen, one file `XScreenshots.kt` with:
- `@PreviewTest @PreviewScreenSizes @PreviewScreenVariants private fun XLoaded()`, the loaded sample.
- `@PreviewTest @PreviewPhoneSize private fun XStates(@PreviewParameter(XStates::class) state)`, where `class XStates : OtherSamples<XState>(XSamples.all)` serves `XSamples.all` minus loaded, so a new sample is picked up automatically. CPST numbers the files in the samples' order (`XStates_phone_…_0.png`); `getDisplayName` names them only in the IDE. A screen whose only sample is loaded (settings) has no states preview, since an empty provider fails to render.

Providers live next to the tests. Components get one file per group: adaptive controls, loading pieces, charts. `TimeSeriesChart` and `ZoneBars` become public for them.

**Coverage.** `ScreenshotCoverageTest` (composeApp jvmTest) lists every `*Ui.kt` with a `@CircuitInject` or `@SubCircuitInject` and fails for any without an `XScreenshots.kt` in `androidApp/src/screenshotTest`. `jvmTest` declares that directory as an input, so deleting a file reruns it.

**Compose Multiplatform resources under layoutlib.** On Android, `Res` strings need an Android context, which a preview gets through Compose Multiplatform's `PreviewContextConfigurationEffect()`. The harness calls it when `LocalInspectionMode` is true on Android (`PreviewResources`, an `expect` with no-op actuals elsewhere). Verified: the segment screen renders its strings under layoutlib.

**Comparison and update.**
- `:androidApp:updateDebugScreenshotTestDefaultTestSuite` writes references to `androidApp/src/screenshotTestDefaultDebug/reference`.
- `:androidApp:testDebugScreenshotTestDefaultTestSuite` compares them and writes the HTML report to `androidApp/build/reports/tests/testDebugScreenshotTestDefaultTestSuite/index.html`.
- The suite's `imageDifferenceThreshold` is set low (start at 0.0001 and adjust only if repeat runs flake).
- AGP doesn't wire the suite into `check`, so `:androidApp`'s `check` depends on `testDebugScreenshotTestDefaultTestSuite`.
- The suite's tasks are Gradle `Test`s rendering every screenshot in one JVM, which runs out of the default 512m heap; `:androidApp` gives them 4g. The whole suite (528 images) takes about four minutes.
- With Android resources in unit tests, the unit-test manifests merge the app's, so they get the `oauthRedirectScheme` placeholder too.
- The ktfmt plugin formats only AGP's classic source sets, so `:androidApp` registers `ktfmtFormatScreenshotTest` and `ktfmtCheckScreenshotTest` (under `ktfmtCheck` and `check`). Detekt already covers the suite's sources.

## Risks / Trade-offs

- **[Test suites and the engine are experimental, on an AGP release candidate]** → Both are pinned. Task names, the reference path and the release-variant workaround are AGP behaviour to recheck on each AGP bump; the `@PreviewTest` functions don't depend on them.
- **[Android look only]** → Desktop and web differ in typography and density, and iOS in its controls. These are covered by hand and by Compose Hot Reload screenshots, not by references.
- **[Layoutlib can't render native or platform views (MapLibre, `UIKitView`)]** → Maps fall back to `RoutePreview` under inspection, and iOS isn't rendered. Any other view that fails shows up as a render error in the first run, and gets an inspection-mode fallback.
- **[Repository growth]** → About 31 screens x 16 images, plus components: roughly 600 PNGs (528, 29MB, as recorded). Non-primary samples render only at phone size. Revisit with Git LFS if clones slow down.
- **[References recorded on one OS]** → Layoutlib rendering is largely OS-independent. If a second machine or CI disagrees, raise the threshold or record on CI.
- **[Slow `check`]** → Rendering is host-side but not free. Keep the screen matrix as specified, and run `testDebugScreenshotTestDefaultTestSuite` on its own while iterating.

## Migration Plan

Additive. The first run records every reference in one commit. Later reference updates go in the same commit as the UI change that caused them.
