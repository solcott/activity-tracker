# Tasks

## 1. Lexicons and core/lexicon

- [x] 1.1 Add `lexicons/app/activitytracker/feed/getComments.json`, a query with these params:
  - `uri` (at-uri, required);
  - `limit` (1–100, default 30);
  - `cursor`;
  - `uris` (an array of at-uris, `maxLength: 25`).

  Its output is `{ uri, comments: [#commentView], cursor? }`, and its errors are `NotFound` and `InvalidRequest`. The description gives the oldest-first order, the lookup mode, and that the visibility rule is the same as for the count. In `feed/getActivity.json`, drop `comments` from `required`, mark it deprecated and always empty, and drop "with its comments" from the description. Verify that both files parse (`python3 -m json.tool`). `publish-lexicons.sh` publishes only space types, so it doesn't apply to queries.
- [x] 1.2 In `core/lexicon`, add `Nsid.FEED_GET_COMMENTS` and a `GetComments` descriptor in `Queries.kt`. Its `Params` are `uri`, `limit`, `cursor` and `uris: List<AtUri>? = null`, and its `Output` is `uri`, `comments` and `cursor`. Add a `MAX_URIS = 25` constant. Change `GetActivity.Output.comments` to `= emptyList()` with a deprecation KDoc. Verify with round-trip cases in `core-lexicon` tests that `GetActivity.Output(activity)` encodes `"comments":[]` and that `GetComments.Params` round-trips with `uris` (`./gradlew :core-lexicon:jvmTest`).

## 2. AppView

- [x] 2.1 In `api/Views.kt`, extract two helpers from `activity()` and `likes()`:
  - `requireVisibleActivity(uri, viewer)`, which throws `NotFound` and returns the athlete DID;
  - `commentViews(rows, athlete)`, which looks up profiles and marks hidden comments.

  Make `activity()` return the activity alone, which drops the comment query and the `HttpStatusCode.InternalServerError.value` cap. Verify that the existing `AppViewTest`, `CommentHidesTest` and `ActivityVisibilityTest` comment cases still pass once they read comments through 2.2 instead of `getActivity` (`./gradlew :server-appview:test`).
- [x] 2.2 Add `Views.comments(viewer, params)` and route `GetComments.method` in `Routes.kt`:
  - **page mode:** `admittedComments()` filtered by `subjectUri`, `commentsListedFor(viewer)` and `keysetAfter(Comments.createdAt, Comments.uri, cursor)`, ordered by `createdAt ASC, uri ASC` and limited to `Paging.clamp(limit)`. The next cursor comes from the last *row* when the page is full.
  - **lookup mode:** with `uris`, apply `Comments.uri inList uris` instead of the keyset, with no cursor. More than `MAX_URIS` is `InvalidRequest`.

  Verify with a new `CommentPagingTest` covering the spec's `Comments are listed in pages, oldest first` and `Comments looked up by URI` scenarios: first and last page, page size clamp, a comment indexed between pages, the same rule as the count, `NotFound`, a malformed cursor, the lookup cases, and 26 URIs.
- [x] 2.3 Add a test for `The count matches the pages`. Seed 70 comments with mixed audiences, five hidden and one by a commenter who unfollowed. Then, for the athlete, a hidden comment's commenter, another follower and a signed-out viewer, check that `hydrate`'s `commentCount` equals the not-hidden comments across every page at limit 30. Also check that `getActivity` returns `comments: []` with the count. Verify with `./gradlew :server-appview:test`.
- [x] 2.4 Check the index on the docker stack's Postgres. Seed about 1,000 comments on one activity, run `EXPLAIN ANALYZE` on the page query for a mid-list cursor, and confirm that it uses `comments_subject_idx` with no full sort. Note the result in the PR description. Add a `V22` index on `(subject_uri, created_at, uri)`, with a `MigrationVersionsTest` pass, only if it doesn't.

  Result (Postgres in the docker stack, 10,000 comments on one activity, 60,000 in all): `Index Scan using comments_subject_idx`, then an `Incremental Sort` with one group, so there's no full sort and no `V22` index is needed. The keyset's `OR` alone wasn't an index condition, so a deep page filtered out every earlier row (501 at 1,000 deep). `keysetAfter` now also ANDs `sort >= cursor`, which makes it `Index Cond: subject_uri = … AND created_at >= …`, and a page 9,000 rows deep filters out 1 row in 0.05 ms.
- [x] 2.5 Update `.agents/rules/appview.md`:
  - `getComments` lists comments, and `getActivity` no longer does;
  - "every comment query, count or list" now covers `getComments`, both modes;
  - the cursor comes from rows, not views.

  Verify that `scripts/check-agent-sync.sh` passes.

## 3. Client data layer

- [x] 3.1 Add `LocalComments` in `core/data/.../feed`:
  - per-comment entries holding `activity`, the desired state (`Present(hidden)` or `Gone`), the reported state (`Listed(hidden)` or `Absent`) and the sent-comment fields;
  - `delta(activity)`, `report(page)`, `reportLookup(activity, requested, returned)`, `applyTo(page)` and `sent(activity)`;
  - entries retire when the two states match.

  Remove the comment parts of `LocalDeletes` and delete `LocalCommentHides`, moving their tests over. Verify with a `LocalCommentsTest` covering every row of design.md decision 4's table, retirement, and a delete of a not-yet-indexed sent comment netting to zero (`./gradlew :core-data:jvmTest`).
- [x] 3.2 In `FeedRepository`:
  - `activity(uri)` returns `ActivityView`. When `localComments` has unretired entries on it, it runs a lookup-mode `getComments` for the 25 newest in parallel with `getActivity`, reports the result, and applies `delta` to `commentCount`.
  - Add `comments(activity, cursor): CommentPage`, which reports and applies overlays, and `sentComments(activity): Flow<List<SentComment>>`.

  Update `FakeAthletePds` to serve `getComments` in both modes. Verify with `FeedRepository` tests for the spec's `Own changes hold on every page` scenarios (delete or hide on a later page, and an AppView that caught up before the reload) and for send-then-refresh after indexing, which must count once.
- [x] 3.3 In `ActivityRepository`:
  - `comment` returns the new comment's `AtUri` and records a sent entry;
  - `deleteComment(activity, uri)` and `setCommentHidden` record their entry before queueing and remove it if queueing throws. A scope refusal still throws before anything is recorded, and someone else's comment is still left alone.

  Update `CommentHidePlacesTest`, `ActivityVisibilityTest`, `ActivityRepositoryTest` and the old `LocalCommentHidesTest` cases to the new API. Verify with `./gradlew :core-data:jvmTest`.
- [x] 3.4 Update `.agents/rules/data-and-auth.md`: replace the `LocalDeletes`/`LocalCommentHides` comment wording with `LocalComments` (desired against reported, the lookup on activity load), and add that `comment` shows the sent comment at once. Verify that `scripts/check-agent-sync.sh` passes.

## 4. Screen, presenter and UI

- [x] 4.1 In `ActivityDetailScreen.kt`:
  - add `CommentsPaging(items, isLoading, endReached, error)`, `sentComments: ImmutableList<SentComment>`, and the `LoadMoreComments` and `RetryComments` events;
  - replace `Loaded.comments`;
  - add `UiError.Kind.LoadComments` with its string resource.

  Verify that `./gradlew composeStabilityCheck` passes for `:app-screens`.
- [x] 4.2 In `ActivityDetailPresenter`:
  - load the activity and the first page concurrently per `loadKey`, so that a comments failure sets `comments.error` only;
  - derive the shown list, the sent comments and the adjusted count from the raw pages plus `localComments`;
  - handle `LoadMoreComments` under `SingleFlight` (a no-op when `endReached` or after an error), and `RetryComments`.

  Simplify `CommentActions` to calling the repository and reporting errors, and change `rememberManage` to the new `feed.activity` return type. Verify with `ActivityDetailPresenterTest` cases for the spec's `The detail screen loads comments as the viewer scrolls` and `A sent comment shows at once` scenarios, and for the existing delete and hide cases on a second page (`./gradlew :app-presenters:jvmTest`).
- [x] 4.3 In `ActivityDetailUi`:
  - render comments as `LazyColumn` items keyed by URI, then the sent comments with Delete, then a loading or "couldn't load, retry" row, then the draft field;
  - send `LoadMoreComments` when the last visible index comes within 5 of the end;
  - show the count from `activity.commentCount`.

  Verify on desktop with `:desktopApp:hotRun` and the hot-reload MCP against the local stack seeded with more than 30 comments: the first page shows, scrolling loads the rest, a sent comment appears last at once, and the count is right after a refresh.

## 5. Integration

- [x] 5.1 Run `lexicon-auditor` on `feed.getComments` and `feed.getActivity`, and verify that it reports no mismatches.
- [x] 5.2 Run `./gradlew check` and verify that it passes.
- [x] 5.3 End to end on the local stack (`scripts/dev.sh --seed alice`, plus a second account), on Android or desktop: a Followers activity with more than 30 comments from mixed audiences. Delete a comment on page 2, hide one on page 2, send one, and refresh before and after Jetstream catches up. Verify that the count matches the comments listed after paging to the end, in each state.

## Workflow follow-up

- Archive the change with `/opsx:archive` once implemented and reviewed. This syncs the `feed/comments` delta into the main spec.
