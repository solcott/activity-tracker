---
paths:
  - "composeApp/**"
  - "webApp/src/**"
  - "desktopApp/**"
---

# Theme, adaptive components and navigation shell

Each platform follows its own conventions in one shared UI. `ui/theme` has the brand (teal, amber `tertiary` for kudos) as full Material 3 schemes. `AppTheme` adapts it by `LocalUiPlatform`: Material 3 on Android; iOS grouped surfaces, Apple text styles and dimming instead of ripples on iOS; Inter (bundled in `nonMobileMain/composeResources`) and a denser scale on desktop and web.

Screens build from `ui/adaptive` (`AppScaffold`, `AppTopBar`, `AppButton`/`AppSecondaryButton`/`AppTextButton`/`AppIconButton`, `AppTextField`, `AppSegmentedControl`, `AppSwitch`, `AppMenu`, `AppBottomSheet`, `AppDatePickerDialog`, `PlatformIcon`, `ReadableList`), never the Material widgets those wrap; detekt's `UseAdaptiveComponents` enforces this. The wrappers use Calf (`calf-ui`) where UIKit has a native piece: menus, sheets, switches, the date picker and the tab bar.

The iOS navigation bar is drawn in Compose, because Calf's native one is pinned to the window and can't follow screen transitions or swipe-back. It is clear at rest and blurs the content scrolled under it with Haze (`AppScaffold` makes the content its source): iOS 26's soft scroll edge effect, or a material and hairline before iOS 26 (`usesLiquidGlass`).

Signed in, `nav/AppShell` wraps the navigable screens. It shows a native tab bar on iOS, floating over the screens so they scroll under its glass (`AppScaffold` pads content clear of it), a bottom bar or rail on Android, and a sidebar on wide desktop and web windows. Each `TopLevelDestination` keeps its own back stack (`resetRoot` with save/restore). `App` tells each screen through `LocalIsNavRoot` whether it is a tab root, which drops its back button and gives it a large title on iOS.
