# Proposal

## Why

Settings has grown into one long scroll. It puts inline controls (units, every default-visibility picker, three segment switches, the grouping choice), navigation drawn as secondary buttons (zones, privacy zones, gear, blocked, muted, moderation) and sign-out side by side, with no grouping and one shared error line. It is hard to scan on a phone and wastes the width of desktop, web and tablet windows. Its state and presenter test have grown to match: about 20 fields, and 642 lines of test.

## What Changes

- Settings becomes a grouped list of rows, each section titled:
  - **General:** units, chosen right on the list.
  - **Privacy & sharing:** Activity defaults, Privacy zones, Segments & groups.
  - **Training:** Training zones, Gear.
  - **Safety:** Blocked accounts, Muted accounts.
  - **Moderation**, for moderators on desktop and web.
  - **Sign out.**

  Rows that open a page show a short summary of the current choice, such as the default activity visibility, the privacy-zone count, or whether matching is on.
- New **Activity defaults** page: the default activity visibility and the per-category defaults that today sit inline in Settings.
- New **Segments & groups** page: segment matching, the leaderboard and bracket-board choices, and the activity grouping choice. These all share a privacy-zone-trimmed route copy with the AppView, so one page explains that once.
- On a wide window (at least 840dp of content), Settings shows as a list and a detail pane side by side:
  - Choosing a row replaces the detail pane.
  - Until a row is chosen, the detail pane shows Activity defaults.
  - Settings pages and their sub-pages open in the detail pane. Anything else they open (a profile from the blocked list, a gear item's activities, Moderation) opens full width as usual.
  - Narrow windows keep today's one-page-at-a-time push and pop, including edge-swipe back.
- New adaptive row and section components in `ui/adaptive` (Material list items, with the inset-grouped look on iOS), so navigation rows are no longer drawn as buttons.
- New dependency: JetBrains' Compose Multiplatform repackage of Material 3 adaptive (`adaptive`, `adaptive-layout`), for `ListDetailPaneScaffold`.

Nothing a setting does changes: each choice keeps its storage, defaults, consent text, save and failure behaviour and the conditions under which it is offered.

## Capabilities

### New Capabilities
- `ui/settings-layout`: how Settings is organized and navigated. Covers the grouped root list and its summaries, which pages it opens, and the list-detail layout on wide windows: what shows in the detail pane, auto-showing Activity defaults, full-width pushes, back behaviour and window resizing.

### Modified Capabilities
None. The existing requirements (`feed/activity-visibility`, `feed/detail-visibility`, `segments/segment-track`, `segments/segment-leaderboards`, `segments/leaderboard-brackets`, `feed/group-activities`, `graph/blocks`, `graph/mutes`, `moderation/private-subjects`, `auth/oauth-session`) say only that Settings offers each choice or screen, and that stays true when a choice moves to a Settings page.

## Impact

- `feature/settings/{api,presenter,ui}`:
  - `SettingsScreen`, `SettingsState` and `SettingsEvent` shrink.
  - New `ActivityDefaultsScreen` and `SegmentsAndGroupsScreen`, with their presenters and UIs.
  - `SettingsPresenterTest` is split.
- `:app-common-api`: a `SettingsPage` marker on the screens that open in the detail pane (settings' own pages, plus `GearListScreen` in `feature/gear` and `BlockListScreen` in `feature/profile`).
- `:ui-designsystem` `ui/adaptive`: list row and section wrappers (expect/actual for iOS).
- `composeApp` `nav/`: a list-detail `NavDecoration` around the existing gesture-navigation decoration in `App.kt`.
- `gradle/libs.versions.toml`: `org.jetbrains.compose.material3.adaptive` 1.3.0 (not `adaptive-navigation`).
- `:app-fixtures`, `:ui-resources` strings, `androidApp` screenshot references, and `.agents/rules/adaptive-ui.md`.
