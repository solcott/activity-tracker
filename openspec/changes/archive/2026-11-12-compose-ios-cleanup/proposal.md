# Proposal

## Why

Phase 5 of the native iOS UI roadmap (`ios-drop-compose`) took Compose UI out of the iOS app: the Compose modules have no iOS
targets, so the shared UI builds for Android, desktop and web only. The iOS look it used to draw is still in the code, compiling into
no build but selectable by a preview (`AppPreview(platform = UiPlatform.Ios)`), and the design system keeps its Material wrappers as
`expect`/`actual` pairs whose only `actual` is `nonAppleMain`'s, which now covers every target. This change removes those leftovers,
the "Left for a cleanup" note in `openspec/explorations/ios-native-ui.md`.

## What Changes

- **`UiPlatform.Ios` goes**, with every branch that tested for it: the theme's iOS colour schemes, `IosColors`/`LocalIosColors`, its
  shapes, the dimming `OpacityIndication`, `AppShell`'s floating UIKit tab bar, the feed's Record-in-menu and the import form's
  Save-in-the-bar on iOS. `isMobile` means Android.
- **iOS-only composition locals go**: `LocalFloatingBottomBarHeight` (the floating tab bar's height `AppScaffold` padded for) and
  `LocalPinnedOverContent` (sticky checkbox headers over UIKit views), and `usesLiquidGlass`, which nothing reads.
- **`AppIcon` loses its Cupertino glyphs**, and `calf-cupertino-icons` leaves the build. Each icon keeps its Material icon and its SF
  Symbol name, which the generated Swift `AppIcon` reads.
- **The design system's `nonAppleMain` folds into `commonMain`**: each wrapper whose only `actual` was there becomes a plain
  `commonMain` function, and the source set goes. `core/textfield`'s `nonAppleMain` stays (that module still builds for iOS).
- **Calf leaves the build.** Off iOS every Calf call the app made (`AdaptiveBottomSheet`, `AdaptiveDropDown`, `AdaptiveSwitch`,
  `AdaptiveBasicAlertDialog`, `AdaptiveNavigationBar`) only forwarded to the Material 3 widget with the same defaults, so the wrappers
  call Material directly, and `DateBounds` (Calf's `SelectableDates` for a date range) becomes the design system's own.
- Docs that describe the iOS Compose look (KDoc of the wrappers, `adaptive-ui.md`, `compose-screens.md`, `build-logic.md`, the
  `reuse-audit` skill, detekt's allowed composition locals, `CLAUDE.md`) stop describing it, and the roadmap's cleanup note is marked
  done.

Out of scope: what Android, desktop and web show (their screenshot references stay as they are), and the shared Account page's
"did you keep it?" dialog after a share (`ArchiveHandOffResult.Shared`), which only iOS's share sheet produced but whose screen code
another change is editing.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `dev/ui-test-harness`: the platform looks a test or preview can choose are Android, desktop and web, and no other.

## Impact

`:ui-designsystem` (theme, `ui/adaptive`, the `nonAppleMain` source set), `:app-common-ui` (`AppPreview`, `SportTypeList`),
`:composeApp` (`AppShell`), `:feature-feed-ui`, `:feature-record-ui`, the `DateBounds` imports in `:feature-feedfilter-ui` and `:feature-gear-ui`,
`gradle/libs.versions.toml`, `config/detekt/detekt.yml`, the
`IosResourcesTest` fixture, rules and docs. No lexicon, AppView or iOS app change; the generated `AppIcon.swift` is unchanged.
