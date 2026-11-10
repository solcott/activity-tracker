# Tasks

## 1. Kotlin 2.5.0-Beta2

- [x] 1.1 Find the releases of Metro, KSP and Room's processor that support Kotlin 2.5.0-Beta2 (Metro's compatibility page first). If Metro has none, stop here and record it in the exploration note. Verify by a written list of the versions to use.
- [x] 1.2 Raise `kotlin` in `gradle/libs.versions.toml` to 2.5.0-Beta2 with the versions from 1.1, and update the catalog comment that ties Circuit to a Kotlin version. Verify `./gradlew help` configures every module.
- [x] 1.3 Fix whatever the beta breaks (explicit backing fields, inference changes, opt-ins) without changing behaviour. Verify `./gradlew check` passes and `./gradlew :core-lexicon:iosSimulatorArm64Test` passes.
- [x] 1.4 Run `./gradlew kotlinWasmUpgradeYarnLock` and commit the lock file if it changed. Verify `./gradlew :webApp:wasmJsBrowserDevelopmentWebpack` succeeds.
- [x] 1.5 Build the iOS app with the Objective-C framework still in place, so a failure here is the compiler's and not Swift export's. Verify the `xcodebuild` command in `AGENTS.md` succeeds, then commit the bump on its own.

## 2. Swift export build

- [x] 2.1 Add `:iosShell` (iOS targets only, `api(:core-activityfile)`, `implementation(:composeApp)`) and `:iosExport` (no code, `api(:iosShell)`) to `settings.gradle.kts`. Verify `./gradlew :iosShell:compileKotlinIosSimulatorArm64` succeeds and `./gradlew checkModuleBoundaries` passes.
- [x] 2.2 Give `:iosShell` the entry points Swift calls: the root view controller, and register and schedule for background uploads, import saves and data exports, each delegating to the existing code in `composeApp/src/iosMain`. Every signature uses only `UIViewController`, `FitDecoder` and its DTOs, and plain values. Verify by compiling and by reading the signatures against that rule.
- [x] 2.3 Configure `export { swift { } }` in `:iosExport` (module name, root package, Xcode integration), with Compose, Circuit, Metro, Ktor, Room, Coil, Calf and coroutine internals not exported. Verify `./gradlew :iosExport:embedSwiftExportForXcode` generates Swift modules when run with Xcode's environment, and record the generated line count.
- [x] 2.4 Read the generated Swift for the entry points and `FitDecoder`'s types. Verify no `fatalError` stub is reachable from anything Swift calls (spec: "Entry points only").
- [x] 2.5 Remove the `binaries.framework` block and its `export(...)` lines from `composeApp/build.gradle.kts`, and the `api` declarations that existed only for them. Verify `./gradlew :composeApp:compileKotlinIosSimulatorArm64` and `./gradlew check` pass.

## 3. iOS app on Swift export

- [x] 3.1 In `iosApp/iosApp.xcodeproj/project.pbxproj`: point the build phase at `:iosExport:embedSwiftExportForXcode`, replace the `ComposeApp` framework search path and linker flags with what Swift export needs, and set `IPHONEOS_DEPLOYMENT_TARGET` to 18.0 in both configurations. Verify the build reaches Swift compilation.
- [x] 3.2 Port `iOSApp.swift` and `ContentView.swift` to the exported module's names. Verify they compile.
- [x] 3.3 Port `SwiftFitDecoder.swift`: the exported `FitDecoder` protocol, native optionals in place of `KotlinLong`/`KotlinInt`/`KotlinDouble`/`KotlinBoolean`, the exported byte array type, and raising `ActivityFileException`. If Swift can't raise it through the bridge, change `FitDecoder.decode` to return a sealed result and map a failure to `ActivityFileException` in its Kotlin caller (design decision 4), updating the Android and JVM decoders and their tests. Verify the `xcodebuild` command succeeds and `./gradlew :core-activityfile:jvmTest` passes.
- [x] 3.4 (Done on an iOS 26.0 simulator: the iOS 18.4 one launches the app and starts sign-in, then its HTTP/3 fails against the tunnel. No background task ran, since simulators refuse them. See the exploration note.) Simulator pass on iOS 18 against `scripts/dev.sh --seed alice`, checking each spec scenario: sign in and see the feed; import a sample FIT file and compare the activity with the same import before the change; import a truncated file and see the unreadable-file error; send the app to the background and bring it back. Verify with simulator screenshots, kept out of the repository.
- [x] 3.5 In the same pass, open each native Calf piece: the tab bar, a bottom sheet, an alert and the date wheels (design decision 6). Verify each appears and responds; a failure stops the change and goes in the exploration note.
- [x] 3.6 Update `AGENTS.md`'s iOS paragraph (the build phase task, iOS 18, the two modules) and any rule under `.agents/rules/` that names the framework task. Verify `scripts/check-agent-sync.sh` passes and the documented `xcodebuild` command runs as written.

## 4. Contract probe (scratch commit, not merged)

- [x] 4.1 On a scratch commit, add `:feature-login-api`, `:feature-segment-api` and `:feature-detail-api` to `:iosExport`. Verify by recording whether the export generates and whether the generated Swift compiles, with the first error if it doesn't.
- [x] 4.2 In a throwaway Swift file, touch each construct in design decision 7's table through `LoginState`, `StarredSegmentsState` and the activity detail state: read each field, call `eventSink` with a `data object` and a `data class` event, switch over a sealed sub-state. Verify by one recorded result per table row.
- [x] 4.3 Measure generated Swift lines and clean and incremental iOS build times with the three contracts exported, against the numbers from 2.3 and from the Objective-C framework. Verify by the recorded figures.
- [x] 4.4 Add a "Spike findings" section to `openspec/explorations/ios-native-ui.md`: the filled-in table, what each result forces in the contracts, whether the umbrella and `:iosShell` stay two modules, and go or no-go on Swift export. Verify `scripts/check-public-safety.sh` passes.
- [x] 4.5 Drop the scratch commit. Verify `git status` shows no probe code and the `xcodebuild` command still succeeds.

## 5. Whole-change checks

- [x] 5.1 Run `./gradlew check`, the `xcodebuild` command, and `openspec validate ios-swift-export-spike --strict`. Verify all three pass.
- [x] 5.2 Confirm the Kotlin bump and the export switch are separate commits, so the switch can be reverted alone (design: Migration Plan). Verify with `git log`.

## Workflow follow-up

- Archive the change once the simulator pass and the findings are reviewed.
- Propose the contract change the findings scope (the app-owned `TextFieldState`, and whichever sealed and collection edits the table requires), or the SKIE fallback if the result is no-go.
