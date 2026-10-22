# Tasks

## 1. Outcome reads (`core/data`)

- [x] 1.1 Split `ActivityTrackStore.read` into `readOrThrow` (null for no record, no blob, `NotFound`, `NoAccess`; throws on a failed read or unparsable blob) and `read` = `runSuspendCatching { readOrThrow(…) }.getOrNull()`. Add `ActivityRepository.ownRouteOutcome(uri)` over `readOrThrow` with `outcomeFlow`. Verify: jvm tests cover a stored track, no track (`Data(null)`), and a connection failure (`Error(DataError.Network)`), and the existing track-store and route-sweep tests still pass.
- [x] 1.2 Add `SegmentStars.starredOutcome(): Outcome<List<SegmentStar>>`, with `Found` and the overrides laid over it, `NotFound` as `Data(empty)`, `NoAccess` as `Error(DataError.Unknown(message = "No access to the self space"))` and a thrown read as `Error(toDataError())`. Verify: `SegmentStarsTest` gains a case for each, including a session override shown over a `NotFound`.
- [x] 1.3 Add `SegmentRepository.segmentLookup(uri): Outcome<SegmentView?>`, with an XRPC `NotFound` as `Data(null)`, and `leaderboardPage(segment, period, scope, eBike, cursor): Outcome<Page<LeaderboardEntry, LeaderboardEntry?>>` with the viewer's entry as meta. Verify: `SegmentRepositoryTest` with `MockEngine` covers a found segment, `NotFound`, a server error (`DataError.Http`/`Api`), and a leaderboard page's entries, cursor and viewer meta.

## 2. CreateSegment

- [x] 2.1 In `CreateSegmentState`:
  - replace `isLoading` and the load half of `error` with `content: ContentState<Unit>` and `actionError: UiError?`;
  - drop `CreateSegmentUnavailable.ZonesUnreadable`;
  - add `CreateSegmentEvent.Retry`;
  - add `UiError.Kind.LoadRoute`, with its message in `errorText`.

  Verify: `./gradlew :app-screens:compileKotlinJvm composeStabilityCheck` passes.
- [x] 2.2 Rewrite `CreateSegmentPresenter` as design.md describes:
  - one `produceRetainedContentStates` group of `activityOutcome`, `ownRouteOutcome` and `PrivacyZones.zonesOutcome()`, keyed on `uri` and `loadKey`;
  - seeded once (`rememberFormSeed`/`formContent`), with the route cut from the seeded zones;
  - `start`/`end`/`duplicates` in `rememberRetained`;
  - `unavailable` derived only once loaded;
  - check and publish failures in `actionError`.

  Verify: it compiles, and `ActivityRepository.privacySettings()` has a caller left or is removed.
- [x] 2.3 Update `CreateSegmentPresenterTest` for the spec scenarios: loading before all three answer, *Route can't be read for a new segment* (offline track, `LoadRoute`, not `NoRoute`), *Zones unreadable* then *Zones readable on retry*, a missing track as `NoRoute`, *Publishing a segment fails offline* (selection and name kept, `actionError`, content still loaded), and a reload that leaves the selection where it was. Verify: `./gradlew :app-presenters:jvmTest --tests "*.CreateSegmentPresenterTest"` passes.
- [x] 2.4 Move `CreateSegmentUi` onto `ContentStateLayout`, with the load's retry. Show `actionError` under the form, and remove the `segment_create_zones_unreadable` string and its branch. Verify: `./gradlew :composeApp:compileKotlinJvm` passes, and on the desktop app (hot reload) the screen shows loading, then the route, and offline shows the route failure with Retry.

## 3. StarredSegments

- [x] 3.1 In `StarredSegmentsState`, replace `segments`, `isLoading`, `canLoadMore` and the load half of `error` with `segments: PagedState<StarredSegment, Unit>`, `actionError: UiError?` and `needsSignIn: Boolean` (for the `SignInAgain` case). Verify: `composeStabilityCheck` passes.
- [x] 3.2 Rewrite `StarredSegmentsPresenter` on `rememberRetainedPaging(StarredSegmentsScreen, idOf = StarredSegment::uri, enabled = stars.canUse)` over a retained `StarPages`. The first page reads `starredOutcome()`, each page looks up its slice through `segmentLookup`, four at a time, and the first `Error` fails the page. Unstar calls `update {}` on success and sets `actionError` on failure, without gating `loadMore`. Remove `StarsUnreadable` and `isNotFound`. Verify: it compiles.
- [x] 3.3 Update `StarredSegmentsPresenterTest` for *Opening starred segments*, *No starred segments*, *Offline list* (`LoadStarredSegments` with `DataError.Network`), *Retry once online*, *Segment deleted by its creator* (unavailable row), *Lookup fails offline* (no row shown unavailable, page failed), a failed second page keeping the first, *Unstar fails* (row stays, next page still loads), and a session without the stars scope. Verify: `./gradlew :app-presenters:jvmTest --tests "*.StarredSegmentsPresenterTest"` passes.
- [x] 3.4 Move `StarredSegmentsUi` onto `LoadMoreAtEnd`, `pagedRefreshStatus` and `pagedFooter`, with the empty state as the footer's `empty`, and drop its `SideEffect`. Verify: `:composeApp:compileKotlinJvm` passes, and on the desktop app the list loads, pages, and offline shows the starred segments failure with Retry.

## 4. SegmentLeaderboard

- [x] 4.1 In `SegmentLeaderboardState`, replace `entries`, `isLoading`, `canLoadMore` and `error` with `entries: PagedState<LeaderboardEntry, LeaderboardEntry?>`, keeping `viewerEntry` as the derived own entry. Verify: `composeStabilityCheck` passes.
- [x] 4.2 Rewrite `SegmentLeaderboardPresenter` on `key(period, scope, eBike) { rememberRetainedPaging(uri, idOf = { it.athlete.did }) { … } }` over `leaderboardPage` (design.md says why not `ClearItems`). `viewerEntry` is `state.meta` once answered and not among `items`. Verify: it compiles.
- [x] 4.3 Update `SegmentLeaderboardPresenterTest` for:
  - *Changing a leaderboard's period*: nothing old is listed while the new board loads;
  - *The viewer's own place follows the filter*, and *Switching to the e-bike board*;
  - *Own place pinned*;
  - *Nobody on a board*;
  - *Leaderboard fails, then retry*;
  - *Back to a leaderboard*, two pages retained with the filter (`presenterTestOf` with a retained registry, as the follow list tests do).

  Verify: `./gradlew :app-presenters:jvmTest --tests "*.SegmentLeaderboardPresenterTest"` passes.
- [x] 4.4 Move `SegmentLeaderboardUi` onto the shared paged-list pieces, keeping the pinned own entry above the footer, and drop its `SideEffect`. Verify: `:composeApp:compileKotlinJvm` passes, and on the desktop app a filter change clears the list to a spinner, then the new board.

## 5. Ending the migration

- [x] 5.1 Delete `app/presenters/.../CursorPaging.kt` and `CursorPagingTest.kt`, and every throwing read the three screens no longer call (`SegmentStars.starred`, `SegmentRepository.leaderboard`, `ActivityRepository.privacySettings`, …), each only if `grep` finds no caller. Verify: `grep -rn "CursorPaging\|rememberCursorPaging" --include=*.kt .` finds nothing outside `build/`, and `./gradlew :core-data:jvmTest :app-presenters:jvmTest` passes.
- [x] 5.2 Edit `openspec/specs/ui/content-loading/spec.md`'s Purpose directly (a delta can't carry it). Add starred segments and the segment leaderboard to the paged lists, and create segment to the form screens, and say that every screen that loads content is the feed, the segment page or in one of the groups. Verify: `openspec validate --specs --strict` passes, and the Purpose lists every screen the delta's new scenarios name.
- [x] 5.3 Drop the `rememberCursorPaging` sentence from `.agents/rules/compose-screens.md`'s "Cursor lists" bullet, and the "Screens not yet moved still call the throwing reads" sentence from `.agents/rules/data-and-auth.md`. Verify: `scripts/check-agent-sync.sh` passes, and `grep -rn "rememberCursorPaging\|not yet moved" .agents` finds nothing.

## 6. Integration

- [x] 6.1 Run `./gradlew check` and `./gradlew composeStabilityCheck`. Verify: both pass, with no new detekt baseline entries.
- [x] 6.2 On the desktop app against the local stack (`scripts/dev.sh --seed alice`), open create segment from an activity, open a duplicate and come back, star and list segments and unstar one, and change leaderboard filters. Verify: each matches its spec scenarios, including *Back from a duplicate* and *Back to a leaderboard*.

Note: the in-app checks in 2.4, 3.4, 4.4 and 6.2 ran in the web app (same shared UI) against the local stack, since the hot-reload MCP server couldn't connect for the desktop app.

## Workflow follow-up

- Sync the delta specs and archive the change once the tasks are done and reviewed.
