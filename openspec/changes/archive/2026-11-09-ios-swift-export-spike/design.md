# Design

## Context

See proposal.md for why. The state this design starts from:

- `:composeApp` builds a static Objective-C framework `ComposeApp` (`composeApp/build.gradle.kts`), exporting `:core-activityfile` (for `SwiftFitDecoder`) and `calf-ui`. Xcode's build phase runs `:composeApp:embedAndSignAppleFrameworkForXcode`.
- Swift calls four Kotlin entry points: `MainViewController(fitDecoder)` and `register`/`schedule` on `IosBackgroundUploads`, `IosImportSaves` and `IosDataExports`, all in `composeApp/src/iosMain`.
- `SwiftFitDecoder` (529 lines) implements the Kotlin `FitDecoder` interface, builds Kotlin DTOs with boxed numbers (`KotlinLong(value:)`), takes a `KotlinByteArray` and throws `ActivityFileException(...).asError()`. All four are Objective-C bridge idioms.
- Kotlin is 2.4.21, Metro 1.5.2, KSP 2.3.13, Compose Multiplatform 1.12.2, Circuit 0.39.0. The repo uses explicit backing fields and `-Xexpect-actual-classes`.
- CI builds nothing for iOS, so every iOS check in this change is run by hand.
- On Kotlin 2.5.0-Beta2 Swift export is configured with `export { swift { } }`, and `api` dependencies of the exporting module are exported automatically. A published migration found that an umbrella module with no code keeps the surface small, that a return type from a hidden dependency becomes a silent `fatalError()` in the generated Swift, and that the bundled coroutine support needs iOS 18.

## Goals / Non-Goals

**Goals:**
- The app runs on Swift export exactly as it does today, on Kotlin 2.5.0-Beta2.
- A written, construct-by-construct record of what Swift export does with the screen contracts, enough to scope the contract change that follows.
- A clean way back: the Kotlin bump and the export switch can each be reverted on its own.

**Non-Goals:**
- Exporting screen contracts, presenters or anything a SwiftUI screen would need. The probe exports three contracts on a scratch commit only.
- Trimming `:core-data`'s public API, a guard task against unsupported constructs, or a macOS CI job. Those belong to the next change, which the probe scopes.
- Supporting both bridges at once. Two Kotlin binaries in one process would be two Kotlin runtimes.

## Decisions

**1. Kotlin 2.5.0-Beta2 repo-wide, landed before the export switch.** The Kotlin version is one catalog entry for every target, so it can't be raised for iOS alone. The bump is its own commit, verified on every platform with the Objective-C framework still in place, so a break is known to be the compiler's and not Swift export's. If a compiler-plugin dependency (Metro first, then KSP and Room's processor) has no release that works with the beta, the change waits for one or for the next Kotlin preview. Alternative considered: staying on 2.4.21, where Swift export is Alpha and uses the `swiftExport { }` block. That block is deprecated in 2.5 and the DSL this design relies on doesn't exist before it, so work done there would be redone.

**2. Two small modules: Swift-facing entry points, and an umbrella that exports them.**

```
:iosExport   no code. export { swift { } }, api(:iosShell)
    |
:iosShell    iosMain only. The entry points Swift calls.
    |            api(:core-activityfile)      FitDecoder and its DTOs
    |            implementation(:composeApp)  everything else, not exported
:composeApp  unchanged apart from losing its framework block
```

`:composeApp` can't be the exported module: its public API is full of Compose types (`App(graph, modifier)`, `AppShell`), and a Compose type in the reachable API breaks the iOS build. So the entry points move behind a facade whose signatures use only `UIViewController`, `FitDecoder` and its DTOs, and plain values. The Metro graph (`IosAppGraph`) stays in `:composeApp`, which is the module with every contributing module on its classpath. The umbrella is separate because the exporting module's own `api` dependencies are exported in full, while the dependencies of what it exports come through only as far as they're referenced. If the probe shows a single module exports no more than the pair does, they're merged. Outcome: they stay two. A module the umbrella names is exported whole, and exporting `:core-activityfile` whole doesn't build, so `:iosShell` also carries `FitSupport`, which repeats the shared FIT helpers the Swift decoder calls. Neither name ends in `-api`, `-presenter` or `-ui` or starts with `:feature-` or `:ui-`, so `checkModuleBoundaries` treats both as plain modules.

**3. Everything Swift doesn't name is hidden, and the generated Swift is read.** Compose, Circuit, Metro, Ktor, Room, Coil, Calf and the coroutine internals are configured as not exported. Because a hidden return type compiles to a `fatalError()` with no warning, the facade's signatures are checked against the generated Swift: no stub may be reachable from an entry point Swift calls (the spec's "Entry points only" scenario). In this change that check is a task done by reading the output; a guard task comes with the contract change. Outcome: almost nothing needed hiding, because a module no exported signature names isn't exported at all, and hiding one that a signature does name is what creates the stubs. Only `:core-location` and kotlinx-serialization-core are hidden; kotlinx-datetime is left to export as far as `DecodedActivity` names it.

**4. `FitDecoder` keeps throwing if Swift export allows it, and returns a result if not.** The port first tries the direct equivalent: a Swift class implementing the exported `FitDecoder` protocol and raising `ActivityFileException`. If a Swift implementation can't raise a Kotlin exception across the bridge, `FitDecoder.decode` changes to return a sealed result (decoded, or failed with a message) and its one Kotlin caller turns a failure into the same `ActivityFileException` it throws today, so nothing above it changes. Alternative considered: moving FIT decoding into Kotlin. Rejected because the Garmin SDK is Swift, which is why the decoder is Swift at all. Outcome: it keeps throwing. A Kotlin exception is a Swift `Error`, and Kotlin receives the same `ActivityFileException` the Swift decoder threw.

**5. The deployment target is set to iOS 18.0 outright.** It is decided, the later SwiftUI work wants iOS 18 APIs anyway, and finding the exact floor Swift export needs would only produce a number nobody will use.

**6. Calf is verified, not assumed.** The framework exports `calf-ui` today because "Calf's UIKit components need it exported". Swift export has no equivalent. The simulator pass opens each native Calf piece the app uses (the tab bar, a bottom sheet, an alert, the date wheels). If one fails, the finding is recorded and the change stops there: the tab bar is replaced in the next phase but the others are needed until their screens are native.

**7. The contract probe is a scratch change with a written result.** (Done on uncommitted changes rather than a commit, then reverted.) On top of the working build, three `-api` modules are added to the umbrella and a throwaway Swift file touches each construct in the table below. The commit is dropped; the table, with what happened and what each result forces, is added to `openspec/explorations/ios-native-ui.md` under "Spike findings". The three contracts were chosen to cover the archetypes: `LoginState` (text fields, a status from `:core-data`), `StarredSegmentsState` (`PagedState`, `ImmutableSet<AtUri>`, a value class in events) and `ActivityDetail` (the largest, with nested sealed sub-states and `ContentState`).

| Construct | What to record |
|---|---|
| `sealed interface` events and sub-states | Do they export, can Swift construct a case and switch with `sealedType()` |
| `data object` and `data class` cases | How Swift names and builds them |
| `eventSink: (Event) -> Unit` | Can Swift call it |
| `ImmutableList`, `ImmutableSet`, `ImmutableMap` fields | Exported, ignored or a build failure (KT-80416) |
| `ContentState<T>`, `PagedState<T, M>` | What `data` and `items` look like after erasure |
| `TextFieldState` field | Build failure, hidden stub or usable |
| `Did`, `AtUri` value classes | Underlying type, wrapper or unusable |
| `Duration`, `Instant`, `LocalDate`, `ByteString` | How each appears |
| `Screen`, `CircuitUiState`, `SubScreen` supertypes | Effect of a supertype from a module that isn't exported |
| `:core-data` and `:core-lexicon` types the contracts take | How much of each module is pulled in |
| Size and time | Lines of generated Swift, clean and incremental iOS build time, against today's |

**6a. The umbrella applies the Compose plugins.** Found during the switch: Compose's Gradle plugin copies every dependency's resources into the app from the module whose Xcode task runs, so without it on `:iosExport` the app builds with no strings or drawables. It refuses to apply without the Compose compiler plugin, so `composeStabilityCheck` falls back to the iOS simulator compilation in a module with no other target.

**8. The gate.** The change merges only if the app passes the simulator pass on Swift export and `check` passes on every other platform. If Swift export can't run the app, nothing merges past the Kotlin bump (which stands on its own if it's green), the finding is written up, and the fallback (SKIE on Kotlin 2.4.20) is proposed as a separate change.

## Risks / Trade-offs

- [Metro 1.5.2 doesn't support the Kotlin 2.5 beta] → Checked first, before any other work. Metro follows Kotlin previews closely; wait for a release rather than patching around it.
- [The beta changes behaviour the repo depends on: explicit backing fields, type inference, cross-module inlining in klibs] → The bump lands alone and `check` runs on every target, plus one iOS simulator test task, before the export switch.
- [No iOS CI, so an iOS regression goes unseen] → Every task that touches iOS names the `xcodebuild` command or simulator step that verifies it. The CI job is the next change.
- [Generated Swift is large and slows the iOS build] → Measured in the probe (decision 7); the facade keeps this change's surface to a handful of declarations.
- [A hidden type reaches Swift as a crash rather than a compile error] → Decision 3.
- [A beta DSL changes again before 2.5.0] → The configuration is in one file of one module with no code.
- [iOS 16 and 17 users lose the app] → Accepted in the proposal.

## Migration Plan

1. Commit the Kotlin bump alone once `check` and an iOS build pass with the Objective-C framework.
2. Commit the two modules, the Xcode project change and the Swift ports together, since none builds without the others.
3. Run the probe on a scratch commit, write the findings, drop the commit.

Rollback: revert step 2's commit to return to the Objective-C framework on Kotlin 2.5; revert step 1's as well to return to 2.4.21. The deployment target goes back with step 2.

## Open Questions

- Whether the umbrella and the entry-point module stay two modules (decision 2). The probe answers it and either answer leaves the specs and tasks as they are.
- Whether Swift export's package generation is worth adopting later, so Swift code could live in local packages. Not needed while all Swift is in the app target.
