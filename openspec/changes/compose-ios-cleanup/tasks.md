# Tasks

## 1. Remove the iOS look

- [ ] 1.1 Drop `UiPlatform.Ios`; collapse its branches in `AppTheme` (colour schemes, shapes, indication, tonal elevation), `AppShell`,
  `FeedUi` and `ImportActivityUi`; delete `IosLight`/`IosDark`, `IosColors`, `LocalIosColors`, `IosShapes` and `OpacityIndication`.
- [ ] 1.2 Delete `LocalFloatingBottomBarHeight` (and `AppScaffold`'s padding for it), `LocalPinnedOverContent` (and `SportTypeList`'s
  provider) and `usesLiquidGlass` with its actuals.
- [ ] 1.3 Drop `AppIcon`'s Cupertino glyphs, `PlatformIcon`'s use of them and `calf-cupertino-icons` from `:ui-designsystem` and the
  version catalog; update `IosResourcesTest`'s fixture. Verify: `generateIosResources` leaves `AppIcon.swift` unchanged.
- [ ] 1.4 Drop the iOS-only arguments of the Calf calls (`AppAlertDialog`, `AppMenu`, `AppShell`'s bar).

## 2. Fold `nonAppleMain`

- [ ] 2.1 Replace each `expect` in `:ui-designsystem` whose only `actual` is in `nonAppleMain` with that body in `commonMain`, KDoc
  without iOS; delete `src/nonAppleMain`. Verify: `:ui-designsystem:jvmTest`, `:composeApp:jvmTest`.

## 3. Docs and guards

- [ ] 3.1 Update `adaptive-ui.md`, `compose-screens.md`, `build-logic.md`, the `reuse-audit` skill, detekt's
  `allowedCompositionLocals` (and any baseline entries naming removed code) and `CLAUDE.md`.
- [ ] 3.2 Mark the roadmap's "Left for a cleanup" note done.

## 4. Verify

- [ ] 4.1 `./gradlew check -Pkotlin.incremental=false` (screenshot references unchanged).
- [ ] 4.2 Ad-hoc-signed iOS Release simulator build.
- [ ] 4.3 `xcodebuild test` on an iOS 26.0 simulator clone.
