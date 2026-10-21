# Proposal

## Why

The activity page is the app's largest screen (`ActivityDetailPresenter`, about 540 lines), and the one most visibly left behind:

- **Raw error text.** Offline on the web, it shows "Fail to fetch".
- **Sealed loading state.** Its state is a sealed `Loading` / `Error` / `Loaded`, so a reload drops the page back to a spinner, and nothing is retained when the athlete comes back from a photo, a profile or a segment.
- **About nine supplementary loads.** Charts, segment efforts, comments, sibling recordings, data sources, combine suggestion, own route, export info and likes each re-key on the same `loadKey` with their own flags.
- **`AwaitIndexed`.** It bumps `loadKey` up to 10 times while a freshly written activity isn't indexed yet.
- **No test** asserts the page's `Error` or `Loading` state, or `AwaitIndexed`.

This is step 4 of moving the rest of the app onto kmp-dataresult.

## What Changes

- **The main load moves to `ContentState`.** `ActivityDetailState`'s sealed `Loading` / `Error` / `Loaded` becomes a retained `ContentState` of the activity, using `activityOutcome(uri)` from `load-activity-and-profile-screens`. Reloads keep the page on screen, and coming back shows it at once.
- **Supplementary loads become `ContentState`s of their own.** They are grouped where they share a key, and a failure stays quiet for those the page already treats as extra: charts, segment efforts, sibling count, combine suggestion, own route and export. Data sources and likes keep showing their own failures.
- **AwaitIndexed becomes a retry policy on the outcome stream.** While the activity was just written (`rewrite` or `suggestOnOpen`) and the AppView answers not found, the stream retries on the same schedule. This replaces bumping `loadKey` from outside. The page shows the activity from the local rewrite meanwhile, as today.
- **Comments stay hand-rolled until `adopt-library-paging`.** That covers their pagination, replies, generation guard and load-more. This change only routes their errors through `UiError` reasons.
- **Separate load and action errors.** The like, comment, hide and delete action errors stay where they are; only the loads move.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `ui/content-loading`: this change extends its requirements to the activity page, including its supplementary loads under "Supplementary content fails quietly".
- `feed/activity-page-layout` and `feed/comments`: only where their requirements describe loading, reloading or waiting for a new activity to be indexed. Confirm while writing the specs. Visible behaviour while waiting for the index must not change.

## Impact

- **`app/screens`:** `ActivityDetailScreen.kt`, where the sealed state is replaced.
- **`app/presenters`:**
  - `detail/ActivityDetailPresenter.kt` and its producers: `ActivitySegments`, `Charts` (`rememberCharts`), `DataSourcesHolder`, `CombineSuggestionPrompt`, `OwnRoute`, `Export`, the likes list and `AwaitIndexed`.
  - The tests built on `ActivityDetailTestBase` (about 9 classes), `ActivityDetailManageTest`, and the DataSources cases in `CombinePresentersTest`.
  - New tests: the page's first-load, failure and retry phases, and AwaitIndexed retrying until the activity is indexed.
- **`composeApp`:** `detail/ActivityDetailUi.kt` renders through `ContentStateLayout` and `RefreshStatus`.
- **Depends on:**
  - `error-reasons`.
  - `load-activity-and-profile-screens`, for `activityOutcome`.
- **Follow-up:** `adopt-library-paging` moves the comments.
