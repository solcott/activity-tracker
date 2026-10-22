# Proposal

## Why

Three segment screens still load by hand.

- **CreateSegment** makes three reads: the activity, the owner's full route and their privacy settings.
  - A route that can't be read (offline) shows as "this activity has no route", since the track read answers null for both.
  - Zones that can't be read show as a dead end, `CreateSegmentUnavailable.ZonesUnreadable`, with no reason and no retry.
  - One `error` is shared between loading, checking and publishing.
  - Its state is `remember`ed, so opening a duplicate segment and coming back reloads everything and resets the chosen stretch.
- **StarredSegments** pages through `CursorPaging`.
  - An unreadable `self` space becomes a presenter-private `StarsUnreadable` exception, so the error never says why.
  - Each starred segment's XRPC `NotFound` is mapped to "unavailable" in the presenter.
- **SegmentLeaderboard** pages through `CursorPaging`, keyed on its filters, with the viewer's entry set as a side effect of the first page.
  - After a filter change, the previous filter's entries stay listed under the new filter until its first page lands.

This is step 6, the last, of moving the app onto kmp-dataresult. Every other screen has moved, so `CursorPaging` goes with it.

## What Changes

- **CreateSegment:**
  - The activity, the owner's route and the privacy zones load as one retained content group, with a first-load phase, a reason and a retry.
  - A route that can't be read, and zones that can't be read, are load failures with a reason and a retry. Neither is shown as "no route" or as a dead end. `CreateSegmentUnavailable.ZonesUnreadable` goes.
  - No route, no sport group and "all hidden" stay as answers derived from the loaded content.
  - Check and publish failures are action errors, apart from the load's status. They keep the screen, the chosen stretch and the name.
  - The chosen stretch is retained, so coming back from a duplicate segment shows the route and stretch at once.
- **StarredSegments:**
  - It moves onto library paging.
  - Unreadable stars become an answer with a reason (`DataError`), as the privacy zones' read does, so the failure says why.
  - A starred segment the AppView no longer has (`NotFound`) still lists as unavailable. Any other lookup failure fails that page.
  - Unstarring removes the row through the pager's `update {}`, and a failed unstar is an action error that doesn't stop paging.
- **SegmentLeaderboard:**
  - It moves onto library paging, with the viewer's entry as the page's meta.
  - A filter change clears the list and shows loading, instead of showing the previous filter's entries.
- **Ending the migration:**
  - `CursorPaging`, `CursorPagingTest` and any throwing read left without a caller are deleted.
  - `ui/content-loading` counts these screens in its groups: starred segments and the leaderboard among the paged lists, create segment among the form screens. Its Purpose says every screen that loads content now belongs to one of them. The requirements keep naming groups, because they hold for different groups (only lists page, only forms keep typed fields).
  - The "Cursor lists" rule in `.agents/rules/compose-screens.md` stops mentioning `rememberCursorPaging`.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `ui/content-loading`: scenarios for the three screens under the requirements of their groups. There is a new requirement that a filter change on a paged list shows only the new filter's items.
- `segments/segment-favorites`: unreadable stars say why, and a retry shows loading and then the list.
- `segments/segment-creation`: unreadable zones or route are a failed load with a retry, not a missing route.
- `segments/segment-leaderboards`: the leaderboard screen's filter change and failure behaviour.

## Impact

- **`core/data`:**
  - `SegmentStars` answers unreadable stars with a reason.
  - `SegmentRepository` gets a leaderboard page read and an outcome read for one starred segment's lookup.
  - The owner's track gets an outcome read that tells "no track" apart from "couldn't read it".
- **`app/screens` and `app/presenters`:** `CreateSegmentState`/`Presenter`, `StarredSegmentsState`/`Presenter`, `SegmentLeaderboardState`/`Presenter`, and their tests.
- **`composeApp`:** the three UIs. CreateSegment uses `ContentStateLayout`, and the two lists use `PagedList.kt`.
- **Deleted:** `app/presenters/.../CursorPaging.kt` and `CursorPagingTest`.
- **Depends on:** `error-reasons`, `load-activity-and-profile-screens`, `load-form-screens` and `adopt-library-paging`, all archived. It doesn't wait for `segment-leaderboard-brackets`, a later roadmap step. That change will only add filters to the leaderboard's paging key.
