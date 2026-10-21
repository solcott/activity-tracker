# Proposal

## Why

Three segment screens still load by hand.

- **CreateSegment** makes three reads (the activity, the owner's full route and privacy settings). It derives a `CreateSegmentUnavailable` reason, has no retry, and shares one `error` between loading, checking and publishing.
- **StarredSegments** pages through `CursorPaging`. It turns an unreadable `self` space into a presenter-private `StarsUnreadable` exception, and maps each starred segment's XRPC `NotFound` to "unavailable".
- **SegmentLeaderboard** pages through `CursorPaging`, keyed on its filters, with the viewer's entry taken from the first page.

These screens sit in the area the in-flight segment changes touch, so they were deliberately left for last. `segment-leaderboard-brackets` in particular changes the leaderboard.

This is step 6 of moving the rest of the app onto kmp-dataresult.

## What Changes

- **CreateSegment:**
  - Its activity read uses `activityOutcome`, and the owner's route and privacy settings are combined into a `ContentState` group.
  - `CreateSegmentUnavailable` (no route, no sport group, zones unreadable, all hidden) stays as derived data over that state.
  - It gains a first-load phase and retry, and its load error separates from its check and publish errors.
- **StarredSegments:**
  - It moves onto library paging (`adopt-library-paging`).
  - The starred list's unreadable space becomes an answer rather than the private `StarsUnreadable` exception, as `load-form-screens` does for zones.
  - A starred segment the AppView no longer has still shows as unavailable.
  - Unstarring edits the list in place through the pager's `update {}`.
- **SegmentLeaderboard:**
  - It moves onto library paging, with the viewer's entry as the pager's meta.
  - Filter changes clear the list rather than showing the previous filter's entries.
- **Ending the migration:**
  - `ui/content-loading`'s requirements are reworded from naming screens to every screen that loads content, if this is the last migration change to land.
  - `CursorPaging` is deleted here if `adopt-library-paging` left it for these screens.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `ui/content-loading`: this change extends its requirements to the create-segment, starred-segments and leaderboard screens. If this change lands last, it rewords them to every screen that loads content.
- `segments/segment-favorites`, `segments/segment-creation`, and the leaderboard spec from `segment-leaderboards` / `segment-leaderboard-brackets`: only where they describe loading, unreadable stars or empty and failed states. Confirm while writing the specs.

## Impact

- **`core/data`:**
  - `SegmentStars.starred()` answers "unreadable" as data.
  - `SegmentRepository.leaderboard` gets a page read.
  - The starred segments' lookups become outcome reads.
- **`app/presenters` and `app/screens`:**
  - `CreateSegmentPresenter`, `StarredSegmentsPresenter` and `SegmentLeaderboardPresenter`, and their states.
  - Their tests: `CreateSegmentPresenterTest`, `StarredSegmentsPresenterTest` and `SegmentLeaderboardPresenterTest`.
- **`composeApp`:** the three UIs, with `ContentStateLayout` for CreateSegment and the library pager's UI for the two lists.
- **Depends on:**
  - `error-reasons`;
  - `load-activity-and-profile-screens` (for `activityOutcome`);
  - `adopt-library-paging`;
  - `segment-leaderboard-brackets` landing first, since it changes the leaderboard;
  - the other in-flight segment changes, if any of them touch these screens by then.
