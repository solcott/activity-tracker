# Tasks

Requires `setup-subcircuit` to be applied first.

## 1. AppView filter params (lexicon change)

- [x] 1.1 Add optional `type` (string array, `maxLength` 20), `since` and `until` (datetime) params to `lexicons/.../feed/getTimeline.json` and `getAuthorActivities.json`, and mirror them in `core/lexicon` `GetTimeline.Params`/`GetAuthorActivities.Params`, following the `lexicon-change` skill; verify `./gradlew :core-lexicon:jvmTest` passes, including a round-trip of the array params
- [x] 1.2 In `Views`, validate filter params (spec: *Filter input is validated*) and AND the type and `started_at` predicates into `activityPage` for `timeline` and `authorActivities`; verify with new `:server-appview:test` cases:
  - no-filter parity
  - sport matches all sub-sports
  - exact `sport:subSport`
  - multiple types OR'd
  - unknown type gives an empty list
  - since-only and since+until bounds, including a backfilled activity
  - type AND date
  - second page under a filter
  - each rejection case returns 400 `InvalidRequest`
  - the timeline still includes the viewer
- [x] 1.3 Run the `lexicon-auditor` agent on the two NSIDs and fix every mismatch it reports; verify it reports none

## 2. Filter-aware feed data

- [x] 2.1 Add `ActivityFilter(types, since, until)` with `None`, canonical sorted `typeTokens()` and `cacheKey(base)` (`base` alone when unfiltered) in `core/data/.../feed`; verify unit tests show that equivalent filters with different orderings produce the same key and that `None` keeps `timeline`/`author:<did>`
- [x] 2.2 Add a filter param (default `ActivityFilter.None`) to `FeedRepository.timeline`, `authorActivities`, `refreshTimeline` and `refreshAuthor`, sending the new query params and writing under the filter's key, and evict filtered keys beyond the 10 most recent on refresh; verify `MockEngine` + in-memory Room jvm tests show:
  - the params are sent
  - a filtered refresh leaves the unfiltered cache untouched
  - a filtered cache is read back
  - eviction keeps the newest 10

## 3. Feed filter SubCircuit

- [x] 3.1 Add the `:app-screens` `app/feedfilter/` contract:
  - `DateRange` (presets `LastDays(7)`, `LastDays(30)` and `ThisYear`, plus `Custom(start, endInclusive)`)
  - `FeedFilter` with `Default`
  - `FeedFilterScreen(initial)`, `FeedFilterOuterEvent.Changed`
  - `FeedFilterState`, `FeedFilterEvent`

  All `@Immutable` with immutable collections. Verify `./gradlew :app-screens:composeStabilityCheck` passes.
- [x] 3.2 Implement `FeedFilterPresenter` (a `@SubCircuitInject(FeedFilterScreen::class, SessionScope::class) @Inject` class with the screen injected, `RecentActivityTypes` for the quick row):
  - a retained live filter seeded from `screen.initial`
  - a type draft that commits on ApplyTypes and is discarded on DismissTypes
  - tri-state category toggles that expand via `SportCatalog.categories`, shortcuts included
  - full-sub-sport collapse to the sport
  - `Changed` emitted when first shown and on ApplyTypes, SetRange and Clear

  Verify with `FeedFilterPresenterTest` using `circuitx-subcircuit-test` (`outerEvents.awaitEvent()`) covering each of these behaviors.
- [x] 3.3 Implement `FeedFilterUi` in `composeApp`, registered by a `@SubCircuitInject(FeedFilterScreen::class, SessionScope::class) @Inject class FeedFilterSubUi : SubUi<FeedFilterState>`:
  - one sideways-scrolling row with type and date chips and clear (hidden when no type or date filter is active)
  - an `AppBottomSheet` multi-select type picker reusing `ActivityTypePicker`'s category layout and `sportLabel`/`activityTypeLabel`, with tri-state headers and the recent + common quick row
  - date presets and a custom range through `AppDatePickerDialog`
  - new strings in `strings.xml`

  Verify `./gradlew :composeApp:composeStabilityCheck` and detekt (`UseAdaptiveComponents`) pass.

## 4. Feed integration

- [x] 4.1 Add `scope`/`scopes` (`FeedScope` in `FeedScreen.kt`), `filter: FeedFilter` and `filterScreen: FeedFilterScreen` to `FeedState`, and `FeedEvent.SetScope(scope)` and `FilterChanged(filter)`. In `FeedPresenter`:
  - hold the retained scope and canonical filter and a once-created retained `filterScreen`
  - map scope to `timeline` or `authorActivities(viewerDid)` and `FeedFilter` to `ActivityFilter`, resolving local-day bounds with an inclusive end and presets against the clock
  - key the source flow and the refresh effect on the filter, running `activities.sync()` only on first load and explicit refresh

  Verify `FeedPresenterTest` cases:
  - the default is unfiltered Following
  - SetScope(Mine) queries the author feed for the viewer
  - ClearFilters keeps the scope
  - a type+date filter sends the matching params and reads its cache key
  - the filter survives with `filterScreen` unchanged
- [x] 4.2 Render `SubCircuitContent(state.filterScreen, outerEventSink = { state.eventSink(FeedEvent.FilterChanged(it.filter)) })` under the feed's top bar in `FeedUi`, make the title the scope picker (`AppTopBar` `titleMenu`), and show a filter-aware empty state ("No activities match these filters" with clear); verify on desktop with Compose Hot Reload (`:desktopApp:hotRun --auto` + compose-hot-reload MCP) by screenshotting the title's scope menu, chips, type sheet and date picker, and confirm that switching scope and applying a type changes the list

## 5. Integration checks

- [x] 5.1 Run `./gradlew check` and `./gradlew composeStabilityCheck` and verify both pass
- [x] 5.2 Build iOS with the `xcodebuild` simulator command and run `:webApp:wasmJsBrowserDevelopmentRun`; verify the feed filter renders and filters on both
- [x] 5.3 End to end against `scripts/dev.sh --seed alice` (done on web and desktop with alice, who follows bob (one run) and carol (none); the client loads only page 1, so the second-page check is covered by the AppView paging test rather than the UI):
  - follow a second account with mixed sports
  - verify Following includes both athletes and Mine only alice
  - verify a sport, sub-sport and date filter narrow the list correctly across a second page
  - verify a previously viewed filter shows its cached results offline

## Workflow follow-up

- Archive the change with `/opsx:archive` after review, which creates `openspec/specs/feed/activity-query-filters` and `openspec/specs/feed/feed-filter`.
