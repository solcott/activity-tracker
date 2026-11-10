# Proposal

## Why

Phase 4 of the native iOS UI (`openspec/explorations/ios-native-ui.md`) moves Settings' pages one change at a time. Activity defaults, Segments & groups, Gear and the blocked and muted lists are native. Account and Leave are the last settings pages that need no chart or map. They bring four things no native page has needed yet:

- the first page deeper than the pane's first, so the column header needs a back button;
- the first system alerts;
- the first hand-off of a file to another app (the data export's share sheet);
- the first time of day a native page shows.

## What Changes

- **The Account page is native on iOS.** It shows:
  - the handle, the DID with Copy (Copied once tapped), and the server;
  - whether the sign-in is current, its PDS can't host spaces, or it is outdated, with what an outdated sign-in is missing and Sign in again;
  - where the password and deleting the account live;
  - the data export: offered, running with its progress and Cancel, ready with its notes and Save and Delete, paused for space, failed, and a failed save;
  - Sign out;
  - Leave Activity Tracker.

  Ending the session with work pending asks first, in a system alert. Same words as the shared page.
- **Saving an export is native on iOS.** Save writes the zip through Kotlin, without holding it in memory, and offers it in the system's share sheet. Then:
  - if an activity ran, the athlete is asked whether they kept it, as `auth/data-export` says;
  - if the sheet is dismissed, the export stays.
- **Leave Activity Tracker is native on iOS.** Before the athlete confirms, it shows:
  - what is deleted and what stays;
  - the shared places that stay, with their counts;
  - what this device would lose;
  - the export: offered, running or ready to save;
  - the grant;
  - the typed-handle confirmation, with Leave on only once the handle matches.

  Once a leave runs, the page shows only its progress: the step, records deleted, until when it is paused, and a stop with Retry. That holds whether it was opened from Account or shown by the shell while a leave resumes. A running leave has no way back.
- **A deeper native page in the column has a back button.** Leave opened from Account beside Settings' list shows its title with a back button, which returns to Account. The first page beside the list still has none. Alone, the stack's own back button serves.
- **Kotlin chooses the text and conditions, both UIs ask.** These move from `AccountUi` and `LeaveUi` to `AccountTexts` and `LeaveTexts` in `:feature-settings-api`, which the Compose pages resolve:
  - the export's status lines and the outdated grant's missing features (a set of enums Swift can't read);
  - the end-session alert;
  - the shared-places counts, the device lines, the leave's step, and the paused-until line.

  No visible change on Android, desktop or web.
- **Samples, previews and snapshots** of both pages in every shared sample, beside Settings' list and alone; design-system gallery entries for the header's back button. Every other screen keeps the fallback.

Out of scope:

- the "left on another device" screen (`LeftElsewhereScreen`), which isn't a settings page;
- Training zones, threshold history and privacy zones, which need charts and a map;
- moving the other composables that format times.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui/settings-layout`:
  - the Account and Leave pages are drawn natively on iOS, alone and beside the list;
  - a native page deeper than the pane's first shows a back button in its header.
- `ui/ios-design-system`: a column header with a back button. Alerts stay SwiftUI's own, with no wrapper.
- `dev/shared-display-text`: the Account and Leave pages' rule-bound text is chosen in shared code.
- `dev/ios-swift-interop`:
  - a finished export's archive is written for Swift to share;
  - a set of enums reaches Swift as the text it stands for;
  - a moment in time reaches Swift as a value it can format.

## Impact

- **`:feature-settings-api`:** `AccountTexts`, `LeaveTexts`, and getters on `AccountState` and `LeaveState` (what shows, what is on); tests.
- **`:feature-settings-ui`:** `AccountUi` and `LeaveUi` ask them, with no screenshot reference changes.
- **`:app-fixtures`:** samples for states the shared set doesn't cover yet (outdated grant, each export status, a paused or stopped leave), as new samples beside the existing ones.
- **`:iosShell`:** an `IosApp` entry that writes a finished archive to a file and reports when done.
- **`iosApp`:**
  - in `Design`: a back button in `pageTitle` and `PageBar`;
  - `AccountView`, `LeaveView`, the share-sheet presenter, type aliases, samples, previews, snapshot and text tests, registry entries.
- **Docs:**
  - in the roadmap: the phase 4 entry, the probes and findings sections, and its open items;
  - `.agents/rules/compose-screens.md` and `adaptive-ui.md`.
- **Risk carried:**
  - iOS 18 can't be walked signed in;
  - the snapshots compare only on iOS 26.0;
  - a real leave would delete the test account's data, so on a device it is walked only up to the confirmation, and its progress only from samples;
  - the share sheet's save can be walked only to the simulator's Files.
