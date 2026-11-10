# Proposal

## Why

A screen picks its layout by its window's width alone (`WindowWidth`), and from 840dp it gets the wide layout: a side pane beside the content. A phone on its side is wider than that (852 to 956 points on an iPhone, much the same on Android) and about 400 tall, so the activity page there is a 244dp column beside a pane, in a strip of screen. The iOS app has turned with the device since `ios-native-settings`, which is where it was seen, and an Android phone on its side has always done the same. The iOS rule for Settings beside its page already asks for 500 points of height for this reason; the shared UI's rule doesn't ask at all.

## What Changes

- **A short window never gets the wide layout.** A screen that lays out by its window's width takes the side-pane layout only when it also has at least 500dp of height. Shorter, a window 600dp wide or more is laid out as a medium one: two columns, no side pane. This holds on every platform alike, so a desktop or web window resized that short loses its side pane too.
- **It covers each screen that has a wide layout:** the activity page, the import preview, and Settings beside its page on Android, desktop and web. Segment browse has no wide layout of its own and is unchanged.
- **Width still decides everything else.** Compact and medium windows, and every window at least 500dp tall, lay out exactly as before.

Out of scope: the app's navigation (bottom bar, rail or sidebar), which has its own rule and uses a sidebar only where there is a pointer; a layout made for a phone on its side; the native iOS views, which take the same rule from Kotlin when they get a wide layout of their own.

## Capabilities

### New Capabilities

- `ui/window-layout`: how a window's size picks a screen's layout: the three widths, and that a short window is never laid out as a wide one.

### Modified Capabilities

None. `feed/activity-page-layout` and `ui/settings-layout` state their wide layouts "from 840dp"; the new capability says when a window that wide still isn't laid out as wide, and they are left as written.

## Impact

- **`:ui-designsystem`:** `WindowWidth.of` takes the height, `WithWindowWidth` passes it. Tests.
- **`:composeApp`:** `SettingsListDetailDecoration` passes its height.
- **`:feature-detail-ui`, `:feature-record-ui`:** no code change; a UI test each for a short wide window.
- **Docs:** `.agents/rules/adaptive-ui.md`, the roadmap's open item.
- **Screenshots:** the activity page's reference at 900 by 400dp, the one preview of a phone on its side, changes from the side pane to the medium layout.
