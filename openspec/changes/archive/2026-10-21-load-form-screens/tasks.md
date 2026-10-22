# Tasks

## 1. Zones read

- [x] 1.1 In `PrivacyZones.kt`:
  - `read()` keeps the failed read's `DataError`;
  - `refresh()` returns `ZonesRead` (`Known` / `Unreadable(reason)`);
  - add `zonesOutcome()` as design.md describes;
  - `add()` keeps throwing `ZonesUnreadableException`.

  Update `SettingsPresenter`'s warm-up and `SegmentTrackTest`'s call. Verify in `PrivacyZonesTest`:
  - a failed read with nothing read before answers `Unreadable` with the failure's reason. The fake PDS fails as a server error, so this asserts `DataError.Api`; `Network` for an `IOException` is checked in 2.5 through the presenter fixture's `offline`;
  - an undecodable record answers `Unreadable(DataError.Serialization)`;
  - a failed read after a successful one answers that earlier `Known`;
  - `zonesOutcome()` emits `Loading`, then `Error` with the reason;
  - `add()` while unreadable still throws.

## 2. Form screens

Each screen moves onto a retained `ContentState`, `content: ContentState<Unit>` in its State, a retained seed and a `Retry` event, and its UI uses `ContentStateLayout`, all as design.md describes. Each presenter test covers:
- loading, then loaded with the fields seeded;
- a first-load failure with its reason, then Retry, then loaded;
- a failed save that keeps the typed fields and sets only `error`;
- a second answer (Retry after loading) that leaves typed text in place.

- [x] 2.1 EditProfile: `ownProfileOutcome()`, `needsSignIn` in place of the `SignInAgain` error, and a new `EditProfileEvent.Retry`. Verify in `EditProfilePresenterTest`, plus a case that `needsSignIn` shows Sign in again with no `error`.
- [x] 2.2 Zones: `ownProfileOutcome()` with `seed` as the `setAt` baseline, and a new `ZonesEvent.Retry`. Verify in `ZonesPresenterTest` (its first load-failure test).
- [x] 2.3 SportZones: `ownProfileOutcome()` with `seed` for the defaults, and a new `SportZonesEvent.Retry`. Verify in `SportZonesPresenterTest` (its first load-failure test).
- [x] 2.4 EditActivity:
  - one `outcomeFlow` for the activity and its quiet streams (`EditActivityLoad`);
  - a saveable seeded guard;
  - re-seeding `DetailsForm` and `EditVisibility`, which are plain `remember`, after recreation (see design.md Risks).

  Verify in `EditActivityPresenterTest`:
  - its first load-failure test, with `LoadActivity` and Retry;
  - a streams failure still loads the form;
  - a retained and saveable round trip (a configuration change) keeps a typed title through the reload it starts, and fills the details again.
- [x] 2.5 PrivacyZones: `zonesOutcome()`, and `needsSignIn` in place of the derived `SignInAgain`. `privacyErrorOf` stays for actions. Verify in `PrivacyZonesPresenterTest`:
  - unreadable offline shows the failure with `LoadPrivacyZones` and `DataError.Network`, and no zone can be changed;
  - Retry, once readable, shows the zones;
  - a failed save or add keeps the zones and sets only `error`;
  - a session that can't save shows `needsSignIn`.

## 3. Integration

- [x] 3.1 Verify with `grep` that none of the five presenters calls `profiles.ownProfile()`, `feed.activity(` or `privacy.refresh()` directly, and that nothing but `add()` and its callers uses `ZonesUnreadableException`. List the remaining callers of the old reads under Workflow follow-up.
- [x] 3.2 Run `./gradlew check` and `./gradlew composeStabilityCheck`, and verify both pass.
- [x] 3.3 Verify in the web app (`:webApp:wasmJsBrowserDevelopmentRun`), with AppView and PDS requests failed in the page:
  - edit profile, default zones and privacy zones each show what failed and why, with Retry, and Retry loads the form once unblocked;
  - a profile save while blocked keeps what was typed and says why, with no load Retry.
  - Verified in the web app, with the page's PDS requests failed:
    - edit profile and default zones each say "Couldn't load profile. You're offline." with Retry, and Retry fills the form once unblocked;
    - a profile save while blocked keeps the typed bio and says "Couldn't save profile. You're offline.", with no load Retry;
    - privacy zones opened offline in a session that has already read them shows the zones last read, as `profile/privacy-zones` requires. The "never read this session" failure can't be reached once the feed has loaded, so it is covered by `PrivacyZonesPresenterTest` only.

## Workflow follow-up

- Callers left after this change (3.1):
  - Within the five, by design: `EditActivityPresenter` calls `feed.activity` inside its one `outcomeFlow` (with the quiet streams read), as CombinePicker does.
  - `ZonesUnreadableException`: only `PrivacyZones.add()` and `PrivacyZonesPresenter`'s action errors.
  - `ownProfile()`: `ImportActivityPresenter` (supplementary) and `TrainingEdit` in `core/data`.
  - `feed.activity`: `CreateSegmentPresenter` (moves in `load-segment-screens`), `AddingRecording`, `CombinePickerPresenter` (inside its outcome); in `core/data`, `ActivityManager` and `CombineSuggestions`.

- Archive the change after review.
