---
name: new-screen
description: Scaffold a new Circuit screen across :app-screens, :app-presenters and composeApp (Screen, State, Event, presenter, UI, navigation entry and a presenter test) following this repo's Metro + Circuit conventions. Use when the user asks to add a screen, page or view to the app.
argument-hint: <ScreenName> [session|app] [feature-package]
---

# New Circuit screen

Arguments: `$ARGUMENTS`. Defaults: scope `session` (signed-in), and a feature package named after the screen. Use `app` scope only for screens shown while signed out.

## 1. Read before writing

Read one existing screen (all three of its files) that resembles the new one: `profile/Profile*` (loads data plus a list), `record/ManualActivity*` (a form that writes), or `login/Login*` (`AppScope`, class presenter). Copy its imports and layout idioms rather than inventing new ones. Look at `composeApp/.../app/ui/` for shared components (`ActivityCard`, `Format.kt` unit formatting) before writing new ones.

## 2. Write three files in the same feature package `io.github.solcott.activitytracker.app.<pkg>`

Prefix each path with `src/commonMain/kotlin/io/github/solcott/activitytracker/app/<pkg>/`:

| Module | File | Holds |
|---|---|---|
| `app/screens` | `<Name>Screen.kt` | `@CircuitSerializable` Screen, State, Event |
| `app/presenters` | `<Name>Presenter.kt` | the `@CircuitInject` presenter |
| `composeApp` | `<Name>Ui.kt` | the `@CircuitInject` UI and its private composables |

The presenter module has no foundation, ui or material3, so keep every UI import in `<Name>Ui.kt`. The UI never imports the presenter.

```kotlin
// app/screens/.../<Name>Screen.kt
/** One-line purpose; document non-obvious args (e.g. "[actor] is a DID or handle"). */
@CircuitSerializable(SessionScope::class) data class FooScreen(val uri: String) : Screen   // or data object if no args

@Immutable
data class FooState(
    // plain immutable values only; null/empty for "not loaded". Collections are ImmutableList, never List.
    val items: ImmutableList<ActivityView>,
    val error: String?,
    val eventSink: (FooEvent) -> Unit,
) : CircuitUiState

sealed interface FooEvent {
    data object Back : FooEvent
}

// app/presenters/.../<Name>Presenter.kt
@CircuitInject(FooScreen::class, SessionScope::class)
@Composable
fun FooPresenter(screen: FooScreen, navigator: Navigator, /* injected repositories */): FooState {
    // remember/collectAsState for state; LaunchedEffect + runSuspendCatching for loads;
    // rememberCoroutineScope().launch { runSuspendCatching { ... } } for event-driven work
    // Convert lists once where they're loaded: flow.map { it.toImmutableList() }, or list.toImmutableList()
    return FooState(items, error) { event ->
        when (event) {
            FooEvent.Back -> navigator.pop()
        }
    }
}

// composeApp/.../<Name>Ui.kt
@CircuitInject(FooScreen::class, SessionScope::class)
@Composable
fun FooUi(state: FooState, modifier: Modifier = Modifier) { /* Scaffold + TopAppBar like siblings */ }
```

Constraints:
- Use the **same scope** in all three annotations. A mismatch compiles and then crashes when navigation state is saved.
- Screen args are serializable primitives (an AT-URI string, a DID or handle), never `ActivityView` or other view models.
- Don't use a presenter class with `@AssistedInject`. Use a plain `@Inject` class only if it needs neither `screen` nor `navigator`.
- Inject repositories from `core/data` (`FeedRepository`, `ActivityRepository`, `ProfileRepository`, `AuthRepository`). Never call XRPC or Room directly from a presenter. If the screen needs new data, add a repository method first, and if that needs a new AppView query, use the `lexicon-change` skill.
- Writes go through `ActivityRepository` (outbox) and update the UI optimistically. Don't wait for the AppView.
- `runSuspendCatching`, not `runCatching`.
- No `remember` of mutable collections in State. Every field of `FooState` must be statically stable: `@Immutable` on the State, `ImmutableList` (kotlinx-collections-immutable) instead of `List`, and `@Immutable` on any model from a non-Compose module that a UI shows. `./gradlew :app-screens:composeStabilityCheck` fails otherwise.

## 3. Wire navigation

Add an event to the screen that leads here, and handle it in that screen's presenter with `navigator.goTo(FooScreen(...))`. There is no route table to register; Metro generates the factories and the serializer registration.

## 4. Test

Add `app/presenters/src/jvmTest/kotlin/io/github/solcott/activitytracker/app/FooPresenterTest.kt`, modelled on `LoginPresenterTest`. Call the presenter with `Presenter.test {}` from `circuit-test`, using a `FakeNavigator` when navigation matters, a `inMemoryDatabase()` and a Ktor `MockEngine` for the AppView. Cover the initial state, one success path, and one failure that surfaces `error`.

## 5. Verify

Use the `gradle-runner` agent:
1. `:app-presenters:compileKotlinJvm :composeApp:compileKotlinJvm`. Metro/Circuit codegen errors (wrong scope, missing binding) appear here.
2. `:app-presenters:jvmTest --tests "*.FooPresenterTest"`. Confirm a non-zero test count.
3. `format`, then `:app-screens:detekt :app-presenters:detekt :composeApp:detekt`. The Compose rules flag modifier ordering and unstable params.

If the user wants to see the screen, launch the web preview (`.claude/launch.json` → `web`) against the local stack (the `local-stack` skill) and navigate to it.
