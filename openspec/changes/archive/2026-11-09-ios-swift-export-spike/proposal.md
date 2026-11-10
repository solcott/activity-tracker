# Proposal

## Why

The iOS app is moving from the shared Compose UI to SwiftUI on the existing Circuit presenters (`openspec/explorations/ios-native-ui.md`), with Swift export as the bridge between Swift and Kotlin. Swift export is Beta only from Kotlin 2.5, needs iOS 18, and is known to reject constructs the screen contracts use, so the toolchain has to be proven on the app as it is, and the contracts measured against it, before any screen is rewritten.

## What Changes

- **Kotlin moves from 2.4.21 to 2.5.0-Beta2** for every module, with the compiler-plugin dependencies that need a matching release.
- **BREAKING: the iOS app's minimum version rises from iOS 16.0 to iOS 18.0.**
- **The iOS app reaches Kotlin through Swift export** in place of the Objective-C framework `:composeApp` builds today. A small Kotlin module holds the entry points Swift calls, an umbrella module exports it, and the Xcode build phase runs the Swift export task.
- **The three existing Swift files are ported** to the exported Swift API: the app entry, the Compose host view and the FIT decoder. The decoder is the one Swift class Kotlin calls into, so it also proves Swift can implement a Kotlin interface and report a failure through it.
- **The UI doesn't change.** The app is still one Compose view controller.
- **A probe, not merged, exports three real screen contracts** (login, starred segments, activity detail) and records what Swift export does with each construct they use. Its findings go into the exploration note and decide the next change's scope.

Out of scope: any SwiftUI screen, any edit to a screen contract, the headless presenter host, and an iOS job in CI. If Swift export fails here, the fallback is SKIE on Kotlin 2.4.20, as its own change.

## Capabilities

### New Capabilities
- `dev/ios-swift-interop`: which iOS versions the app supports, how its Swift code reaches the shared Kotlin code, and that Kotlin can call Swift the app supplies.

### Modified Capabilities

None.

## Impact

- **Every platform:** `gradle/libs.versions.toml` (Kotlin, and Metro, KSP or others as 2.5 requires), `kotlin-js-store/wasm/yarn.lock`.
- **Build:** `composeApp/build.gradle.kts` loses its framework block; two new modules in `settings.gradle.kts`, one for the Swift-facing entry points and one umbrella with the export configuration.
- **iOS app:** `iosApp/iosApp/iOSApp.swift`, `ContentView.swift`, `SwiftFitDecoder.swift`, and `iosApp/iosApp.xcodeproj/project.pbxproj` (build phase, deployment target, framework search paths and linker flags).
- **Possibly `core/activityfile`:** `FitDecoder` returns a result instead of throwing, only if a Swift implementation can't raise a Kotlin exception through Swift export.
- **Docs:** `AGENTS.md`'s iOS paragraph and any rule file that names the framework task.
- **People with iOS 16 or 17 devices** can no longer install the app.
