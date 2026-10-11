# Design

## Context

See proposal.md for motivation.

What already exists:
- **Circuit instances.** `CircuitBindings.kt` builds `AppCircuit`/`SessionCircuit` from Metro multibound `Presenter.Factory`/`Ui.Factory` sets. `App.kt` renders the session circuit under a `key(did)` `SessionGraph`.
- **Code generation.** Metro 1.4.5 generates Circuit factories itself (`metro.enableCircuitCodegen=true`, no KSP), and its `CircuitClassIds` include `SubCircuitInject`, `SubPresenterFactory` and `SubUiFactory`. Detekt's `ComposableNaming` already ignores `SubCircuitInject` (`config/detekt/detekt.yml:36`).
- **The library.** `circuitx-subcircuit` 0.39.0 targets android, jvm, iosArm64, iosSimulatorArm64 and wasmJs, among others. Its API:
  - contracts: `SubScreen<OuterEvent>`, `SubCircuitUiState`
  - presenters and UIs: `SubPresenter.present(outerEventSink)`, `SubUi.Content(state, modifier)`
  - wiring: `SubCircuit.builder()`, `LocalSubCircuit`
  - rendering: `SubCircuitContent(screen, outerEventSink, modifier, contentKey = screen)`
  - All of it sits behind `@ExperimentalSubCircuitApi`.

## Goals / Non-Goals

**Goals:**
- Injecting a sub-screen needs only annotations, the same as a top-level screen.
- One documented anatomy, so the first feature (feed filters) and later ones look the same.

**Non-Goals:**
- An AppScope (signed-out) `SubCircuit`. Add it when a login-side screen needs one.
- Any real sub-screen. Feed filters is the first.

## Decisions

### 1. Session-scoped `SubCircuit` next to `SessionCircuit`
- `@Multibinds(allowEmpty = true)` sets of `SubPresenterFactory` and `SubUiFactory`.
- `@Provides @SingleIn(SessionScope::class) fun subCircuit(presenters, uis): SubCircuit`.
- `SessionGraph` exposes `subCircuit`. `App.kt` provides `LocalSubCircuit provides graph.subCircuit` inside the existing session `CircuitCompositionLocals`.

Why session scope: sub-presenters inject session-scoped repositories, and the graph is dropped on sign-out together with the session circuit.

`allowEmpty` keeps the graph valid before the first sub-screen exists, and in `webApp`, whose graph aggregates the same factories.

### 2. Anatomy and the `@AssistedInject` exception
- **Contract** in `:app-screens`:
  - `@Immutable` `SubScreen<OuterEvent>`
  - State (`SubCircuitUiState`, stable)
  - UI Event and OuterEvent
- **Presenter** in `:app-presenters`: a class implementing `SubPresenter`, because SubCircuit has no function form.
  - Without the screen: an `@Inject` class with `@SubCircuitInject` on the class.
  - With the screen: `@AssistedInject constructor(@Assisted screen, deps…)` with `@SubCircuitInject(Screen::class, SessionScope::class)` on the nested `@AssistedFactory`. *Found in the spike:* on the class, Metro calls the constructor directly and the build crashes (`Null argument in ExpressionCodegen`). Circuit's KDoc shows the class form for its KSP processor.
  - This exception is limited to SubPresenters. Regular presenters stay `@CircuitInject` functions.
- **UI** in `composeApp`: a plain `@Composable fun XUi(state, modifier = Modifier)`, registered by `@SubCircuitInject(Screen::class, SessionScope::class) @Inject class XSubUi : SubUi<State>` delegating to it.
  - *Found in the spike:* putting `@SubCircuitInject` on the function makes Metro generate a factory holding a function reference, which crashes the Kotlin/Wasm backend (`UpgradeCallableReferences`, `IndexOutOfBoundsException`). JVM, Android and iOS are unaffected, and Metro 1.4.5 is the latest release. A class target avoids the reference, as in solcott/Countries' `SearchAndFilterSubUi`. A hand-written `SubUiFactory` also worked, but needs more boilerplate.
- **Communication**: a parent talks to a child only through the screen (down) and outer events (up).
  - The screen carries only *initial* config, never live state. `SubCircuitContent` keys on the screen, so a changing screen remounts the child and drops its state.
  - The host keeps the screen instance stable with `rememberRetained`.
- **Tests**: `circuitx-subcircuit-test` `.test {}` with `outerEvents.awaitEvent()`.

### 3. Verification without a real consumer
Metro checks the graph when it compiles, so a missing binding or a broken generated factory fails each target's compile. That leaves two checks:
- **Per-target compile and launch.** This guards the wiring from now on, because missing or empty-set bindings fail the build.
- **A one-time spike.** A throwaway `@SubCircuitInject` presenter and UI is rendered from an existing screen, built on all four platforms, then deleted. This proves the codegen generates a sub-screen correctly before a feature relies on it, and anything it uncovers goes into the docs.

Alternative considered: keeping a sample sub-screen in the app. Rejected, because it would be dead code in production, and feed filters gives a real consumer soon enough.

## Risks / Trade-offs

- **The API is `@ExperimentalSubCircuitApi`** → Circuit upgrades may break it. The opt-in and wiring are confined to `CircuitBindings.kt`/`App.kt` and to sub-screen files.
- **The `@AssistedInject` exception could spread to regular presenters** → the rules doc limits it to SubPresenters explicitly.
- **The Kotlin/Wasm crash on function-target `@SubCircuitInject` is caught only by a wasm build** → the rules doc forbids the function form, and `check`/CI compile wasm. Revisit when Metro or Kotlin fixes it.
- **Once the spike is deleted, nothing exercises a real sub-screen until feed filters lands** → acceptable, because feed filters is the next change and its tests cover the path end to end.

## Migration Plan

Additive. There is no data or API change, and rolling back means reverting the commit.
