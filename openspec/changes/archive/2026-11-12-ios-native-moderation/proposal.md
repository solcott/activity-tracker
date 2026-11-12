# Proposal

## Why

Moderation is the last `Screen` the iOS app can show that has no `ScreenRegistry` entry, so it is the last one `FallbackScreen` would
draw with the shared Compose UI. Phase 5 removes Compose from the iOS app and needs `FallbackScreen` to have nothing left to show. Its
pieces are native already: the text field, the error row and the native activity card.

## What Changes

- **Moderation is native on iOS** (`ModerationView`), a grouped list: the subject's URI as a field with the help under it, Open (held
  while opening, with progress beside it), a failed or refused open, and the subject: "Visible to" with its tier over the activity as
  the native card (no author link, Like or photo opening, as the shared page has none), or the comment's author and text, or "Not under
  review".
- **Kotlin chooses what the page says that follows a rule** (dev/shared-display-text): `ModerationSubjectTexts`
  (`:feature-moderation-api`) gives the "Visible to" line and the comment's author (display name, else `@handle`), and `canOpen`. The
  Compose page asks the same.
- **The samples cover every state**: `ModerationSamples.variants` adds a comment, a comment by an athlete without a display name, not
  under review, opening, an invalid URI and a failed open.
- **A check that every screen has a native view** (`checkIosScreenRegistry`, in `check`): it reads every `@CircuitSerializable` screen
  in the contract modules and fails naming any that `ScreenRegistry.app` doesn't register.

Out of scope: offering Moderation in the iOS Settings list (moderators still review on desktop and web, `ui/settings-layout`); Android,
desktop and web beyond the shared texts; removing `FallbackScreen` (phase 5).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `moderation/private-subjects`: the Moderation page is native on iOS.
- `dev/shared-display-text`: the Moderation page's texts are chosen in shared code.
- `ui/ios-native-shell`: every screen the iOS app can show has a native view, which `check` holds.

## Impact

- **`:feature-moderation-api`, `-ui`:** `ModerationSubjectTexts`; `ModerationUi` asks it.
- **`:app-fixtures`:** `ModerationSamples.variants`.
- **`build-logic`:** `checkIosScreenRegistry` in `activitytracker.swift.export`, with its unit test.
- **`iosApp`:** `ModerationView`, aliases, samples, previews, snapshots, text tests, the registry entry and its tests.
- **Docs:** the roadmap's phases, findings and open items (nothing goes through Compose any more).
