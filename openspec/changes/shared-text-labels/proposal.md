# Proposal

## Why

Round 3 of moving rule-chosen text into shared code (the roadmap's "Feature labels" item). A dozen labels are still picked by a UI: a
`when` or an `if` in a composable, a `switch` or a ternary in a Swift view, sometimes the same choice written in both. Each is small, but
each is a rule a UI rewrites, and a few already disagree: the iOS forms' Save never reads "Saving…", which the Compose forms do.

## What Changes

Each label becomes a shared function that both UIs ask, in the contract module its state lives in, or `:app-common-api` when several
features share it:

- **A metric card's title**: the Compose card asks `FigureTexts.cardTitle`, as the native card already does.
- **A name else `@handle`**: `LabelText.shownName(account)` (a `ProfileViewBasic` or a `ProfileView`), which `shownNameLabel`, the
  activity page's and the profile's report and block names, the moderation subject and Swift's comment report name ask. `shownName`, the
  bare-handle fallback avatars and rows use, is unchanged.
- **Expanded or collapsed**: `LabelText.expanded(expanded)` for the type list's sub-sport toggle (Compose only; SwiftUI's disclosure group
  speaks its own state).
- **A gear section's header**: `GearListTexts.section(type)`, the type's name or Retired.
- **Show or Hide likes and reposts** on the post page: the Compose page asks `PostPageTexts.likesToggle`/`repostsToggle`, as iOS does.
- **Save or Saving…**: `LabelText.save(saving)`, asked by Edit activity, Edit profile, Default zones, Sport zones and Manual entry in both
  UIs, and by `ImportActivityTexts.save`.
- **The tabs' titles**: `TopLevelDestination.title` in `:app-host`, which the Compose shell and the SwiftUI tab bar read.
- **The visibility note**: `VisibilityTexts.activityNote(fields)`, the help while the tier can be chosen, else why it's locked; the
  Compose form and the five Swift forms ask it.
- **A comment's confirmation**: `CommentsTexts.confirmation(action, author)`, a `CommentConfirmation` (title, message, confirm button)
  for Delete and Block author, null otherwise; the Compose menu and the Swift alert ask it.
- **None and All choices**: `SegmentBrowseTexts.sportChoice(sport)`, `ImportActivityTexts.deviceKindChoice`, `deviceMountChoice`,
  `profileLinkChoice` and `ZoneTexts.modelChoice`, each naming one option of a picker, its null option included.
- **The export states**: `AccountTexts.exportControls(export)` and `LeaveTexts.exportLine(export)`/`exportControls(state)`, as
  `ExportControl`s (an `ExportAction`, its label, whether it is the primary button and whether it is enabled); and
  `SettingsTexts.accountSummary(state)`, the account row's sign-in-again summary.

Where the two UIs said different things, the Compose wording wins: it is the clearer one in the one case (Save reads "Saving…" while a
save runs).

Visible text changes:
- iOS: the Save button in the bar of Edit activity, Edit profile, Default zones, Sport zones and Manual entry reads "Saving…" while a save
  runs (it read "Save", greyed).
- Android, desktop and web: none. Every Compose label reads as it did.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `dev/shared-display-text`: feature labels (a card's title, a name with its handle fallback, a toggle, a section header, Save, the tabs,
  the visibility note, a confirmation, a picker's null choice, the export steps) are chosen once.

## Impact

- `:app-common-api` (`LabelText`, `VisibilityTexts`, `CommentsTexts`, `FigureTexts` unchanged), `:app-host` (`TopLevelDestination`),
  `:feature-gear-api`, `:feature-post-api` (unchanged), `:feature-record-api`, `:feature-segment-api`, `:feature-settings-api`,
  `:feature-profile-api`, `:feature-detail-api`, `:feature-moderation-api`, with tests.
- Compose: `PageCardsUi`, `ShownNameLabel`, `SportTypeList`, `GearListUi`, `PostPageUi`, the five forms, `TopLevelDestinationLabels`
  and `AppShell`, `ActivityVisibilityForm`, `CommentMenuUi`, `NewDevicesForm`, `SegmentBrowseUi`, `LeaveUi`, `AccountUi`, `SettingsUi`.
- Swift: `CommentsSection`, `GearListView`, the five forms' views, `SessionView`, the five visibility sections (`RecordView`,
  `ManualActivityView`, `EditActivityView`, `AcceptInviteView`, `ImportActivityView`), `SegmentBrowseView`, `ImportBatchSections`,
  `ZoneRows`, `LeaveView`, `AccountView`, `SettingsView`, with text tests.
- iOS 26.0 snapshots of a form saving, where one shows the bar. No Android reference changes.
- Roadmap `openspec/explorations/ios-native-ui.md`: the item ticked and a findings section.
