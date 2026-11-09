# Native iOS UI on the existing Circuit presenters

Exploration from 2026-11-09. No code changed. It records why the iOS app moves from Compose Multiplatform to SwiftUI, the decisions made,
the target architecture and the phases. Each phase becomes its own change; the first is `ios-swift-export-spike`.

## Where the app is today

- The iOS app is one `ComposeUIViewController` inside a three-file SwiftUI shell (`iosApp/iosApp/`). The only Swift class Kotlin calls
  into is `SwiftFitDecoder`. Location, auth, the Keychain, the database, background tasks and sharing are Kotlin.
- `:composeApp` builds a static Objective-C framework that exports only `:core-activityfile` and `calf-ui`. Nothing uses SKIE, Swift
  export or KMP-NativeCoroutines. The deployment target is iOS 16.0. CI has no iOS job.
- The shared UI imitates iOS: a Compose-drawn navigation bar with Haze, Calf for the tab bar, sheets and alerts, and `*.ios.kt` actuals
  in `:ui-designsystem`.
- There are 51 screens and 3 SubCircuit sub-screens. iOS would stop using about 28,600 lines of Compose UI (`feature/*/ui` 14.0k,
  `:app-common-ui` 10.1k, `:ui-designsystem` 4.5k). Presenters are 13.3k lines with 22.6k lines of tests, all `commonMain`.
- Navigation is one `SaveableBackStack`; tabs switch with `resetRoot(save, restore)`. No Circuit overlays and no result passing: sheets,
  alerts and menus are driven by fields in each screen's `State`.
- Presenters read no composition locals. They do rely on Circuit's retained state, `rememberSaveable` and `rememberTextFieldState`.

## Decisions

| Decision | Choice |
|---|---|
| Kotlin to Swift interop | Swift export on Kotlin 2.5, where it is Beta. Not SKIE |
| Contract edits | Accepted where the spike proves them necessary. Presenter edits stay at imports and renamed calls |
| iOS minimum | iOS 18, from 16.0 |
| End state | Fully native: Compose renders nothing on iOS |
| UI framework | SwiftUI. UIKit only through representables (the map view, a zoomable image, the Compose fallback host) |
| Architecture on iOS | Circuit stays. Swift views are the `Ui` half only: no Swift view models, no TCA |

**Why not SKIE.** Its latest release (0.10.15) supports Kotlin 2.4.20 and the repo is on 2.4.21. It cannot take 2.5, it waits on
Touchlab for every Kotlin bump, and every Swift screen would be rewritten again on a later move to Swift export (`onEnum(of:)` to
`sealedType()`, different names for every type). Its advantage is real: the Objective-C bridge tolerates `TextFieldState`, sealed
interfaces and `ImmutableList` as they are, so it needs no contract edits. It is the fallback if the spike fails.

**What Swift export costs.** The kmp-dataresult library already guards its modules against three constructs Swift export could not
handle on Kotlin 2.4.21: `sealed interface` (generic subtypes of an erased parent protocol are rejected), `kotlinx.collections.immutable`
types (types inheriting `List`, `Set` or `Map` are ignored, KT-80416) and any Compose type in the reachable API, which breaks the iOS
build with no warning. The app's contracts have all three: 83 sealed interfaces, `ImmutableList` in 42 files, `TextFieldState` in 25.
Kotlin 2.4.20 mapped sealed hierarchies to Swift enums and the app's sealed interfaces are not generic, so some of this may no longer
apply. The spike measures it rather than assuming.

Other facts about Swift export on 2.5.0-Beta2, from JetBrains' docs and one published migration:
- The Gradle DSL is `export { swift { } }`. `api` dependencies are exported automatically, so an umbrella module with no code of its own
  keeps the exported surface small.
- Generic type parameters are erased to their upper bounds. `ContentState<T>` and `PagedState<T, M>` reach Swift untyped.
- A function returning a type from a hidden dependency compiles to a silent `fatalError()` in the generated Swift.
- Its bundled coroutine support needed an iOS 18 deployment target.
- Swift can implement Kotlin interfaces (2.4.20), which `SwiftFitDecoder` needs.
- Kotlin 2.5.0 stable is planned for December 2026.

## Target architecture

```
 Swift (iosApp)                               Kotlin
+-------------------------------+            +--------------------------------------------+
| RootView (login / session)    | <--flows-- | :app-host  (new, commonMain, JVM-tested)   |
| TabView                       |            |   AppHost: root state, SessionEffects      |
|   NavigationStack(path) x3    | <--path--> |   NavStack x3: implements Circuit Navigator|
|     ScreenView(record)        |            |   one Molecule composition per session:    |
|       registry[screen type]   | <--state-- |     key(record) { presenter.present() }    |
|         FeedView(state)       | --events-> |     per-record retained + saveable state   |
|         ... or fallback:      |            |   child hosts: SubCircuit, nested screens  |
|       ComposeScreen(record)   | ---------> | composeApp iosMain: one screen's Compose UI|
+-------------------------------+            +--------------------------------------------+
        ^ Swift export umbrella  :ios-export (no code; api -> contracts + :app-host)
```

- **Presenters run headless.** `:app-host` runs each visible record's presenter in a Molecule composition and publishes its state as a
  flow. Swift activates a record when its view appears and deactivates it when it disappears, so a covered screen's presenter leaves
  composition while its retained and saveable state survive until the record is popped. That is what `NavigableCircuitContent` does
  today, and what presenters are written and tested against. Molecule is already resolved transitively and publishes every client target.
- **Kotlin owns navigation.** Each tab has a Kotlin stack that implements Circuit's `Navigator`, so presenters call `goTo`, `pop` and
  `popUntil` unchanged. SwiftUI binds `NavigationStack(path:)` to it, and a swipe back writes the shorter path to Kotlin. Tabs become
  three real stacks. Sheets, alerts and menus stay driven by `State`.
- **Presenter factories without `Circuit`.** The `Circuit` class is in `circuit-foundation`, which depends on Compose UI, as do
  `circuit-backstack` and `circuitx-subcircuit`. The host resolves presenters from Metro's multibound `Set<Presenter.Factory>` and keeps
  its own back stack. `circuit-retained` and `circuit-runtime-presenter` need only the Compose runtime.
- **Coexistence.** A record with no Swift view yet renders through a per-screen `ComposeUIViewController` running `CircuitContent` for
  that one screen. The app is complete at every commit and screens move one at a time, in any order.

The alternative, keeping the Compose shell and embedding SwiftUI views in it, was rejected: the Compose-drawn bar and transitions would
stay until a final big-bang cutover, and native views inside Compose have z-order and gesture problems the design system already works
around.

## TextFieldState

`TextFieldState` cannot stay in exported contracts. The recommended handling keeps the presenters' code as it is:

- An app-owned type with the same simple name in `:app-common-api`: an `expect class TextFieldState`, with `expect` versions of
  `rememberTextFieldState`, `setTextAndPlaceCursorAtEnd` and `clearText`, and a `textFieldStateOf(initial)` factory.
- On Android, desktop and web it is an `actual typealias` to Compose's `TextFieldState`. Behaviour is unchanged and `AppTextField` takes
  it as before.
- On iOS it is an `actual class` over snapshot state with a `String` value, a setter and a flow of programmatic changes (a seed, a
  clear, a restore after a failed send). Swift wraps it in an `@Observable` model behind a `TextField` binding. Reads and writes are
  synchronous, so the cursor doesn't jump; the flow only carries Kotlin-side changes back.
- Presenters, contracts, fixtures and tests change imports only, plus `TextFieldState()` to `textFieldStateOf()` in the few `State`
  defaults. `compose-foundation` leaves the iOS side of the `feature.api` convention.
- While Compose screens remain on iOS, the iOS `AppTextField` actual adapts the app type to a local Compose `TextFieldState`.
- Rules that live only in Compose UI today (`DigitsOnly`, maximum length, keyboard type) get SwiftUI equivalents in the Swift field.

Rejected: an `XChanged(String)` event per field (51 fields, new presenter logic, and the reason the `UseTextFieldState` rule exists),
and hiding the Compose type behind per-field accessor functions (51 shims, and it depends on hidden-type behaviour that fails silently).

## Phases

Each phase leaves a shippable app.

0. **Toolchain and spike** (`ios-swift-export-spike`). Kotlin 2.5.0-Beta2; Swift export replaces the Objective-C framework for the
   existing three Swift files with the UI unchanged; three real contracts are probed from Swift. The exit is a running app and a written
   list of which contract constructs must change. If Swift export fails the gate, the fallback is SKIE on Kotlin 2.4.20, and everything
   from phase 2 on is unchanged.
1. **Swift-clean contracts.** The `TextFieldState` type; whichever of `sealed interface` to `sealed class` and `ImmutableList` to `List`
   the spike requires; `LocalSettingsPane` out of `:app-common-api`; model types the contracts take from `:core-data` moved somewhere
   exportable; a `checkSwiftExport` guard on every exported module; a macOS CI job. No visible change.
2. **Headless host, native shell, first native screen.** `:app-host`; the UI-free parts of `App.kt` shared by both shells; the SwiftUI
   root, tabs, navigation stacks, screen registry and Compose fallback. Login is the first native screen.
3. **Native foundations.** A String Catalog generated from `strings.xml` (2,026 strings, 68 plurals), so it stays the single source; SF
   Symbols and asset-catalog sport icons; composable-free formatting and label logic moved out of `:app-common-ui` so `ui/number-format`
   and `ui/error-messages` aren't reimplemented in Swift; the Swift design system; previews and snapshot tests from `:app-fixtures`.
4. **Features, one change each**, cheapest infrastructure first: lists and forms (settings with a split view on iPad, profile lists,
   gear, invites, moderation, uploads, search, post pages); the feed (card, comments, posts, the sub-screens); maps; charts; record and
   import.
5. **Remove Compose rendering from iOS.** Delete the fallback host, the `*.ios.kt` actuals and the iOS targets of the UI modules; drop
   Calf, Haze, Vico, Coil and MapLibre Compose from iOS; split the Metro graph so the iOS one needs no `Ui.Factory`; retire
   `ui/ios-navigation-bar`.

## Recommended iOS stack

| Need | Use | Replaces |
|---|---|---|
| State observation | Observation (`@Observable`), `AsyncSequence` from exported flows | |
| Navigation | `TabView`, `NavigationStack(path:)`, `NavigationSplitView` for Settings on iPad | Calf's tab bar, the nav decoration |
| Sheets, alerts, menus, dates | `.sheet`, `.alert`, `.confirmationDialog`, `Menu`, `DatePicker` | Calf's adaptives |
| Maps | MapLibre Native for iOS with its SwiftUI DSL; `MLNMapSnapshotter` for thumbnails | MapLibre Compose |
| Charts | Swift Charts | Vico |
| Images | Nuke with NukeUI, and a custom loader for `heldphoto:` URLs | Coil |
| Pickers and sharing | `PhotosPicker`, `.fileImporter`, `ShareLink` | FileKit's Compose launchers |
| Icons and text | SF Symbols, a String Catalog, Dynamic Type | Compose resources |
| Tests | Swift Testing, swift-snapshot-testing, Xcode Previews, a small XCUITest smoke run | |
| Lint and format | swift-format and SwiftLint | |
| Concurrency | Swift 6 mode with default main-actor isolation | |
| Project layout | One app target with a synchronized folder per feature; Swift Package Manager for dependencies only | |

MapKit was set aside for maps because it can't render the OpenFreeMap styles the other platforms use.

## Open items

- Kotlin 2.5.0-Beta2 with Metro 1.5.2 is unverified. Metro is a compiler plugin.
- How Swift export treats `Did` and `AtUri` value classes, `Duration`, `Instant`, `LocalDate` and `ByteString`, and Circuit supertypes
  (`Screen`, `CircuitUiState`, `SubScreen`) from modules that aren't exported.
- How much of `:core-data` and `:core-lexicon` the contracts drag into the exported surface.
- Photo compression is WebP through Skia on iOS, and iOS has no system WebP encoder.
- Swipe back on a Compose fallback screen, whose native navigation bar is hidden.
- Settings' list beside its page on iPad regresses between phase 2 and the settings migration unless settings goes first.
- After the migration every UI change is made twice. Shared fixtures and snapshot coverage on both sides are the parity check.
