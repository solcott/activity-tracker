# Design

## Context

See proposal.md for the motivation and `openspec/explorations/ios-native-ui.md` for the roadmap and what phases 0 and 1 found. What shapes the approach:

- `App.kt` (`:composeApp`, 242 lines) mixes three things: session logic that draws nothing (restore, the mode rule, the `LaunchedEffect`s that run the session's background work, recording recovery, two request streams), composition locals for the Compose UI (theme, units, `LocalSubCircuit`, `LocalIsNavRoot`, Coil's loader) and the navigable content (`NavigableCircuitContent` over one `SaveableBackStack`).
- Tabs are one stack swapped with `navigator.resetRoot(save, restore)` from `AppShell`, the only `resetRoot` call in the app. Presenters call only `goTo` (69 places), `pop` (73) and `popUntil` (2), and pass no pop results.
- Presenters import from Circuit only `Navigator`, `rememberRetained`, `Presenter`, `Screen`, `popUntil` and the SubCircuit presenter types, and from Compose only the runtime. Their convention gives them `circuit-retained`, `circuit-runtime-presenter` and `compose-runtime-saveable`: nothing with a UI.
- The `Circuit` class, `circuit-backstack` and `NavigableCircuitContent` are in modules that depend on Compose UI. The graphs already bind `Set<Presenter.Factory>` per scope (`CircuitBindings.kt`).
- Circuit 0.39's `Navigator` also has `forward`, `backward`, `peek`, `peekBackStack` and `peekNavStack`.
- `AppGraph` and `SessionGraph` are declared in `:composeApp`, which has the Compose UI on its classpath.
- `checkSwiftExportSources` refuses any Compose import in a module exported whole. A host that composes presenters imports the Compose runtime, so it can't be one of those modules.
- The repo's presenter tests already run presenters in Molecule (`presenterTestOf`, 2.2.0 through `circuit-test`), with immediate recomposition on the test dispatcher because Molecule's immediate clock isn't thread-safe.
- Three UIs embed sub-screens with `SubCircuitContent` (feed, place names, reporting), and athlete search embeds a whole screen, `CircuitContent(SegmentBrowseScreen)`, with a navigator that forwards to its own event sink. Both run the embedded presenter inside the UI's composition. No UI has a back handler.
- The Xcode project has one target, synchronized folders (a new Swift file needs no project edit), Swift 5 mode, iPhone and iPad, and no test target.
- Not yet tried in this repo: a Kotlin `Flow` or `suspend` function reached from Swift, and two Compose view controllers in one navigation stack.

## Goals / Non-Goals

**Goals:**

- A native screen is added by writing one Swift view and one registry line, with no Kotlin change.
- Presenters, their tests and the screen contracts don't change.
- Every screen works on iOS at every commit of this change.
- The host's behaviour is tested on the JVM, where the rest of the presenter tests run.

**Non-Goals:**

- Running sub-screens or embedded screens in the host. Fallback screens compose theirs as today (the feed's filter, the reporting and place-name sheets, search's segment browser); the first native screen that embeds one adds it.
- Deleting the Compose shell's iOS branches (`AppShell`'s UIKit tab bar, the gesture decoration on iOS). They stop running on iOS here and are removed with Compose in phase 5.
- Swift lint and format tooling, and a typed Swift wrapper for `ContentState` and `PagedState`: with the Swift design system in phase 3.
- State restoration after iOS ends the process.

## Decisions

### 1. Probe first, with limits that stop the work

Four things this design rests on have not been tried here, so the first task group tries each on throwaway code and records the result in the roadmap:

- **Flows from Swift.** Whether a `StateFlow` returned by `:iosShell` is an `AsyncSequence` in Swift, whether its elements arrive typed or as `any _KotlinBridgeable`, and whether cancelling the Swift task cancels the Kotlin collector. If it is not usable, decision 6's fallback applies and nothing else changes.
- **Object identity.** Whether reading the same Kotlin object twice gives Swift the same instance (`===`). Decision 9 needs to tell a replaced text field from the same one.
- **Two Compose view controllers in a `NavigationStack`** with the navigation bar hidden and the pop gesture re-enabled (decision 8): the push transition, the edge swipe over Compose content, and the memory each stacked controller adds. If one stacked controller adds more than 40 MB, or eight deep exceeds 400 MB, stop and report before group 5: the fallback would need to release covered controllers, which loses their scroll position.
- **Swift 6 language mode** with main-actor default isolation, against the generated Swift and `SwiftFitDecoder` (called from Kotlin's background threads, so it is `nonisolated`). If the export's types need more than the bridging wrappers marked unchecked, the project stays in Swift 5 mode with complete concurrency checking as warnings.

**Results** (in full in the roadmap's "Phase 2 probes"): flows arrive typed as `AsyncSequence`s and cancellation reaches Kotlin, so decision 6 needs no callback twin; identity holds, so decision 9 needs no `id`; Compose controllers stack with no blank frame at 2 to 3 MB each, with one gesture conflict on iOS 18.4 that decision 8 now handles; and the project is in Swift 6 mode.

### 2. `:app-host`, and what moves into it

A new common module at `app/host`, package `io.github.solcott.activitytracker.app.host`. It depends on `:core-data`, `:core-location`, `:core-textfield`, `:app-common-api`, the feature API modules whose screens it names (feed, search, profile, record, settings, login, edit, media, map, segment), `circuit-runtime`, `circuit-runtime-presenter`, `circuit-retained`, `compose-runtime`, `compose-runtime-saveable` and Molecule. `checkModuleBoundaries` gets a rule for it: API modules and `:core-*` only.

Moved out of `:composeApp`, keeping their packages so no caller's import changes:

- `SessionMode` and `sessionModeOf`, with `SessionModeTest`.
- `TopLevelDestination`, without its label and icon, which stay in `:composeApp` as extension properties since they are Compose resources and design-system types. `TopLevelDestinationTest` moves with it.
- `RecordScreenRequests` and `AccountScreenRequests`.
- The session logic of `App.kt`, as a `SessionRunner` (session scope): `mode`, a flow of `SessionMode`, and `run()`, which does what the `LaunchedEffect`s do today in the same order and under the same conditions (spaces support and units at once; the still-here check once the mode is known not to be leaving; the background work only while the mode is open and that check is done, restarting when the mode returns to open). `App.kt` calls `run()` from one `LaunchedEffect` and reads `mode`; its `onImportsActive` collection stays where it is, since only desktop and web use it.

Alternative: leave `App.kt` alone and write the logic again for iOS. Rejected, because the leave and left-elsewhere rules decide what is written to an athlete's PDS and must not differ by platform.

### 3. The host's shape

```
AppHost (app scope)        root: StateFlow<RootState>   Restoring | SignedOut(record) | SignedIn(session)
SessionHost (session)      mode, tabs: one TabStack per TopLevelDestination, selected tab, select(tab)
TabStack : Navigator       records: StateFlow<List<HostRecord>>, popTo(size)
HostRecord                 key, screen, isRoot, showsTabs, state: StateFlow<CircuitUiState?>, setVisible(Boolean)
```

`AppHost` restores the session, ends expired ones and builds a `SessionHost` per signed-in account through a small factory interface that `:composeApp` implements with `createSessionGraph`, since the session graph's type lives there. `AppGraph` and `SessionGraph` gain an accessor each. On Android, desktop and web nothing asks for them, so they are never built.

The leave and left-elsewhere modes each show one record with no stack, as `App.kt` shows one `CircuitContent` today.

### 4. Presenters run in one Molecule composition per scope

`AppHost` has a composition for the signed-out record and each `SessionHost` has one for its records. Inside, each running record is `key(record.key)`, wrapped in a `SaveableStateHolder` entry and a `circuit-retained` `RetainedStateHolder` entry for that key, around `presenter.present()`, whose result goes to the record's `state`. That is what `NavigableCircuitContent` does per record, so `rememberRetained`, `rememberSaveable` and `rememberTextFieldState` behave as presenters and their tests expect. A record that leaves its stack has both entries removed.

A record runs when it is the top of the selected tab, or when the UI has reported it visible (a screen showing beneath a back swipe). A stopped record keeps its last state in `state`, so whatever still draws it has something to draw. The first half of the rule is the host's own and needs no signal from Swift, so a missed `onDisappear` can leave a presenter running too long but never leaves the shown screen without one.

The composition runs on the main dispatcher with a frame clock the platform passes in: Molecule's `DisplayLinkClock` on iOS, immediate recomposition on the test dispatcher in tests. Presenters are looked up in the scope's `Set<Presenter.Factory>` with the record's `TabStack` as navigator; a screen none of them takes is an error naming the screen.

Alternative: one composition per record. Rejected: it needs its own retained and saved state plumbing where the holders already do it, and a frame clock subscription per screen.

### 5. Kotlin owns the stacks

`TabStack` implements `Navigator` so no presenter changes:

- `goTo` pushes a record, unless the screen equals the top one (Circuit's rule). `pop` removes the top record unless it is the root, and ignores a result, which nothing passes. `popUntil` works through `peek` and `pop`.
- `resetRoot` replaces the stack with the new root and ignores `save` and `restore`. No presenter calls it; tabs are switched with `SessionHost.select`, and selecting the selected tab pops it to its root.
- `forward` and `backward` return false: there is no forward history.
- `popTo(size)` is for the UI alone, when the athlete goes back by a gesture or the system's back menu and SwiftUI reports a shorter path.

The two request streams and recording recovery call `goTo` on the selected tab's stack, as they act on the current stack today. `showsTabs` is `TopLevelDestination.showsNavigation(screen)`.

SwiftUI's `NavigationStack(path:)` binds to `records` without its root: reading gives the keys above the root, and a write is only ever shorter and becomes `popTo`. Pushes come only from Kotlin.

### 6. What Swift sees

`:app-host` is not exported whole, because the source guard can't pass a module that composes (Context). `IosApp` in `:iosShell` returns the `AppHost`, so the export carries the host's types as far as their signatures reach, under the Swift module name `AppHost`, as `:core-activityfile` is carried today. The stub check's fail list gains that module, which is the guard that matters here: a hidden type in a member Swift reads would otherwise stop the app.

The host's Swift-facing types are plain classes and sealed interfaces holding flows. Swift consumes a flow as an `AsyncSequence` in a `.task`, behind one generic `@Observable` wrapper (`Observed<T>`) that casts elements once, so a view reads `model.value` typed. If probe 1 fails, each flow gets a twin `observe(onChange) -> Cancellable` in the host and the same wrapper calls that instead; views don't change.

Alternative: put the Swift-facing types in a second, Compose-free module exported whole. Rejected as a module for five small classes; the stub check gives the same protection.

Three things building it added (roadmap, "Phase 2 findings"). A flow's elements reach Swift only as objects, so the flows of an enum or a list (`mode`, `selected`, `records`) have twins whose elements are small objects holding the value (`shownMode`, `shownTab`, `shown`), which Swift follows. Swift never reads a record's `screen`: an interface-typed value needs its Kotlin class's generated Swift class in the binary, and Xcode links only the generated modules the app's Swift names, so a record carries its screen's class name and the registry is keyed by that. And the hosts' constructors are `internal`, because a public one led the export through `Presenter.Factory` to a Circuit type it can't carry.

### 7. The fallback draws the UI half only

For a record with no Swift view, Swift asks `IosApp.screenViewController(record)`, which `:composeApp`'s `iosMain` answers with a `ComposeUIViewController` that collects the record's `state` and calls the screen's Circuit `Ui` with it. The presenter stays in the host. So presenters run in one place whichever way a screen is drawn, and moving a screen to Swift is a registry line.

The controller provides what `App.kt` provided: `AppTheme`, `LocalUnitSystem`, `CircuitCompositionLocals` and `LocalSubCircuit` (so embedded sub-screens and search's embedded segment browser run as today), and `LocalIsNavRoot` from the record. It does not consume the bottom inset or provide `LocalFloatingBottomBarHeight`: inside a `TabView` the controller's safe area already includes the tab bar, so `AppScaffold`'s own insets end content clear of it.

Swift keeps one controller per record key for as long as the record is in its stack and drops it when the record leaves. A covered screen's composition therefore stays alive, which is what keeps its scroll position and the retained state of its sub-screens; SwiftUI is free to rebuild the representable around it.

What `App()` did once per app or session for the Compose UI moves to `iosMain` glue that follows `AppHost.root`: Coil's loader with `HeldPhotoFetcher` (set outside composition), the held photos of the session, and `onRouteImageSession`. `MainViewController` is deleted.

Alternative: run `CircuitContent(screen)` in the controller, presenter and UI together, as the roadmap's text says. Rejected: presenters would run in two places with two ways of keeping state, covered presenters would keep running, and each migrated screen would move its presenter as well as its view.

### 8. Swipe back on fallback screens

A fallback screen draws its own Compose bar, so its destination hides the native one, and UIKit then disables the navigation controller's `interactivePopGestureRecognizer`. The fallback's container view controller finds its navigation controller when it is added and gives that recogniser a delegate that lets it begin when the stack holds more than one controller. The guard matters: the gesture beginning on a stack of one freezes the interface. The system's pop then shortens the path, which reaches Kotlin as `popTo` (decision 5).

Native screens show the native bar and need nothing. The delegate is the same for every screen of the stack, and its rule is the one the system applies when the bar shows.

The probe found one conflict (roadmap, "Phase 2 probes"). On iOS 18.4 Compose keeps following the touch while UIKit drives the pop, so a back swipe that begins over a pager turns its page. When the delegate lets the pop gesture begin it therefore switches off every gesture recogniser in the top screen's view tree, which cancels the touch Compose is following, and switches them back on when the pop gesture ends, completed or cancelled. Asking the other recognisers to wait for the edge pan to fail was tried and does not stop it. iOS 26.0 has no such conflict and the same code does no harm there. iOS 26's second recogniser, for a swipe from the content area, does not begin on a screen with the bar hidden and is left alone.

Still checked on real screens in group 5, since the probe used a plain list and pager: the media gallery's pager and both maps.

### 9. Text fields from Swift

The iOS `TextFieldState` (`:core-textfield`) gains `values`, a flow of its text made with `snapshotFlow`, so Swift hears of a seed, a clear or a restore. `AppTextField` in Swift takes the state: its binding reads a small `@Observable` model and writes `state.value`, which already ignores text equal to what it holds, so typed text coming back through `values` changes nothing and the cursor stays put.

A presenter can replace the state object (`LoginPresenter` keys the handle on the last session's end). The model re-binds when the state it is given is a different object, by identity if probe 2 shows identity holds, else by an `id` added to the iOS class.

Options the field takes: keyboard type, capitalisation, autocorrection, submit label and action, and two rules applied in the binding's setter before the write, digits only and a maximum length, where a rejected edit leaves the text as it was. Only the keyboard options are used by Login; the two rules are small, shape the setter, and were assigned to this phase.

A write from Swift is a snapshot write outside any composition. Molecule sends apply notifications for those, and a JVM test of the host holds that a text set from outside reaches the presenter.

As built: the model never takes the text a report carries. `values` reaches Swift a keystroke at a time and late, so a report can be older than what the field holds; on each one the model reads the state as it is now, which is what the field last wrote unless a presenter changed it. The screen holds the model (`LoginView` needs the text for Sign in), and the field moves it to a replaced state with `.task(id:)` on the state's identity. A refused edit bumps a counter the field reads, because SwiftUI's `TextField` otherwise keeps showing what was typed.

### 10. Native Login, and its text

`LoginView` is a SwiftUI view over `LoginState`, laid out as `LoginUi` is: name, intro, the notices, the field, the failure or waiting text, then progress or Sign in. The brand teal becomes the asset catalog's accent colour (`#00796B` light, `#4FD8C4` dark, from `ui/theme`).

Strings: a hand-written `Localizable.xcstrings` holds only the 20 strings Login and the tabs show, under the names they have in `strings.xml`. A `checkIosStrings` task in `check` fails when an entry's text differs from the shared string of that name after `%1$s` is read as `%1$@`, or names a string that no longer exists. Phase 3 replaces the file with the generated one and keeps the names. The tabs use the three SF Symbol pairs `AppIcon` names today.

Why a failure happened: the rule that only unreachable, server and unreadable failures say why is `errorReason` in `:app-common-ui`, typed with a Compose resource. Its choice moves to a function in `:app-common-api` returning an enum, which `errorReason` maps to a resource and Swift to a string name, so the rule is written once.

As built: the filled button is `AppButton`, which draws its label in a second catalog colour (`OnAccentColor`, the theme's on-primary: white, or `#00382F` on the dark theme's light teal), since the system's white label doesn't read there. `LoginText` holds the choices that aren't layout (the failure text, the left message, whether a handle can be submitted), where Swift Testing reaches them.

Alternative: have Swift ask Kotlin for each string through Compose resources. Rejected: its reader is suspending, and the catalog arrives in phase 3 anyway.

### 11. Settings on iPad

`SettingsListDetailDecoration` is a `NavDecoration`, which only `NavigableCircuitContent` runs. Keeping the split would mean projecting Settings and the pages above it into one path element drawn by one controller, for a layout the settings migration then throws away. Settings shows one page at a time on iOS until it is native, and the roadmap puts Settings first in phase 4.

### 12. Swift tests

A unit test target (`iosAppTests`, Swift Testing) covers the logic that isn't a view: the path binding's two directions, the registry's lookup and fallback, the field's rules and its model, the failure text. Views are checked on the simulator. CI's `ios` job runs `xcodebuild test` on the first available iPhone simulator.

## Risks / Trade-offs

- [One Compose view controller per stacked fallback screen uses more memory than one Compose stack, and each has its own first frame] → Measured in probe 3 with a stop limit; the push transition is watched for a blank first frame.
- [Kotlin 2.5.0-Beta2's flow bridging may be incomplete] → Probe 1, with the callback twin as a decided fallback behind the same Swift wrapper.
- [The edge swipe fights Compose's gestures on iOS 18, as the probe showed on a pager] → The screen's recognisers are off while the pop gesture runs (decision 8); the gallery and the maps are checked in group 5.
- [Two UIs to keep alike once Login is native] → Login's strings are checked against the shared ones, and its states are walked on the simulator against the Compose screen on Android.
- [The Compose shell's iOS branches become dead code until phase 5] → Accepted; removing them now would touch code the other platforms share.
- [iPad loses Settings' split] → Accepted and specified; Settings is first in phase 4.
- [The CI `ios` job has never run, and this adds a simulator test step to it] → The same commands are run locally; the job itself is unverified until the branch is pushed.
- [A missed disappear signal leaves a covered presenter running] → It costs work, not correctness; the top record always runs regardless.

## Migration Plan

Groups 1 to 3 change no behaviour on any platform. Group 4 adds entry points beside the existing one. The switch is group 5's first task, which replaces `ContentView` with the shell while every screen, Login included, still renders through the fallback; Login becomes native in group 6. Rolling back is restoring `ContentView.swift` and `MainViewController`, which are deleted only at the end of group 5.

## Open Questions

- Whether the erased-generic and `fun interface` linkage problems the spike met are fixed in a later 2.5 build. Neither blocks this change.
