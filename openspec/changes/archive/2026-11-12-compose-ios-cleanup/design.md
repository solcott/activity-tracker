# Design

## Context

`ios-drop-compose` gave the Compose modules (`activitytracker.kmp.compose`) no iOS targets. What only iOS used survived because it
was reachable from `commonMain`: `UiPlatform.Ios` and the branches on it, the iOS colour schemes, shapes and indication in `AppTheme`,
two composition locals for UIKit views under the canvas, Cupertino glyphs on every `AppIcon`, and `AppShell`'s floating UIKit tab
bar. `:ui-designsystem` keeps 13 wrappers as `expect` in `commonMain` with their only `actual` in `nonAppleMain` (`*.nonApple.kt`).

## Goals / Non-Goals

**Goals:** remove what only the iOS Compose look used; make every wrapper a plain `commonMain` function; keep Android, desktop and
web rendering exactly as before (screenshot references unchanged).

**Non-Goals:** changing any wrapper's signature beyond iOS-only parameters; touching the parallel `AppText`
work in feature `-ui` text code.

## Decisions

- **Fold, don't move.** Each `Foo.nonApple.kt` body replaces its `expect` in `Foo.kt`, keeping the `expect`'s defaults and KDoc
  (rewritten without iOS). Default arguments live on the `expect` today, so the folded function takes them from there. With no file
  left, `nonAppleMain` goes; the convention's `nonApple` group stays for `core/textfield`, which still builds for iOS.
- **Drop `calf-ui`.** Its Material source set (0.14.0) shows each call the app makes forwarding to Material 3 with Material's own
  defaults: `AdaptiveBottomSheet` → `ModalBottomSheet` (its sheet state wraps a `SheetState` from the same arguments
  `rememberModalBottomSheetState` uses), `AdaptiveDropDown` → `DropdownMenu`, `AdaptiveSwitch` → `Switch`,
  `AdaptiveBasicAlertDialog` → `BasicAlertDialog`, `AdaptiveNavigationBar` → `NavigationBar`. So the wrappers call Material, and
  `rememberAppSheetState` returns Material's `SheetState`. `DateBounds`, a `SelectableDates` comparing UTC days, becomes ours in
  `ui/adaptive` (a few lines; no library offers it apart from Calf). `checkIosDependencies` keeps naming Calf, as it keeps Haze.
- **`AppIcon` keeps `sfSymbol`/`filledSfSymbol`.** `generateIosResources` builds the Swift `AppIcon` from those string literals; the
  generator reads string literals only, so dropping the `Int` glyph arguments leaves `AppIcon.swift` byte for byte the same
  (`checkIosResources` holds this).
- **`UiPlatform.isMobile` stays** (Android), since the import notes and Settings read it; `hasPointer` stays its negation.
- **Branches on iOS collapse to their other arm**: the feed's Add menu never includes Record in the Compose UI (Android has the
  button; desktop and web can't record), the import form's pinned Save is always the bottom bar, `AppShell` picks the bar by width.

## Risks / Trade-offs

- A folded wrapper picking up a different default than its `actual` used, or a Material call differing from Calf's → defaults were
  only on the `expect`, and Calf passed Material's own; screenshot tests catch a visible change.
- A sibling branch editing a `*.nonApple.kt` file conflicts with its deletion → the merge re-applies the edit to the `commonMain` file.
