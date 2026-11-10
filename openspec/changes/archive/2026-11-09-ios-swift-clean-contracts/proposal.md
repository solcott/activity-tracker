# Proposal

## Why

The spike (`ios-swift-export-spike`, archived) showed Swift export carries every construct the screen contracts use except three: a Compose `TextFieldState` in a state, a property named `self`, and any type from a module the export hides, which becomes a stub that stops the app with no warning. Phase 2 of the native iOS UI (`openspec/explorations/ios-native-ui.md`) writes the first SwiftUI screen against those contracts, so they have to export cleanly first, with checks that keep them that way.

## What Changes

- **Text field state becomes an app-owned type.** A new small module holds `TextFieldState` and the four functions presenters call on it. On Android, desktop and web it is Compose's own class under the same name, so nothing changes there. On iOS it is a class Swift can read and write. Contracts, presenters, fixtures and UIs change their imports, and `TextFieldState()` becomes `textFieldStateOf()` in common code.
- **Compose leaves the contract modules.** `compose-foundation` comes off the feature API convention and `:app-common-api`; `LocalSettingsPane` moves to `:app-common-ui`. Only the `@Immutable` and `@Stable` annotations stay.
- **`PlaceNameFields.self` is renamed `viewerDid`.**
- **Every contract module is exported to Swift**: `:app-common-api`, the 19 feature API modules and the text field module, beside `:iosShell`. Whatever else fails to export when all of them are in (the probe covered three) is fixed here.
- **Two guards.** A source check, in `./gradlew check`, fails on a Compose type, a `self` property or a `Pair` in a contract module. A check of the generated Swift, in the Xcode build, fails on a stub in a contract module, the text field module or `:iosShell`.
- **CI builds the iOS app** for the simulator on a macOS runner. There is no iOS job today.
- **No visible change** on any platform. The iOS app is still one Compose view controller, and its text fields keep the Compose state they have today.

Out of scope, each decided later by the phase that needs it: changing `ImmutableList` and `ImmutableSet` fields to `List` and `Set` (decided on the first native list screen), moving model types out of `:core-data` (a size question, not a blocker), any SwiftUI view, the headless presenter host, and iOS tests in CI.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `dev/ios-swift-interop`: adds that every screen contract is exported to Swift, that a contract holds nothing the export rejects, that no declaration Swift can reach there is a stub, and that text field state is a type Swift reads and writes.
- `dev/continuous-integration`: adds a macOS job that builds the iOS app.
- `dev/module-boundaries`: an API module may also depend on the text field state module.

## Impact

- **New module:** `:core-textfield` (`core/textfield`), in `settings.gradle.kts`.
- **Contracts:** 25 files across `app/common/api` and 13 feature API modules hold 52 text field declarations; `PlaceNameFields.kt`; `SettingsPage.kt`.
- **Presenters, fixtures, UI:** imports in about 26 presenter files, 18 files of `:app-fixtures` (54 constructor calls become `textFieldStateOf`), 9 UI files and 5 design-system files, including `AppTextField` and its iOS and non-Apple actuals.
- **Build logic:** `FeatureConventionPlugins.kt`, a new source check and a new generated-Swift check with TestKit and unit tests, `ModuleBoundaries.kt` only if the new module needs a rule, `config/detekt/detekt.yml` (a forbidden import).
- **iOS:** `iosExport/build.gradle.kts` (the exported modules and the hidden list), the Xcode build phase in `project.pbxproj`. The generated Swift grows from 5,352 lines to several times the 42,361 the three-contract probe produced, with the build time and binary size that go with it; both are measured.
- **CI:** `.github/workflows/ci.yml` gains a macOS job, free for a public repository but slower than the Linux one.
- **Docs:** `AGENTS.md`, `.agents/rules/compose-screens.md`, `.agents/rules/build-logic.md`, the `new-screen` skill, `README.md`'s module table, and the roadmap's phase 1 and text field sections.
