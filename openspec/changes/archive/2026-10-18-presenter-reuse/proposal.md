# Proposal

## Why

Several presenters re-implement the same small pieces of presentation logic. Five screens declare their own pair of visibility events and forward them identically. Two presenters hand-roll the same cursor-paging state machine. Three presenters each load the athlete's follow policy. Three presenters wire up the activity type picker separately. Circuit's [presenter patterns](https://slackhq.github.io/circuit/docs/presenter-patterns/) (decomposition, StateProducer, use cases) fit these spots. The codebase already uses these patterns elsewhere (`ActivityListEvent` + `rememberActivityListEventSink`, `DetailsForm`, `NewActivityVisibility`), so this change applies them where they are still missing. Fixing these now, before more screens copy them, keeps each new screen smaller.

## What Changes

- **Shared visibility event.** A `VisibilityEvent` contract in `app/visibility` (activity changed, category changed) replaces the `VisibilityChanged`/`CategoryVisibilityChanged` pairs on `RecordEvent`, `ManualActivityEvent`, `ImportActivityEvent`, `EditActivityEvent`, and the `DefaultVisibilityChanged`/`DefaultCategoryVisibilityChanged` pair on `SettingsEvent`. `VisibilityFields` carries an `eventSink` that the visibility holder fills with its own `onEvent` (`NewActivityVisibility`, `EditVisibility`, `DefaultVisibilitySettings`). As a result, hosts declare no visibility event and `ActivityVisibilityForm` sends to the fields directly. This is the pattern `ActivityDetailsFields` + `DetailsForm` already use. Single-group visibility pickers (`TrainingZonesEvent`, `EditProfileEvent`) are a different shape and stay as they are.
- **Cursor-paging producer.** A `rememberCursorPaging` StateProducer in `:app-presenters` owns items, cursor, loading, error, load-more, retry and de-duplication. `FollowListPresenter` and `AthleteSearchPresenter` move onto it. The detail screen's comments keep their own paging.
- **Small extractions.**
  - A `rememberApprovesFollowers` producer replaces the three ad hoc `ownProfileRecord()` follow-policy loads in `ActivityDetailPresenter`, `FollowListPresenter` and `EditActivityPresenter`.
  - `FollowRequestsPresenter`'s `answerError` delegates to the shared `uiErrorOf`.
  - The detail screen's like toggle goes through the shared activity-list sink instead of duplicating it.
- **Activity type StateProducer.** An `ActivityTypeChoice` holder, produced by a Metro-injected `ActivityTypeChoices` producer, owns the chosen type (saved across process death) and the quick row from `RecentActivityTypes`. It exposes them as an `ActivityTypeField` sub-state with its own `eventSink`. `RecordPresenter`, `ManualActivityPresenter` and `ImportActivityPresenter` use it instead of each wiring `quickActivityTypes` and `ActivityTypeSaver` and declaring `TypeChanged`. A SubCircuit was considered and rejected: the host has to push the type in and disable the picker (see design.md). Import can still set the type from a parsed file. The feed filter's multi-select type picker is a different control and is unchanged.
- **Docs.** `compose-screens.md` gains a short section on shared event contracts and StateProducers, and when to choose one over a SubCircuit.

None of this changes what the athlete sees or does. It is a pure refactor, with one accepted edge difference: a follow list whose reload fails on Retry now shows empty under the error instead of keeping its earlier accounts (see design.md, Risks).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This change has no spec-level behavior change, so `.openspec.yaml` sets `skip_specs: true`.

## Impact

- **`:app-screens`:** new `VisibilityEvent` (in `visibility/`, and `VisibilityFields` gains `eventSink`) and `ActivityTypeField`/`ActivityTypeEvent` (in `record/`). Event classes are removed from `RecordScreen`, `ManualActivityScreen`, `ImportActivityScreen`, `EditActivityScreen` and `SettingsScreen` (all internal contracts; no external API).
- **`:app-presenters`:** new `CursorPaging.kt`, `ApprovesFollowers.kt` and `ActivityTypeChoice.kt`. Visibility holders gain `onEvent`. Seven presenters are simplified.
- **`composeApp`:**
  - `ActivityVisibilityForm` and the Settings pickers send to `fields.eventSink`. Their callers in `RecordUi`, `ManualActivityUi`, `ImportActivityUi`, `DiveBatchForm`, `EditActivityUi` and `SettingsUi` drop their per-screen lambdas.
  - `ActivityTypePicker` takes an `ActivityTypeField`.
- **Tests:** presenter tests that send the removed events are updated (about 15 visibility sends, plus the type changes in the Manual and Import tests). The new producers get their own JVM tests.
- **No** lexicon, AppView, data-layer, dependency or migration changes.
