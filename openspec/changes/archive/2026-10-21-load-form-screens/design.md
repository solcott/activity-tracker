# Design

## Context

See proposal.md (Why) for the motivation and `specs/ui/content-loading` for the behaviour. This section covers only the current state that shapes the approach.

**How the forms seed today.**
- **EditProfile, Zones, SportZones.** Each loads with `LaunchedEffect(Unit) { runSuspendCatching { profiles.ownProfile() } }`, then calls `form.fillOnce(...)`.
  - The guard is a `rememberSaveable` `filled` flag, so a second load never overwrites typed fields.
  - The loaded value (`own`, `saved`) is a plain `remember`, so a configuration change refetches it.
  - No `loadKey` and no retry. `loaded: Boolean` in State is `own != null`.
- **EditActivity.**
  - It has a `loadKey` and an `if (activity != null) return` guard.
  - Its one load reads `feed.activity(uri)` and then, quietly, `feed.streams(view.uri)`. It seeds `title`, `form.seed(...)` and `visibility.seed(view, streams)`.
  - `activity` and `seededTitle` are plain `remember`, but `title` is saveable. So after a configuration change the load runs again and `setTextAndPlaceCursorAtEnd` writes the saved title over the typed one. This change closes that gap.
- **PrivacyZones.**
  - `PrivacyZones.refresh()` throws `ZonesUnreadableException` when `read()` gives `RouteTrim.Unknown`: nothing read this session.
  - `read()` swallows the failure (`runSuspendCatching { getRecord }.getOrNull()`, then `NoAccess` or an undecodable record become null), so why it failed is lost. A failed read with an earlier one this session answers that earlier one.
  - The screen shows `UiError.of(LoadPrivacyZones, e)`, whose reason is `Unknown`.
  - `SignInAgain` is derived into `error` when the session can't save zones.

**Callers.**
- `refresh()` is called by `PrivacyZonesPresenter` and `SettingsPresenter`'s warm-up (plus `PrivacyZonesTest` and `SegmentTrackTest`).
- `add()` also throws `ZonesUnreadableException`. Its callers are `PrivacyZonesPresenter` and `ActivityRepository.addZoneAt`, and both are actions.

**Shared pieces:**
- From `load-activity-and-profile-screens`: `ProfileRepository.ownProfileOutcome()` and `FeedRepository.activityOutcome(uri)`.
- `outcomeFlow`, `outcomeOf` and `toDataError` in `core/data/.../OutcomeOf.kt`.
- `produceRetainedContentState` from `uistate-circuit`.
- `mapContent` in `app/presenters/.../ContentStates.kt`.
- `ContentStateLayout` in `composeApp/.../ui/ContentStateLayout.kt`.
- Reference shapes: `TrainingZonesPresenter` for a profile load with an action error beside it, and `CombinePickerPresenter` for dependent reads in one `outcomeFlow`.

## Goals / Non-Goals

**Goals:**
- Each of the five forms gets a first-load phase, a reason and Retry, from one retained `ContentState`.
- Load errors and save errors are separate fields.
- A form is seeded once. Nothing reloaded later writes over it, including after a configuration change.
- An unreadable zones read says why.

**Non-Goals:**
- Showing a refresh over a loaded form (see Decisions).
- Removing `ownProfile()` or `activity()`. `ImportActivityPresenter`, `TrainingEdit`, `CreateSegmentPresenter`, `ActivityDetailPresenter` and others still call them.
- `PrivacyZones.add()`'s exception.

## Decisions

### Each form's load is retained and seeds once
```kotlin
var loadKey by remember { mutableIntStateOf(0) }
val loaded = produceRetainedContentState<OwnProfile?>(null, loadKey) { profiles.ownProfileOutcome() }
val own = rememberFormSeed(loaded) { form.fillOnce(it) }        // retained; fills once, from a keyed SideEffect
content = loaded.formContent(seeded = own != null)              // loading or failed until seeded, then loaded for good
```
- `rememberFormSeed` and `formContent` (`app/presenters/.../FormSeed.kt`, `ContentStates.kt`) are shared by four of the five. EditActivity has its own `rememberEditSeed` for its split between the saveable title and its plain-`remember` holders (see Risks).
- The form's baseline, which was `own`, `saved` or `activity`, becomes `seed`: the value the form was filled from, retained. So `nowHidden`, the zones' `setAt` times and EditActivity's `isChanged` compare against what the athlete started from.
- Retaining the content and the seed means a recreated screen shows the form at once. The retained producer still restarts its load when the screen comes back, and that answer changes nothing, since the form is already seeded.
- `fillOnce` keeps its saveable guard for the fields. EditActivity gains a saveable `titleFilled` flag, so its title is never filled twice.
- **Alternative rejected: stale-while-revalidate into the form**, as the read screens do. A refresh would overwrite typing, or need a per-field merge for no real gain.
- **Alternative rejected: re-seeding the baseline from later answers.** It would change what the athlete is told they're narrowing or changing while they edit.

### No refresh UI over a loaded form
- `ContentStateLayout` gives the spinner, or the reason with Retry, until the first load. After that it draws the form only. `RefreshStatus` isn't used on these screens.
- A later reload is rare: nothing is pushed above these screens, and a retained producer restarts only when its record comes back to the top. Neither its progress nor its failure is shown.
- A save that can't reach the server reports its own error, which is what the athlete needs.

### State shape
- `content: ContentState<Unit>` (`loaded.mapContent {}`) replaces `loaded: Boolean` and the load half of `error`. The UI uses `content` only to pick the phase. The fields are in State already.
- `error: UiError?` is now set by actions only.
- EditProfile and PrivacyZones get `needsSignIn: Boolean` in place of a derived `SignInAgain` error, as FollowRequests did. Their UIs keep the Sign in again button.
- EditActivity keeps `type`, `details` and `leaderboardExclusion` as they are (null until loaded). The UI decides from `content.hasLoaded`, never `!= null`.
- New `Retry` events on EditProfile, Zones and SportZones, which bump `loadKey`. EditActivity and PrivacyZones already have one, and it now only bumps `loadKey`.
- `ContentState` is `@Immutable` and `Unit` is stable, so `composeStabilityCheck` stays green.

### EditActivity: one outcome for the activity and its streams
```kotlin
produceRetainedContentState<EditActivityLoad?>(null, loadKey) {
    outcomeFlow {
        val view = feed.activity(uri)
        EditActivityLoad(view, runSuspendCatching { feed.streams(view.uri) }.getOrNull())
    }
}
```
- The streams only change which categories are offered and are no use alone. So they're read inside the activity's outcome and stay quiet, as today and as `CombinePick` does.
- `EditActivityLoad` is a small `@Immutable` holder in the presenter's file.
- The supplementary reads (`rememberApprovesFollowers`, `rememberLeaderboardExclusion`) are unchanged.

### Unreadable zones are an answer that carries its reason
```kotlin
sealed interface ZonesRead {
    data class Known(val trim: RouteTrim.Known) : ZonesRead
    data class Unreadable(val reason: DataError) : ZonesRead
}
suspend fun refresh(): ZonesRead
fun zonesOutcome(): Flow<Outcome<PrivacySettings>> = flow {
    emit(Outcome.Loading)
    emit(when (val r = refresh()) { is ZonesRead.Known -> Outcome.Data(r.trim.settings, Origin.Network); is ZonesRead.Unreadable -> Outcome.Error(r.reason, Origin.Network) })
}
```
- **`read()` keeps the reason:**
  - a failed `getRecord` gives `toDataError()` of the caught exception;
  - a record that doesn't decode gives `DataError.Serialization`;
  - `NoAccess` gives `DataError.Unknown(message = "no access")`.
  
  `forPublish`, `readFresh` and `add` ignore the reason, so publishing is unchanged.
- **The shape follows `SpaceRead`:** an expected "can't read" is a case of the answer, not an exception. `refresh()` is still the one read Settings uses.
- **`zonesOutcome()` turns it into the screen's load outcome.** The screen then shows `LoadPrivacyZones` with the real reason ("You're offline") and Retry through `ContentStateLayout`. With no data, nothing can be changed, as `profile/privacy-zones` and today's screen require.
- A failed read with an earlier one this session still answers `Known` with that earlier read, as today.
- `SettingsPresenter`'s warm-up ignores the answer and no longer needs `runSuspendCatching`.
- `ZonesUnreadableException` stays, for `add()` only: its callers catch it as an action failure, and the type isn't lost there.
- **Alternative rejected: keep throwing and classify in the presenter.** `toDataError()` would flatten it into `Unknown`, and the reason was already lost in `read()`.
- **Alternative rejected: a separate unreadable phase in the UI.** It would duplicate `ContentStateLayout`'s failure-with-Retry, which already says the same thing.

### UI
- Each of the five wraps its form in `ContentStateLayout(state.content, kind, onRetry = { eventSink(Retry) })`.
  - Kinds: `LoadProfile` for EditProfile, Zones and SportZones, `LoadActivity` for EditActivity, and `LoadPrivacyZones`.
- Save buttons and action errors render where they do now.
- PrivacyZones' editing is gated on loaded data and `!needsSignIn`.

## Risks / Trade-offs

- **[A long-open form's baseline goes stale]** → Acceptable. Zones and SportZones already save through `updateTraining { current -> }`, and EditProfile and EditActivity overwrite with what the athlete sees, as today.
- **[EditActivity's holders after recreation]** `DetailsForm` and `EditVisibility` are plain `remember`ed, shared with other screens. After a configuration change they're filled again from the retained `seed`, so a description or visibility typed before a rotation is lost, as it is today. Only the saveable title survives, and it's never filled twice. → Making the shared holders retained is a change of its own for their other screens.
- **[`NoAccess` reads as an unknown reason]** The screen says it couldn't load the zones, with no specific why. → Same as today. Sessions without the `space:` grant answer `Known` (no zones) before reaching it.

## Migration Plan

- Nothing persisted or served changes. Room, lexicons and the AppView are untouched.
- `ownProfile()` and `activity()` stay for their other callers.
- Rollback is reverting the change.
