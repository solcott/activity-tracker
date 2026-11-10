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

`TextFieldState` could not stay in exported contracts. What phase 1 built keeps the presenters' code as it was:

- An app-owned type with the same simple name, in its own module `:core-textfield` (package `textfield`): an `expect class
  TextFieldState`, with `expect` versions of `rememberTextFieldState`, `setTextAndPlaceCursorAtEnd` and `clearText`, and a
  `textFieldStateOf(initial)` factory. Its own module because both the contracts and the design system's `AppTextField` take it, and
  neither may depend on the other.
- On Android, desktop and web it is an `actual typealias` to Compose's `TextFieldState`. Behaviour is unchanged, `AppTextField` takes it
  as before, and JVM tests keep editing it with Compose's API.
- On iOS it is an `actual class` that wraps a Compose `TextFieldState` until Compose leaves iOS, not a second copy of the text: the
  Compose field on iOS edits the same object as before, so typing, the cursor, selection and restoring behave as they did. Swift reads
  and sets `value`, a `String`. `composeState`, typed `Any`, is what the iOS `AppTextField` casts back; it goes in phase 5, when the
  class's inside becomes plain snapshot state. A two-way copy between two text states on every keystroke, the first idea, is how
  characters get dropped and cursors jump.
- Presenters, contracts and fixtures changed imports only, plus `TextFieldState()` to `textFieldStateOf()`. Compose foundation left the
  contract modules. detekt's `UseAppTextFieldState` refuses Compose's four names outside test files, since a missed import compiles
  everywhere but iOS.
- Still to do in phase 2: what Swift needs to observe a field (a flow of Kotlin-side changes such as a seed, a clear or a restore after a
  failed send, behind an `@Observable` model and a `TextField` binding), and SwiftUI equivalents of the rules that live only in
  Compose UI today (`DigitsOnly`, maximum length, keyboard type).

Rejected: an `XChanged(String)` event per field (51 fields, new presenter logic, and the reason the `UseTextFieldState` rule exists),
and hiding the Compose type behind per-field accessor functions (51 shims, and it depends on hidden-type behaviour that fails silently).

## Phases

Each phase leaves a shippable app.

0. **Toolchain and spike** (`ios-swift-export-spike`). Kotlin 2.5.0-Beta2; Swift export replaces the Objective-C framework for the
   existing three Swift files with the UI unchanged; three real contracts are probed from Swift. The exit is a running app and a written
   list of which contract constructs must change. If Swift export fails the gate, the fallback is SKIE on Kotlin 2.4.20, and everything
   from phase 2 on is unchanged.
1. **Swift-clean contracts** (`ios-swift-clean-contracts`). The `TextFieldState` type in `:core-textfield`; `PlaceNameFields.self`
   renamed; `LocalSettingsPane` out of `:app-common-api`; every contract module exported to Swift, with a second constructor on the four
   events that took an immutable collection; two guards, `checkSwiftExportSources` in `check` and `checkSwiftExportStubs` in Xcode's
   build; a macOS CI job. No visible change. Left for the phase that needs them: `ImmutableList` and `ImmutableSet` fields, which Swift
   reads as Kotlin collection protocols with untyped elements, become `List` and `Set` or get one Swift wrapper (decide on the first
   native list screen); and moving model types out of `:core-data`, which is about the size of the export, not about making it work.
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

## Spike findings

Filled in by `ios-swift-export-spike` as it runs.

### Toolchain for Kotlin 2.5.0-Beta2 (2026-11-09)

2.5.0-Beta2 is the newest Kotlin on Maven Central; there is no RC yet.

| Dependency | Version | Why it works with the beta |
|---|---|---|
| Metro | 1.5.2 (unchanged) | Its compatibility table lists 2.5.0-Beta2 for Metro 1.5.0 and later |
| KSP | 2.3.13 (unchanged) | KSP2 runs on its own Analysis API, not on the build's compiler, so it has no per-Kotlin release |
| Room's processor | 3.0.3 (unchanged) | A KSP processor, so the same applies |
| Compose compiler, serialization | 2.5.0-Beta2 | Both ship with Kotlin and take `version.ref = "kotlin"` |
| Compose Multiplatform | 1.12.2 (unchanged) | Its plugin accepts the beta |

### What the beta broke (2026-11-09)

`./gradlew check` passes on every target with one source change, and running the app found a second break that `check` can't see:

- `FitRoundTripTest` (`:core-activityfile` jvmTest) no longer compiled: an `assertEquals` over two nested lambdas failed with "the
  value of the type parameter 'T' must be mentioned in input types". The type argument is now written out. Nothing else needed a change:
  explicit backing fields, Metro's generated graphs, Room's KSP output and the Compose compiler all build as before.
- The signed-in app died on its first frame on iOS with `IrLinkageError: No function found for symbol
  'com.slack.circuit.foundation.navstack/ProvidedValues.provideValues|provideValues(){}[0]'`. `App.kt` passed Circuit a
  `ProvidedValues { … }` lambda: a lambda converted to a library's `fun interface` whose method is composable. The beta links it against
  the method's signature from before the Compose compiler adds its parameters. Nothing warns at build or link time, the JVM targets are
  unaffected, and no iOS test draws the signed-in shell. The lambda is now a small class. `SessionPreviewSubUis` and `AppPreview` convert
  lambdas to SubCircuit's `SubUi` the same way; they run only on the JVM (tests and screenshots), so they are left alone. The web app
  shares `App.kt` and so had the same break; it wasn't run to confirm.
- The wasm yarn lock is unchanged, and the web app's development bundle builds.
- Detekt's type-resolution tasks print "compiler errors found during analysis" in a dozen modules. That isn't the beta: detekt reads
  every source set of a module as one module and runs no compiler plugins, so it reports expect and actual declared together and every
  serialization `serializer()` as unresolved. Neither error comes from the compiler the build uses (not compared with a 2.4.21 run,
  since CI's detekt tasks came from the build cache).

### The app on Swift export (2026-11-09)

The app runs on Swift export. On an iOS 18.4 simulator it installs, launches and shows the sign-in screen with its strings, the handle
field takes text, and Sign in reaches the system's web sign-in prompt. The signed-in pass was done on an iOS 26.0 simulator, for the
reason below:

| Check | Result |
|---|---|
| Sign in and see the feed | Signed in as `alice.test`; the feed, a profile and an activity page load |
| Import a FIT file | `mtb-ride.fit` from `WriteWorkoutSamples` shows 7.83 mi, 45:00, 886 ft, 193 W and 152/167 bpm, the figures the file was written with (12.6 km, 2,700 s, 270 m, 193 W, a 140 to 168 bpm ramp), with its map and chart |
| Import a truncated file | The first half of the same file gives "Couldn't read that file" and the app carries on: the exception the Swift decoder throws reaches Kotlin |
| Background and return | Going to the background calls all three schedule functions; the same process comes back. The simulator refuses every `BGTaskScheduler` request (error 1), so no background task ran |
| Calf's native pieces | The tab bar switches tabs, the activity-type sheet opens with its grabber, the delete alert shows and cancels, the date wheels turn; so do the native menus |

Not checked: a background task actually running (it needs a device), and the signed-in app on iOS 18.

The iOS 18.4 simulator can't be used for the signed-in pass against the tunnel. Its first requests go over HTTP/2 and work; once it has
learned the host speaks HTTP/3, each QUIC connection is closed as soon as it is established (`quic_conn_retire_dcid unable to find
DCID` in the system log) and the request hangs for its 60 s timeout, which the app shows as "Couldn't reach the server". That is the
simulator's network stack, not the app: the same host answers `curl` in 0.2 s and the iOS 26.0 simulator reaches it.

How the build ended up:

- `:iosExport` names one `api` dependency, `:iosShell`. A module the umbrella names is exported whole. A module reached through
  another's signatures (`:core-activityfile`, kotlinx-datetime) is exported only as far as those signatures name its types. That is what
  keeps the surface small, so the two modules stay two.
- Exporting `:core-activityfile` whole doesn't build. `PerSecond.hold(samples: List<Pair<Double, Int>>)` becomes a Kotlin bridge that
  reads the argument as `List<Pair<Any?, Any?>>`, which the compiler rejects. So `:iosShell` has a `FitSupport` object that repeats the
  seven helper calls the Swift decoder makes on `FitDives`, `FitHeart` and `FitUnits`, and makes the exception it throws.
- Hiding a dependency is not safe by default. With kotlinx-datetime hidden, `DecodedActivity`'s constructor (it takes a
  `LocalDateTime?`) was generated as `fatalError()`, with no warning. Left unconfigured it exports as far as it is named (1,892 lines);
  marked `EXPOSED` it exports whole (8,250 lines). Only `:core-location` and kotlinx-serialization-core are hidden.
- The Compose Multiplatform plugin copies resources into the app from the module Xcode's task runs in. Without it on `:iosExport` the
  build succeeds and the app bundle has no `compose-resources`. It needs the Compose compiler plugin beside it, which is why the
  stability check gained an iOS-only fallback.
- A Swift class implements a Kotlin interface by extending `KotlinBase` (`final class SwiftFitDecoder: KotlinBase, FitDecoder`). A
  Kotlin exception is a Swift `Error`: the decoder throws an `ActivityFileException` and Kotlin receives that same object, so
  `FitDecoder.decode` keeps throwing (design decision 4's first option).
- Nullable numbers are native optionals (`Int64?`, `Int32?`, `Double?`, `Bool?`) and lists of them are Swift arrays, which took the
  boxing (`KotlinLong(value:)` and the like) out of about 200 lines of `SwiftFitDecoder`. A `ByteArray` is a Kotlin class read one element at a time, as before.
- A module exported through another's signatures gets no short names: Swift writes
  `ExportedKotlinPackages.io.github.solcott.activitytracker.activityfile.DecodedActivity`, so `SwiftFitDecoder` declares type aliases.
  `:iosShell`, named by the umbrella with its package as the root package, gets `IosApp` and `FitSupport` as top-level names.
- Calf needed nothing: the framework's `export(libs.calf.ui)` has no counterpart, the app links without one and its native pieces
  work (the table above).

Size and time, debug build for the simulator on this machine:

| | Objective-C framework | Swift export | Swift export with three contracts | Swift export with every contract (phase 1) |
|---|---|---|---|---|
| Generated Swift | none (a header) | 5,352 lines | 42,361 lines | 91,668 lines |
| App's debug dylib | 213.9 MB | 198.3 MB | 218.6 MB | 220.2 MB |
| Build with nothing changed | 6 s | 5 s | 5 s | 5 s |
| Build after a one-line Kotlin change in `:iosShell` | not measured | 50 s | 57 s | 54 s |
| First build of the binary, other modules already compiled | 187 s | 396 s | not measured | not measured |

The last column has 21 contract modules exported whole and only kotlinx-serialization-core hidden. Twice the Swift of the probe costs
nothing measurable in a rebuild, so exporting the contracts whole stands and the facade alternative isn't needed.

### Contract probe (2026-11-09)

`:feature-login-api`, `:feature-segment-api` and `:feature-detail-api` were added to the umbrella on uncommitted changes, since removed.
A throwaway Swift file read `LoginState`, `StarredSegmentsState` and `ActivityDetailState`, called each `eventSink` and switched over
their sealed types. It compiled without an error or a warning. Nothing ran it, so every "works" below means "compiles".

What it took to get there:

1. As added, the export generated 71,192 lines of Swift and didn't build. The contracts name `TextFieldState`, which pulled in Compose
   foundation, ui-text and ui-graphics. Their generated Kotlin bridge doesn't compile (an `AnnotatedString` constructor taking a
   `List<Range<Any?>>`) and neither does their Swift (a nested type named `Type`).
2. `PlaceNameFields` (`:app-common-api`) has a property named `self`. The bridge of its `copy` then has two parameters named `self` and
   doesn't compile. Renaming the property fixed it.
3. With Compose (foundation, ui, ui-text, ui-graphics, ui-geometry, ui-unit, runtime, runtime-saveable), Ktor, Room, SQLite, Kermit,
   kotlinx-io and kotlinx-serialization-json hidden, and that property renamed, everything built.
4. Exposing Compose foundation alone, to see whether `TextFieldState` could stay, doesn't build either: its Swift names
   `AnnotatedString.Range` from the hidden ui-text.

| Construct | Result | What it means for the contracts |
|---|---|---|
| `sealed interface` events and sub-states | A Swift protocol with `sealedType()`, an enum Swift switches over exhaustively with no `default`. Works for the contracts' own, for `AuthCoordinator.Status` in `:core-data` and for `LoadStatus` in the uistate library | No change. They stay `sealed interface` |
| `data object` and `data class` cases | `LoginEvent.Submit.shared`; `StarredSegmentsEvent.Open(uri:)`. A case's payload is `case let .open(open)` then `open.value.uri` | No change |
| `eventSink: (Event) -> Unit` | A Swift closure, `state.eventSink(event)` | No change |
| `ImmutableList`, `ImmutableSet` fields | Exported, but as the Kotlin collection protocols: `size`, `_get(index:)`, `iterator()`, with untyped elements to cast. A plain `List<T>` field is a typed Swift array | Change to `List` and `Set` where Swift reads them, or wrap them once in Swift. `ImmutableMap` wasn't in the three contracts |
| `ContentState<T>`, `PagedState<T, M>` | Exported classes. `data` is `(any _KotlinBridgeable)?`, `items` is `[(any _KotlinBridgeable)?]`, cast with `as? T`. Status, `canLoadMore` and the rest are typed | No change. The Swift design system needs typed wrappers, as planned |
| `TextFieldState` field | With Compose exposed, a build failure. With Compose hidden, the field's getter and the state's constructor and `copy` are `fatalError()` stubs and the rest of the state works | The app-owned type goes ahead as designed |
| `Did`, `AtUri` value classes | Ordinary classes: `AtUri(value:)`, `.value`, and the companion's `of` and `parseOrNull` | No change |
| `Duration`, `Instant`, `LocalDate`, `ByteString` | The three contracts hold none of the first three. `LocalDateTime` (from `:core-activityfile`) is an exported class. `ByteString` (`PickedPhoto.bytes`) wasn't read | Still open for `Duration` and `Instant`; check them on the first contract that has one |
| `Screen`, `CircuitUiState`, `SubScreen` supertypes | Circuit's runtime modules export as far as named: `LoginState` is an `any CircuitUiState`, `LoginScreen.shared` an `any Screen`. `SubScreen` wasn't touched | No change |
| A property named `self` | Build failure in the generated bridge | Rename `PlaceNameFields.self`; the guard should refuse the name |
| `:core-data` and `:core-lexicon` types | 10,258 and 7,303 lines of Swift for what three contracts reach, a quarter and a sixth of everything generated | Moving model types out of `:core-data` is about size and about not needing Ktor and Room hidden, not about making the export work |
| Size and time | In the table above | Fine at three contracts; measure again at ten |

### Go or no-go

Go on Swift export. The app passes the simulator pass on it, the export carries every construct the contracts use except Compose types,
and the failures it has are build failures or, for a hidden type, a stub found by reading the generated Swift. The one runtime failure
the spike met came from the Kotlin beta, not from the export (the composable `fun interface` lambda above): it is in how the beta
compiles `:composeApp` against Circuit's klib, which the Objective-C framework linked too. That build wasn't run signed in to confirm.

What phase 1 has to do is smaller than planned:

- `TextFieldState` leaves the contracts (the app-owned type).
- `PlaceNameFields.self` is renamed.
- Collections the Swift UI reads become `List` and `Set`, or get one Swift wrapper. Decide per the first native list screen.
- The guard checks exported modules for Compose types, properties named `self`, and a hidden module's type in a signature Swift calls.
- Sealed interfaces, value classes and the Circuit supertypes need nothing.

### Phase 1 findings (`ios-swift-clean-contracts`)

The text field module, `:core-textfield`, exported whole as the Swift module `CoreTextfield`:

- `TextFieldState` is a Swift class. `value` is a `Swift.String` property with a getter and a setter, which is what a Swift field binds.
- `text`, a Kotlin `CharSequence`, is exported as `any ExportedKotlinPackages.kotlin.CharSequence`: usable (`state.text.length`), not a
  stub, and not a Swift `String`. Swift reads `value`.
- `rememberTextFieldState` is not in the generated Swift at all. A `@Composable` function is left out of the export, not stubbed, so it
  needs no exception in the stub check.
- `textFieldStateOf(initialText:)` is a function of the module's package (`ExportedKotlinPackages.….textfield.textFieldStateOf`; a
  top-level one when the module is given its package as root package, which the contract modules are not), and
  `setTextAndPlaceCursorAtEnd(text:)` and `clearText()` are methods on the class. A Kotlin default argument is not exported: Swift
  always passes `initialText`.
- `composeState`, typed `Any`, is `any _KotlinBridgeable`. Nothing in the module is a stub.
- An `expect class` whose actual is a class on iOS and a type alias to Compose's class in `nonAppleMain` compiles on every target and
  resolves from other modules' common code with no compiler warning.

Every contract module exported (`:app-common-api` and the 19 feature API modules, beside `:core-textfield` and `:iosShell`). A
throwaway Swift file, since removed, read a state and sent an event for each construct the probe had left open. It compiled without an
error or a warning; nothing ran it.

| Construct | Result | What it means |
|---|---|---|
| `Duration`, `Instant` (`UploadsState`) | Exported standard-library classes: `timeLeft?.inWholeSeconds`, `serverPausedUntil?.epochSeconds` | No change |
| `LocalDate` (`EditGearState`, `EditGearEvent.AcquiredChanged`) | An exported class Swift reads (`year`, `day`) and builds: `LocalDate(year:month:day:)` | No change |
| `ByteString` (`ImportActivityEvent.FilePicked`) | Readable as far as named, but Swift can't make one: Kotlin's `ByteArray` initialiser is a stub. With kotlinx-io-bytestring marked `EXPOSED` (about 900 lines), `toByteString(data as NSData)` builds it | The module is exposed |
| `ImmutableMap` (`ImportActivityEvent.SaveBatch`) | The getter is exported as the Kotlin map protocol (`size`, `_get`). The class had no initialiser at all | See the next row |
| A constructor taking `ImmutableList`, `ImmutableSet` or `ImmutableMap` | The class is exported with its getters and **no initialiser or `copy`**, with no warning. 100 contract classes, nearly all states, which Swift never builds. Four were events Swift could not send | Those four have a second constructor taking `List` or `Map`, which exports as an initialiser taking a Swift array or dictionary. The stub check fails on an event with no initialiser |
| `SubScreen` (`ReportScreen`) | Built and read in Swift like any class. The class has no `SubScreen` conformance in Swift: the generic interface isn't exported | Phase 2's host matches sub-screens in Kotlin |
| `GeoPoint` in `RouteEnds` | `ends.start?.lat`, `RouteEnds(start:end:)`, `RouteEnds.Companion.shared.HIDDEN` | No change |
| A text field in a state (`LoginState.handle`) | `state.handle.value`, read and set | As designed |
| A `@Serializable` screen | Its `serializer()` is a stub, typed with the hidden serialization-core: 55 in the contracts | Allowed by name in the stub check; Swift has no use for it |
| A type from a module that is neither named nor hidden (`:composeApp`) | Exported as far as named, under a generated module name. Not a stub | Only a hidden module's type makes a stub |
| An object extending an exported class; a declaration deprecated as an error | `private init()` without `override`; `@available(*, unavailable)` over a stubbed body | The stub check knows both |

Hidden modules: only kotlinx-serialization-core. The probe hid Ktor, Room, SQLite, Kermit, kotlinx-io and kotlinx-serialization-json
too, but with no Compose type left in the contracts the export builds without hiding them, and each comes through only as far as
`:core-data`'s signatures name it.

## Open items

- A background task running on Swift export, which needs a device, and the signed-in app on iOS 18, which needs a simulator runtime
  whose HTTP/3 works or a tunnel without it.
- The macOS CI job has not run: it needs the branch pushed. Its runner label and the memory it gives Gradle are untested.
- A class whose constructor takes an immutable collection has no Swift initialiser. The stub check catches that on events; a payload
  class an event takes (`FeedFilter`, `CategorySelection`) shows up only when Swift tries to build one.
- Whether the erased `List<Pair<…>>` bridge and the `self` clash are reported upstream, and fixed before Kotlin 2.5.0.
- Whether the composable `fun interface` lambda linkage error is reported upstream, and a check for such lambdas until it is fixed: a
  missed one crashes only on iOS and web, and only when its screen draws.
- Photo compression is WebP through Skia on iOS, and iOS has no system WebP encoder.
- Swipe back on a Compose fallback screen, whose native navigation bar is hidden.
- Settings' list beside its page on iPad regresses between phase 2 and the settings migration unless settings goes first.
- After the migration every UI change is made twice. Shared fixtures and snapshot coverage on both sides are the parity check.
