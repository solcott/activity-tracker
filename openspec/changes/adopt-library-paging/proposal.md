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

**Decision (explore session):** paging goes into kmp-dataresult as a library module, so the app has one load pattern everywhere. This change is the app's side, and it records what the library must provide.

This is step 5 of moving the rest of the app onto kmp-dataresult.

## What Changes

**In kmp-dataresult** (separate work in that repository, which this change depends on):
- **A paged state in `uistate`.** A page is just another emission: a `PagedState<T, M>` folds `Outcome<Page<T, M>>` the way `ContentState` folds `Outcome<T>`. It holds:
  - the items so far, de-duplicated by id;
  - `meta`, the first page's header (the leaderboard's viewer entry, the follow list's subject);
  - `origin`;
  - separate `refresh` and `append` `LoadStatus`es;
  - `endReached`.
- **A retained `Paging` holder in `uistate-circuit`** with `loadMore()`, `retry()` (which retries what failed: a refresh, or only the next page), `refresh()` and `update {}` for optimistic edits.
- **Rules it must follow:**
  - A failed refresh keeps the items.
  - A failed page keeps the earlier pages.
  - A new key either keeps the old items until the new first page settles (search) or clears them (filters), as the caller chooses.
  - It must satisfy the library's Swift-export rules (sealed classes, no Compose types in `uistate`).
- **Open questions for the library design:**
  - Should the cursor type be generic or `String`?
  - Should a cached (Room) first page be supported, so the feed could page later?
  - Does nested reply paging need first-class support, or one `Paging` per thread?
  - Should keeping or clearing items on a new key be a parameter or two entry points?

**In this app:**
- **Paged reads as outcomes.** Repository page reads return `Outcome<Page>` through `outcomeOf`.
- **These screens move to library paging:**
  - BlockList (blocks and mutes);
  - FollowList (followers and following, with the subject as meta);
  - AthleteSearch (keeps the old results while a new query loads; the presenter keeps its 300 ms debounce);
  - the activity page's comments and replies.
- **`CursorPaging` and its test are removed** once nothing uses them. The segment screens' paged lists (leaderboard, starred segments) move in `load-segment-screens`.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `ui/content-loading`: this change extends its requirements to paged lists. It adds what paging needs: a failed later page keeps the earlier pages and retries only itself, and a list reached again shows its pages at once.
- `feed/comments`: its requirement that a failed page keeps what's listed and offers to try again stays. Comments move onto the library pager, so check its wording while writing the specs.
- Search, block and follow list specs (`graph/blocks`, `graph/mutes`, `graph/follow-requests`): only where they describe loading or paging. Confirm while writing the specs.

## Impact

- **kmp-dataresult:** new API in `uistate` and `uistate-circuit`, with its ABI dumps, tests and README.
  - That design should be captured in the library repository, which has no OpenSpec today: either set OpenSpec up there, or write a design doc.
  - Then a snapshot or release that includes the new API.
- **`core/data`:** page reads for blocks, mutes, followers, follows, athlete search and comments.
- **`app/presenters`:**
  - BlockList, FollowList and AthleteSearch.
  - The activity page's `Comments.kt` / `CommentActions.kt`.
  - Their tests: `BlockListPresenterTest`, `FollowListPresenterTest`, `AthleteSearchPresenterTest` and the comments and replies tests.
- **Removed:** `CursorPaging.kt` and `CursorPagingTest`.
- **`composeApp`:** paged list UIs render refresh and append states (a footer spinner, an inline retry for a failed page).
- **Depends on:**
  - the kmp-dataresult paging module;
  - `error-reasons`;
  - `load-activity-detail` (for comments).
