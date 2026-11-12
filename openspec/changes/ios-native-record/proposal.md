# Proposal

## Why

Record is the last full screen of the athlete's own that iOS still draws with the shared Compose UI inside the native shell; only the
moderator's Moderation page is left beside it. Once it is native, phase 5 can start taking Compose and Skia out of the iOS app. Its
pieces are native already: the type row and sheet, the title, gear and visibility sections from the native forms, and the figures of
the native activity card. Recording itself stays Kotlin's (`ActivityRecorder`, the iOS `LocationTracker`): the view draws the state and
sends events.

## What Changes

- **Record is native on iOS** (`RecordView`), a grouped `Form`: a failed finish and the recording's notices (offered back after the app
  stopped, not saved on the device, location off with Open Settings), the elapsed time large with the distance, the sport's rate and the
  climb in the athlete's units under it, then the type (outdoor types only, fixed once recording starts), the title, the gear and who
  sees it, as the native forms draw them. The controls sit in a bar at the bottom: Start; Pause; Resume, Finish & save and Discard, which
  asks first; with progress while finishing.
- **Location is asked with Core Location** (`LocationAuthorization`): Start without access asks for access while in use and sends
  `PermissionResult` with the answer; access already refused shows the note instead, and Start asks again once it is allowed.
- **Kotlin chooses what the screen says and offers** (dev/shared-display-text): `RecordTexts` (`:feature-record-api`) gives the elapsed
  time, the figures in the athlete's units, the notices, the controls for the recording's status (`RecordAction`) and their labels, and
  whether the type can still change. The Compose screen asks the same, so it now also holds its controls while finishing.
- **The type row and sheet take `outdoorOnly`**, as the shared picker does, so recording offers no indoor type.
- **The samples cover every state**: `RecordSamples.variants` adds no permission, a full form (gear, visibility categories), saving and a
  failed finish.

Out of scope: Android, desktop and web beyond the shared texts; how the recorder samples, journals or publishes; Moderation (left to
phase 5's list); a live map while recording.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `record/gps-recording`: the recording screen is native on iOS.
- `dev/shared-display-text`: the recording screen's texts and controls are chosen in shared code.

## Impact

- **`:feature-record-api`, `-ui`:** `RecordTexts`, `RecordAction`; `RecordUi` asks them. New strings `record_location_off`,
  `record_open_settings`, `record_discard_confirm` (the catalog regenerated).
- **`:app-fixtures`:** `RecordSamples.variants`.
- **`iosApp`:** `RecordView`, `LocationAuthorization`, `outdoorOnly` on `ActivityTypeRow`/`ActivityTypeSheet`, a wide `AppButton`,
  aliases, samples, previews, snapshots, text tests, the registry entry and its tests.
- **Docs:** the roadmap's phases, findings and open items (what still goes through Compose, what phase 5 needs); `compose-screens.md`.
