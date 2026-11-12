# Design

## Context

See proposal.md for why. `ModerationPresenter` already runs in the presenter host and publishes `ModerationState`: the URI's
`TextFieldState`, `loading` while Open runs, the last subject (`NotUnderReview`, `Activity` with its visibility, `Comment` with its
visibility) and an error (`InvalidSubjectUri`, typed but not an AT-URI; `OpenSubject`, the request failed). Its events are `Back` and
`Open`. The Settings list offers the page only off mobile (`ui/settings-layout`), so on iOS nothing opens it today; it is drawn natively
so that every screen the host can show has a view and `FallbackScreen` can go.

## Goals / Non-Goals

- Goals: a native page registered in `ScreenRegistry`; every rule asked of Kotlin; the Compose page unchanged to the eye; a check that
  no screen is left without an entry.
- Non-goals: offering Moderation on iOS; changing the presenter or `getSubject`.

## Decisions

- **One grouped list.** The URI field leads (stacked, its label over a field that doesn't capitalize or correct, Go on the keyboard
  sending `Open`), the help as its section's footer; Open in its own section, disabled while `canOpen` is false and with a spinner
  beside it while opening; the error as the shared error row; then the subject in a section headed "Visible to: Followers". Alternative:
  a plain scroll column as the Compose page has; rejected, since the other native forms are grouped lists.
- **The activity as the native card, inert.** `ActivityCardView` with `isOwn` false, no author link, no Like, and taps and photo taps
  sending nothing, which is what the shared page passes (`onClick = {}`, `onOpenMedia = {}`, no reveal). A moderator opening photos
  isn't offered on either UI; adding it would be its own change.
- **Text rules to Kotlin:** `ModerationSubjectTexts.visibility(visibility)` (`moderation_visibility` over `LabelText.visibility`),
  `commentAuthor(comment)` (display name, else `LabelText.handle`, which is what `shownNameLabel` shows), and `canOpen(state)` (not
  while loading). The plain strings stay the generated catalog's.
- **The coverage check reads sources.** `checkIosScreenRegistry` scans the contract modules' main sources for `@CircuitSerializable`
  `data object`/`data class` declarations (every screen a navigator can push; sub-screens aren't serializable and are drawn by their
  parent's native view) and `ScreenRegistry.swift` for `register(Name.self`. A text scan, like `checkSwiftExportSources`, so it runs in
  `check` on any machine, Linux included. Alternative: a Swift test reading the screens from the Kotlin graph; rejected, since the
  session graph's serializer set needs a signed-in session.

## Risks / Trade-offs

- [The parent session merges other branches touching the same Swift lists] → each addition is a separate block at the end.
- [A screen declared outside a contract module] → none is today; the check names the modules it reads in its description.
