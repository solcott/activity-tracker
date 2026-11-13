# Tasks

## 1. Remove the iOS look

- [x] 1.1 Drop `UiPlatform.Ios`; collapse its branches in `AppTheme` (colour schemes, shapes, indication, tonal elevation), `AppShell`,
  `FeedUi` and `ImportActivityUi`; delete `IosLight`/`IosDark`, `IosColors`, `LocalIosColors`, `IosShapes` and `OpacityIndication`.
- [x] 1.2 Delete `LocalFloatingBottomBarHeight` (and `AppScaffold`'s padding for it), `LocalPinnedOverContent` (and `SportTypeList`'s
  provider) and `usesLiquidGlass` with its actuals.
- [x] 1.3 Drop `AppIcon`'s Cupertino glyphs, `PlatformIcon`'s use of them and `calf-cupertino-icons` from `:ui-designsystem` and the
  version catalog; update `IosResourcesTest`'s fixture. Verify: `generateIosResources` leaves `AppIcon.swift` unchanged.
- [x] 1.4 Call Material directly where the wrappers and `AppShell` called Calf, add `DateBounds` to `ui/adaptive`, and drop
  `calf-ui` from the build.

## 2. Fold `nonAppleMain`

- [x] 2.1 Replace each `expect` in `:ui-designsystem` whose only `actual` is in `nonAppleMain` with that body in `commonMain`, KDoc
  without iOS; delete `src/nonAppleMain`. Verify: `:ui-designsystem:jvmTest`, `:composeApp:jvmTest`.

## 3. Docs and guards

- [x] 3.1 Update `adaptive-ui.md`, `compose-screens.md`, `build-logic.md`, the `reuse-audit` skill, detekt's
  `allowedCompositionLocals` (and any baseline entries naming removed code) and `CLAUDE.md`.
- [x] 3.2 Mark the roadmap's "Left for a cleanup" note done.

## 4. Verify

- [x] 4.1 `./gradlew check -Pkotlin.incremental=false` (screenshot references unchanged).
- [x] 4.2 Ad-hoc-signed iOS Release simulator build.
- [x] 4.3 `xcodebuild test` on an iOS 26.0 simulator clone.
