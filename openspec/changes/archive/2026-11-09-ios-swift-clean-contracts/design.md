# Design

## Context

See proposal.md for the motivation. What shapes the approach:

- 52 text field declarations in 25 contract files across `:app-common-api` and 13 feature API modules are Compose's `TextFieldState`. Common presenter code uses three things on them: `.text` (24 uses), `setTextAndPlaceCursorAtEnd` (8) and `clearText` (5), and creates them with `rememberTextFieldState` (44 calls, plus 4 in UIs). Presenter tests, all on the JVM, also call Compose's `edit { }`.
- `AppTextField` in `:ui-designsystem` takes the state. `:ui-designsystem` holds no app contracts and doesn't depend on `:app-common-api`, and `checkModuleBoundaries` lets an API module depend only on API modules and `:core-*`.
- The contract modules import nothing else from Compose except `@Immutable` (75 files) and one `compositionLocalOf` (`LocalSettingsPane`).
- Swift export exports a module the umbrella names whole, and a module reached through signatures only as far as it is named. A signature naming a hidden module's type is generated as a function, getter or initialiser whose body is `fatalError()`. The same call appears legitimately in every exported object's `private override init()` and in the `default:` of generated enum initialisers.
- The generated Swift exists only after Xcode's build phase runs `:iosExport:embedSwiftExportForXcode`: the tasks that write it are set up from Xcode's environment.
- CI's Linux job turns off cross-compilation of the iOS klibs, so nothing there compiles iOS code. On the JVM the app-owned type and Compose's are the same class, so a JVM compile can't tell them apart.
- The probe left open how `Duration`, `Instant`, `LocalDate`, `ByteString`, `ImmutableMap` and `SubScreen` export. The contracts hold all of them.
- A `List<Pair<Double, Int>>` parameter is what failed to export in `:core-activityfile`. The contracts' one `Pair`, in `RouteEnds`, was replaced with `:core-location`'s `GeoPoint` before this change, so they hold none now.

## Goals / Non-Goals

**Goals:**

- A SwiftUI view could be written against any screen's contract the day this lands, with the build telling us when a contract stops exporting.
- No behaviour change anywhere, including every Compose text field on iOS.
- Presenters and their tests change imports and nothing else.

**Non-Goals:**

- Anything Swift needs to observe a text field (a flow of Kotlin-side changes, an `@Observable` wrapper). Phase 2 builds the Swift field and decides its shape. This change gives it a readable, settable value.
- Short Swift names for the contracts. Swift spells them as the export generates them; aliases are written where they are first used.
- Reducing what `:core-data` and `:core-lexicon` export.

## Decisions

### 1. The type lives in a new module, `:core-textfield`

`TextFieldState` has to be visible to the contracts and to `AppTextField`, and neither side may depend on the other. `:core-textfield` (`core/textfield`, package `io.github.solcott.activitytracker.textfield`) sits below both: `:app-common-api` and `:ui-designsystem` each take it as an `api` dependency, and the feature API modules get it through `:app-common-api`. The `:core-` prefix is what `checkModuleBoundaries` already allows an API module to depend on, so that rule doesn't change.

Rejected: `:app-common-api` (the roadmap's first idea), because the design system would have to depend on the app's contracts; and `:ui-designsystem`, because every signature there is Compose and it would have to be exported.

### 2. `expect class`, an alias to Compose's class off iOS

Common code declares:

```kotlin
@Stable expect class TextFieldState { val text: CharSequence }
expect fun textFieldStateOf(initialText: String = ""): TextFieldState
@Composable expect fun rememberTextFieldState(initialText: String = ""): TextFieldState
expect fun TextFieldState.setTextAndPlaceCursorAtEnd(text: String)
expect fun TextFieldState.clearText()
```

In `nonAppleMain` (Android, desktop, web: the group the library convention already defines) the class is `actual typealias TextFieldState = androidx.compose.foundation.text.input.TextFieldState` and each function calls Compose's function of the same name. Everything on those platforms is the object it is today: `AppTextField`'s non-Apple actual passes it straight to the Material field, and JVM tests keep calling `edit { }` and `TextFieldState("text")`.

The expect class declares no constructor, because Compose's takes a `TextRange`; that is why common code gets `textFieldStateOf`. The names match Compose's so the change in a presenter is its import lines.

Rejected: an `XChanged(String)` event per field and per-field accessor shims, for the reasons in the roadmap; and one common wrapper class on every platform, which would change 52 declarations' behaviour off iOS for nothing and break the tests' `edit { }`.

### 3. On iOS the class wraps Compose's state until Compose leaves iOS

This departs from the roadmap, which had the iOS class hold its own snapshot state and `AppTextField.ios.kt` copy text between it and a local Compose state. That copy runs in both directions on every keystroke of every Compose field on iOS until phase 5, and a two-way copy between two text states is how characters get dropped and cursors jump (it is the reason the `UseTextFieldState` rule exists).

Instead the iOS `actual class TextFieldState` holds a Compose `TextFieldState` privately:

- `text` reads it; the two functions call Compose's; `rememberTextFieldState` saves and restores through Compose's saver. The Compose field on iOS edits the same object as today, so nothing about typing changes.
- `value: String`, a property Swift gets and sets. The setter does nothing when the text is unchanged.
- `composeState: Any`, which `AppTextField.ios.kt` casts back. It is typed `Any` because a public member typed with a hidden module's class would be a stub, and `internal` doesn't reach `:ui-designsystem`. It is the one place the wrapper leaks, documented as for the design system's field only.

`:core-textfield` keeps `compose-foundation` as an `implementation` dependency on iOS and an `api` one elsewhere. That costs nothing: Compose is linked into the iOS app until phase 5 regardless. Phase 5 swaps the class's inside for plain snapshot state and deletes `composeState`, touching no contract.

### 4. Imports change everywhere; a detekt rule keeps Compose's out

Contracts, presenters, `:app-fixtures`, the feature UIs, `:app-common-ui` and the design system import the four names from `:core-textfield`. `TextFieldState(...)` in common code (60 calls in fixtures and contracts, 7 in presenters, a few in UI) becomes `textFieldStateOf(...)`. `TextFieldLineLimits`, `InputTransformation`, `KeyboardActionHandler` and `maxLength` are UI-only parameters and stay Compose's.

A missed import compiles on the JVM, where the two types are one, and fails only in an iOS compile, which CI's Linux job doesn't run. So a detekt rule, `UseAppTextFieldState` in `:detekt-rules`, forbids importing Compose's `TextFieldState`, `rememberTextFieldState`, `setTextAndPlaceCursorAtEnd` and `clearText` everywhere except test files, `:core-textfield`'s two actuals and the design system's iOS field. That is in `check` on every platform.

It is the repo's own rule and not detekt's stock `ForbiddenImport`, as first planned, because the exceptions can't be written as paths: each detekt task matches `excludes` against a path relative to its own source set, so `**/jvmTest/**` matches in one task and not in another (the reason `UseSingleFlight` checks the file name). The rule skips files named `*Test.kt`, and the three bridging files suppress it by name.

`compose-foundation` comes off `FeatureApiConventionPlugin` and `:app-common-api`.

### 5. `LocalSettingsPane` moves to `:app-common-ui`; `self` becomes `viewerDid`

`LocalSettingsPane` is read by `SettingsUi` and provided by `composeApp`'s list-detail decoration, both of which already depend on `:app-common-ui`. It keeps its package, so no import changes. `SettingsPane`, a plain class, stays in the contracts.

`PlaceNameFields.self` holds the signed-in viewer's DID. `viewerDid` says so; about 9 uses change.

### 6. `:iosExport` names every contract module, from the build's module list

`:iosExport` gains an `api` dependency on `:core-textfield`, `:app-common-api` and every project whose path ends in `-api`, read from the root project's module names so a new feature needs no step. Each gets an explicit Swift module name derived from its path (`:feature-login-api` is `FeatureLoginApi`, `:core-textfield` is `CoreTextfield`), which the stub check also uses. The text field module isn't named `TextField`: a Swift module with the name of SwiftUI's own type can't be told from it in a file that imports both.

Hidden modules follow one rule: hide a module only when no contract signature names one of its types. kotlinx-datetime and `:core-location` stay unconfigured, because states hold `LocalDate`, `GeoPoint` and `RecordingState`. `:core-location` was hidden, so its line in `iosExport/build.gradle.kts` goes.

As built, only kotlinx-serialization-core is hidden, where the plan was to start from the probe's longer list (Ktor, Room, SQLite, Kermit, kotlinx-serialization-json, the Compose modules). With `TextFieldState` out of the contracts nothing reaches Compose, and the export builds with the rest left unconfigured: they come through only as far as `:core-data`'s signatures name them, about 12,000 of the 90,000 generated lines. Leaving them costs that Swift; hiding them would turn every `:core-data` member that names one of their types into a stub the check doesn't fail on, a silent runtime failure in place of a slightly larger build. The measurements decide whether that holds.

`kotlinx-io-bytestring` is marked `EXPOSED`. Unconfigured, only the `ByteString` class is exported, and Swift has no way to make one: Kotlin's `ByteArray` initialiser is itself a stub. Whole, the module adds about 900 lines and `NSData.toByteString()`, which is how Swift turns a picked file's `Data` into the `ByteString` of `ImportActivityEvent.FilePicked`.

Rejected: reaching the contracts only through `:iosShell`'s signatures, to keep the surface small. Nothing in `:iosShell` names them until phase 2's host exists, and "as far as named" would leave most of each contract unexported and unchecked.

### 7. What fails with all twenty modules in is fixed in the contract, smallest change first

The open constructs are settled by building. For each that fails or stubs: first try leaving its module unconfigured rather than hidden; then change the contract's type to one that exports (a data class in place of a tuple, as `RouteEnds` now has), keeping presenters' edits to renamed calls. A construct that can't be fixed that way stops the work for a decision; it is not allow-listed to get past the check.

Building found one more kind of failure, which is neither a build error nor a stub. A class whose constructor takes an `ImmutableList`, `ImmutableSet` or `ImmutableMap` is exported with its getters and no initialiser, with no warning. For a state that costs nothing: Swift reads states and never builds one. Four events were affected (`DetailsEvent.PhotosPicked`, `EditGearCollectionEvent.Add`, `ImportActivityEvent.FilesPicked` and `SaveBatch`), which Swift could then not send. Each gets a second constructor taking the plain `List` or `Map`, which Swift export turns into an initialiser taking a Swift array or dictionary. The field keeps its immutable type, so the presenters, the Compose UIs and the stability check see no change. The stub check fails on an event with no initialiser (decision 8).

### 8. Two guards, because the two failures show up in different places

**`checkSwiftExportSources`**, a text scan modelled on kmp-dataresult's `CheckSwiftExport`, registered by the feature API convention and applied to `:app-common-api` and `:iosShell`, and part of `check`. It fails on an `androidx.compose.` import other than `runtime.Immutable` and `runtime.Stable`, on a property or parameter named `self`, and on `Pair` or `Triple` in a declaration, which a contract names with a data class instead. It is cheap, runs on Linux and catches the mistakes that are easy to make. `sealed interface` and the immutable collections are allowed here, unlike in kmp-dataresult: the spike showed they export.

**`checkSwiftExportStubs`**, on `:iosExport`, reads the generated Swift under `build/SwiftExport`. A stub is a `fatalError()` body whose declaration isn't `private override init()` and that isn't an enum initialiser's `default:`. It fails on a stub in the Swift modules of the contracts, `:core-textfield` and `:iosShell`, except declarations matching an allow-list kept in `iosExport/build.gradle.kts` with a reason each. The list holds `serializer()` alone (every `@Serializable` screen gets one, typed with a hidden class); `rememberTextFieldState` needs no entry, because a composable is left out of the export, not stubbed. A declaration Swift marks unavailable (a Kotlin function deprecated as an error) isn't counted: nothing can call it. Stubs in every other module are written to `build/reports/swift-export/stubs.txt` without failing.

The same task fails on an event Swift can't build: a class named for a case of a sealed `…Event` interface that has neither a public initialiser nor a `shared` instance (decision 7). It can't see a dropped initialiser on any other class, since a state legitimately has none.

The Xcode build phase runs `./gradlew :iosExport:embedSwiftExportForXcode :iosExport:checkSwiftExportStubs`, with the check ordered after the export, so a local build and CI's both stop on a stub. Its parsing is a plain class with unit tests in `build-logic`, fed snippets of real generated Swift.

Rejected: only the source scan (it can't know which modules are hidden or how a type is reached), and only reading the generated Swift by hand, which is what the spike did and what AGENTS.md asks for today.

### 9. A macOS job that runs the same `xcodebuild` as a developer

A second job, `ios`, beside `check`: same `preflight` gate, a `macos-26` runner, the same JDK and `setup-gradle` steps, then the simulator `xcodebuild` from AGENTS.md with `CODE_SIGN_IDENTITY=-`. It builds every module for `iosSimulatorArm64`, runs the export and the stub check, compiles the generated Swift and the app's own. No simulator is booted and no test runs.

## Risks / Trade-offs

- [An `expect class` with a class on iOS and an alias elsewhere may export oddly, or the alias may not resolve from another module's `nonAppleMain`] → Task group 1 builds the module, exports it and compiles Swift against it before any call site moves. If the alias doesn't resolve through the group, the actuals go in each target's source set instead.
- [Build time and size grow with twenty modules exported whole: the probe's three took the one-line rebuild from 50 s to 57 s and the generated Swift from 5,352 to 42,361 lines] → Measure the same four figures as the spike. If the one-line rebuild passes two minutes, stop and decide before going on: the alternative is exporting the contracts only as far as a facade names them.
- [The stub check's text match breaks when a Kotlin release changes the generated Swift] → Its tests use real snippets, and it fails when it finds no `private override init()` stub at all in `:iosShell`'s Swift, which means the format changed rather than that the code is clean.
- [The allow-list becomes the way past the check] → Entries are matched by declaration name with a written reason, and decision 7 forbids using it for a contract's own field.
- [The macOS job is slow on a cold cache: the local first build of the binary was 396 s with the other modules compiled] → It runs beside `check`, not after it, with the Gradle cache `setup-gradle` keeps. A 45 minute timeout, as on `check`.
- [A Compose field on iOS regresses anyway] → The spec's scenario is checked by hand in the simulator on three fields that differ: the sign-in handle, a comment draft the presenter clears, and a search query.
- [`composeState: Any` is called from app code] → It is documented, exists only in the iOS source set, and is deleted in phase 5. Not worth a lint rule.

## Migration Plan

Each task group leaves the app building on every platform, so the change can land as several commits. Order matters in one place: the stub check exists before the contracts are added to the export, so they are added against it. Rolling back is reverting the commits; no data, record or stored state changes.

## Open Questions

- Settled: `TextFieldState.text` exports as `any kotlin.CharSequence`, usable and not a stub. Swift uses `value`.
- Which runner label is newest when the job runs, if `macos-26` has been superseded, and whether the sizes given to Gradle there fit the runner's memory: the job can't be run before the branch is pushed.
