# Proposal

## Why

Every screen the iOS app can show now has a native view (`checkIosScreenRegistry`), and photos compress through ImageIO and libwebp,
so nothing on iOS draws with Compose any more. The app still links Compose UI, Foundation, Material, Skia and the Compose-only
libraries (Calf, Haze, Vico, Coil, MapLibre Compose) for a fallback that has nothing left to draw. This is phase 5 of the native iOS UI
roadmap (`openspec/explorations/ios-native-ui.md`): take them out of the iOS app, keeping only the Compose runtime the presenters run on.

## What Changes

- **The fallback goes**: `FallbackScreen`, its controller cache and container, `BackSwipe` (it only served the fallback's hidden bar),
  `IosApp.screenViewController` and the Compose host behind it (`ScreenViewController`, the theme and Circuit locals it set up). A
  screen with no native view can no longer be drawn; `check` already refuses one.
- **The text field state is plain snapshot state on iOS**: `TextFieldState`'s iOS class holds its text itself instead of wrapping
  Compose's, and `composeState` goes. Swift reads and sets it as before; the presenters and contracts are unchanged.
- **The UI modules have no iOS targets**: every feature's `-ui`, `:app-common-ui`, `:ui-designsystem`, `:ui-resources` and
  `:composeApp` build for Android, desktop and web only, and their `*.ios.kt` actuals (the iOS design-system look,
  `LocationPermission.ios.kt`, the iOS file hand-offs, Skia's iOS photo encoder) are deleted.
- **The iOS graph moves to a module of its own**: a new `:app-graph` holds what every app's graph shares (`AppGraph`, `SessionGraph`,
  the network config, the background passes) and, in iOS code, the iOS graph and host. The Compose apps' Circuit (`AppCircuit`,
  `SessionCircuit`, `SubCircuit`) and their `Ui.Factory` and `SubUiFactory` sets stay in `:composeApp`, so the iOS graph binds no UI
  factory. Android, desktop and web graphs bind what they bound before.
- **The avatar's JPEG is made in `:iosShell`** with the same FileKit call, size and quality the shared pick uses, so `:iosShell` no longer
  depends on `:app-common-ui` or `:composeApp`.
- **Compose UI can't come back to iOS**: SubCircuit's published dependencies on Compose UI and Foundation are excluded from iOS
  resolution (only its runtime classes are used there), and a check in `check` fails when the iOS export resolves Compose UI, Foundation,
  Material, Skia or a Compose-only UI library.
- **Haze leaves the build** (only the iOS bar used it); Calf, Vico, Coil and MapLibre Compose stay for Android, desktop and web only.
- **`ui/ios-navigation-bar` is retired**: the Compose-drawn iOS bar and its spec go.
- Build conventions, their TestKit tests, the rules (`build-logic.md`, `adaptive-ui.md`, `compose-screens.md`) and `CLAUDE.md` stop
  describing iOS as using the shared Compose UI.

Out of scope: removing `UiPlatform.Ios` and the iOS branches of the shared theme and shell that previews could still select (they no
longer compile into any iOS build); any change to what Android, desktop and web show.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui/ios-native-shell`: no screen is drawn by the shared UI any more; the iOS app ships no Compose UI, which `check` holds.
- `ui/settings-layout`: a page beside the iOS list is always a native one.
- `dev/ios-swift-interop`: the text field state on iOS is no longer edited by a Compose field.
- `ui/ios-navigation-bar`: retired (every requirement removed).

## Impact

- **New module** `:app-graph` (`app/graph`); `:composeApp`, `:desktopApp`, `:webApp`, `:androidApp` depend on it.
- **iOS**: `:iosShell`, `:iosExport`, `:core-textfield`, `iosApp/iosApp/Shell`, `iosAppTests`. Smaller Release app (measured in
  tasks.md).
- **Build logic**: a convention option for modules without iOS (the Compose conventions), the iOS exclusion of SubCircuit's UI
  dependencies, and the iOS dependency check, each with TestKit tests.
- **Dependencies**: Haze removed from `libs.versions.toml`.
