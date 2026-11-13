# Tasks

## 1. Remove the fallback and the Compose bridge

- [ ] 1.1 Delete `FallbackScreen.swift` and `BackSwipe.swift`, drop `FallbackControllers` from `ScreenView`, `SingleScreen`, `StackView`
  and `StackDestination`, and delete `FallbackControllersTests.swift`; `ScreenView` always draws the native screen. Verify: the Debug
  app builds.
- [ ] 1.2 Delete `IosApp.screenViewController`, and `ScreenViewController` with the theme and Circuit locals behind it from `IosHost`.
  Verify: `:iosShell:compileKotlinIosSimulatorArm64`.
- [ ] 1.3 Make the iOS `TextFieldState` plain snapshot state (no Compose Foundation, no `composeState`), saved by
  `rememberTextFieldState`'s saver; update `TextFieldStateIosTest` (set, clear, values, restore). Verify:
  `:core-textfield:iosSimulatorArm64Test`.

## 2. Split the graph

- [ ] 2.1 Add `:app-graph` (`app/graph`) with `AppGraph`, `SessionGraph` (no Circuit members), `sessionHosts()`, `NetworkConfigs`
  and its generated config, the background passes and the `SubPresenterFactory` multibinds; `:composeApp` depends on it. Verify:
  `:composeApp:jvmTest`.
- [ ] 2.2 Contribute `AppCircuits` and `SessionCircuits` from `:composeApp` with the Circuit bindings and the `SubUiFactory`
  multibinds; `App` reads them through `circuits`. Verify: `:composeApp:jvmTest`, `:desktopApp:compileKotlin`,
  `:webApp:compileKotlinWasmJs`, `:androidApp:assembleDebug`.
- [ ] 2.3 Move `IosAppGraph`, `IosAuthBrowser`, `IosHost` and the background tasks into `:app-graph`'s `iosMain`, with every feature
  presenter on its iOS classpath. Verify: `:app-graph:compileKotlinIosSimulatorArm64`.
- [ ] 2.4 `:iosShell` depends on `:app-graph` instead of `:composeApp` and `:app-common-ui`; `IosApp.compressAvatar` calls FileKit's
  `compressImage` with `EditProfileLimits`. Verify: `:iosShell:compileKotlinIosSimulatorArm64` and the avatar Swift tests.

## 3. Drop the UI modules' iOS targets

- [ ] 3.1 Split the library convention's setup so `activitytracker.kmp.compose` declares no iOS targets; TestKit: a Compose module has no
  iOS targets, a library module has both. Verify: `-p build-logic :convention:test`.
- [ ] 3.2 Delete the UI modules' `iosMain` sources (`:ui-designsystem`, `:app-common-ui`, `LocationPermission.ios.kt` among them) and
  `:composeApp`'s `iosMain`; `:ui-designsystem`'s `nonAppleMain` actuals stay, now covering every target. Verify:
  `:ui-designsystem:jvmTest`, `:app-common-ui:jvmTest`.
- [ ] 3.3 Remove Haze (`LocalBarBackdrop`, `rememberBarBackdrop`, the catalog entries). Verify: `:ui-designsystem:compileKotlinJvm`,
  screenshot tests in 5.1.
- [ ] 3.4 Drop the Compose plugins from `:iosExport`. Verify: the Debug app builds and shows its strings and images.

## 4. Keep Compose UI out of iOS

- [ ] 4.1 Exclude Compose UI and Foundation from iOS resolution in the library, swift.contract and swift.export conventions; TestKit
  covers the exclusion. Verify: `:iosExport:dependencies` for `iosSimulatorArm64` lists no Compose UI, Foundation or Material.
- [ ] 4.2 Add `checkIosDependencies` to `:iosExport` (in `check`), failing on the forbidden groups with the path; TestKit for a
  forbidden and an allowed graph. Verify: `-p build-logic :convention:test`, `:iosExport:checkIosDependencies`.

## 5. Verify and document

- [ ] 5.1 `./gradlew check -Pkotlin.incremental=false` passes (Android, desktop and web UI and screenshot tests included).
- [ ] 5.2 Ad-hoc-signed Release simulator build passes; the binary has no Skia or Compose UI symbols and the bundle no
  `compose-resources`; record the `.app` size before and after.
- [ ] 5.3 `xcodebuild test` passes on an iOS 26.0 and an iOS 18.4 clone (the 18.4 run on a build made for 18.4); the Release build
  launches to sign-in on a clone with no crash.
- [ ] 5.4 Update `.agents/rules/build-logic.md`, `adaptive-ui.md`, `compose-screens.md`, `CLAUDE.md` and the roadmap (phase 5 done, with
  findings). Verify: `scripts/check-agent-sync.sh`.

## Not verified

## Workflow follow-up

- Archive the change: sync the deltas, retire `ui/ios-navigation-bar`, and update `ui/ios-native-shell`'s purpose.
