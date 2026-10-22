# Tasks

## 1. Library build with paging

- [x] 1.1 Confirm the published `io.github.solcott:uistate-circuit:0.1.0-SNAPSHOT` contains `rememberRetainedPaging` and `PagedState`. Inspect the resolved jar, or compile a throwaway import with `./gradlew :app-presenters:compileKotlinJvm`. If they're missing, publish a snapshot from kmp-dataresult main, or build with `-Pdataresult.dir=$HOME/projects/kmp-dataresult`, and record which in the commit message. Verify: `:app-presenters:compileKotlinJvm` resolves `io.github.solcott.uistate.circuit.rememberRetainedPaging`.
- [x] 1.2 Add `dataresult-uistate` to `core/data`'s dependencies for `Page`, then run `./gradlew format`. Verify: `./gradlew :core-data:compileKotlinJvm` passes.

## 2. Page reads as outcomes (`core/data`)

- [x] 2.1 Add `blocksPage`/`mutesPage` to `BlockRepository`, `followersPage`/`followsPage` to `ProfileRepository` (meta = subject) and `searchPage` to `AthleteSearchRepository`. Each returns `Outcome<Page<T, M>>` through `outcomeOf`. Verify: jvm tests with `MockEngine` cover each read's items, cursor, meta, a `DataError.Network` on a connection failure, and a server error.
- [x] 2.2 Add `commentsPage(activity, cursor)` (threads) and `repliesPage(activity, root, cursor)` to `FeedRepository`, keeping `localComments.report` on every page. Verify: a jvm test asserts that both report what they list and map failures to `DataError`s.
- [x] 2.3 Remove any suspend page read left with no caller (`grep` for each). Verify: `./gradlew :core-data:jvmTest` passes.

## 3. Shared paged-list UI (`composeApp`)

- [x] 3.1 Add `LoadMoreAtEnd`, `pagedRefreshStatus` and `pagedFooter` in `ui/PagedList.kt` as design.md describes:
  - before an answer, a first-load spinner, or the failure with Retry;
  - refresh feedback over the items;
  - a footer spinner, or the append failure with Retry, and nothing at the end;
  - load-more near the end while `canLoadMore` holds.

  Use `ui/adaptive` components and `errorText`, and add strings for "couldn't load more" to `strings.xml` if no existing `UiError.Kind` covers it. Verify: `./gradlew :composeApp:compileKotlinJvm composeStabilityCheck` passes.

## 4. BlockList

- [x] 4.1 Change `BlockListState` to hold `accounts: PagedState<BlockListAccount, Unit>` and `actionError: UiError?`, dropping `isLoading`/`canLoadMore`/`error`. Verify: `./gradlew :app-screens:composeStabilityCheck` passes in strict mode.
- [x] 4.2 Move `BlockListPresenter` to `rememberRetainedPaging(screen, …)` over `blocksPage`/`mutesPage`. Use `update {}` after a remove, keep Retry for loads only, and let a failed remove leave load-more working. Verify: `BlockListPresenterTest` covers:
  - the first-load failure and its retry;
  - a failed page 3 that keeps pages 1–2, with Retry requesting only page 3;
  - a failed unblock that still loads the next page;
  - the Bluesky-only entry staying after unblock;
  - return-shows-at-once.
- [x] 4.3 Render `BlockListUi` through `PagedStateLayout`/`PagingFooter`. Verify: it compiles, and the desktop hot-reload check in 8.2 shows the first-load, empty and footer states.

## 5. FollowList

- [x] 5.1 Change `FollowListState` to hold `accounts: PagedState<ProfileViewBasic, ProfileViewBasic>` (meta = subject), keeping `canRemove`/`removing` and adding `actionError`. Take `subject` from `meta`. Verify: strict stability passes.
- [x] 5.2 Move `FollowListPresenter` to `rememberRetainedPaging(screen, …)` over `followersPage`/`followsPage`, with `rememberCanRemove` reading the subject from `meta`. Verify: `FollowListPresenterTest` covers:
  - the subject from the first page;
  - a failed later page with Retry requesting only that page;
  - remove-follower success with `update {}`;
  - a remove failure that leaves paging working;
  - the *Back to a followers list* scenario (three pages shown, none re-requested).
- [x] 5.3 Render `FollowListUi` through the shared pieces. Verify: it compiles, and it is covered by 8.2.

## 6. AthleteSearch

- [x] 6.1 Change `AthleteSearchState` to hold `results: PagedState<ProfileViewBasic, Unit>`, dropping `isLoading`/`canLoadMore`/`searched`/`error`. Verify: strict stability passes.
- [x] 6.2 Move `AthleteSearchPresenter` to `rememberRetainedPaging(trimmed, …, onKeyChange = KeepItems, enabled = trimmed.isNotEmpty())`, with the 300 ms debounce inside the first page's flow. Verify: `AthleteSearchPresenterTest` covers:
  - the debounce, so a single request follows fast typing;
  - "ann" results kept while "anna" loads, with no "ann" page appended after it;
  - a blank query making no request and showing no loading;
  - nobody matched;
  - an offline error with a reason;
  - a failed page 2 with Retry.
- [x] 6.3 Render `AthleteSearchUi` through the shared pieces: a searching indicator over old results, and nobody matched only after an answer. Verify: it compiles, and it is covered by 8.2.

## 7. Comments and replies

- [x] 7.1 Change `CommentsPaging` (in `ActivityDetailScreen.kt`) to `items` plus `refresh`, `append`, `hasAnswer`, `canLoadMore` and `endReached`, as design.md describes. Keep `RepliesState`, mapped from each thread's replies holder. Verify: strict stability passes.
- [x] 7.2 In `Comments.kt`, replace `Pages`, the generation guard, `firstLoading` and the `LoadMoreComments`/`RetryComments` flights with a threads holder keyed on `(activity, extrasKey)` with `KeepItems` over `commentsPage`. Keep `shownThreads`'s overlays and `Comments.listed`/`isSent`/`countedBy` working from the holder's items. Verify: `ActivityDetailCommentsTest` covers:
  - the first page;
  - scroll-to-end loading a page once;
  - a failed next page with Retry requesting only it;
  - first-page failure with the activity still shown;
  - a reload keeping the threads until its first page lands;
  - return-shows-at-once;
  - sent, deleted and hidden comments on later pages, as today.
- [x] 7.3 Give each thread a replies holder inside `key(thread.uri)`. Key it on `(thread.uri, settledKey)` and seed it with `initial` from the served thread. `settledKey` advances once the threads' refresh settles. Map `LoadMoreReplies` to `retry()` after a failure, else `loadMore()`. Verify: `ActivityDetailRepliesTest` covers:
  - view more;
  - many pages until the end;
  - a failed page kept, with Retry requesting only it;
  - extra replies kept through a gated reload until it lands, then replaced;
  - sent replies at the thread's end.
- [x] 7.4 Render the comments section in `ActivityDetailUi` with `PagingFooter` and the first-load piece inline under the activity. Verify: it compiles, and it is covered by 8.2.

## 8. Docs and integration

- [x] 8.1 Update the "Cursor lists" bullet in `.agents/rules/compose-screens.md` to `rememberRetainedPaging`, `PagedState` in `State` and the shared UI pieces, noting that `rememberCursorPaging` stays only for the two segment screens until `load-segment-screens`. Verify: `scripts/check-agent-sync.sh` passes.
- [x] 8.2 Bring up the local stack (`scripts/dev.sh --seed alice`) and run `./gradlew :desktopApp:hotRun --auto`. Use the hot-reload MCP to check:
  - blocked and muted accounts (loading, then the list, and an empty list);
  - a followers list, and back from a profile shows it at once;
  - search (refining a query keeps old results; clearing it shows nothing);
  - an activity's comments (load more, view more replies, back from a profile).

  Verify: screenshots of each.
- [x] 8.3 Run `./gradlew check`. Verify: every target's tests, lint, ktfmt, detekt and `composeStabilityCheck` pass.

## Workflow follow-up

- `load-segment-screens` moves SegmentLeaderboard and StarredSegments and deletes `CursorPaging` and `CursorPagingTest`.
- Archive this change after review, then sync `ui/content-loading` and `feed/comments`.
