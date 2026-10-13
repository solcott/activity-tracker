# Design

## Context

The SubCircuit rules in `compose-screens.md`: the screen carries only initial config, because a changed screen remounts the child and drops its state. The child seeds itself from the screen and reports through a keyed `SideEffect`. The host replaces the screen only to reset the child. `FeedFilterScreen` is the existing example.

The record picker today is driven by live host state that doesn't fit that model cleanly:
- `enabled`: `RecordUi` passes `status == Idle`, and `ImportActivityUi` and the form pass `!saving`.
- Host-driven type: `ImportActivityPresenter` sets `type` from the parsed file after load (`type = first.type`), not only from the picker.

## Goals / Non-Goals

**Goals:**
- Quick types are loaded once, in the picker's presenter, instead of in three hosts.

**Non-Goals:**
- Moving search into the presenter. Labels are resources, so filtering stays in the UI.
- Any visual change. The UI keeps using `ui/SportTypeList` from `collapsible-sport-list`.

## Decisions

**Screen = `ActivityTypePickerScreen(initial: ActivityType, outdoorOnly: Boolean)`.** Both are config fixed for the host's lifetime, except as noted below.

**`enabled` stays outside the screen.** Options:
- (a) The host wraps `SubCircuitContent` in a disabled container. Compose has no built-in disabled container, so this would need a `CompositionLocal` that the picker UI reads.
- (b) An `enabled` field in the screen. Rejected: changing it would remount the picker and lose its open sheet.
- (c) Keep `ActivityTypePicker` as a plain composable and extract only the quick-types loading into a shared presenter helper. That helper already exists as `quickActivityTypes`.

Lean (a) with a small `LocalPickerEnabled`, but decide in review.

**Import's type from the file:** the host replaces the screen with `ActivityTypePickerScreen(fileType, ...)` once the file is parsed. This is a legitimate "reset the child", since the athlete hasn't touched the picker yet. The child then reports the type back through its first `SideEffect`, which is harmless.

## Risks / Trade-offs

- [The refactor adds a module-spanning contract for modest savings: one `quickTypes` field in three states] → Revisit after `collapsible-sport-list` lands. If (c) looks enough, close this change without implementing it.
- [Replacing the screen while the sheet is open on Import] → The file is parsed before the form shows, so the picker can't be open yet.

## Open Questions

- Choose between (a) and (c) for `enabled` in review. Either way, the picker UI stays the same.
