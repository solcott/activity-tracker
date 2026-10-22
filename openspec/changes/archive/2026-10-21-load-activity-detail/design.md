# Design

## Context

See proposal.md (Why) for the motivation, and `specs/ui/content-loading` for the behaviour. This section covers only the current state that shapes the approach.

**The page's main load** (`ActivityDetailPresenter`):
- `LaunchedEffect(loadKey)` sets `loaded: CountedActivity?` and `error: UiError?`. `detailState` turns them into `Loading`, `Error` or `Loaded`, and `Loaded` carries 30-odd fields.
- `CountedActivity` pairs the view with `commentMoves`: how far the viewer's comment sends, deletes, hides and unhides on this screen have moved the count since the load. It is one value so that a reload replaces both. `FeedRepository.activity` already counts changes the AppView hasn't caught up with.
- Two overlays are laid over the loaded view as it shows: the live rewrite (`feed.rewrites.observe(uri)`) and the local likes (`localLikes.overrides`).
- `loadKey` is bumped by Retry, by `AwaitIndexed`, and by the owner's ungroup, member removal and data-source choice, which reload the page.

**`AwaitIndexed`:**
- It bumps `loadKey` every 1.5 s, up to 10 times, while the load failed for an activity this device wrote (`rewrite != null`) or `suggestOnOpen`.
- It doesn't look at why the load failed. So offline it spins for 15 s.
- Each try first clears `error` and then sets it again, so the page alternates between the error and the spinner.
- The AppView answers a missing activity with XRPC `NotFound`, which `toDataError` turns into `DataError.Api(code = "NotFound")`.

**Supplementary loads.** Each is a `LaunchedEffect` into a plain `remember`, so none survives leaving the screen:

| Load | Keyed on | On failure today |
|---|---|---|
| Charts (`feed.streams`, then `ChartsBuilder.buildOffMain`) | `loadKey`, units | kept the previous value, quietly |
| Segment efforts (`rememberSegments`) | `loadKey` | empty, quietly |
| Sibling count (`rememberManage`, a recording's main activity) | `recordingOf`, `loadKey` | null, quietly. Ungroup is then disabled |
| Data sources (`rememberDataSources`) | combined URI, `loadKey`, `readKey` | `LoadDataSources` shown with RetrySources |
| Combine suggestion | activity URI | null, quietly |
| Owner's track (`activities.ownRoute`) | activity URI | null. `ActivityTrackStore.read` already swallows failures |
| Original file info (`exporter.originalInfo`) | activity URI | null. Already swallowed |
| Likes list (`rememberLikesList`) | shown | `LoadLikes` |

**Comments** (`rememberComments`) re-key on `loadKey` with their own generation guard. `adopt-library-paging` replaces them.

**Shared pieces, from adopt-dataresult and `load-activity-and-profile-screens`:**
- `FeedRepository.activityOutcome(uri)` and `outcomeFlow`.
- `produceRetainedContentState`, including the `Flow<P>` form, which restarts per parameter value.
- `ContentStateLayout` and `RefreshStatus`.
- The convention, from that change's design, that derived content goes into the state with `copy(data = …)`.

The library has no retry operator for outcome streams.

## Goals / Non-Goals

**Goals:**
- One retained `ContentState` drives the page's phase. The supplementary loads are retained alongside it and restart with it.
- `AwaitIndexed` becomes a stream operator that only retries `NotFound`, and keeps the page loading while it waits.
- The page's existing tests keep their meaning. They read the loaded page through `content` instead of a cast.

**Non-Goals:**
- Comments' paging, replies and generation guard. `adopt-library-paging` moves those. Comments stay in `remember` and re-key on `loadKey`.
- `rememberApprovesFollowers`, reporting and moderation reveal. Those producers aren't loads of this page.
- The throwing reads in `core/data` (`activity()`, `streams()`, `likes()`) and their other callers.

## Decisions

### State: one `ContentState` of the derived page
```kotlin
@Immutable data class ActivityDetailState(
    val content: ContentState<ActivityDetailPage?>,
    val eventSink: (ActivityDetailEvent) -> Unit,
)
@Immutable data class ActivityDetailPage(/* today's Loaded fields, eventSink included */)
```
- The presenter collects `activityOutcome(uri)` (with the index wait) into `ContentState<ActivityView?>`. It then builds the page from the shown view and every supplementary state, and wraps it as `ContentState(page, content.origin, content.status)`. `copy` can't change the type. So the status and origin are the main load's, and the data is what the UI draws.
- `Loaded` is renamed `ActivityDetailPage` and keeps its `eventSink`. The UI's ~30 helpers and the tests' `loaded.eventSink(…)` calls then only change type. Dropping the sink would have touched every one of them for no behaviour.
- **Alternative:** keep the sealed interface and add a `refresh` field to `Loaded`. Rejected: it keeps the null-or-not checks the content-loading convention replaces, and a reload couldn't show the failure over the content without a second error field.
- **Alternative:** `content: ContentState<ActivityView?>` and the page as a separate nullable field. Rejected: two sources of truth for "has it loaded".

### Comment moves are an overlay tied to the view they moved
```kotlin
private class CommentMoves(val base: ActivityView, val moves: Int)
```
- An action adds to `moves` when `base === content.data`. Otherwise it starts again from 0 on the current view.
- The shown count applies `moves` only while `base` is still the loaded view.
- A fresh emission is a new object, so the moves drop with it, which is today's "a reload replaces both". Identity rather than equality matters here: an equal view answered by a reload already counts the changes, and must not be moved twice.
- The rewrite and like overlays are unchanged. They are laid over `content.data`.

### The index wait is a stream operator
```kotlin
internal fun <T> Flow<Outcome<T>>.awaitIndexed(justWritten: suspend () -> Boolean): Flow<Outcome<T>>
```
- It lives in `detail/AwaitIndexed.kt`, replacing the composable.
- On an `Outcome.Error` whose error is `DataError.Api` with code `NotFound`, while `justWritten()` is true, it emits nothing, waits `INDEX_WAIT_MILLIS`, and collects the upstream again, without its leading `Loading`. After `MAX_INDEX_WAITS` it lets the error through.
- Any other error, or a `NotFound` for an activity not written here, passes at once.
- The constants are today's: 10 tries, 1.5 s apart.
- `justWritten` is `screen.suggestOnOpen || feed.rewrites.observe(uri).first() != null`, read when the error comes. Reading it then avoids the race with the first composition, where `collectAsState(null)` still says no rewrite.
- A Retry bumps the key, which re-collects the operator, so a manual retry gets a fresh 10 tries. Today the count was never reset.
- **The extras restart once after a late index.** They ran alongside the first try, while the AppView had nothing for the activity, so its streams, efforts and comments came back empty or failed. Bumping `loadKey` used to reload them with every try. Now the operator calls `indexed()` when a try after the first answers. That bumps `indexedLate`, and the extras are keyed on `loadKey + indexedLate`, so they read again exactly once. The web check of a manual save caught this: its comments said they couldn't load.
- **Alternative:** Flow's `retryWhen`. Rejected: `outcomeFlow` never throws, because failures are values, so `retryWhen` never sees them.
- **Alternative:** keep the composable, and feed it `content.errorOrNull`. Rejected: bumping the key from outside flips the status to Failed and then to Loading on each try, which is the flicker the proposal removes.

### Supplementary loads: one retained `ContentState` each, keyed on `loadKey`
Every one restarts when the page reloads. Being retained, each shows at once when the athlete comes back.

| Load | Producer shape | Shown as |
|---|---|---|
| Charts | `snapshotFlow { units }.produceRetainedContentState(null, loadKey) { outcomeFlow { feed.streams(uri)?.let { s -> s to ChartsBuilder.buildOffMain(s, units) } } }` | `data`: charts and streams, null without. Quiet |
| Segment efforts | `produceRetainedContentState(persistentListOf(), loadKey) { outcomeFlow { … } }` | `data`. Quiet |
| Sibling count | `snapshotFlow { recordingOf }.filterNotNull().produceRetainedContentState(null, loadKey) { … }` | `data`, null until known. Quiet |
| Combine suggestion | `snapshotFlow { ownUri }.produceRetainedContentState(null, loadKey) { … }` | `data`, minus the dismissed overlay. Quiet |
| Owner's track | same shape on the owner's URI | `data`. Quiet; the read already returns null on failure |
| Original file | same shape on the owner's URI | `data`. Quiet |
| Data sources | `snapshotFlow { combinedUri }.produceRetainedContentState(null, loadKey, readKey) { … }` | `DataSources` from `data`, with `isLoading = !hasLoaded && status is Loading`, and `error` the load failure (`LoadDataSources`) unless a choice failed (`SaveDataSource`) |
| Likes list | `produceRetainedContentState(emptyList(), shownCount) { if shown outcomeFlow { feed.likes(uri) } else emptyFlow() }` | `LikesList` from `data`, with `isLoading` while it loads and `error` from `errorOrNull` (`LoadLikes`) |

- **Quiet** means the producer keeps the last data on failure and the page never reads `status`. That is the library's own behaviour for a failed refresh, so "kept the previous value" holds with no extra code.
- **Owner-only loads** take their parameter from the shown activity, so they start once the page has loaded. They emit nothing for someone else's activity (`filterNotNull`).
- **Action results are overlays**, as in the previous change. A `setSources` result shows until the next load answers. A dismissed suggestion stays hidden for the screen's life.
- **Alternative:** group the reads that share a key with `combineOutcomes`. Rejected: they have different gates (owner-only, combined-only, units), and grouping would make one quiet failure decide another's status.

### UI
- `ActivityDetailUi` wraps its body in `ContentStateLayout(state.content, UiError.Kind.LoadActivity, onRetry = { sink(Retry) })`. Inside, it draws the page from `content.data`.
- It puts `RefreshStatus(state.content, …)` at the top of the page's list, in both the column and the paned layouts.
- The top bar's menus read `content.data`, so they show over a refresh and a failed refresh as well.

## Risks / Trade-offs

- **[Comments restart on coming back]** They stay in plain `remember` until `adopt-library-paging`. Coming back reloads them under the retained page, and they show their own loading below it. → Accepted for one change. The activity and its count show at once, which is what the spec requires.
- **[Coming back refetches everything]** Circuit restarts a paused record's producers on resume, so about 8 reads go out when the athlete comes back. → This is today's cost too, but now under the content rather than over a spinner.
- **[Identity-based comment moves]** A producer that re-emitted the same view instance would keep the moves. → `outcomeFlow` decodes a fresh view on each call, and the rewrite and like overlays are applied after the base check, never before.
- **[Index wait reads the rewrite once per error]** An edit saved after the first error but before a later one counts from then on. → Matches today, where `written` was live.

## Migration Plan

- Nothing persisted or served changes. Room, lexicons and the AppView are untouched.
- Rollback is reverting the change.
