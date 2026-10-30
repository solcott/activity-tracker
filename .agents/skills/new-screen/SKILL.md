---
name: new-screen
description: Scaffold a new Circuit screen across a feature's api, presenter and ui modules (feature/<pkg>/…) and :app-fixtures (Screen, State, Event, presenter, UI, navigation entry, sample states, a presenter test and a UI test) following this repo's Metro + Circuit conventions. Use when the user asks to add a screen, page or view to the app.
argument-hint: <ScreenName> [session|app] [feature-package]
---

# New Circuit screen

Arguments: `$ARGUMENTS`. Defaults: scope `session` (signed-in), and a feature package named after the screen. Use `app` scope only for screens shown while signed out.

## 1. Read before writing

Read one existing screen (all three of its files) that resembles the new one: `profile/Profile*` (loads data plus a list), `record/ManualActivity*` (a form that writes), or `login/Login*` (`AppScope`, class presenter). Copy its imports and layout idioms rather than inventing new ones. Look at `:app-common-ui` (`app/common/ui/.../app/ui/`: `ActivityCard`, `Format.kt` unit formatting), `:ui-designsystem` (`ui/adaptive`, charts) and `:app-common-presenter` (`rememberSingleFlight`, `uiErrorOf`) for shared pieces before writing new ones; anything a second feature will need goes there, not into the feature.

## 2. Write three files in the same feature package `io.github.solcott.activitytracker.app.<pkg>`

A new feature gets its own modules: `feature/<pkg>/api`, `feature/<pkg>/presenter` and `feature/<pkg>/ui`, each with a `build.gradle.kts` of just `plugins { id("activitytracker.feature.api") }` (`.presenter`, `.ui`), plus `implementation(projects.feature<Other>Api)` in the presenter for each other feature whose screen it opens (`implementation(projects.appScreens)` for one that hasn't moved yet). Register the three in `settings.gradle.kts`' list, and add `-presenter` and `-ui` to `composeApp`'s, `webApp`'s and `desktopApp`'s dependencies (Metro aggregates their factories there), `-api` to `:app-fixtures`, and `-ui` to `androidApp`'s screenshot suite. A screen in a feature that hasn't moved yet (its package is still in `app/screens`) goes beside its siblings in `app/screens`, `app/presenters` and `composeApp` instead.

Prefix each path with `src/commonMain/kotlin/io/github/solcott/activitytracker/app/<pkg>/`:

| Module | File | Holds |
|---|---|---|
| `feature/<pkg>/api` | `<Name>Screen.kt` | `@CircuitSerializable` Screen, State, Event |
| `feature/<pkg>/presenter` | `<Name>Presenter.kt` | the `@CircuitInject` presenter |
| `feature/<pkg>/ui` | `<Name>Ui.kt` | the `@CircuitInject` UI and its private composables |

The presenter module has no ui or material3 (foundation only for `TextFieldState`), so keep every UI import in `<Name>Ui.kt`. The UI never imports the presenter, and `checkModuleBoundaries` fails the build if either module depends on the other. A text field the presenter reads is a `TextFieldState` in State from `rememberTextFieldState()` in the presenter, not a `String` plus a changed event (see `compose-screens.md`).

```kotlin
// feature/<pkg>/api/.../<Name>Screen.kt
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

// feature/<pkg>/presenter/.../<Name>Presenter.kt
@CircuitInject(FooScreen::class, SessionScope::class)
@Composable
fun FooPresenter(screen: FooScreen, navigator: Navigator, /* injected repositories */): FooState {
    // remember/collectAsState for state; LaunchedEffect + runSuspendCatching for loads;
    // val flights = rememberSingleFlight(); flights.launch(FooEvent.Save) { runSuspendCatching { ... } } for event-driven work
    // (a repeat tap is dropped while it runs; saving = flights.isRunning(FooEvent.Save)), never rememberCoroutineScope()
    // Convert lists once where they're loaded: flow.map { it.toImmutableList() }, or list.toImmutableList()
    return FooState(items, error) { event ->
        when (event) {
            FooEvent.Back -> navigator.pop()
        }
    }
}

// feature/<pkg>/ui/.../<Name>Ui.kt
@CircuitInject(FooScreen::class, SessionScope::class)
@Composable
fun FooUi(state: FooState, modifier: Modifier = Modifier) { /* AppScaffold + AppTopBar(title, onBack = ...) and ui/adaptive components, like siblings; its padding goes to the list as content padding; a scrolling screen adds rememberAppTopBarScrollBehavior() */ }
```

Constraints:
- Use the **same scope** in all three annotations. A mismatch compiles and then crashes when navigation state is saved.
- Screen args are serializable primitives (an AT-URI string, a DID or handle), never `ActivityView` or other view models.
- Don't use a presenter class with `@AssistedInject`. Use a plain `@Inject` class only if it needs neither `screen` nor `navigator`.
- Inject repositories from `core/data` (`FeedRepository`, `ActivityRepository`, `ProfileRepository`, `AuthRepository`). Never call XRPC or Room directly from a presenter. If the screen needs new data, add a repository method first, and if that needs a new AppView query, use the `lexicon-change` skill.
- Writes go through `ActivityRepository` (outbox) and update the UI optimistically. Don't wait for the AppView.
- `runSuspendCatching`, not `runCatching`.
- No `remember` of mutable collections in State. Every field of `FooState` must be statically stable: `@Immutable` on the State, `ImmutableList` (kotlinx-collections-immutable) instead of `List`, and `@Immutable` on any model from a non-Compose module that a UI shows. `./gradlew :feature-<pkg>-api:composeStabilityCheck` fails otherwise.

## 3. Wire navigation

Add an event to the screen that leads here, and handle it in that screen's presenter with `navigator.goTo(FooScreen(...))`. There is no route table to register; Metro generates the factories and the serializer registration.

## 4. Samples

Add `app/fixtures/src/commonMain/kotlin/io/github/solcott/activitytracker/app/<pkg>/<Name>Samples.kt`: an `object FooSamples` with a named `FooState` per state the screen shows and an `all` map keyed by `SampleKind`, modelled on `FeedSamples` (content) or `FollowListSamples` (a paged list). Build lexicon views with the `fake…` builders, states with `contentSamples`/`pagedSamples` (or `ContentStates`/`PagedStates`), and pass `{}` sinks. Make each sample a getter, so a `TextFieldState` is new each time. A screen that loads content covers loading, failed, loaded, refreshing and refresh failed (plus empty for a list, and the page kinds for a paged one); a form adds an invalid sample.

## 5. Test

Add `feature/<pkg>/presenter/src/jvmTest/kotlin/io/github/solcott/activitytracker/app/FooPresenterTest.kt`, modelled on `AthleteSearchPresenterTest` (the convention gives it `presenterTestOf`/`Presenter.test` from `:app-common-presenter-testing`). Call the presenter with `Presenter.test {}` from `circuit-test`, using a `FakeNavigator` when navigation matters, a `inMemoryDatabase()` and a Ktor `MockEngine` for the AppView. Cover the initial state, one success path, and one failure that surfaces `error`.

Add `feature/<pkg>/ui/src/jvmTest/kotlin/io/github/solcott/activitytracker/app/<pkg>/FooUiTest.kt`, modelled on `SegmentCreditUiTest`: `runComposeUiTest { setContent { AppPreview { FooUi(FooSamples.loaded) } } }`, asserting what the loaded sample shows by text or semantics. If `FooState` holds a `ContentState` or `PagedState`, add `FooSamples.all` and `FooUi` to `ContentLoadingConformanceTest`'s table (`composeApp` jvmTest, which scans every `-api` module), which fails until you do.

Add `androidApp/src/screenshotTest/kotlin/io/github/solcott/activitytracker/android/screenshots/FooScreenshots.kt`, modelled on `FeedScreenshots.kt`: a `@PreviewTest @PreviewScreenSizes @PreviewScreenVariants private fun FooLoaded()` rendering `FooUi(FooSamples.all.loaded())` in `ScreenPreview`, and a `@PreviewTest @PreviewPhoneSize private fun FooStates(@PreviewParameter(FooStates::class) state: FooState)` with `class FooStates : OtherSamples<FooState>(FooSamples.all)` (leave it out if `loaded` is the only sample, like `SettingsScreenshots.kt`). `ScreenshotCoverageTest` fails until the file exists.

## 6. Verify

Use the `gradle-runner` agent/skill or run directly:
1. `./gradlew :feature-<pkg>-presenter:compileKotlinJvm :feature-<pkg>-ui:compileKotlinJvm :composeApp:compileKotlinJvm checkModuleBoundaries`. Metro/Circuit codegen errors (wrong scope, missing binding) appear here.
2. `./gradlew :feature-<pkg>-presenter:jvmTest --tests "*.FooPresenterTest"`, `./gradlew :feature-<pkg>-ui:jvmTest --tests "*.FooUiTest"` and `./gradlew :composeApp:jvmTest --tests "*.ContentLoadingConformanceTest" --tests "*.ScreenshotCoverageTest"`. Confirm a non-zero test count.
3. `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite`, then look at the new references in `androidApp/src/screenshotTestDefaultDebug/reference/.../FooScreenshotsKt/` before committing them.
4. `./gradlew format`, then `./gradlew :feature-<pkg>-api:detekt :feature-<pkg>-presenter:detekt :feature-<pkg>-ui:detekt :app-fixtures:composeStabilityCheck`. The Compose rules flag modifier ordering and unstable params.

If the user wants to see the screen, launch the web preview (in Claude Code the `web` launch config via `preview_start`; elsewhere `./gradlew :webApp:wasmJsBrowserDevelopmentRun --continuous` in the background) against the local stack (the `local-stack` skill) and navigate to it.
