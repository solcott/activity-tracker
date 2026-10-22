# Design

## Context

See proposal.md (Why) for what each screen gets wrong today.

The other screens' patterns, which this change follows:
- **One-shot loads:**
  - A screen holds a `ContentState`, or a group of them, from `produceRetainedContentState(s)` keyed on a `loadKey` that Retry bumps. `SegmentPresenter` groups the segment and the viewer's efforts with `contentStatesOf`.
  - The UI draws the phase with `ContentStateLayout`.
- **Reads:** reads that screens load return `Flow<Outcome<T>>`, built with `outcomeFlow`, and `Throwable.toDataError` classifies their failures.
  - `FeedRepository.activityOutcome` reads the activity.
  - `PrivacyZones.zonesOutcome()` reads the zones fresh. It answers `Outcome.Error(reason)` when they can't be read, and the last read this session otherwise.
- **Paged lists:**
  - They use `rememberRetainedPaging(key, idOf, onKeyChange) { cursor -> flow { emit(repo.xPage(cursor)) } }` over an `Outcome<Page<T, M>>` read, and `State` holds the `PagedState` whole.
  - Action errors are a separate `actionError: UiError?`.
  - The UI uses `LoadMoreAtEnd`, `pagedRefreshStatus` and `pagedFooter` (`composeApp/.../ui/PagedList.kt`).
- **Forms:** `rememberFormSeed` and `formContent(seeded)` keep a loaded form from being replaced by a later load (`PrivacyZonesPresenter`).

Data-layer facts the decisions rest on:
- `ActivityTrackStore.read` swallows every failure into null, the same answer as "no track". The route sweep and the owner's map rely on that, since it never throws.
- `SegmentStars.starred()` answers null for both `NoAccess` and a failed read. Its private `read {}` drops the exception.
- `SegmentRepository.segment` and `leaderboard` throw. `segment` throws an `XrpcException` with `error == "NotFound"` for a segment the AppView doesn't serve.

## Goals / Non-Goals

**Goals:**
- The three screens use the shared loading, paging and error pieces. No screen keeps a hand-rolled `isLoading`/`error` pair for a load.
- `CursorPaging` has no callers and is deleted.

**Non-Goals:**
- Changing what the AppView serves, or the leaderboard's filters (`segment-leaderboard-brackets`).
- A track that is still queued and not sent. It still reads as no track, as today.
- Caching any of these reads.

## Decisions

### CreateSegment loads one retained group of three
- `produceRetainedContentStates(contentStatesOf<ActivityView?, List<LocationSample>?, PrivacySettings?>(null, null, null), uri, loadKey) { … }` combines three reads:
  - `feed.activityOutcome(uri)`;
  - `activities.ownRouteOutcome(uri)`;
  - `privacy.zonesOutcome()`.
- `CreateSegmentState` gets `content: ContentState<Unit>`. Its status is the group's `combinedStatus()`, and its error is the first failure's reason with that read's `UiError.Kind`: `LoadActivity`, a new `LoadRoute`, or `LoadPrivacyZones`.
  - Retry bumps `loadKey`. `isLoading` and the load half of `error` go.
- `unavailable` is derived only once all three have loaded. It is `NoRoute` (track null or under two points), `NoSportGroup` or `AllHidden`.
  - `ZonesUnreadable` is removed, because unreadable zones are now the group's failure. Its string and the UI branch go too.
- **Seeding.** The screen is a form, so it seeds once with `rememberFormSeed` and `formContent(seeded)`. A later load (a retry after a later failure, or a change in the zones) doesn't move the stretch or the name.
  - The cut route is computed from the seeded zones, not the live ones, so a zones save elsewhere can't shift the stretch under the athlete.
  - *Alternative:* keep the route live on the zones. Rejected because the stretch is in meters along the route, and a re-cut route would make the same numbers point somewhere else.
- **Retained choices.** `start`, `end` and `duplicates` move from `remember` to `rememberRetained`. The name is already a saveable `rememberTextFieldState`. So coming back from a duplicate segment shows everything at once, as the spec's *Back from a duplicate* scenario requires.
- **Action errors.** Check and publish failures go to `actionError: UiError?` (`CheckSegment`, `PublishSegment`). The UI shows them under the form, never through `ContentStateLayout`'s failure.
- **The zones read.** `PrivacyZones` is injected for `zonesOutcome()`. It replaces `activities.privacySettings()`, which answers null with no reason. A fresh read on opening is also the right check before cutting a public line.

### The owner's track gets a read that can fail
- `ActivityTrackStore` gets `readOrThrow(activity): List<LocationSample>?`. It is the current body without the `runSuspendCatching`, so it answers null for no record, no blob, `NotFound` or `NoAccess`, and throws on a failed read or a blob that won't parse.
- `read` becomes `runSuspendCatching { readOrThrow(activity) }.getOrNull()`, so the sweep and `rememberOwnRoute` are unchanged.
- `ActivityRepository.ownRouteOutcome(uri) = outcomeFlow { tracks.readOrThrow(uri) }`.
- *Alternative:* make `read` throw and catch in each caller. Rejected because `read` documents "never throws", and the route sweep depends on it across many activities.

### StarredSegments pages a star list read once
- `SegmentStars.starredOutcome(): Outcome<List<SegmentStar>>` returns:
  - `Data` for `Found`, `Data(empty)` for `NotFound`;
  - `Error(DataError.Unknown(message = "No access to the self space"))` for `NoAccess`, the reason `PrivacyZones` uses;
  - `Error(e.toDataError())` for a thrown read.
  - The session's overrides are laid over the data as `starred()` does today. `starred()` stays only if a caller remains (`grep` at apply time).
- `SegmentRepository.segmentLookup(uri): Outcome<SegmentView?>` is `outcomeOf`. A `NotFound` `XrpcException` maps to `Data(null)` in the repository, so the presenter no longer knows XRPC error names.
- **Paging.** The presenter keeps a small retained `StarPages` holder and pages it with `rememberRetainedPaging(StarredSegmentsScreen, idOf = StarredSegment::uri, enabled = stars.canUse)`. Each page:
  - for the first cursor, reads `starredOutcome()` and keeps the list. An `Error` becomes the page's `Error`, so the first load fails with the reason.
  - slices `PAGE_SIZE` stars from the cursor offset, as today.
  - looks them up four at a time. The first `Error` among them becomes the page's `Error` (`Outcome<Page<StarredSegment, Unit>>`), so a later page fails alone and keeps the earlier pages.
  - Building the page stays in the presenter's `StarPages`, over the two outcome reads. `StarredSegment` is an `:app-screens` type, which `core/data` can't depend on.
- **Unstarring.** It calls `paging.update { it.afterRemoving(uri) }` on success. A failure sets `actionError`, which no longer gates `loadMore`.
- **No stars scope.** A session that can't use stars stays `SignInAgain`, shown as the screen's state rather than as a load failure, as today.
- *Alternative:* read every star's segment on the first page. Rejected because it is up to hundreds of AppView calls before anything shows.

### SegmentLeaderboard pages on its filters and clears on change
- `SegmentRepository.leaderboardPage(segment, period, scope, eBike, cursor): Outcome<Page<LeaderboardEntry, LeaderboardEntry?>>`. Its meta is the viewer's own entry, which every page carries.
- The presenter calls `key(period, scope, eBike) { rememberRetainedPaging(uri, idOf = { it.athlete.did }) { … } }`, so each board is its own retained list.
  - A new filter composes a fresh holder, which is empty and loading in the same frame, as the spec's *A new filter lists only its own items* requires. The meta goes with the holder, so the viewer's old entry leaves with the old board.
  - The filters stay `rememberSaveable`, so on return the key matches and the retained pages show at once.
  - *Alternative:* the filters as paging keys with `OnKeyChange.ClearItems`. Rejected after trying it: the paging restarts in an effect, one frame after the filters change, so that frame shows the new filter over the old board. `FeedPresenter` keys its filter with `key(…)` for the same reason.
- `viewerEntry` is `state.meta`, shown only once the state has an answer and the viewer isn't among `items`, as today.
- `State` holds `entries: PagedState<LeaderboardEntry, LeaderboardEntry?>` in place of `entries`, `isLoading`, `canLoadMore` and `error`. The `LoadMore` and `Retry` events stay. Retry calls `retry()`.
- *Alternative:* `KeepItems`, as search does. Rejected because the old board's ranks under a new heading are wrong data, not stale data.

### Ending the migration
- Delete `app/presenters/.../CursorPaging.kt` and `CursorPagingTest.kt`, and any suspend read the screens no longer call (`grep` for each).
- **`ui/content-loading`'s Purpose.** It goes straight into the main spec, since a delta can't carry an existing capability's Purpose (as in `tidy-long-requirements`).
  - Starred segments and the segment leaderboard join the paged lists, and create segment joins the form screens.
  - One sentence says every screen that loads content is the feed, the segment page or in one of these groups.
- **Rules doc.** `.agents/rules/compose-screens.md`'s "Cursor lists" bullet drops its last sentence about `rememberCursorPaging`. `.agents/rules/data-and-auth.md`'s "Screens not yet moved still call the throwing reads" sentence goes too.

## Risks / Trade-offs

- [`zonesOutcome()` reads the zones fresh each time the screen opens, where `privacySettings()` reused the session's read] → It is one `getRecord` on the athlete's own PDS. It fails over to the last read this session, so a flaky connection after one good read still loads.
- [Seeding the zones means a zone saved on another device while the screen is open isn't applied to the cut] → The same trade the other forms make. Nothing on this screen or the screens it opens saves zones, and opening the screen again reads them fresh.
- [The first page of stars still reads every star] → Unchanged from today. The PDS orders by a hash rkey, so ordering by date needs them all.
- [A new holder per filter shows a spinner on every filter tap] → The spec asks for it. Boards are one AppView query.

## Migration Plan

Each screen moves in its own commit with its tests, then the deletion and the docs. Nothing persists, so a rollback is a revert.
