# Proposal

## Why

Phase 2 (`ios-native-shell`, archived) gave the iOS app a SwiftUI shell and one native screen, sign-in, built on a hand-written catalog of 20 strings, one colour pair, one button and one text field. Phase 4 of the native iOS UI (`openspec/explorations/ios-native-ui.md`) moves the other 50 screens, one change each. Every one of them needs the app's text, icons, numbers, error messages and common controls, and a way to see and check a view without a session. Built per screen, the 2,026 shared strings would be retyped as they are needed, and the rules in `ui/number-format` and `ui/error-messages` would be written a second time in Swift, to drift. Phase 3 builds those foundations once, before the first feature needs them.

## What Changes

- **The iOS String Catalog is generated from `strings.xml`.** Every shared string, plural and string array is in `Localizable.xcstrings` under its shared name, written by a Gradle task and committed. `check` fails when the committed file isn't what the task writes, which replaces the name-by-name comparison of phase 2. Swift names a string through the symbols Xcode generates from the catalog, so a string removed from `strings.xml` stops the iOS build where it is used.
- **Text whose choice is a rule is chosen once, in Kotlin.** A small type, `AppText` (a shared string's name and its arguments, a plural with its quantity, or text as written), lets common code say what to show without reading resources. The unit formatters (`Format`), the labels with a fallback (`sportLabel` and the rest of `Labels.kt`), and the error text (`errorText`, `failureText`) return it from `:app-common-api`; the Compose UI resolves it from Compose resources as today and the SwiftUI views from the catalog. No screen's text changes.
- **Icons.** The Swift `AppIcon` (each icon's SF Symbol, outlined and filled) is generated from the Kotlin `AppIcon`, and the 82 vendored drawables become template images in a generated asset catalog, so a sport's icon is the same shape on every platform. Which icon a sport gets moves beside `AppText`, named rather than typed with a Compose resource.
- **A Swift design system**, in `iosApp/iosApp/Design`: the colours the theme adds to the system's, the three buttons and the icon button, the text field with its supporting and error text, a list row, a card, progress, the icon and sport icon views, the error message with Retry, and the layout for loaded content (first-load spinner, failure with Retry, refresh status) that `ui/content-loading` describes.
- **Sample states reach Swift.** `:app-fixtures` is exported to Swift in debug builds only, so a native view's Xcode previews and tests render the same samples the Compose UI tests and Android screenshots do. Release builds don't carry them.
- **Snapshot tests for native views**, with swift-snapshot-testing: sign-in in each of its samples and each design system piece, light and dark, compared on the simulator OS the references were recorded on and skipped elsewhere.

Out of scope: any other native screen (phase 4); date and time text, the dive and stat tables and every other composable that builds text, which move with the feature that shows them; translations (the catalog is English, as `strings.xml` is); right-to-left mirroring of icons; `ImmutableList` fields (decided on the first native list screen); SwiftLint and swift-format; removing Compose from iOS (phase 5).

## Capabilities

### New Capabilities

- `dev/ios-native-resources`: the iOS app's strings, plurals, icons and sport images are generated from the shared UI's sources, and a check holds the generated files to them.
- `dev/shared-display-text`: text that follows a rule (a stat with its unit, a label with a fallback, a failure with its reason) is chosen in shared code and reads the same in the shared and native UIs.
- `ui/ios-design-system`: what the native iOS controls and the loaded-content layout do, so native screens behave alike.
- `dev/ios-snapshot-tests`: native views render from the shared sample states in previews and in snapshot tests, and where those tests compare.

### Modified Capabilities

- `ui/ios-native-shell`: the requirement that native text matches the shared text moves to `dev/ios-native-resources`, where it covers every string instead of a hand-kept few.

## Impact

- **Build logic:** `IosStrings.kt` becomes the catalog generator (strings, plurals, arrays, format specifiers) with `generateIosResources` and `checkIosResources`; a drawable-to-SVG converter and the `AppIcon` reader join it; unit and TestKit tests. `checkIosStrings` goes.
- **`:app-common-api`:** `AppText`, the stat formatters, the labels, the error text's names and the sport icon names; it gains `:core-data`'s units and sport catalog in its signatures if it doesn't have them. **`:app-common-ui`:** `Format`, `Labels.kt`, `ErrorText.kt` and `SportIcons.kt` keep their signatures and resolve `AppText`; a test holds every name they can return to an existing resource.
- **`:app-fixtures`:** gains the Swift source check; `ProfileSamples.self` is renamed, since Swift export can't carry a member named `self`.
- **`:iosExport`:** exports `:app-fixtures` unless Xcode is building Release.
- **`iosApp`:** the generated catalog replaces the hand-written one; a generated asset catalog and `AppIcon.swift`; the design system; `AppText` and stat resolution in Swift; previews; swift-snapshot-testing as the project's second Swift package (after the FIT SDK), on the test target only; reference images under `iosApp/iosAppTests/__Snapshots__`.
- **CI:** no new step. `check` runs the resource check on Linux; the `ios` job's tests skip the snapshot comparisons unless its simulator's OS is the recorded one.
- **Docs:** `AGENTS.md`, `.agents/rules/compose-screens.md`, `build-logic.md` and `adaptive-ui.md`, the `new-screen` skill, and the roadmap's phase 3 line, findings and open items.
- **Risk carried:** a 2,026-entry catalog with generated symbols may slow the Swift build, and the plural form that takes its quantity apart from its arguments is unproven. Both are probed first, each with a fallback.
