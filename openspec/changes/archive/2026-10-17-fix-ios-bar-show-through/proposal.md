# Proposal

## Why

On iOS, after scrolling an activity page, its "Details" section heading (a `titleMedium` row in the list) can be read sharply behind the back button, as if the bar weren't there. The Compose-drawn navigation bar is clear at rest and blurs content under it with Haze once scrolled; on iOS 26 that's a soft scroll-edge effect that fades from full strength to none over the bar's last 30 %, so text passing that band, beside the back button, reads almost untouched. UIKit's own bars keep content under the buttons illegible.

## What Changes

- Content scrolled under the iOS navigation bar is never legible behind the bar's title or buttons, on iOS 26 (soft edge) and earlier (material).
- The bar stays clear at rest, and the soft edge still fades out toward the content, as UIKit's does.
- The cause is confirmed on the simulator before the fix (see design: it may instead be Haze's source missing the list, or the scroll state not reaching the bar on the detail screen).

## Capabilities

### New Capabilities
- `ui/ios-navigation-bar`: how the iOS navigation bar treats content scrolled under it.

### Modified Capabilities
None.

## Impact

- `composeApp/src/iosMain/.../ui/adaptive/AppTopBar.ios.kt` (`barMaterial`, `EDGE_FADE_START`, tint), possibly `AppScaffold`'s Haze source or the detail screen's scroll behavior wiring.
- iOS only; Android, desktop and web are unaffected.
