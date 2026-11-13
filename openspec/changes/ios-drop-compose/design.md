# Design

## Context

See proposal.md for why. Today, on iOS:

- `:iosExport` exports `:iosShell`, the contracts and `:app-fixtures`. `:iosShell` depends on `:composeApp` (for `IosAppHost`, the
  background passes and `ScreenViewController`) and on `:app-common-ui` (for `compressAvatar`).
- `:composeApp`'s `commonMain` is both the graphs every platform shares (`AppGraph`, `SessionGraph`, `CircuitBindings`,
  `NetworkConfigs`, `BackgroundUploads`) and the Compose app (`App`, `nav/`, `preview/`), and it depends on every feature's `-ui`.
  `IosAppGraph` and `IosHost` live in its `iosMain`.
- `AppGraph.appCircuit`, `SessionGraph.sessionCircuit` and `SessionGraph.subCircuit` make every graph build a `Circuit` from
  `Set<Ui.Factory>` (circuit-foundation, Compose UI) and a `SubCircuit` from `Set<SubUiFactory>`.
- Every Compose convention module (`activitytracker.kmp.compose`: the feature `-ui`s, `:app-common-ui`, `:ui-designsystem`,
  `:ui-resources`, `:composeApp`) gets iOS targets from `activitytracker.kmp.library`.
- `circuitx-subcircuit`, which the contracts, `:app-common-api` and `:app-host` expose as `api`, publishes `api` dependencies on
  Compose Foundation and UI for every target, though only `SubCircuitContent` and `SubUi` use them. So even with every UI module gone
  from iOS, Compose UI would still resolve there.
- `:core-textfield`'s iOS `TextFieldState` wraps Compose Foundation's state so the Compose field could edit it.
- `:iosExport` applies the Compose Gradle plugin only so Xcode's task copies Compose resources into the app.

## Goals / Non-Goals

**Goals:**
- No Compose UI, Foundation, Material, Skia, Calf, Haze, Vico, Coil, MapLibre Compose or FileKit's Compose dialogs in what the iOS app
  resolves or links, held by `check`.
- Android, desktop and web graphs, UIs and tests unchanged in behaviour.

**Non-Goals:**
- Removing `UiPlatform.Ios` and the iOS branches of the shared theme and shell (dead on every build now, but selectable by a preview);
  a follow-up can.
- Moving Calf, Vico, Coil or MapLibre Compose off the other platforms.

## Decisions

**1. A new `:app-graph` module holds the shared graph and the iOS graph.** `app/graph`, `activitytracker.kmp.library` with Metro and
the native OAuth config. `commonMain`: `AppGraph` and `SessionGraph` without their Circuit members, `sessionHosts()`, `NetworkConfigs`
with its generated config, the background passes, and the `@Multibinds` for `Set<SubPresenterFactory>` (the host's sub-screens need
it on every shell). `iosMain`: `IosAppGraph`, `IosAuthBrowser`, `IosHost` (now only the host's start-up and the held photos) and the
background tasks. Its iOS source set depends on every feature's `-presenter` so the iOS graph aggregates their factories, as
`:composeApp` did. `:composeApp` depends on it (`api`) and keeps `App`, `nav/`, `preview/` and the Circuit bindings.
- *Rejected: an iOS-only source-set split inside `:composeApp`* (UI in a `nonIos` group, graphs in `commonMain`): it keeps the iOS
  graph in a module named and configured for Compose, applies the Compose plugin to iOS, and every UI dependency line would need a
  non-iOS source set.
- *Rejected: the iOS graph in `:iosShell`*: that module is exported to Swift whole, and its sources must pass
  `checkSwiftExportSources`; a Metro graph and the presenter host's Compose-runtime types would not.

**2. The Compose apps reach their Circuits through contributed accessors.** `:composeApp` contributes `AppCircuits` (`appCircuit`) to
`AppScope` and `SessionCircuits` (`sessionCircuit`, `subCircuit`) to `SessionScope`, with the bindings that build them and the
`Set<SubUiFactory>` multibinds. Metro makes each platform's graph and session graph implement the contributed interfaces, and `App`
reads them through one extension each (`AppGraph.circuits`, `SessionGraph.circuits`), a cast that holds on every graph `:composeApp`
generates. The iOS graph is generated in `:app-graph`, where neither interface nor the Circuit bindings are on the classpath, so it
binds no `Ui.Factory` or `SubUiFactory` and needs no `Circuit`.
- *Rejected: a second `@GraphExtension` per shell* (`ComposeSessionGraph`, `IosSessionGraph`): `AppGraph` would have to declare its
  session factory abstractly and every platform override it covariantly, which Metro's factory rules don't promise to accept, and the
  background passes would need a common factory anyway.

**3. The Compose convention declares no iOS targets.** `KmpLibraryConventionPlugin`'s setup becomes a function taking whether the
module has iOS targets; `activitytracker.kmp.library` passes true, `activitytracker.kmp.compose` false and no longer applies the library
plugin's id (it applies the same setup). Everything else (the hierarchy groups, Android, JVM, wasm) is shared. With no iOS targets,
a `nonApple` group equals `commonMain`, so `:ui-designsystem`'s `nonAppleMain` actuals move into `commonMain` and their `expect`s become
plain declarations; `nonWeb`, `nonAndroid` groups keep working with their native part empty. TestKit asserts a Compose module has no
iOS targets and a library module still does.

**4. SubCircuit's UI dependencies are excluded from iOS resolution.** The library convention, `activitytracker.swift.contract` and
`activitytracker.swift.export` exclude `org.jetbrains.compose.ui` and `org.jetbrains.compose.foundation` (and their `androidx`
redirects) from every iOS configuration (`ios*` and the native metadata ones). The iOS code uses only SubCircuit's runtime interfaces;
`SubCircuitContent` is never reached, so the linker drops it. Verified by the iOS link and the Swift tests.
- *Rejected: leaving them*: the app would still carry Foundation and UI klibs on its link path, and nothing would stop a UI library
  coming back.

**5. `checkIosDependencies` in `:iosExport` holds it.** A task over the resolved iOS link configuration of `:iosExport` (both iOS
targets) fails, naming the module and the path that brought it, on any component in a forbidden group: Compose UI, Foundation,
Material and Material 3, Skiko, Calf, Haze, Vico, Coil, MapLibre Compose and FileKit's dialogs. It is part of `check`. A TestKit test
covers a forbidden and an allowed graph.

**6. The iOS `TextFieldState` is a snapshot string.** `mutableStateOf(initial)` inside; `value` reads and sets it (setting the same
text changes nothing), `values` is a `snapshotFlow`, and `rememberTextFieldState` remembers it with `rememberSaveable` and a saver of
its text. No cursor or selection to keep: SwiftUI's field owns those.

**7. The avatar's JPEG is made in `:iosShell`** with FileKit core's `compressImage`, the same call, format, size and quality
`compressAvatar` makes off the web. `:iosShell` depends on `:app-graph`, `:core-data` (FileKit core) and `:feature-profile-api`,
not on `:composeApp` or `:app-common-ui`.

**8. `:iosExport` drops the Compose Gradle plugins.** No iOS module has Compose resources any more (strings and images come from the
generated catalogs); the export needs only Kotlin.

**9. Haze leaves the build.** It was only the iOS bar's backdrop: `LocalBarBackdrop`, `rememberBarBackdrop` and their uses go and the
scaffold's content is drawn as on the other platforms.

## Risks / Trade-offs

- [Excluding SubCircuit's UI klibs breaks the iOS link] → Kotlin/Native's partial linkage tolerates unreferenced declarations whose
  types are missing; if it doesn't, the fallback is to keep the two klibs and rely on dead-code elimination, measured by the binary
  check, and record it.
- [The cast in `SessionGraph.circuits` fails at run time on a graph without the contribution] → only graphs `:composeApp` generates call
  it; `:composeApp`'s JVM UI tests and the screenshot tests create real graphs and would fail first.
- [A `nonApple`-to-common move changes the Material wrappers] → it is a file move with `actual` and `expect` dropped; screenshot tests
  hold the output.
- [Restored text field state on iOS loses its cursor] → SwiftUI owns the cursor; acceptable.
