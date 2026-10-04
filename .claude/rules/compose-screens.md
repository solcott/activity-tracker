---
paths:
  - "app/**"
  - "composeApp/**"
  - "webApp/src/**"
  - "androidApp/src/**"
  - "iosApp/**"
---

# Screens, Circuit and Metro

Use the `new-screen` skill to scaffold a screen. The rules it follows:

- A screen is a `@CircuitSerializable(<scope>::class) data object/class XScreen : Screen`, plus an `XState : CircuitUiState` with an `eventSink`, an `XEvent` sealed interface, a presenter and a UI. Screen args must be serializable: pass IDs and AT-URI strings, not views.
- Use the same scope in `@CircuitSerializable` and both `@CircuitInject`s. `SessionScope` is for signed-in screens. `AppScope` is only for signed-out ones such as login. A mismatched or missing registration compiles fine but crashes at runtime the first time navigation state is saved.
- The presenter is a `@CircuitInject(XScreen::class, <scope>::class) @Composable fun XPresenter(screen, navigator, ...deps): XState`. Circuit supplies `screen`/`navigator`, and Metro injects the rest. Don't write a presenter class with `@AssistedInject`. A class with plain `@Inject` is fine only when it needs neither screen nor navigator (like `LoginPresenter`).
- The UI is `@CircuitInject(XScreen::class, <scope>::class) @Composable fun XUi(state: XState, modifier: Modifier = Modifier)`.
- **One screen spans three modules, all in the same feature package `io.github.solcott.activitytracker.app.<pkg>`:** `XScreen.kt` (Screen, State, Event) in `app/screens`, `XPresenter.kt` in `app/presenters`, and `XUi.kt` in `composeApp`. Presenters and UIs see each other only through the contracts. `:app-presenters` has the Compose runtime but no foundation, ui or material3, so it can't hold UI. `composeApp` depends on `:app-presenters` only so its graphs aggregate the factories. Never import a presenter from UI code. Shared composables (`ui/`, `SportPicker`) and `expect` UI helpers (`LocationPermission`) stay in `composeApp`.
- Metro generates the Circuit factories (`metro.enableCircuitCodegen=true`). There is no Circuit KSP processor, so don't add one.
- Wrap suspend calls in `runSuspendCatching`, not `runCatching`, so cancellation propagates. Writes go through repositories (`ActivityRepository` → outbox), never straight to XRPC from a presenter.
- **Graphs.** `AppScope` is app-wide. `SessionScope` is the `SessionGraph` extension created per signed-in account (`key(did)` in `App.kt`). The concrete `@DependencyGraph`s are `AndroidAppGraph`/`IosAppGraph` in `composeApp` platform source sets and `WebAppGraph` in `webApp`. A module that declares a graph needs every contributing module (including `:app-screens` and `:app-presenters`) on its compile classpath. `androidApp` can't use Metro intrinsics; it calls `createAndroidAppGraph` from `composeApp`.
- **Stability.** Every `State` is `@Immutable`, and its collections are `ImmutableList` (kotlinx-collections-immutable; convert with `toImmutableList()` where the presenter loads them), never `List`. Models from non-Compose modules that a UI shows (lexicon views, `AuthCoordinator.Status`) carry `@Immutable` from `androidx.compose.runtime:runtime-annotation`. Composable params must be statically stable, so pass `ImmutableList`s and annotated types, not `List`, `vararg` or plain interfaces (`@Stable` it if one must be a param, like `AppGraph`). `composeStabilityCheck` (part of `check`) enforces this from the compiler reports in `build/compose/reports`; `:app-screens` also runs it in strict mode, which fails on any field that isn't statically stable.
- **Tests.** Presenter tests live in `app/presenters/src/jvmTest` and use `circuit-test`'s `Presenter.test {}` with a Room `inMemoryDatabase()` and a Ktor `MockEngine` (see `LoginPresenterTest`).
