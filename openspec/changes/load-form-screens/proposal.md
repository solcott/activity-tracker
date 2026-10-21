# Proposal

## Why

Five screens load a record only to fill a form the athlete then edits and saves: EditProfile, Zones, SportZones, EditActivity and PrivacyZones.

- **Most can't recover from a failed load.** EditProfile, Zones and SportZones have no retry, so a failed `ownProfile()` leaves the screen dead until the athlete leaves and comes back. Only EditActivity and PrivacyZones have a `loadKey`.
- **Load and save failures are mixed.** Every one of them shares a single `error` field between the load and the save.
- **PrivacyZones depends on an exception.** It tells "zones can't be read" apart by catching `ZonesUnreadableException`, which a `DataError` classification would flatten into `Unknown`.

This is step 3 of moving the rest of the app onto kmp-dataresult.

## What Changes

- **Each form's seed load becomes an outcome** in a `ContentState`, giving a first-load phase, a reason and retry.
- **The form state stays the presenter's own.** It is seeded once from the first successful load, as today, so stale-while-revalidate doesn't apply. A refresh never overwrites what the athlete is editing.
- **Load and save errors separate.** A failed load shows on the form's first-load phase with Retry. A failed save stays the action `UiError`.
- **Unreadable zones become an answer, not an exception.** `PrivacyZones.refresh()` answers "unreadable" as data, following the codebase's own `SpaceRead` idiom, instead of throwing `ZonesUnreadableException`. The screen keeps saying the zones can't be read, and saving stays blocked, as `profile/privacy-zones` requires.
- **The screens:**
  - **EditProfile:** `ownProfileOutcome()`. `needsSignIn` stays SignInAgain.
  - **Zones and SportZones:** `ownProfileOutcome()`.
  - **EditActivity:** `activityOutcome(uri)` plus its optional streams read. Its supplementary reads (approves-followers, leaderboard exclusion) stay as they are.
  - **PrivacyZones:** the zones read through the unreadable-as-answer change.
- **Not in scope:**
  - Settings: every read it makes is supplementary and never shows a load error.
  - Import, Record and Manual: forms whose reads are supplementary.
  - CreateSegment: `load-segment-screens`.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `ui/content-loading`: this change extends its requirements to the form-seeding screens' first load. It names their difference: once a form has loaded, a later reload never replaces what the athlete is editing.
- `profile/privacy-zones`: only if its requirements describe how an unreadable zones record is reported. Confirm while writing the specs. The observable behaviour (can't read, can't save) must not change.

## Impact

- **`core/data`:** `PrivacyZones.refresh()` returns an unreadable answer instead of throwing. Its other callers move with it: `ActivityRepository` route trimming, `CreateSegment`'s `ZonesUnreadable`, and `SettingsPresenter`.
- **`app/presenters` and `app/screens`:** the five presenters and states, and their tests: `EditProfilePresenterTest`, `ZonesPresenterTest`, `SportZonesPresenterTest`, `EditActivityPresenterTest` and `PrivacyZonesPresenterTest`. EditActivity and the zones screens have no load-failure test today, so they gain one.
- **`composeApp`:** the five UIs show the first-load phase through `ContentStateLayout`.
- **Depends on:**
  - `error-reasons`.
  - `load-activity-and-profile-screens`, which adds `activityOutcome` and `ownProfileOutcome`.
