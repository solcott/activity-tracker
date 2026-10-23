# Design

## Context

- **The UIs.** All 31 `@CircuitInject` UIs are `XUi(state: XState, modifier: Modifier)` and inject nothing (`compose-screens.md`).
- **What a test can already pin.** `LocalUiPlatform` (`ui/theme/UiPlatform.kt`) and `LocalUnitSystem` (`ui/Format.kt`) are CompositionLocals, so a test can already choose the platform look and the units.
- **What a UI reads that a test can't pin:**
  - `isSystemInDarkTheme()` inside `AppTheme`.
  - `Clock.System.now()` in `ProfileUi`, `EditProfileUi`, `EditGearUi` and `FeedFilterUi`.
  - `TimeZone.currentSystemDefault()` in `DataSourcesUi`.
  - `uses24HourClock()`, an `expect` in `ui/HourClock.kt`.
  - MapLibre on non-web targets (`ActivityMap.nonWeb.kt`) and Coil `AsyncImage`.
  - `LocalSubCircuit`, provided only in `App.kt`, for the feed filter and report sub-screens.
- **Rendering without MapLibre.** The web build already draws maps with `RoutePreview` (commonMain, pure Compose).
- **Existing UI tests.** UI tests run on the JVM (`composeApp` `jvmTest`, `runComposeUiTest` v2). Wasm tests are disabled.
- **Where the harness must work.** `compose-screenshot-tests` will render the same harness and fixtures from `:androidApp`'s `screenshotTest` source set under layoutlib, which sets `LocalInspectionMode`. So the harness must live in production source sets, not in a test source set.

## Goals / Non-Goals

**Goals:**
- Production behaviour is unchanged: every new seam defaults to today's system value.
- One harness call per test or preview, with defaults that need no arguments.
- Fixtures any client module's tests can use, without Room, Ktor or `composeApp`.

**Non-Goals:**
- Screenshot tooling (`compose-screenshot-tests`).
- Rendering the iOS controls on the JVM. The adaptive wrappers stay `expect`/`actual`.
- Tagging every screen's own content. Screens are still asserted by text and semantics roles.

## Decisions

**Seams are CompositionLocals that default to the system.**
- `LocalClock` (a `kotlin.time.Clock`, default `Clock.System`) and `LocalTimeZone` (default `TimeZone.currentSystemDefault()`) sit next to `LocalUnitSystem`.
- `LocalHourClock` is a `Boolean?` that defaults to `null`. The `expect` becomes `platformUses24HourClock()`. A common `uses24HourClock()` returns the local's value when it's set and the platform's answer otherwise, so `StartTimeText` and other callers don't change.
- Reads in `remember` keep their current semantics (`remember { clock.now() }`).
- Alternatives considered:
  - Passing `now` through `State` from presenters. Presenters already inject a `Clock`, but these are display-only values, and threading them through four `State`s and their presenters for age and date-picker bounds adds noise to contracts.
  - Plain parameters on each UI. A screen can't pass them to deep helpers without changing signatures.

**`AppTheme(darkTheme: Boolean = isSystemInDarkTheme())`.** This is the conventional Material shape. The platform still comes from `LocalUiPlatform`.

**Maps fall back to `RoutePreview` under inspection.** The non-web `ActivityMap` actual checks `LocalInspectionMode.current` and returns `RoutePreview(polyline, modifier, height, gaps, ends, highlight)`, as web does today. A test sees the route, layoutlib never touches MapLibre's `AndroidView`, and nothing fetches a style.
- Alternative considered: a `LocalMapRenderer`. It adds API that only tests would use, and inspection mode is already the signal that previews and screenshot tools set.

**Images use Coil's `LocalAsyncImagePreviewHandler`.** The harness provides a handler that returns a flat placeholder (optionally the URL's hash as a colour, so different images differ). No UI code changes.

**The harness: `AppPreview` in `composeApp` `commonMain` (`ui/preview/`).**
- Parameters, each with a default:
  - `platform` (default Android)
  - `darkTheme` (false)
  - `units` (Metric)
  - `width` (`WindowWidth.Compact` → 400dp; Medium 700dp; Expanded 1000dp; `null` fills the available space, for previews that set their own size)
  - `now` (a fixed instant, `2026-06-01T12:00Z`)
  - `timeZone` (UTC)
  - `use24Hour` (true)
  - `isNavRoot` (false)
  - `subStates` (see below)
- It provides `LocalInspectionMode = true`, the locals above, a recording no-op `LocalUriHandler`, the image preview handler and a `LocalSubCircuit`. It wraps the content in `AppTheme` and a box of the chosen width.
- It lives in production code because `:androidApp`'s screenshot tests and Compose previews need it. It's a few dozen lines that nothing in the app calls.
- Alternative considered: a test-only source set. KMP has no `testFixtures`, and a source set in `composeApp` can't be seen from `:androidApp`.

**Sub-screens in the harness.**
- The harness builds a `SubCircuit` from the real sub-UIs: `FeedFilterSubUi` and `ReportSubUi` have no dependencies.
- It adds a presenter factory that returns a fixed state per sub-screen class from `subStates`, defaulting to each sub-screen's default sample.
- An unknown sub-screen renders nothing instead of crashing.
- The exact `circuitx-subcircuit` factory API is confirmed while implementing. The behaviour is set by the spec.

**Fixtures: a new `:app-fixtures` module (`app/fixtures`, KMP library convention, all client targets).**
- Depends on `:app-screens`, `:core-lexicon` and `uistate`, nothing else, so it adds no Room or Ktor.
- **`Fake` builders** for lexicon views: `fakeProfileBasic()`, `fakeActivityView()`, `fakeSegmentView()`, `fakeCommentView()`… Every parameter has a valid default: stable DIDs, CIDs, TID rkeys and a fixed `createdAt`.
- **`ContentStates`** builders: `loading()`, `loaded(x)`, `refreshing(x)`, `failed(kind, reason)`, `refreshFailed(x, kind)`. **`PagedStates`** builders: first page, loading more, page failed, end.
- **`XSamples` objects**, one per screen. Each has named `XState` values and an `all` map (name → state) for the conformance test and previews. Event sinks are `{}`. `TextFieldState`s are created per access (a getter), because they're mutable.
- **Users:**
  - `composeApp` `jvmTest`.
  - `:app-presenters` `jvmTest`, for lexicon builders. Existing `TestSegments.kt`/`TestZones.kt` move over when touched, not in bulk.
  - `:androidApp`'s screenshot tests.
- Alternative considered: fixtures in `:app-screens`. They would ship in every client and pull test data into contracts.

**Anchors: one `TestTags` object in `composeApp` `ui/`.**
- String constants: `Loading`, `LoadError`, `Retry`, `Refreshing`, `RefreshError`, `PageLoading`, `PageError`, `PageRetry`, `ListEnd`, `Back`.
- Applied with `Modifier.testTag` in `ContentStateLayout`, `RefreshStatus`, `pagedFooter` and the `AppTopBar` back button, both `ios` and `nonApple` actuals. They carry no meaning beyond tests and don't change semantics for screen readers.
- `testTagsAsResourceId` stays off until a device-level test needs it.

**Conformance test: `ContentLoadingConformanceTest` in `composeApp` `jvmTest`.**
- A table lists each content-loading screen with its samples and its UI function. For each sample kind the screen has, the test renders it in the harness and asserts the ui/content-loading rules through the anchors:
  - `loading` → `Loading` shown, no empty text
  - `failed` → `LoadError` + `Retry`
  - `refreshFailed` → content node present + `RefreshError`, no `LoadError`
- Completeness: it uses JVM reflection over `:app-screens` to find every `CircuitUiState` with a `ContentState` or `PagedState` property, and fails for any that is missing from the table, naming it.

**Detekt rule `NoSystemClockInUi`** in `:detekt-rules`, modelled on `UseAdaptiveComponents`.
- It flags `Clock.System` and `TimeZone.currentSystemDefault()` in `composeApp` source files, except in the file that defines the locals.
- The message names `LocalClock`/`LocalTimeZone`.
- It is enabled in `config/detekt/detekt.yml`, with a rule test like `UseAdaptiveComponentsTest`.

**Build wiring.** The Compose convention (`KmpComposeConventionPlugin`) adds `compose-ui-test` and `compose.desktop.currentOs` to `jvmTest` for modules that opt in through a small extension flag. `composeApp` opts in, and its hand-written `jvmTest` dependencies go away. `:app-fixtures` is added per module, since it's a project dependency.

## Risks / Trade-offs

- **[Samples drift from what presenters produce]** → Samples use the same `@Immutable` types, and `composeStabilityCheck` covers `:app-fixtures`. Presenter tests that build expected states from the fixtures catch shape drift.
- **[The placeholder map hides map bugs in UI tests]** → Map behaviour is covered by its own specs and checked by hand on devices. UI tests check layout around the map, not the map.
- **[The harness ships in production code]** → It's small, has no dependencies beyond `composeApp`'s own, and is dead code in release builds. R8 strips it on Android.
- **[The reflection-based completeness check is brittle]** → It looks only for `ContentState`/`PagedState` property types. A screen that loads content any other way already breaks the `compose-screens.md` rule.
- **[Locals read in `remember` don't update when the local changes]** → This matches today's `remember { Clock.System.now() }`. Tests set the local before composition.

## Migration Plan

Additive. Defaults keep production behaviour. Existing UI tests move to the harness in the same change. Nothing to roll back beyond reverting.
