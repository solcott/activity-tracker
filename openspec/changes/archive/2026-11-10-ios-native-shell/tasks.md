# Tasks

## 1. Probes that gate the design (throwaway code, not merged)

- [x] 1.1 Flows from Swift (design decision 1): add a temporary object to `:iosShell` exposing a `StateFlow<Int>`, a `StateFlow<CircuitUiState?>` and a `suspend` function; build with the `xcodebuild` command in AGENTS.md and compile a throwaway Swift file that iterates each flow in a `Task` and cancels it. Verify the build passes, read the generated Swift for how the flows and their element types are spelt, run it once in the simulator to confirm values arrive and that cancelling the task stops the Kotlin collector (a log line in `onCompletion`), and record the result in the roadmap's findings. If flows aren't usable, record that decision 6's callback fallback applies.
- [x] 1.2 Object identity: in the same throwaway file, read one Kotlin object twice through a getter and compare with `===`. Verify and record whether identity holds, which decides between identity and an `id` in decision 9.
- [x] 1.3 Two Compose view controllers in a `NavigationStack`: a throwaway SwiftUI view pushing a second `ComposeUIViewController` (any existing screen) with the navigation bar hidden and the pop gesture delegate of decision 8. Verify on the iOS 18.4 and iOS 26.0 simulators that the push shows no blank frame, the edge swipe begins over Compose content and can be cancelled, and nothing freezes at the root; record resident memory with 1, 2 and 8 controllers stacked. Stop and report before group 5 if one controller adds more than 40 MB or eight exceed 400 MB.
- [x] 1.4 Swift 6 language mode with main-actor default isolation on the current three Swift files, `SwiftFitDecoder` made `nonisolated`. Verify the app builds without concurrency errors and a FIT import still decodes in the simulator; if the generated Swift's types need more than the bridging code marked unchecked, revert to Swift 5 mode with complete checking as warnings and record why.
- [x] 1.5 Remove the throwaway code and write the four results into `openspec/explorations/ios-native-ui.md` under a "Phase 2 probes" heading. Verify `git status` shows only that file, plus the project setting if 1.4 stays.

## 2. `:app-host` and the session logic both shells share (no behaviour change)

- [x] 2.1 Create `:app-host` (`app/host`, `activitytracker.kmp.library` with the Compose compiler and Metro plugins, the dependencies of design decision 2), add it to `settings.gradle.kts`, and add `molecule-runtime` 2.2.0 to `gradle/libs.versions.toml`. Verify `./gradlew :app-host:assemble` builds every target, wasmJs included.
- [x] 2.2 Give the host its rule in `ModuleBoundaries.kt`: API modules, `:app-common-api` and `:core-*` only. Verify with the convention's tests that a dependency on a `-ui` module, on `:ui-designsystem` and on a `-presenter` module each fail naming both modules and one on an `-api` module passes, and that `./gradlew checkModuleBoundaries` passes.
- [x] 2.3 Move `SessionMode` and `sessionModeOf` with `SessionModeTest`, and `RecordScreenRequests` and `AccountScreenRequests`, to `:app-host`, keeping their packages; `:composeApp` depends on `:app-host`. Verify no other file's imports change and `:app-host:jvmTest` and `:composeApp:jvmTest` pass.
- [x] 2.4 Move `TopLevelDestination` (its screens, `of` and `showsNavigation`) with `TopLevelDestinationTest` to `:app-host`, leaving `label` and `icon` in `:composeApp` as extension properties. Verify `AppShell` compiles against them unchanged in behaviour and both modules' tests pass.
- [x] 2.5 Write `SessionRunner` (design decision 2) in `:app-host`: `mode` and `run()`, doing what `App.kt`'s session `LaunchedEffect`s do in the same order and conditions. Verify with JVM tests over fakes: nothing but spaces support and units runs while the mode is unknown; the still-here check runs once, only when not leaving; background work starts only when open and checked, stops when the mode becomes left elsewhere, and starts again on return to open.
- [x] 2.6 Make `App.kt` use `SessionRunner` for the mode and the work, and `SessionGraph` expose it. Verify `App.kt` holds no repository call except `onImportsActive`'s collection, `./gradlew :composeApp:jvmTest :androidApp:testDebugScreenshotTestDefaultTestSuite` passes with no reference image changed, and the desktop app (`:desktopApp:hotRun`, against `scripts/dev.sh --seed alice`) signs in, shows the feed and uploads a manual activity.
- [x] 2.7 Add `:app-host` to `README.md`'s module table and to AGENTS.md's screens paragraph (one sentence: what it holds). Verify `scripts/check-agent-sync.sh` passes.

## 3. The host: stacks, presenters, root

- [x] 3.1 `TabStack` and `HostRecord` (design decisions 3 and 5): the `Navigator` implementation, `popTo`, record keys, `isRoot`, `showsTabs`. Verify with JVM tests each scenario of the spec's "Presenters navigate the host's stacks" requirement, plus `popUntil`, `resetRoot` replacing the stack, and `forward`/`backward` returning false.
- [x] 3.2 The presenter composition (design decision 4): Molecule, a saved-state and a retained-state entry per record key, the running rule (top of the selected tab, or reported visible), entries removed when a record leaves. Verify with JVM tests over fake presenters, immediate recomposition on the test dispatcher: a state and an event round trip; a covered record stops (its fake's flow is no longer collected) and keeps its last state; `rememberRetained` and `rememberSaveable` values survive cover and return and are fresh after pop and reopen; a record reported visible runs while covered; a screen with no factory fails naming it.
- [x] 3.3 `SessionHost` (design decision 3): a stack per `TopLevelDestination`, `select` (and selecting the selected tab pops to its root), `mode` from `SessionRunner`, the single records for the leaving and left-elsewhere modes, the two request streams and recording recovery going to the selected tab's stack. Verify with JVM tests: stacks are independent; reselect pops to root; a record request while on Search pushes Record there and is marked handled; an account request pushes Settings then Account; a recovered recording pushes Record over the feed.
- [x] 3.4 `AppHost`: `root` (restoring, signed out with the sign-in record, signed in with a `SessionHost`), built from the auth repository and the session factory interface. Verify with JVM tests: restoring then signed out; signing in gives a session whose selected tab is Feed; signing out and in as another account gives a new `SessionHost` holding nothing from the first; an expired session returns to signed out.
- [x] 3.5 Wire Metro: `@Inject` the hosts in their scopes, implement the session factory in `:composeApp` with `createSessionGraph`, and add the accessors to `AppGraph` and `SessionGraph`. Verify an integration test in `composeApp/src/jvmTest` that builds the real graph's presenter set, shows `LoginScreen` through `AppHost`, types a handle into the published state's field and sends Submit, and sees the status become starting (the spec's "A state and an event" and "Text set from outside" scenarios).
- [x] 3.6 Describe the host in `.agents/rules/compose-screens.md` (what runs a presenter on iOS, that presenters must keep reading no composition locals, where the session logic lives). Verify `scripts/check-agent-sync.sh` passes and `./gradlew :app-host:check` passes, detekt and ktfmt included.

## 4. What Swift calls

- [x] 4.1 `:core-textfield`'s iOS class gains `values` (design decision 9), and an `id` only if probe 1.2 says identity doesn't hold. Verify with `:core-textfield:iosSimulatorArm64Test`: `values` emits the initial text, a set, a clear and a `setTextAndPlaceCursorAtEnd`, and emits nothing when `value` is set to the text it holds.
- [x] 4.2 The fallback view controller in `:composeApp`'s `iosMain` (design decision 7): a `ComposeUIViewController` drawing a record's Circuit `Ui` from its `state`, with `AppTheme`, units, Circuit's locals, `LocalSubCircuit` and `LocalIsNavRoot`. Verify `:composeApp:compileKotlinIosSimulatorArm64` passes; it is exercised in 5.2.
- [x] 4.3 The iOS session glue in `:composeApp`'s `iosMain`, following `AppHost.root`: Coil's loader with `HeldPhotoFetcher` set once outside composition, the session's held photos, `onRouteImageSession`. Verify it compiles and is started from the graph's first use; its effects are checked in 5.6.
- [x] 4.4 `IosApp` (`:iosShell`) gains `host(fitDecoder)` returning the `AppHost` and `screenViewController(record)`, beside `rootViewController`, which stays until 5.7. Name the host's Swift module `AppHost` in `iosExport/build.gradle.kts` and add it to `checkSwiftExportStubs`' fail list. Verify the `xcodebuild` build passes with the stub check, the generated Swift shows `RootState` with a `sealedType()` and `HostRecord` with its `state` in the form probe 1.1 found (or the callback twins if the fallback applies), and the build fails naming the member when a `HostRecord` member is temporarily typed `KSerializer<*>`, from kotlinx-serialization-core, the one module the export hides.
- [x] 4.5 Update `.agents/rules/build-logic.md` (the host's module in the stub check, why it isn't exported whole) and AGENTS.md's iOS paragraph (what `IosApp` holds now). Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.

## 5. The SwiftUI shell, every screen through the fallback

- [x] 5.1 Add the `iosAppTests` unit test target (Swift Testing) to the Xcode project and the Swift support code: aliases for the exported host types, and `Observed<T>` (design decision 6) over a flow or, if the fallback applies, a callback. Verify `xcodebuild test` on a simulator runs one passing test of `Observed` against a small Kotlin flow exposed for tests.
- [x] 5.2 `ScreenRegistry` (empty for now) and `FallbackScreen`: the representable, its container controller, the per-record controller cache dropped when a record leaves, and the pop gesture delegate of decision 8. Verify unit tests for the registry's lookup falling back and for the cache dropping a key that left the path.
- [x] 5.3 `SessionView`: the `TabView` over `SessionHost`'s tabs with the three SF Symbol pairs `AppIcon` names, selection bound to `select`, a `NavigationStack(path:)` per tab bound as decision 5 describes, tabs hidden when the top record's `showsTabs` is false, `setVisible` on appear and disappear, and the single-screen leaving and left-elsewhere modes. Verify unit tests of the path binding (keys above the root; a shorter path becomes `popTo`; an equal path does nothing).
- [x] 5.4 `RootView` following `AppHost.root` (blank while restoring, the sign-in record through the fallback, `SessionView`), and switch `iOSApp.swift` to it. Verify on the simulator against `scripts/dev.sh --seed alice`: sign in, the feed loads, each tab opens its first screen, an activity page opens and closes with the system transition, and the app survives going to the background and back.
- [ ] 5.5 Walk the spec's `ui/ios-native-shell` scenarios on the iOS 18.4 and iOS 26.0 simulators, iPhone and iPad: each tab keeps its place and reselect returns to its root; swipe back on an activity page, cancelled and completed, at a tab's root, on the route map and the media gallery; the feed's scroll position after return; tabs hidden over Record, manual entry, import, edit, edit profile, gallery and both maps; content ending clear of the tabs; the feed's filter, a report sheet and search's segment browser; Settings one page at a time on iPad; sign out and in; a leave started from Settings. Verify each by a screenshot or a noted result, and fix what fails before going on.
- [ ] 5.6 Check what the glue and the host took over from `App()`: a held photo shows in the feed right after an upload, the feed's map images are dropped on sign-out, the recording notification opens Record, and a recording left running opens Record at launch. Verify each on the simulator, or note which needs a device.
- [x] 5.7 Delete `ContentView.swift`, `IosApp.rootViewController` and `MainViewController`. Verify the app builds, `grep` finds no caller, and the walk of 5.4 still passes.
- [x] 5.8 Measure as phase 1 did (generated Swift lines, debug dylib size, no-change build, one-line rebuild) and resident memory with eight screens stacked, and add them to the roadmap's table. Update `.agents/rules/adaptive-ui.md` for iOS (the system's tabs and stacks hold the screens; `AppShell` and the gesture decoration no longer run there; Settings' split is off on iOS). Verify `scripts/check-agent-sync.sh` passes.

## 6. Native Login

- [x] 6.1 Move the choice of which failures say why to a function in `:app-common-api` returning an enum, and make `errorReason` in `:app-common-ui` map it to its resource. Verify a `jvmTest` of the function covers network, HTTP 500 and above, the internal-server-error API name, serialization and a failure that says nothing, and that `:app-common-ui`'s error text tests pass unchanged.
- [x] 6.2 Add `Localizable.xcstrings` with the 20 strings of design decision 10 and the accent colour to the asset catalog, and add `checkIosStrings` to `build-logic`, wired into `check`. Verify with TestKit: a differing text fails naming the key, a key missing from `strings.xml` fails, `%1$s` against `%1$@` passes; and `./gradlew checkIosStrings` passes on the repository.
- [x] 6.3 `AppTextField` in Swift with its model (design decision 9): binding, re-binding to a replaced state, keyboard options and submit, digits only, maximum length. Verify unit tests: a Kotlin-side clear reaches the model; a replaced state re-binds; typed text coming back doesn't write again; a non-digit edit and an over-length edit leave the text as it was.
- [x] 6.4 `LoginView` over `LoginState` and its registry entry, with the failure text built from 6.1's enum and the catalog. Verify unit tests of the failure text for the three stages with and without a reason, and that the app builds with Login no longer reaching the fallback.
- [ ] 6.5 Walk the sign-in scenarios of `ui/ios-native-shell` and `dev/ios-swift-interop` on the simulator, against the Compose screen on Android or desktop for wording: empty and blank handle, submit from the keyboard, waiting for the browser and Cancel, a failure with the local stack stopped, an expired session with its handle filled in, Sign in again from Account, and the message after leaving; typing mid-text keeps the cursor. Verify each by a screenshot or a noted result, in light and dark.
- [x] 6.6 Add to the `new-screen` skill and `.agents/rules/compose-screens.md` that a new screen needs nothing on iOS (it renders through the fallback) and how a native view is registered. Verify `scripts/check-agent-sync.sh` passes.

## 7. CI and the whole change together

- [x] 7.1 Make the `ios` job in `.github/workflows/ci.yml` run `xcodebuild test` on the first available iPhone simulator after the build. Verify the same commands pass locally; the job itself runs only once the branch is pushed.
- [x] 7.2 Run `./gradlew check` and `./gradlew -p build-logic :convention:test`. Verify both pass, with no screenshot reference changed.
- [ ] 7.3 Run the Android app and the web app against the local stack: sign in, feed, open an activity, Settings beside its page on a wide web window, sign out. Verify nothing differs from before this change.
- [x] 7.4 Rewrite the roadmap's phase 2 line to what was built, move the open items this change settled (swipe back on a fallback screen, the Settings regression now specified) out of "Open items", and add what it leaves open. Verify `openspec validate ios-native-shell --strict` passes.

## Not verified

- 2.6: the desktop app was started (`:desktopApp:hotRun`) but not driven: the hot reload MCP server didn't connect to this worktree's
  app, so there was no way to see or click its window. The same check was made in the iOS simulator instead, which at that point
  still ran the shared `App.kt`: it opened signed in on the feed, and a manual activity saved there reached the local PDS, so the
  session's background work ran under `SessionRunner`. The desktop, web and Android apps compile against it.

- 5.1: `Observed` is tested against a Swift `AsyncStream`, not a Kotlin flow exposed for tests: the wrapper takes any sequence, and
  the probe and the running app cover the Kotlin side.
- 5.5 is open. Walked on the iOS 26.0 iPhone simulator, signed in: each tab's first screen, a tab keeping its place, reselecting a
  tab, the feed's scroll position on return, the back swipe (an activity page, a tab's first screen, the full-screen route map,
  manual entry), tabs hidden over Record, manual entry, edit and the route map and back again, content under the tab bar, the feed's
  filter sheet and add menu, Search's segment browser, background and return. Not walked, because each needs an account signed in
  where there is none, or a sign-out of the one simulator that has one, and signing in is the athlete's step: anything on an iPad or
  on iOS 18.4 (whose simulator also can't reach the local stack: its requests time out, as the roadmap's open items say), sign out
  and in, a leave from Settings, Settings one page at a time on iPad. Also not walked: the media gallery and a report sheet, which
  need an activity with photos and another athlete's activity in the feed.
- 5.6 is open: none of the four checks was made (a held photo after an upload, map images dropped on sign-out, the recording
  notification, a recording left running). The first needs a photo picked from the simulator's library, the second a sign-out, and
  the last two a recording, which wants a device.
- 6.5 is open. Walked on the iOS 18.4 iPhone simulator and the iOS 26.0 iPad simulator, both signed out, in light and dark: the
  empty handle (Sign in off), typing, typing mid-text keeping the cursor (`alice.x.test` typed as `alicetest`, four left, `.x`, `.`),
  submit from the keyboard's Go, the spinner with the field off while starting, a failure with its reason (`Couldn't start sign-in.
  Couldn't reach the server.`, on iOS 18.4, where the request times out), Sign in from the button, the browser prompt over
  `Continue in your browser…` and Cancel, and declining the prompt returning the form. Not walked: the app's own Cancel (the
  system's prompt covers it until answered), a blank handle (covered by `LoginTextTests`; the simulator tool types no spaces), an
  expired session, Sign in again from Account and the message after leaving, which need the signed-in simulator signed out; and the
  wording beside the Compose screen, which the catalog check holds to `strings.xml` instead.
- 7.3 is open: the Android and web apps were not run. Both need a sign-in.
- 6.3: the two rules are unit-tested on the model. No screen uses them yet, so a refused edit has not been seen in a field.

## Workflow follow-up

- Push the branch when asked, and confirm the `ios` CI job, which has never run, passes with its new test step.
- Phase 3 (native foundations) is its own change; Settings is the first native screen of phase 4, which brings back its split on iPad.
