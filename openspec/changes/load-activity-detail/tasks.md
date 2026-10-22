# Tasks

## 1. Index wait as a stream operator

- [ ] 1.1 Replace the `AwaitIndexed` composable with `Flow<Outcome<T>>.awaitIndexed(justWritten)` in `detail/AwaitIndexed.kt`, as design.md describes, keeping `MAX_INDEX_WAITS` and `INDEX_WAIT_MILLIS`. Leave the presenter on the old call until 2.2. Verify with a new `AwaitIndexedTest` in `app/presenters/src/jvmTest`, using `runTest` virtual time:
  - a `NotFound` that turns into `Data` on the third collection emits `Loading` and then `Data`, with no `Error` between them;
  - after 10 `NotFound`s, the error comes through;
  - a `Network` error passes at once, with no retry;
  - a `NotFound` while `justWritten()` is false passes at once.

## 2. Main load and page state

- [ ] 2.1 In `ActivityDetailScreen.kt`, replace the sealed `ActivityDetailState` with the data class `ActivityDetailState(content: ContentState<ActivityDetailPage?>, eventSink)`. Rename `Loaded` to `ActivityDetailPage` and drop its `eventSink`. Verify that `:app-screens` compiles and that `composeStabilityCheck` in strict mode passes for it.
- [ ] 2.2 In `ActivityDetailPresenter`:
  - collect `feed.activityOutcome(uri).awaitIndexed { … }` with `produceRetainedContentState(null, loadKey)`;
  - build the page from the shown view and set it with `content.copy(data = page)`;
  - replace `CountedActivity` with the identity-based `CommentMoves` overlay;
  - remove `error`, `detailState` and the composable `AwaitIndexed` call;
  - keep Retry, ungroup, member removal and the data-source choice bumping `loadKey`.

  Verify that `:app-presenters` compiles.
- [ ] 2.3 Move the tests to the new state:
  - `ActivityDetailTestBase.loaded()` and `awaitLoaded()` read `content.data` once `hasLoaded`;
  - update the ~9 `ActivityDetail*Test` classes, `ActivityDetailManageTest` and the detail cases in `CombinePresentersTest` and `ImportCombinePresenterTest`.

  Verify with `./gradlew :app-presenters:jvmTest` that the suites pass with no assertion weakened.
- [ ] 2.4 Add presenter tests to `ActivityDetailPresenterTest`:
  - loading, then loaded;
  - a first-load failure (offline) shows `Failed(Network)`, then Retry shows loading and then the page;
  - a reload after a data-source choice keeps `content.data` while the status is `Loading`;
  - a failed reload keeps the page with `Failed`;
  - a `RetainedStateRegistry` round trip shows the page at once, as in `SegmentPresenterTest`;
  - a sent comment moves the count, and a reload answering the moved count doesn't move it twice.
- [ ] 2.5 Add index-wait tests through the presenter, in `CombinePresentersTest` or a new `ActivityDetailIndexTest`:
  - `suggestOnOpen` with a fake AppView answering `NotFound` twice, then the activity, never shows `Failed` and ends loaded;
  - someone else's missing activity fails at once.
- [ ] 2.6 In `ActivityDetailUi.kt`:
  - render through `ContentStateLayout(state.content, UiError.Kind.LoadActivity, onRetry)`, with `RefreshStatus` at the top of both the column and the paned layouts;
  - pass `ActivityDetailPage` plus the sink to the helpers;
  - have the top-bar menus read `content.data`.

  Verify that `:composeApp:compileKotlinJvm` passes and that detekt `UseAdaptiveComponents` is clean.

## 3. Supplementary loads

Each task makes the load a retained `ContentState` keyed on `loadKey`, as in design.md's table. Its presenter test covers:
- a failure: quiet, or with its own error where it has one;
- the value kept through a page reload;
- the value shown at once after a retained round trip.

- [ ] 3.1 Charts and streams (`rememberCharts`), with units as the `snapshotFlow` parameter. Verify in `ActivityDetailPresenterTest`:
  - a streams failure leaves `charts` null and no error;
  - a change of units rebuilds the lap table;
  - charts are kept through a failed reload.
- [ ] 3.2 Segment efforts (`rememberSegments`). Verify in `ActivityDetailSegmentsTest`: a failure gives an empty list and no error, and efforts are kept through a failed reload.
- [ ] 3.3 Sibling count in `rememberManage`, on `recordingOf`. Verify in `ActivityDetailManageTest` that `canUngroup` holds after a round trip and is false while the count failed.
- [ ] 3.4 Combine suggestion, with the dismissed overlay. Verify in `CombinePresentersTest`:
  - a failed lookup shows no suggestion and no error;
  - Don't combine hides it across a reload;
  - the prompt state survives a round trip.
- [ ] 3.5 Owner's track (`rememberOwnRoute`) and original file info (`rememberExport`), on the owner's URI. Verify in `ActivityDetailRouteTest` and `ActivityDetailExportTest`:
  - the full route and the original file show at once after a round trip;
  - nothing loads on someone else's activity.
- [ ] 3.6 Data sources, with the `setSources` overlay and `readKey` for RetrySources. Verify with the DataSources cases in `CombinePresentersTest`:
  - a load failure shows `LoadDataSources`, and RetrySources reloads it;
  - a failed choice shows `SaveDataSource`, and RetrySources repeats the choice;
  - a successful choice shows at once and reloads the page under the content.
- [ ] 3.7 Likes list, keyed on each time it's shown. Verify in `ActivityDetailPresenterTest`:
  - loading, then the athletes;
  - an offline failure gives `LoadLikes` with `DataError.Network` as the reason;
  - the viewer's own like is merged as today.

## 4. Integration

- [ ] 4.1 Verify with `grep` that `ActivityDetailPresenter` and its producers no longer call `feed.activity(`, and that no `LaunchedEffect` loads anything in `detail/` except `Comments.kt` and the likes-list profile lookup. List the remaining callers of `feed.activity` in this file's follow-up.
- [ ] 4.2 Run `./gradlew check` and `./gradlew composeStabilityCheck`, and verify both pass.
- [ ] 4.3 Verify in the web app (`:webApp:wasmJsBrowserDevelopmentRun`), with AppView requests failed in the page:
  - an activity opened offline says "Couldn't load activity. You're offline." with Retry, and Retry loads it once unblocked;
  - a loaded activity going offline and reloading through a data-source choice or Retry keeps the page and says why;
  - going back from a segment or a profile to the activity shows it, its charts and its efforts at once.

  Then save a new activity on the desktop app against the local stack (`scripts/dev.sh`), and verify that its page shows loading until it's indexed, with no error flashing.

## Workflow follow-up

- `adopt-library-paging` moves the comments, which are still `remember` and keyed on `loadKey`, onto the library's `Paging`.
- Archive the change after review.
