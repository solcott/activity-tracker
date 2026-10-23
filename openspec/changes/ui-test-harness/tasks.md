# Tasks

## 1. Deterministic seams

- [x] 1.1 Add `darkTheme: Boolean = isSystemInDarkTheme()` to `AppTheme`. Verify that the app builds and that a JVM UI test rendering `AppTheme(darkTheme = true)` reads the dark `background` colour from `MaterialTheme.colorScheme`.
- [x] 1.2 Add `LocalClock` and `LocalTimeZone` (defaults `Clock.System`, `TimeZone.currentSystemDefault()`) next to `LocalUnitSystem`. Switch `ProfileUi`, `EditProfileUi`, `EditGearUi`, `FeedFilterUi` and `DataSourcesUi` to them. Verify with a UI test: a profile's age is computed from a fixed `LocalClock` instant, and a device-less data source is named by its start in a set `LocalTimeZone`.
- [x] 1.3 Rename the `uses24HourClock` expect/actuals to `platformUses24HourClock`, and add `LocalHourClock: Boolean?` and a common `uses24HourClock()` that prefers it. Verify a UI test of `StartTimeText` showing "14:05" and "2:05 PM" for the two settings, and that the Android, iOS, JVM and wasm targets compile (`./gradlew :composeApp:compileKotlinJvm :composeApp:compileKotlinWasmJs :composeApp:compileKotlinIosSimulatorArm64` and the Android compile).
- [x] 1.4 Make the non-web `ActivityMap` draw `RoutePreview` when `LocalInspectionMode` is true. Verify a JVM UI test that renders a route map under inspection without error, with no network.
- [x] 1.5 Add `NoSystemClockInUi` to `:detekt-rules` and enable it in `config/detekt/detekt.yml`. Verify the rule tests (`./gradlew :detekt-rules:test`), and that `./gradlew detekt` passes on the changed sources.

## 2. Fixtures module

- [x] 2.1 Create `:app-fixtures` (`app/fixtures`, KMP library convention, client targets) depending only on `:app-screens`, `:core-lexicon` and `uistate`, and add it to `settings.gradle.kts`. Verify `./gradlew :app-fixtures:compileKotlinJvm` and `./gradlew sortDependencies` (or `format`) leave the build files clean.
- [x] 2.2 Add the `fake…` lexicon view builders (profile basic and detailed, activity, segment, comment, gear item, effort) with valid fixed defaults. Verify a jvmTest that serialises each with `AtJson` and decodes it again.
- [x] 2.3 Add `ContentStates` and `PagedStates` builders for every ui/content-loading state. Verify a jvmTest that each builder's `hasLoaded`/status matches the state it names.
- [x] 2.4 Add `XSamples` for every content-loading screen (feed, segment, activity and profile screens, form screens, paged lists, per `ui/content-loading`'s groups) and for the remaining screens' main state. Each has an `all` map, `{}` sinks, and `TextFieldState`s created per access. Verify `./gradlew composeStabilityCheck` passes with `:app-fixtures` included.

## 3. Harness

- [x] 3.1 Add `AppPreview` (`composeApp` `ui/preview/`) with the parameters and defaults in design.md. It provides inspection mode, the seams, a recording `LocalUriHandler`, Coil's preview handler and a width-sized box. Verify a UI test that renders a screen at Compact and Expanded and finds each layout's distinguishing node.
- [x] 3.2 Add the harness `SubCircuit` (real `FeedFilterSubUi`/`ReportSubUi`, presenters that return `subStates`, and nothing rendered for unknown sub-screens). Verify a UI test that renders the feed's loaded sample with its filter bar.
- [x] 3.3 Wire `compose-ui-test` and desktop `currentOs` into `jvmTest` through the Compose convention's opt-in, with `composeApp` and `:app-fixtures` as users. Move `SegmentCreditUiTest` and `ChartReadoutUiTest` onto `AppPreview` and the fixtures. Verify `./gradlew :composeApp:jvmTest` and `./gradlew -p build-logic :convention:test` pass.

## 4. Anchors and conformance

- [x] 4.1 Add `TestTags`, and tag `ContentStateLayout`, `RefreshStatus`, `pagedFooter` and the `AppTopBar` back button (both actuals). Verify UI tests that find each anchor on a sample with the matching state, and an iOS compile.
- [x] 4.2 Add `ContentLoadingConformanceTest`: a table of the content-loading screens with their samples, the ui/content-loading assertions per sample kind, and the reflection completeness check. Verify it passes, and that removing one screen's entry makes it fail and name that screen.
- [x] 4.3 Update `.agents/rules/compose-screens.md` (seams, harness, fixtures, anchors, "a new screen ships samples and a UI test"), the `new-screen` skill (scaffold `XSamples` and a UI test) and `AGENTS.md` (the new module). Verify that `scripts/check-agent-sync.sh` passes and that the `new-screen` skill's steps name the samples file.

## 5. Integration

- [x] 5.1 Run `./gradlew check` and verify it passes.
- [ ] 5.2 Run `:desktopApp:hotRun` and confirm the app still uses the system theme, clock and real maps (production defaults unchanged), with a Compose Hot Reload screenshot of an activity page with a map.

## Workflow follow-up

- `compose-screenshot-tests` builds on this change. Apply it after this one lands.
- Archive with `/opsx:archive` once implemented and reviewed.
