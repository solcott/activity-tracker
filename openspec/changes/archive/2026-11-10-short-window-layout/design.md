# Design

## Context

- `WindowWidth` (`:ui-designsystem`, `ui/adaptive/WindowWidth.kt`) is `Compact`, `Medium` or `Expanded`, from `WindowWidth.of(width)`. `WithWindowWidth` is a `BoxWithConstraints` at a screen's root that passes `maxWidth`.
- Three screens use it: the activity page (`Expanded` → `PanedPage`, else `ColumnPage`, which has a medium form), the import preview (`Expanded` puts the save panel beside the preview) and segment browse (only `Compact` or not). `SettingsListDetailDecoration` calls `WindowWidth.of(maxWidth)` in its own `BoxWithConstraints`.
- `AppShell` picks the navigation style from its own constants and `platform.hasPointer`; it doesn't use `WindowWidth`.
- On iOS the presenter host's `settingsSplitFits` already asks for 700 points of width and 500 of height.
- Seen on the signed-in iPhone 17 Pro on its side (874 by 402 points): the activity page in `PanedPage`, a 244dp column beside an empty pane.

## Goals / Non-Goals

**Goals:** one rule, in the one place screens already ask, that keeps the side-pane layout off a window too short for it; the same number as the iOS Settings rule.

**Non-Goals:** a landscape-phone layout; changing the navigation style's rule; changing the enum's name or its three values.

## Decisions

### 1. The height is a second argument to the same function

`WindowWidth.of(width, height)` returns `Medium` where it would return `Expanded` when `height < EXPANDED_MIN_HEIGHT` (500dp). `WithWindowWidth` passes `maxHeight`, and `SettingsListDetailDecoration` passes its own. No call site that reads the result changes, so every screen with a wide layout gets the rule, and one added later can't miss it.

The alternative, a height check at each screen, would be three copies of a number and a fourth for the next screen.

### 2. Medium, not compact

A short window 600dp wide or more is laid out as medium. On a phone on its side that is two columns across 850dp, which uses the width; compact would be one stretched column.

### 3. 500dp, the iOS Settings rule's number

Every phone on its side is at most about 440dp tall, and the shortest tablet at full screen is 600dp (744 points on an iPad mini), so any number between separates them. Material's compact height class ends at 480dp; the iOS Settings rule shipped with 500, and one number for "too short for two panes" across the app is worth more than matching Material's by 20dp.

### 4. The height is the one the screen is given

`BoxWithConstraints.maxHeight` at the screen's root. An unbounded height (a preview that wraps its content) is `Dp.Infinity`, which is tall, so previews and screenshot tests that give only a width keep their layout. Nothing outside a screen pads for the on-screen keyboard (Android runs edge to edge, and no `imePadding` wraps a screen), so a keyboard doesn't make a tablet's window "short" and flip its layout while typing.

The alternative, the window's own size from `LocalWindowInfo`, is not what a screen in Settings' detail pane or a test's sized box has.

## Risks / Trade-offs

- [A desktop or web window made very short loses its side pane] → Intended: the pane's map would be a strip. Stated in the spec.
- [A future wrapper that pads a screen for the keyboard would make a tablet's layout flip while typing] → Written in `adaptive-ui.md` beside the rule.
- [Screenshot references] → Expected unchanged; `check` runs them.
