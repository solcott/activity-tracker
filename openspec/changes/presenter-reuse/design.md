# Design

## Context

See proposal.md (Why) for the motivation. Two existing ways of sharing presenter logic shape the approach:

- **Sub-state carries its own sink.** `ActivityDetailsFields` (import and edit) is a sub-state with an `eventSink: (DetailsEvent) -> Unit` that `DetailsForm.fields()` fills with `::onEvent`. The host declares no event and adds no `when` branch for it.
- **Host wraps a shared event.** `ActivityListEvent` is wrapped by each host (`FeedEvent.Activity(...)`, `ProfileEvent.Activity(...)`) and handled by `rememberActivityListEventSink`. The wrapping is needed because handling it requires the host's `Navigator`.

State holders follow one shape: a `@Stable` class plus a `@Composable rememberX(...)` (`NewActivityVisibility`, `EditVisibility`, `DefaultVisibilitySettings`, `DetailsForm`). Repository dependencies come in as arguments.

Constraints from `compose-screens.md`:

- Every `State` is `@Immutable` and `:app-screens` runs the stability check in strict mode.
- Event-driven work goes through `rememberSingleFlight()`, and loads stay in `LaunchedEffect`.
- Suspend calls are wrapped in `runSuspendCatching`.

## Goals / Non-Goals

**Goals:**
- Each shared piece has one owner: its holder or producer handles its events, and hosts only place its state.
- Every presenter test keeps passing with only mechanical edits (how events are sent), proving there is no behavior change.

**Non-Goals:**
- SubCircuit for any of these. The type picker was considered and rejected (Decision 4).
- Comments paging, the zones screens, and `TrainingZones`/`EditProfile`'s single-group visibility pickers.
- Converting existing `remember*` holders to injected producers wholesale. Only the new type producer is injected (Decision 4).

## Decisions

### 1. Visibility: the sink lives in `VisibilityFields`, not a host-wrapped event

`VisibilityFields` gains `val eventSink: (VisibilityEvent) -> Unit = {}`. `VisibilityEvent` sits next to it in `app/screens/.../visibility` and has two cases, `ActivityChanged(visibility)` and `CategoryChanged(category, visibility)`.

- **Holders fill the sink.** `fieldsOf(...)` takes the sink, and each holder passes its own `::onEvent`: `NewActivityVisibility`, `EditVisibility` and `DefaultVisibilitySettings`. Each holder's `onEvent` replaces its `activityChanged`/`categoryChanged` pair and keeps its existing guards (`canChoose`, `offered`, `saving`).
- **The shared form emits directly.** `ActivityVisibilityForm(fields)` sends to `fields.eventSink` and drops its `onActivityChange`/`onCategoryChange` parameters. In Settings, `VisibilityPicker`/`DetailVisibilityPicker` send to `defaults.eventSink`.
- **Host events go away.** The removed events are `RecordEvent`/`ManualActivityEvent`/`ImportActivityEvent`/`EditActivityEvent`'s `VisibilityChanged` and `CategoryVisibilityChanged`, and `SettingsEvent`'s `DefaultVisibilityChanged` and `DefaultCategoryVisibilityChanged`. Their `when` branches go with them.
- **Settings' single-flight key.** `DefaultVisibilitySettings` keys its flight on `SettingsEvent.DefaultVisibilityChanged::class`, which this change removes. The key becomes `VisibilityEvent::class`, so all defaults still save as one flight.

*Alternatives considered:*
- **Host-wrapped `XEvent.Visibility(VisibilityEvent)`**, the `ActivityListEvent` style. It still leaves one event and one branch per host. Handling a visibility event needs nothing from the host (no navigator), so wrapping buys nothing.
- **A SubCircuit.** The three holders behave differently (seed from defaults; seed from the saved activity and confirm narrowing; save on change), and the hosts need the live choice to publish. That is three sub-screens plus outer events for no gain.

*Equality.* `VisibilityFields` stays a data class. A bound reference (`holder::onEvent`) is equal across recompositions for the same holder, the same reasoning `ActivityDetailsFields` already relies on. Tests compare fields through `tierOf` and properties, not whole-object equality against fresh instances. The `VisibilityFields()` defaults in `RecordState`/`ManualActivityState`/`ImportActivityState` get the no-op sink.

### 2. `rememberCursorPaging`: loads run in a `LaunchedEffect`, and load-more is a conflated request

```
rememberCursorPaging(key, enabled = true, idOf = { it.did }) { cursor -> CursorPage(items, next) }
  -> CursorPaging<T>: items, isLoading, canLoadMore, loaded, failure: Throwable?,
                      loadMore(), retry(), update { items -> items' }
```

- **Loading.** One `LaunchedEffect(key, enabled, reloadKey)` loads the first page, then serves load-more requests from a conflated `Channel` one page at a time.
  - Changing `key` (search's query) cancels any page in flight, which replaces search's hand-held `nextPage: Job`.
  - `loadMore` is ignored while loading, after a failure, or with no cursor. That is the guard both presenters hand-roll today.
  - Paging is a load, not an event-driven write, so it belongs in `LaunchedEffect` under the repo rules rather than in `SingleFlight`.
- **Appending.** Pages are de-duplicated by `idOf`, keeping the first occurrence. Both presenters do this now.
- **First pages.**
  - A first page replaces the items only when it settles. Items already shown stay visible while a new key loads, as search's results do today.
  - A failed first page empties the list, so a new query that fails never shows the previous query's results.
  - `enabled = false` empties the list at once, as search does for a blank query.
- **Errors.** `failure` is a raw `Throwable`, and each host maps it to its own `UiError.Kind`: FollowList by `screen.kind`, Search to `SearchAthletes`. `canLoadMore` is `cursor != null && failure == null`, and `retry()` reloads from the first page. Both presenters already behave this way.
- **What hosts keep.**
  - Search keeps its debounce inside the loader for the first page (`if (cursor == null) delay(SEARCH_DEBOUNCE)`), and passes `enabled = trimmed.isNotEmpty()`. `searched` becomes `enabled && paging.loaded && !paging.isLoading`.
  - FollowList captures `subject` in its loader.
  - FollowList's Remove stays a `SingleFlight` write in the host and calls `paging.update { it.filterNot { a -> a.did == did } }` on success. A Remove failure is the host's own `error`. It also suppresses load-more (`canLoadMore && removeError == null`), as it does today because the two share one `error`.

*Alternative considered:* a generic producer with a `SingleFlight` inside. Cancelling on a key change would then need extra code, which the `LaunchedEffect` gives for free.

### 3. `rememberApprovesFollowers(profiles, enabled)`

This is a `@Composable` producer in `app/profile`. It reads `ownProfileRecord()?.followPolicy == APPROVE` once `enabled` turns true, and falls back to `false` if that fails. It replaces three loads:

- `ActivityDetailPresenter`: `enabled = isOwn`.
- `FollowListPresenter`'s `rememberCanRemove`: `enabled = ownFollowers`.
- `EditActivityPresenter`: always on.

`EditVisibility.approvesFollowers` stops being a public `var` written from an effect. It becomes a constructor lambda `approvesFollowers: () -> Boolean`, matching `NewActivityVisibility`.

The other `FollowPolicy.APPROVE` reads (Profile, TrainingZones, Import, EditProfile) use a profile those presenters already loaded for other reasons, so they stay.

**Small companions:**
- `FollowRequestsPresenter.answerError` becomes `uiErrorOf(UiError.Kind.AnswerFollowRequest, cause)`. The extra `PendingWritesException` case can't occur there: approve and decline never go through `ActivityZonesStore`.
- The detail screen's `ToggleLike` clears `likeError`, then sends `ActivityListEvent.ToggleLike(current)` to a `rememberActivityListEventSink(navigator, activities) { likeError = it }`. Its other events stay in its own `Navigator.open`. The like's flight moves from the screen's `flights` to the sink's. Nothing else checks `flights.isRunning(activityUri)`.

### 4. The activity type is an injected StateProducer, not a SubCircuit

**Why not a SubCircuit.** Import sets the type from the parsed file, and Record and Import disable the picker while recording or saving. A SubCircuit can only receive these through its screen, and a new screen remounts it. The host would have to hold a second copy of the type and rebuild the screen on each push. The user chose a StateProducer for this reason.

**The producer:**

```
@Inject class ActivityTypeChoices(recentTypes: RecentActivityTypes) {
    @Composable fun produce(initial: ActivityType, outdoorOnly: Boolean = false): ActivityTypeChoice
}
@Stable class ActivityTypeChoice { val selected; val field: ActivityTypeField; fun select(type) }
```

**The state.** `ActivityTypeField(selected, quickTypes, eventSink: (ActivityTypeEvent) -> Unit)` and `ActivityTypeEvent.Select(type)` live in `app/screens/.../record`. Hosts replace their `type` + `quickTypes` state fields with one `type: ActivityTypeField`, and drop their `TypeChanged` event.

**Inside `produce`:**
- The selection is a `rememberSaveable(ActivityTypeSaver)`, so it still survives process death.
- The quick row comes from the existing `quickActivityTypes`.
- Import calls `choice.select(first.type)` when a file is read.

**Where the UI pieces live.**
- `enabled` stays a UI argument to `ActivityTypePicker`, as it is today.
- `ActivityTypePicker(field: ActivityTypeField, ...)` sends to `field.eventSink`.

**Injection.** The producer is an unscoped `@Inject` class, so Metro can inject it into the three `@CircuitInject` presenter functions in place of `recentTypes: RecentActivityTypes`. Import keeps no other use of `recentTypes`. The feed filter's multi-select picker keeps calling `quickActivityTypes` directly.

*Alternative considered:* a `rememberActivityTypeChoice(recentTypes, ...)` function, matching the other holders. The injected class follows the Circuit StateProducer shape the user asked for, and drops a repository parameter from each host. It's the only injected producer in this change, and the docs note both shapes.

### 5. Testing new producers

The producers are tested with `circuit-test`'s `presenterTestOf { ... }` in `app/presenters/src/jvmTest`, which runs a bare composable without needing a screen. Molecule isn't a direct dependency and isn't needed. The coverage is:

- **Paging:** de-duplication, the load-more guard, a key change cancelling a page in flight, and retry.
- **Approves followers:** the `enabled` gate and the fallback on failure.
- **Type choice:** select, the quick row updating after a publish, and the `outdoorOnly` filter.

Everything else is covered by the existing presenter tests, with their event sends updated.

## Risks / Trade-offs

- **[Risk] A failed retry in a follow list now empties it.** Today FollowList keeps the accounts it had when a first-page reload fails. That reload only happens on Retry after a failed load-more. The producer follows search's rule instead (a failed first page empties the list), which search needs so a new query never shows stale results. → The error and Retry still show, and a successful retry restores the list. This is the one visible edge difference, and it is accepted. If a test pins the old behavior, update it and note it in the commit.
- **[Risk] Equality of `VisibilityFields` or `ActivityTypeField` might break skipping or strict stability.** → The lambda field mirrors `ActivityDetailsFields`, which already passes the strict check. Run `composeStabilityCheck` after group 1.
- **[Risk] Detail clears `likeError` even when a repeat tap is dropped.** → This is harmless: the error shown belongs to the attempt still running.
- **[Trade-off] Two producer shapes now coexist:** `remember*` functions and one injected class. → `compose-screens.md` documents when to use each.

## Migration Plan

Internal refactor in one app release, with no data or API migration. Each task group in tasks.md leaves `./gradlew check` green and can be committed and reverted alone.
