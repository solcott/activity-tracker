# Design

## Context

See proposal.md (Why) for the motivation and `specs/ui/content-loading` for the behaviour. This section covers only the current state that shapes the approach.

**The seven presenters.** Each loads with `LaunchedEffect` + `runSuspendCatching` into a `remember`ed value and one `error: UiError?`, which actions also write.
- RouteMap, MediaGallery, Profile, TrainingZones and ThresholdHistory have no retry.
- FollowRequests and CombinePicker bump a `loadKey`.
- MediaGallery has no first-load phase: `items` is empty while loading.

**Each one edits its loaded value after an action:**
- Profile sets `profile = it` after a follow, block or mute.
- FollowRequests removes or moves accounts in its two lists after approve and decline.
- TrainingZones copies the saved training visibility into `profile`.
- ThresholdHistory folds `TrainingHistory.remove` into `training`.

**Repositories.**
- `FeedRepository.activity(uri)` applies rewrites, local likes and deletes, and moves the comment count. It then writes the result back with `updateCached`.
- `ProfileRepository.profile(actor)` already lays pending follows (`PendingFollows`) and the athlete's unsaved own edits over the AppView's answer. `BlockRepository.applyTo` lays local blocks and mutes over a view, and isn't `suspend`.
- `FollowRequestsRepository.followRequests()` has no such overlay, so an answer the AppView hasn't indexed comes back as pending.
- `FeedRepository.authorOutcomes(did)` exists from adopt-dataresult. It observes the cache under the key for `did`, while `refreshAuthor` writes under the DID the AppView returns. `ProfileScreen.actor` may be a handle.

**Shared pieces from adopt-dataresult:**
- `outcomeFlow`/`outcomeOf` in `core/data/.../OutcomeOf.kt`.
- `produceRetainedContentState(s)`, including the `Flow<P>` variants that restart on a parameter change.
- `ContentStateLayout` and `RefreshStatus` in `composeApp/.../ui/ContentStateLayout.kt`.
- `SegmentPresenter`, the reference shape.

**Error text.** `error-reasons` is applied and archived (`ui/error-messages`). `UiError(kind, reason: DataError?)` and `errorText(UiError)` are the one renderer, and `ContentStateLayout` and `RefreshStatus` call `errorText(UiError(errorKind, error))`. So load and action errors on these screens already say what failed and why; this change only routes them to the right field.

## Goals / Non-Goals

**Goals:**
- The seven screens load into a retained `ContentState`, with retry, a reason, and content kept through refreshes and coming back.
- Load errors and action errors are separate fields.
- Outcome reads that `load-form-screens` and `load-activity-detail` reuse.

**Non-Goals:**
- The screens the proposal leaves out.
- Removing the throwing reads. `activity()` is still called by ActivityDetail, EditActivity, CreateSegment, `AddingRecording`, `ActivityManager` and `CombineSuggestions`. `ownProfile()` is still called by the zone editors, EditProfile, import and `TrainingEdit`.
- Pending-answer bookkeeping in `FollowRequestsRepository`.

## Decisions

### Outcome reads are thin `outcomeFlow`s beside the old reads
```kotlin
fun activityOutcome(uri: AtUri): Flow<Outcome<ActivityView>> = outcomeFlow { activity(uri) }
fun ownProfileOutcome(): Flow<Outcome<OwnProfile>> = outcomeFlow { ownProfile() }
fun profileOutcome(actor: String): Flow<Outcome<ProfileView>> = outcomeFlow { profile(actor) }
fun followRequestsOutcome(): Flow<Outcome<FollowRequests>> = outcomeFlow { followRequests() }
```
- Wrapping keeps every side effect of the throwing read: `updateCached`, the comment lookups, the pending-follow overlay.
- `needsSignIn` stays on the data. It is an answer, not a failure.
- **Alternative:** presenters wrap the throwing reads themselves. Rejected, as in adopt-dataresult: the classification belongs in `core/data`, and the next two changes reuse these reads.

### Local edits are overlays over the loaded value
The producer owns `ContentState`, so a presenter can't write into it. Each keeps a small overlay instead and derives what it shows from `content.data` plus the overlay:

| Screen | Overlay | Dropped |
|---|---|---|
| Profile | the `ProfileView` an action returned | when the next load answers. `profile()` reapplies pending follows, and `applyTo` reapplies blocks, so a fresh answer already includes them |
| TrainingZones | the saved training visibility | when the next load answers. `ownProfile()` reads the PDS directly, so it's never behind |
| ThresholdHistory | the removed entries, folded with `TrainingHistory.remove` | when the next load answers, for the same reason |
| FollowRequests | answered DIDs: approved, or declined with the account | never: `rememberRetained` for the screen's life, applied over every answer |

**Why FollowRequests differs:** its read goes through the AppView, which can lag, and nothing in the repository remembers answers. Keeping the overlay on the screen is enough for "answered follow requests stay answered". An answered DID won't legitimately become pending again while the screen is open.

### Profile: two states; the header drives the page
```kotlin
val header = produceRetainedContentState<ProfileView?>(null, loadKey) {
    profiles.profileOutcome(screen.actor).map { o -> o.mapData(blocks::applyTo) }
}
val did = (shownProfile?.did?.value ?: screen.actor.takeIf { it.startsWith("did:") })
val activities = remember { snapshotFlow { did } }.filterNotNull()
    .produceRetainedContentState(persistentListOf<ActivityView>(), loadKey) { feed.authorOutcomes(it).map { … toImmutableList } }
```
- The page's loading, error and retry come from `header`. The activities render inside the loaded page with their own `ContentState`: a spinner, or a failure with Retry under a new `UiError.Kind.LoadActivities` ("Couldn't load activities").
- **Why the activities aren't quiet:** they're the page's main list. Today, their refresh failing fails the whole page.
- **Why `snapshotFlow` on the DID:** for a handle actor, the activities can only be keyed once the header resolves it. When coming back, the retained header gives it at once.
- Retry bumps `loadKey`, which restarts both.
- The follow-request count stays a `runSuspendCatching { }.getOrNull()` (supplementary).
- The blocked-athlete rule (no activities while blocking) is unchanged, and stays in the UI.

### CombinePicker: one stream for two sequential reads
```kotlin
produceRetainedContentState<CombinePick?>(null, loadKey) {
    outcomeFlow {
        val into = feed.activity(uri)
        CombinePick(into, candidatesFor(into, feed.ownViewsStartedBetween(start - WINDOW, start + WINDOW)).toImmutableList())
    }
}
```
- The second read needs the first's start time, and neither is any use alone, so they form one outcome. `outcomeFlow` is still `core/data`'s failure boundary.
- `CombinePick` is a small `@Immutable` holder in the presenter's file.
- `candidates` in State comes from it, and `Pick` needs `into`.

### RouteMap and MediaGallery
- Each loads `feed.activityOutcome(uri)` retained, keyed on `loadKey`, and gains a `Retry` event.
- The live rewrite overlay (`feed.rewrites.observe(uri)`) still applies over `content.data`. Everything derived (route, items, moderation) is computed from the shown activity as today.
- MediaGallery's block failure stays its action `error`.

### State shape, the same on all seven
- `content: ContentState<T>` replaces `isLoading`, the load half of `error`, and fields that were null until loaded.
- `error: UiError?` stays for action failures only.
- For each screen, `T` is the loaded value the UI needs:
  - RouteMap: a small `RouteMapContent(route, title, activityType)`.
  - MediaGallery: `ImmutableList<MediaItem>`.
  - Profile: `ProfileView?`, with a second `activities` state.
  - FollowRequests: `FollowRequestsContent(requests, declined, truncated)`.
  - TrainingZones: `TrainingZonesContent(defaults, sports, addableSports, …)`.
  - ThresholdHistory: `ImmutableList<ThresholdHistoryGroup>`.
  - CombinePicker: `ImmutableList<ActivityView>`.
- Derived content that depends on presenter state outside the stream (expanded groups, units, overlays) is rebuilt with `ContentState.map`-style copying in the presenter, so the State always holds what the UI draws. Where the library has no `map` on `ContentState`, use `copy(data = …)`.
- The UI picks its phase with `hasLoaded`, never `!= null`.
- FollowRequests gets `needsSignIn: Boolean` in place of the `SignInAgain` error. The UI keeps the Sign in again button.
- `ContentState` is `@Immutable`, and every `T` above is immutable, so `composeStabilityCheck` stays green.

### UI
- Each UI wraps its body in `ContentStateLayout(state.content, kind, onRetry)` and puts `RefreshStatus` over loaded content.
- The kinds: `LoadActivity` for RouteMap and MediaGallery, `LoadProfile` for Profile, TrainingZones and ThresholdHistory, `LoadFollowRequests`, and `LoadCombineCandidates`.
- MediaGallery's full-screen dark body uses the same layout for its first-load spinner and error.
- Action errors keep rendering with `errorText` where they do today.

## Risks / Trade-offs

- **[Coming back refetches]** Circuit restarts a paused record's producer on resume. Each screen reloads under `RefreshStatus` when the athlete returns. → Intended: the content shows at once. TrainingZones relies on this to reflect an editor's save, as it does today.
- **[Profile overlay vs a lagging AppView]** A follow could look undone if `PendingFollows` didn't cover a case. → Fix it in the repository, not with a longer-lived presenter overlay.
- **[FollowRequests overlay outlives a real change]** A DID answered and then re-requested while the screen is open wouldn't show. → Negligible, and the overlay ends with the screen.

## Migration Plan

- Nothing persisted or served changes. Room, lexicons and the AppView are untouched.
- The old reads stay for their other callers. `load-form-screens` and `load-activity-detail` move those callers.
- Rollback is reverting the change.
