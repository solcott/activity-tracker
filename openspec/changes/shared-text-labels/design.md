# Design

## Context

The roadmap's "Feature labels" group lists labels one or both UIs pick. Most are a two-way choice (`if (x) a else b`) over state a
contract already holds, so the move is a function next to the contract's other texts.

## Goals / Non-Goals

**Goals:**
- Every label in the group is an `AppText` a shared function returns, and neither UI chooses it.
- One wording where the UIs differed.

**Non-Goals:**
- `shownName` (the display name else the bare handle), which avatars, rows and texts as written use. It is a different rule (a name to
  show beside a separate `@handle` line) and stays.
- Icons beside the tabs' titles, and the platform's own state descriptions (SwiftUI's disclosure groups).
- What a button does on one platform only: the Account page's Save hands the archive off through each platform's own saver, the web's
  "keep this tab open" line, and Swift disabling Save while its share sheet is up.

## Decisions

- **`LabelText` for what several features share**: `shownName`, `expanded` and `save` sit with `handle`, which `shownName` builds on.
  `ImportActivityTexts.save` now returns `LabelText.save`, so record keeps its function.
- **`TopLevelDestination.title` is a member of the enum** in `:app-host`, which already depends on `:app-common-api`, so the Compose shell
  and Swift read the same property; Swift's own `title` extension goes, and its `icon` stays.
- **A comment's confirmation is one value** (`CommentConfirmation(title, message, confirm)`), returned for the actions that confirm
  (`CommentMenuItem.confirms`) and null for the others, so the Compose menu keeps one `confirming` action rather than two flags.
- **A picker's choices are named one at a time** (`sportChoice(sport: String?)`, `deviceKindChoice(type, kind: String?)`,
  `deviceMountChoice(fields, uri: String?)`, `profileLinkChoice(offer, uri: String?)`, `modelChoice(models, token)`): Swift's pickers name
  an option by its tag, and a URI arrives as its text. The existing `deviceKind(card)`, `deviceMount` and `profileLink` (the chosen
  option) now call them.
- **Export steps as controls**: `ExportControl(action, label, primary, enabled)` with `ExportAction` (`Export`, `Cancel`, `Save`), like
  `RecordControl`. Each UI maps an action to its event (Account's Save to its platform's archive hand-off). Leave's line is
  `LeaveTexts.exportLine`; Account's lines were shared already (`exportLines`).

## Risks / Trade-offs

- [The iOS bar button's width changes while saving ("Saving…")] → It is in the trailing slot of the navigation bar, which fits it; the
  Compose forms already do this.
