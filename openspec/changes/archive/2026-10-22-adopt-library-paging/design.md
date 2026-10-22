# Design

## Context

See proposal.md (Why) for what the current pagers get wrong.

Four screens move:
- BlockList, FollowList and AthleteSearch page through `rememberCursorPaging` (`app/presenters/.../app/CursorPaging.kt`). Each `State` flattens it into `accounts`/`results`, `isLoading`, `canLoadMore`, `error` and, for search, `searched`. Each UI requests the next page from a `SideEffect` when the list reaches its end.
- The activity page's comments use `rememberComments` (`app/presenters/.../detail/Comments.kt`). It is a `remember`ed `Pages` with a generation guard, a `firstLoading` flag, per-thread `Replies` and its own load-more/retry through `SingleFlight`. It also lays the viewer's own changes (`LocalComments`, `localBlocks`, sent comments and replies) over the served pages. It reloads on `extrasKey`, which is the page's `loadKey` plus `indexedLate`.

The library (`~/projects/kmp-dataresult`, spec `uistate/paging`) provides two pieces:
- `PagedState<T, M>` in `uistate`, which is `@Immutable`.
- `rememberRetainedPaging(vararg keys, idOf, onKeyChange, enabled, initial, load)` in `uistate-circuit`, which returns a `Paging` with `state`, `loadMore()`, `retry()`, `refresh()` and `update {}`.

`Paging.drive` restarts on keys or `enabled`:
- A new key with an `initial` page re-seeds from it, and one without restarts the fold under `onKeyChange`.
- Coming back with the same keys re-requests only the requests the departure interrupted.

The repositories return lexicon outputs from suspend functions. `outcomeOf` (`core/data/.../OutcomeOf.kt`) turns a suspend read into an `Outcome` with a `DataError` reason.

Two screens keep `CursorPaging`: SegmentLeaderboard and StarredSegments. They move in `load-segment-screens`.

## Goals / Non-Goals

**Goals:**
- The four screens hold library paged state and render it through one shared UI piece. Their tests pin the behaviour in `specs/`.
- Comments keep every own-change overlay they have today, on whichever page or thread a change lands.

**Non-Goals:**
- Deleting `CursorPaging`, and the segment screens (`load-segment-screens`).
- Paging the likes list, which loads in one request.
- Cached first pages. These lists read the network only; the library supports a cached page later without new API.
- Pull to refresh on these lists. Only the comments reload, with the activity page.

## Decisions

### `State` holds `PagedState` whole
- Each list's `State` gets one `PagedState<T, M>` field (`accounts`, `results`, `comments.threads`). It replaces the flattened `items`/`isLoading`/`canLoadMore`/load `error`/`searched` fields, the same way screens hold `ContentState` whole.
- `PagedState` is `@Immutable`, so `:app-screens`' strict stability check accepts it.
- The UI reads `hasLoaded`/`hasAnswer`, `refresh`, `append`, `canLoadMore` and `items` from it.
- Action errors (a failed unblock, unmute or follower removal) stay a separate `actionError: UiError?`.
- *Alternative:* keep the flattened fields and fill them from `PagedState`. Rejected because it re-derives the fold's rules in every presenter, and the UI loses the refresh/append split it needs to draw a footer.

### Page reads return `Outcome<Page<T, M>>` from `core/data`
- New reads sit beside the suspend ones, each `outcomeOf { … }` mapped to the library's `Page`:
  - `BlockRepository.blocksPage`/`mutesPage`;
  - `ProfileRepository.followersPage`/`followsPage`, with `M` = the subject `ProfileViewBasic`;
  - `AthleteSearchRepository.searchPage`;
  - `FeedRepository.commentsPage` (threads) and `repliesPage` (with `root`).
- `core/data` therefore depends on `uistate` for `Page`. It already uses `dataresult` for `Outcome`.
- The presenter's loader is `flow { emit(repo.xPage(cursor)) }`.
- BlockList's mapping to `BlockListAccount` (removable/onBluesky) stays in the presenter and maps the page's items.
- The suspend reads stay where other callers use them. Check with `grep` at apply time, and drop any left unused.
- *Alternative:* `runSuspendCatching` in the presenter and map to `Outcome` there. Rejected because `outcomeOf` is the one place that turns exceptions into `DataError`s, as `error-reasons` set up.

### BlockList and FollowList
- They use `rememberRetainedPaging(screen, idOf = …)` with the default `KeepItems`.
- A successful remove calls `paging.update { it.afterRemoving(did) }`, as today.
- FollowList's `subject` comes from `paging.state.meta`, which retires the `var subject` the loader set as a side channel.
- A failed remove no longer gates `loadMore`; the spec's *Unblock fails* scenario covers this. The Retry event retries the load only, and a dismiss or the next action clears the action error.

### AthleteSearch
- It uses `rememberRetainedPaging(trimmed, idOf = ProfileViewBasic::did, onKeyChange = KeepItems, enabled = trimmed.isNotEmpty())`.
- The 300 ms debounce stays inside the first page's flow (`flow { delay(SEARCH_DEBOUNCE); emit(…) }`). A new keystroke changes the key, which cancels the delay, so the debounce is unchanged.
- "Searching" is `refresh is Loading` while the query isn't blank.
- "Nobody matched" is `hasAnswer && items.isEmpty() && refresh !is Loading`.
- The query stays in `rememberTextFieldState()`, which is saveable, so the key matches on return and the retained pages show at once.

### Comments: one holder for threads, one per thread for replies
- **Threads:** `rememberRetainedPaging(activity, extrasKey, idOf = ThreadView::uri, onKeyChange = KeepItems)` replaces `Pages`, its generation guard, `firstLoading` and the `LoadMoreComments`/`RetryComments` flights.
  - A reload (`extrasKey` changes) is a key change that keeps the threads until the new first page lands.
  - Being retained, the pages survive a trip to a commenter's profile.
- **Replies:** inside `key(thread.uri)`, each served thread gets `rememberRetainedPaging(thread.uri, settledKey, idOf = CommentView::uri, initial = Outcome.Data(Page(thread.replies, Unit, thread.repliesCursor)))`.
  - `settledKey` is the `extrasKey` whose first page has settled. The presenter advances it to `extrasKey` once the threads' `refresh` is no longer loading.
  - So a reload re-seeds every thread's replies from the newly served thread only when that page arrives. Until then, the extra replies stay, which the spec's *Reload* scenario requires.
  - The holder with `initial` requests nothing until "View more", and a key change with `initial` re-seeds from it (`Paging.drive`).
- **Overlays:** `shownThreads` stays. It now reads the threads from the threads holder and each thread's replies from its holder, and lays `LocalComments.applyTo`, `localBlocks.applyTo` and the sent replies over them as today.
  - `moreReplies` is `replyCount - shown`, at least 1 while the replies holder isn't at its end.
  - `repliesState` maps from the replies holder's `append` status.
  - `LoadMoreReplies(uri)` calls that thread's `retry()` if its last page failed, else `loadMore()`.
- **Contract:** `PagedState`'s constructor is internal, and `updated {}` keeps the item type, so app code can't build a `PagedState<CommentThread, …>` from the overlaid threads.
  - `CommentsPaging` keeps `items: ImmutableList<CommentThread>`, the overlaid threads.
  - Its `isLoading`/`endReached`/`error` become `refresh: LoadStatus`, `append: LoadStatus`, `hasAnswer`, `canLoadMore` and `endReached`, all copied from the threads holder's state.
  - The UI reads those statuses in its own tail item (see the shared UI decision).
- *Alternative:* one holder for threads with replies folded into it. Rejected because the library's design gives nested lists one holder per parent, and folding would make the replies' failures look like the threads' failures.

### One shared paged-list UI
- `composeApp/.../ui/PagedList.kt` holds the pieces, beside `ContentStateLayout`:
  - `LoadMoreAtEnd(listState, canLoadMore, onLoadMore)` requests the next page once the last row shows. It replaces each UI's `SideEffect(atEnd, canLoadMore, isLoading)`.
  - `LazyListScope.pagedRefreshStatus(state, kind, onRetry)` goes above the items once the list has answered. It shows a small spinner while a refresh or a new key's first page loads, or that load's failure with Retry.
  - `LazyListScope.pagedFooter(state, kind, onRetry, empty)` goes below the items. Before an answer it shows a spinner or the failure with Retry, and nothing while the list is disabled. After an answer it shows `empty` when nothing is listed, and a footer spinner or the append failure with Retry. Each has an overload that takes the statuses apart instead of a whole `PagedState`.
- BlockList, FollowList and AthleteSearch use all three. Search shows its prompt instead of the footer while the query is blank.
- The comments section keeps its own tail item and `LoadMoreNearTheEnd`, whose fixed keys decide when the next page is wanted. Its `CommentsLoading` now reads `refresh`/`append` and shows a failure with `errorText(UiError(LoadComments, reason))`.

### Rules doc
- `.agents/rules/compose-screens.md`'s "Cursor lists" bullet changes to `rememberRetainedPaging`, with `State` holding `PagedState`, and points at the shared UI piece.
- It notes that `rememberCursorPaging` remains only for the two segment screens until `load-segment-screens`.

## Risks / Trade-offs

- [The kmp-dataresult snapshot on GitHub Packages may predate paging] → Task 1 checks the published `0.1.0-SNAPSHOT` for `Paging`. If it is missing, publish a snapshot from `~/projects/kmp-dataresult` main, or build with `-Pdataresult.dir`; don't vendor the code.
- [A paused record stops paging silently in a multi-pane layout, the hazard noted in `Paging.kt`] → The activity page is the only one shown beside another pane, and its `produceRetainedContentState`s already share the hazard without `ProvideRecordLifecycle`. Note it, and don't fix it here. A presenter test can't see it, so the hot-reload check exercises comments on a wide window.
- [Retained replies holders for threads that leave the list (blocked, deleted) stay in the retained registry] → `key(thread.uri)` drops a holder when its thread stops composing, and the registry releases it with the screen. That is acceptable for at most a few hundred threads.
- [`settledKey` adds presenter state beside the library holder] → It is one retained `Int` that advances in a `SideEffect`, and a replies test with a gated reload covers it.
- [Removing the action error's gate on load-more changes behaviour] → The spec now asks for this (*Unblock fails*), and the presenter tests assert it.

## Migration Plan

Each screen moves in its own commit, with its tests. Nothing persists, so a rollback is a revert.
