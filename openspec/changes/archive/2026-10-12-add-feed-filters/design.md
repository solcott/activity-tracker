# Design

## Context

See proposal.md for motivation. The specs define the behavior: `specs/feed/activity-query-filters` and `specs/feed/feed-filter`.

Current state that shapes the approach:
- **Timeline query.** `Views.timeline` already builds `authors = follows + viewer`, so Following needs no server change. Mine is `getAuthorActivities(viewerDid)`.
  - Both go through `Views.activityPage(filter: Op<Boolean>, limit, cursor, viewer)`, which applies keyset paging on `(sort_at, uri)` plus `shownInFeeds()`. A new predicate just gets ANDed into `filter`.
- **Precedent for date filtering.** `getAuthorActivitiesByStart` already filters on `started_at` with `since`/`until` and a single `sport`, and rejects `until <= since` with `InvalidRequest`. We follow its parameter names and validation.
- **Client cache.** `FeedRepository` caches only page 1 in Room, keyed by string (`timeline`, `author:<did>`), and `FeedPresenter` reads `feed.timeline()`. Client-side filtering would therefore filter just one page. That is why filtering must run on the server.
- **Feed table.** `Activities` has `sport varchar(64)` and a nullable `sub_sport varchar(64)`. Its indexes are `(author_did, sort_at)` and `(author_did, started_at)`.
- **SubCircuit.** It is wired up and documented by `setup-subcircuit`, which this change depends on: a session-scoped `SubCircuit`, `LocalSubCircuit`, and the anatomy in `.claude/rules/compose-screens.md`. `SubCircuitContent` remembers the presenter per `screen` and keys content on it.
- **Repo rules.** No Circuit KSP processor. `:app-screens` State is under a strict stability check.

## Goals / Non-Goals

**Goals:**
- Establish SubCircuit as the repo's pattern for a reusable piece of screen (contract, presenter, UI), injected by Metro like top-level screens.
- Keep the filter control unaware of the feed. It reports a filter value, and the host decides what to load.
- One filter model the server can evaluate in a single indexed query per page.

**Non-Goals:**
- A general query language. Filters stay as a fixed set of typed parameters.
- Changing feed ordering. Results still sort by `sort_at`, even under a date filter.
- Embedding the filter in Profile. The contract fits it as is (it has no scope), but wiring it is a separate change.

## Decisions

### 1. Filter on the server, through new optional query params
Add `type: string[]` (max 20), `since` and `until` (datetime) to `getTimeline` and `getAuthorActivities`.
- `Views` builds the predicate `(OR over types) AND started_at >= since AND started_at < until` and passes it to `activityPage`.
- Type tokens: `sport` becomes `sport = ?`, and `sport:subSport` becomes `sport = ? AND sub_sport = ?`.
- Validation follows the spec. It happens in `Views` before any query, like `authorActivitiesByStart`.

Alternatives considered:
- *Client-side filtering of cached pages*: wrong results beyond page 1.
- *A new `feed.getFiltered` query*: duplicates both endpoints and their auth or visibility rules.
- *Encoding types as two parallel arrays (`sport[]`, `subSport[]`)*: awkward to keep aligned, and it can't express "sport, any sub-sport" without a sentinel.

### 2. Two filter models: one for the UI, one for the data layer
- `:app-screens` `FeedFilter(types: ImmutableSet<ActivityType>, range: DateRange?)`. `FeedScope` lives with the feed (`FeedScreen.kt`), not the filter.
  - `DateRange` is either `Preset(LastDays(7) | LastDays(30) | ThisYear)` or `Custom(start: LocalDate, endInclusive: LocalDate)`.
  - A preset stays a preset, so "Last 7 days" still means the last 7 days after midnight. It is resolved to instants only when a query is built.
- `core/data` `ActivityFilter(types: Set<ActivityType>, since: Instant?, until: Instant?)`.
  - It provides canonical `typeTokens()`: sorted, `sport` or `sport:subSport`.
  - It provides a canonical `cacheKey(base)`, for example `timeline|type=ride,run:trail|since=…|until=…`.
  - Without a filter, the key is just `base`, so existing cache rows and keys are unaffected.
- `FeedPresenter` maps `FeedFilter` to (source, `ActivityFilter`).
  - It resolves dates with `TimeZone.currentSystemDefault()`: start of `start` up to start of `endInclusive + 1 day`.
  - It resolves presets against `Clock.System`.

Why two models: `FeedRepository` (core/data) can't depend on `:app-screens`, and scope is a UI concept that picks *which* query to run rather than a predicate.

### 3. `FeedRepository` API
- `timeline(filter = ActivityFilter.None)` and `authorActivities(did, filter = ActivityFilter.None)`.
- `refreshTimeline(filter)` and `refreshAuthor(actor, filter)`.
- Each uses `filter.cacheKey(...)` and passes `filter`'s params.
- Defaults keep existing callers (Profile, tests) unchanged.

### 4. The filter is a SubCircuit
Contract in `:app-screens` `app/feedfilter/`:
- `FeedFilterScreen(initial: FeedFilter) : SubScreen<FeedFilterOuterEvent>`, `@Immutable`.
- `FeedFilterOuterEvent.Changed(filter)`.
- `FeedFilterState(filter, draftTypes, quickTypes, categories, sheet, eventSink) : SubCircuitUiState`.
- `FeedFilterEvent` = OpenTypes, ToggleType, ToggleCategory, ApplyTypes, DismissTypes, SetRange, Clear.

Presenter in `:app-presenters`:
- `FeedFilterPresenter` is a `@SubCircuitInject(FeedFilterScreen::class, SessionScope::class) @Inject class` taking `screen: FeedFilterScreen` and `recentTypes: RecentActivityTypes`; Metro injects the screen, so no assisted injection.
- It owns the live filter (`rememberRetained`, seeded from `screen.initial`) and the type draft. A keyed `SideEffect(filter)` emits `Changed` when it is first shown and on every change.
- Category toggles expand through `SportCatalog.categories`, shortcuts included, and are shown tri-state. A sport whose sub-sports are all selected collapses to the bare sport before it is emitted.

UI in `composeApp`:
- `@Composable fun FeedFilterUi(state, modifier = Modifier)`, registered by `@SubCircuitInject(FeedFilterScreen::class, SessionScope::class) @Inject class FeedFilterSubUi : SubUi<FeedFilterState>` (the SubUi anatomy from `setup-subcircuit`).
- One row, scrolling sideways when it doesn't fit, with a type chip, a date chip and clear.
- An `AppBottomSheet` multi-select type picker that reuses `ActivityTypePicker`'s category layout and labels.
- Date presets plus `AppDatePickerDialog` for a custom range.

Host (feed):
- `FeedPresenter` holds the scope and the canonical `FeedFilter` in `rememberRetained`.
- It exposes `filterScreen = rememberRetained { FeedFilterScreen(filter) }`, created once so the child isn't remounted.
- It handles `FeedEvent.FilterChanged(filter)`. `FeedState` gains `filterScreen` and `filter` (for the empty state).
- `FeedUi` renders `SubCircuitContent(state.filterScreen, outerEventSink = { state.eventSink(FeedEvent.FilterChanged(it.filter)) })` under the top bar.

Clearing from outside the control (the empty feed's "Clear"): the host can't reach the child's live filter, so `FeedEvent.ClearFilters` resets the host's filter and replaces `filterScreen` with one seeded from the cleared filter, in one snapshot. That remounts the control, which is fine for an explicit reset. Changes made inside the control never replace the screen.

Scope is not part of the SubCircuit. The feed's title is the scope picker ("Following ⌄", through `AppTopBar`'s `titleMenu`), handled by `FeedEvent.SetScope`. A segmented control couldn't grow to the planned club scopes, and keeping scope out of the chip row means Clear plainly never touches it. `FeedState.scopes` lists the menu's choices, so clubs become more entries without a UI change.

Retained state: `:app-presenters` adds `circuit-retained` (already in the app through `circuit-foundation`) for `rememberRetained`, which both presenters use for the filter so it survives opening an activity and coming back.

The control sits between the top bar and the list, not as a `LazyColumn` item, because a lazy item that scrolls off is disposed and the control's state with it.

Why the screen carries only `initial`: `SubCircuitContent` keys on the screen. If the live filter were in the screen, every change would remount the child and drop its draft and sheet state.

Alternatives considered:
- *Plain composable plus a state holder*: not injectable, not swappable in tests.
- *A nested Circuit `Screen` with an overlay*: wrong lifetime, and it can't sit inline.
- *Custom KSP codegen*: unnecessary, since Metro already supports `@SubCircuitInject`.

### 5. Feed reacts with `flatMapLatest`
- `FeedPresenter` keys the source flow on the filter: `remember(filter) { feed.timeline(f) or feed.authorActivities(viewer, f) }`.
- The refresh `LaunchedEffect` is keyed on `(refreshKey, filter)`.
- Each filter has its own cache key, so switching filters never shows stale rows from another filter (spec: *Feed reacts to filter changes*).
- `activities.sync()` keeps running only on explicit refresh and the first load, not on every filter change.

## Risks / Trade-offs

- **Date-filtered timeline on large follow graphs scans `sort_at` order and drops rows outside the range** → fine at current scale. Don't add `(sport, …)` or `(started_at, …)` indexes until `EXPLAIN` on realistic data says so (non-goal here).
- **Results sort by posted time, not start time, under a date filter**, so a backfilled old activity appears near the top → matches the existing feed semantics. Revisit with a sort option later.
- **Cache growth: every distinct filter adds a Room key** → each entry holds at most one page. Cap it by evicting filtered keys beyond the N most recent (N = 10) when refreshing.
- **Child state lost but host retained** → the child re-seeds from `screen.initial`, the host's filter when the screen was made, and reports it when shown, so the host follows. The two stay consistent because the host updates only from `Changed`.
- **Outbox activities aren't in the feed today, and filtered feeds don't add them** → unchanged behavior (out of scope).

## Migration Plan

- The lexicon change is additive and optional, so older clients are unaffected.
- Deploy the AppView first. A new client against an old AppView would have its unknown params ignored and get unfiltered results, so ship the client after the AppView.
- There is no database migration.
- Rollback means reverting the client. The AppView params are inert when unused.
