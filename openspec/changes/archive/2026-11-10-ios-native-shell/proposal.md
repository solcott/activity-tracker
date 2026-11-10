# Proposal

## Why

Phase 1 (`ios-swift-clean-contracts`, archived) made every screen contract readable from Swift, but the iOS app is still one Compose view controller: nothing runs a presenter without the Compose UI, and Swift has nowhere to put a view. Phase 2 of the native iOS UI (`openspec/explorations/ios-native-ui.md`) builds that place: presenters run headless, SwiftUI owns the tabs and stacks, and every screen without a Swift view keeps rendering through Compose, so screens can move one at a time with the app complete at every commit.

## What Changes

- **A presenter host, `:app-host`.** It runs each shown screen's presenter with no Compose UI and publishes its state, keeps a screen's retained and saved state while it is covered and drops it when it closes, and owns one navigation stack per tab that presenters drive through Circuit's `Navigator` as they do today. It is common code, tested on the JVM.
- **The session logic in `App.kt` that draws nothing moves to `:app-host`** and both shells call it: the session mode rule, the session's background work, the tab roots and the full-screen-flow rule, and the requests to open Record or Account. Android, desktop and web behave as before.
- **A SwiftUI shell replaces the Compose root on iOS.** A root view follows the session (sign-in, leaving, left elsewhere, the app), a `TabView` holds a `NavigationStack` per tab bound to the Kotlin stack, and a registry maps a screen to its Swift view.
- **A Compose fallback for every screen without a Swift view.** One Compose view controller per screen draws that screen's existing UI from the state the host publishes. All 51 screens but Login start here.
- **Swipe back works on fallback screens**, whose native navigation bar is hidden, by re-enabling the navigation controller's pop gesture recogniser with a delegate that allows it whenever the stack has more than one screen. Decided against showing the native bar on fallback screens, and against `circuitx-gesture-navigation`, which animates between two records of one Compose stack and has nothing to act on here.
- **Login is the first native screen**, with the same content, messages and behaviour as the shared one.
- **Swift follows a text field.** The iOS `TextFieldState` publishes text a presenter sets, clears or restores, and a Swift field binds to it, with keyboard options, a digits-only rule and a maximum length.
- **The iOS app gets its first unit tests**, in Swift, and CI runs them.
- **BREAKING (iPad only): Settings shows one page at a time on iOS at every width.** The list beside its page is drawn by a Compose navigation decoration that the SwiftUI stacks replace. It comes back when Settings is drawn natively, which should be the first screen migrated in phase 4.
- **Each tab keeps a real stack on iOS**, where today one stack is swapped on a tab change. On iPad the tabs sit where iPadOS puts them.

Out of scope: any other native screen; hosting sub-screens (SubCircuit) in Kotlin, which nothing needs until a native screen embeds one, since fallback screens keep running theirs inside Compose; the generated String Catalog, SF Symbol and icon assets, the Swift design system and snapshot tests (phase 3); `ImmutableList` fields (decided on the first native list screen); removing the Compose shell's iOS branches (phase 5); restoring navigation after iOS ends the process, which the app doesn't do today either.

## Capabilities

### New Capabilities

- `dev/presenter-host`: running the shared presenters and their navigation without the shared UI: states published, events taken, state kept while a screen is covered and dropped when it closes.
- `ui/ios-native-shell`: how the iOS app's tabs, stacks, back gestures and session switching behave, how a screen without a native view appears in it, and the native sign-in screen.

### Modified Capabilities

- `dev/ios-swift-interop`: adds that Swift follows a screen's states and a text field's Kotlin-side changes; the stub check also covers the host's Swift-facing types; the entry-point example names a screen's view controller.
- `dev/module-boundaries`: adds the presenter host as a module that depends only on contracts and `:core-*`.
- `dev/continuous-integration`: the iOS job also runs the iOS app's Swift unit tests.
- `ui/settings-layout`: adds that iOS shows one settings page at a time at every width.

## Impact

- **New module:** `:app-host` (`app/host`), in `settings.gradle.kts`; Molecule joins the version catalog (2.2.0, already resolved through `circuit-test`).
- **`:composeApp`:** `App.kt` loses its session logic to the host and keeps the composables; `TopLevelDestination`, `SessionMode`, `RecordScreenRequests` and `AccountScreenRequests` move, keeping their packages; `AppGraph` and `SessionGraph` expose the host; `iosMain` loses `MainViewController` and gains the fallback view controller and the iOS session glue (image loader, held photos, route images).
- **`:iosShell`, `:iosExport`:** `IosApp` gives Swift the host and a screen's view controller instead of the root view controller; the host's Swift module is named and joins the stub check.
- **`:core-textfield`:** the iOS class publishes its text changes.
- **`:app-common-api`, `:app-common-ui`:** the rule for which failures say why moves to a function both the Compose error text and Swift call.
- **`iosApp`:** `ContentView.swift` is replaced by the shell, the registry, the fallback host, the text field and `LoginView`; a String Catalog holds the 20 strings Login and the tabs show; an accent colour; a unit test target; the project moves to the Swift 6 language mode if the probe allows.
- **Build logic:** `ModuleBoundaries.kt` (the host's rule), a check that the iOS String Catalog matches `strings.xml`, with TestKit tests.
- **CI:** `.github/workflows/ci.yml`, the `ios` job runs `xcodebuild test`. That job has never run (the branch isn't pushed).
- **Docs:** `AGENTS.md`, `.agents/rules/compose-screens.md`, `adaptive-ui.md` and `build-logic.md`, the `new-screen` skill, `README.md`'s module table, and the roadmap's phase 2 line, findings and open items.
- **Risk carried:** one Compose view controller per stacked fallback screen costs memory that a single Compose stack didn't. It is measured first, with a limit that stops the work.
