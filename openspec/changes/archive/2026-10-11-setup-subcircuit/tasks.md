# Tasks

## 1. Dependencies

- [x] 1.1 Add `circuitx-subcircuit` and `circuitx-subcircuit-test` (`version.ref = "circuit"`) to `gradle/libs.versions.toml`, add them to `:app-screens` (api), `:app-presenters`, `composeApp` and `:app-presenters` commonTest (next to `circuit-test`), and opt in to `com.slack.circuit.subcircuit.ExperimentalSubCircuitApi` in those modules; verify `./gradlew format` leaves the dependency blocks sorted and `./gradlew :composeApp:compileKotlinJvm` succeeds

## 2. Metro wiring

- [x] 2.1 In `composeApp/.../app/di/CircuitBindings.kt`, add `@Multibinds(allowEmpty = true)` sets of `SubPresenterFactory`/`SubUiFactory` and a `@SingleIn(SessionScope::class)` `SubCircuit` provider, and expose `subCircuit` on `SessionGraph`; verify `./gradlew :composeApp:compileKotlinJvm :webApp:compileKotlinWasmJs` succeeds with the sets empty
- [x] 2.2 Provide `LocalSubCircuit provides graph.subCircuit` alongside the session `CircuitCompositionLocals` in `App.kt`; verify the desktop app launches, signs in and shows the feed (`./gradlew :desktopApp:run`)

## 3. All-platform spike

- [x] 3.1 Add a throwaway contract, an `@AssistedInject` presenter with a nested `@SubCircuitInject @AssistedFactory`, and a UI registered by a `@SubCircuitInject @Inject class : SubUi`, and render it with `SubCircuitContent` from an existing screen; verify it builds and renders on jvm (desktop), android (`:androidApp:installDebug`), iOS (the `xcodebuild` simulator build in CLAUDE.md) and wasmJs (`:webApp:wasmJsBrowserDevelopmentRun`), and that a `circuitx-subcircuit-test` jvm test of the presenter passes
- [x] 3.2 Delete the spike and note any Metro or SubCircuit gotchas it surfaced for 4.1; verify `git status` shows no spike files and `./gradlew :composeApp:compileKotlinJvm` still succeeds

## 4. Conventions

- [x] 4.1 Document SubCircuit anatomy in `.claude/rules/compose-screens.md`:
  - the contract (SubScreen with initial config only, State, Event, OuterEvent) in `:app-screens`
  - the `@AssistedInject` presenter class with a nested `@SubCircuitInject @AssistedFactory` in `:app-presenters`, as an exception limited to SubPresenters
  - the plain UI function plus the `@SubCircuitInject @Inject class XSubUi : SubUi` that registers it in `composeApp` (the function form crashes Kotlin/Wasm)
  - screen down, outer events up, with the host keeping the screen stable via `rememberRetained`
  - testing with `circuitx-subcircuit-test`
  - the spike's gotchas

  Add one line to CLAUDE.md's "Screens span three modules". Verify by checking each documented step against the code the spike needed.

## 5. Integration checks

- [x] 5.1 Run `./gradlew check` and verify it passes (including ktfmt, detekt and sorted dependencies)

## Workflow follow-up

- Apply and archive this change before starting `add-feed-filters`, which depends on it.
