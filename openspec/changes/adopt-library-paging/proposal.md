# Proposal

## Why

Paged lists load through the app's own `CursorPaging` (`app/presenters/.../CursorPaging.kt`). It behaves against the rules `ui/content-loading` sets for everything else:

| | CursorPaging today | `ui/content-loading` |
|---|---|---|
| A failed first page or retry | Empties the list | A failed refresh keeps the content |
| A later page fails | `retry()` reloads from page 1, losing the earlier pages and the scroll position | Retry only what failed |
| Coming back to the list | Reloads from scratch (`remember`) | Loaded content survives coming back |
| Errors | A raw `Throwable` | `DataError` reasons |

The activity page's comments use a second, hand-rolled pager: generation guard, first-load flag, load-more and per-thread replies. It has the same gaps.

**Decision (explore session):** paging goes into kmp-dataresult as a library module, so the app has one load pattern everywhere. This change is the app's side.

This is step 5 of moving the rest of the app onto kmp-dataresult.

## What Changes

**In kmp-dataresult** (done): the library's `add-paging` change ([solcott/kmp-dataresult#27](https://github.com/solcott/kmp-dataresult/pull/27), archived as `openspec/changes/archive/2026-10-21-add-paging/`) added the [`uistate/paging`](https://github.com/solcott/kmp-dataresult/blob/main/openspec/specs/uistate/paging/spec.md) spec:
- `Page<T, M>` and `PagedState<T, M>` in `uistate`, which fold `Outcome<Page<T, M>>` the way `ContentState` folds `Outcome<T>`, with separate `refresh` and `append` `LoadStatus`es.
- `rememberRetainedPaging` in `uistate-circuit`, which returns a retained `Paging` with `loadMore()`, `retry()` (only what failed), `refresh()` and `update {}`. Its loader returns `Flow<Outcome<Page<T, M>>>`, and a one-shot read is `flow { emit(...) }`.

Its design settled the open questions:
- **Cursor:** `String` (`Page.next: String?`).
- **Cached first page:** supported through the same fold. Each page's flow may re-emit, so the feed could page later without new API.
- **Nested replies:** one `Paging` per thread, started from the thread's first replies with `initial`.
- **New key:** `onKeyChange: OnKeyChange`, defaulting to `KeepItems`; filters pass `ClearItems`.

**In this app:**
- **Paged reads as outcomes.** Repository page reads return `Outcome<Page<T, M>>` (the library's `Page`) through `outcomeOf`.
- **These screens move to library paging:**
  - BlockList (blocks and mutes);
  - FollowList (followers and following, with the subject as meta);
  - AthleteSearch (keeps the old results while a new query loads; the presenter keeps its 300 ms debounce);
  - the activity page's comments and replies (one `Paging` per thread, as the library's design describes).
- **`CursorPaging` and its test are removed** once nothing uses them. The segment screens' paged lists (leaderboard, starred segments) move in `load-segment-screens`.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `ui/content-loading`: this change extends its requirements to paged lists. It adds what paging needs: a failed later page keeps the earlier pages and retries only itself, and a list reached again shows its pages at once.
- `feed/comments`: its requirement that a failed page keeps what's listed and offers to try again stays. Comments move onto the library pager, so check its wording while writing the specs.
- Search, block and follow list specs (`graph/blocks`, `graph/mutes`, `graph/follow-requests`): only where they describe loading or paging. Confirm while writing the specs.

## Impact

- **kmp-dataresult:** the paging API is merged but not yet published. This change needs a snapshot or release that includes it.
- **`core/data`:** page reads for blocks, mutes, followers, follows, athlete search and comments.
- **`app/presenters`:**
  - BlockList, FollowList and AthleteSearch.
  - The activity page's `Comments.kt` / `CommentActions.kt`.
  - Their tests: `BlockListPresenterTest`, `FollowListPresenterTest`, `AthleteSearchPresenterTest` and the comments and replies tests.
- **Removed:** `CursorPaging.kt` and `CursorPagingTest`.
- **`composeApp`:** paged list UIs render refresh and append states (a footer spinner, an inline retry for a failed page).
- **Depends on:**
  - a kmp-dataresult build with `uistate/paging`;
  - `error-reasons`;
  - `load-activity-detail` (for comments).
