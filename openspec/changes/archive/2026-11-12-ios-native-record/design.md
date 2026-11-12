# Design

## Context

See proposal.md for why. `RecordPresenter` already runs in the presenter host and publishes `RecordState`: the recorder's status,
elapsed time, distance, moving seconds and climb, `recovered`, `saveFailed`, the type, title, gear and visibility fields, whether the
tracker has location access, `saving` while Finish publishes, and a failed finish. `ManualActivityView` (ios-native-forms) draws the same
fields and is the template for the form's lower half.

## Goals / Non-Goals

- Goals: a native screen registered in `ScreenRegistry`; every rule asked of Kotlin; the Compose screen unchanged to the eye.
- Non-goals: a map of the route while recording; changing the presenter or the recorder.

## Decisions

- **One form, controls in a bottom bar.** The figures lead the form (the elapsed time in a large rounded monospaced face, the figures
  under it as the activity card's `CardStats`), the fields follow, and the controls stay reachable in a `safeAreaInset` bar over the
  form, wide and large, as a workout app's are. Finish & save and Resume are filled, Discard tinted and confirmed first: it throws the
  recording away for good. While paused, Finish & save takes a row of its own over Resume and Discard: three in a row on a phone
  wrapped or cut its label. Alternative: the controls at the form's end, as the Compose screen has them; rejected, since a long
  visibility section pushes them off a phone's screen mid-ride.
- **The controls are Kotlin's.** `RecordTexts.controls(state)` lists `RecordControl`s for the status (Idle: Start; Recording: Pause;
  Paused: Resume, Finish, Discard), each holding its `RecordAction` and label (an object each, since a list of enums can't reach
  Swift). Both UIs draw them in that order and hold them all while saving.
- **Asking for location natively.** The shared iOS UI's permission request answered "granted" and left the asking to the tracker's
  `start`. Natively `LocationAuthorization` (a `CLLocationManager` delegate on the main actor) asks `requestWhenInUseAuthorization` when
  undetermined and resumes with the answer from `locationManagerDidChangeAuthorization`; a refused or restricted status answers false
  without a prompt. The view sends `PermissionResult(granted)` and, when refused, shows `RecordTexts.locationOff` with Open Settings
  (`UIApplication.openSettingsURLString`). It reads the status again when the app becomes active, so returning from Settings clears the
  note. Previews and snapshots set the refused case explicitly instead of reading the simulator's.
- **Text rules to Kotlin:** `RecordTexts`: `elapsed(state)` (`StatText.duration`), `stats(state, units)` (distance, the sport's rate when
  it has one, elevation, as `LabeledText`s), `recovered`, `saveFailed`, `locationOff`, `openSettings`, `discardConfirm`,
  `typeChangeable(state)` (idle only) and the controls above.
- **The indoor rule stays Kotlin's.** `SportCatalog` has no Swift type, so `ActivityTypeField.offeredQuickTypes(outdoorOnly)` filters
  the quick row for the native sheet.
- **`outdoorOnly` reaches the native picker.** `ActivityTypeRow` and `ActivityTypeSheet` gain `outdoorOnly` (default false), passed to
  `SportTypeGroups.subSportChoices` and `matching`, as the shared picker passes it.

## Risks / Trade-offs

- [The parent session merges other branches touching the same Swift lists] → each addition is a separate block at the end.
- [Swift export drops `Duration`] → the view never reads `elapsed`; it reads `RecordTexts.elapsed`.
