---
paths:
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
- The UI is `@CircuitInject(XScreen::class, <scope>::class) @Composable fun XUi(state: XState, modifier: Modifier = Modifier)`. Everything lives in one `XScreen.kt` in a feature package under `composeApp/src/commonMain/.../app/`.
- Metro generates the Circuit factories (`metro.enableCircuitCodegen=true`). There is no Circuit KSP processor, so don't add one.
- Wrap suspend calls in `runSuspendCatching`, not `runCatching`, so cancellation propagates. Writes go through repositories (`ActivityRepository` → outbox), never straight to XRPC from a presenter.
- **Graphs.** `AppScope` is app-wide. `SessionScope` is the `SessionGraph` extension created per signed-in account (`key(did)` in `App.kt`). The concrete `@DependencyGraph`s are `AndroidAppGraph`/`IosAppGraph` in `composeApp` platform source sets and `WebAppGraph` in `webApp`. A module that declares a graph needs every contributing module on its compile classpath. `androidApp` can't use Metro intrinsics; it calls `createAndroidAppGraph` from `composeApp`.
- **Tests.** Presenter tests live in `composeApp/src/jvmTest` and use `circuit-test`'s `Presenter.test {}` with a Room `inMemoryDatabase()` and a Ktor `MockEngine` (see `LoginPresenterTest`).
