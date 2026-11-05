# Tasks

## 1. Lexicon and the Kotlin mirror

- [x] 1.1 Add `lexicons/app/activitytracker/feed/searchSegmentNames.json`: `q` (required, max 100 graphemes), the optional box `minLat/minLng/maxLat/maxLng` and centre `nearLat/nearLng` (1e-7 degrees, same bounds as `searchSegments`), `sport` (same `knownValues`), `limit` (1 to 100, default 30) and `cursor`. Its output is `cursor?` and `segments: segmentView[]`, and `InvalidRequest` covers a partial box or centre and a box over 1 degree. The description says a page may be short with a cursor. Mirror it in `core/lexicon` (`Queries.kt` `SearchSegmentNames`, `Nsid`). Verify that an `AtJson` round-trip test passes and that `lexicon-auditor` reports no mismatch.

## 2. AppView name search

- [x] 2.1 Add `searchNames(viewer, params)` to `api/SegmentViews.kt`, per design decision 1. It uses the same served, unhidden and listed predicates as `search`, leaves out `kind = lift`, and runs an escaped `LIKE` over `lower(current_name)`. With a box, it adds the overlap, the `segment_cells` `exists` and `crosses()`. It orders by a rank `Case`, then distance from `near` (the cosine bound as a literal), then `uri`, with an offset cursor. Clamp with `Paging.clamp` and validate the box with `requireSearchBox`. Verify that `./gradlew :server-appview:test` compiles it.
- [x] 2.2 Route `feed.searchSegmentNames` in `api/Routes.kt` with optional viewer auth, as `searchSegments` is. Verify with a route test that a signed-out request answers 200.
- [x] 2.3 Add tests in `SegmentNameSearchTest` for every `segments/segment-records` name scenario: current name, not the original; lift excluded; proposed visible only to its creator and riders; blank query; the rank order prefix, then word, then anywhere; nearest within a rank; box filter; box over 1 degree; partial box answers `InvalidRequest`; sport group; and a `!hide` segment excluded except for its author. Verify that `./gradlew :server-appview:test --tests "*.SegmentNameSearchTest"` passes.

## 3. Client data

- [x] 3.1 Add `SegmentRepository.namesPage(params): Outcome<Page<SegmentView, Unit>>` and `areaSegments(box, sport, cap = 300): Outcome<AreaSegments>`. The second follows `searchSegments` cursors until none are left or the cap is reached, and reports whether the cap was hit. Verify with `SegmentRepositoryTest` cases: all cursors followed, the cap stops paging, and the name params are encoded (`MockEngine`).
- [x] 3.2 Add a way to read the start of the athlete's latest own activity with a route from the cached feeds, else the AppView's first page of them (`FeedRepository.latestOwnStart`): the first point of its served, privacy-trimmed polyline, or null. Verify with a core-data jvmTest over an in-memory Room database.

## 4. Map composable

- [x] 4.1 Add `MapSegmentLine`, `MapViewport` (box, centre, too wide) and `MapCameraRequest` to `:app-common-api` (`@Immutable`), and `expect fun SegmentsMap(...)` with `expect val interactiveMapSupported` to `:app-common-ui`. Verify that `./gradlew :app-common-ui:compileKotlinJvm` passes and `composeStabilityCheck` passes on `:app-common-api`.
- [x] 4.2 Implement the `nonWebMain` actual with MapLibre: one GeoJSON source; run, lift (muted, dashed) and selected layers; a click on a run line selects it, and a click elsewhere clears; on camera idle, report the viewport with too wide set when the box is over 1 degree; apply each camera request once. Add the `LocalInspectionMode` canvas fallback. Make the wasmJs actual a no-op with `interactiveMapSupported = false`. Verify on desktop with `./gradlew :desktopApp:hotRun --auto` once the screen exists (task 6.3), and through the browse screen's screenshots, which draw the fallback.

## 5. Browse screen contract and presenter

- [x] 5.1 Add `SegmentBrowseScreen`, `SegmentBrowseState` and `SegmentBrowseEvent` to `feature/segment/api` (use the `new-screen` skill). The state holds a `TextFieldState` query, `hasArea`, `tooWide`, `area: ContentState<ImmutableList<BrowsedSegment>>` with a cap-hit flag, `mapLines`, `results: PagedState<SegmentView, Unit>`, `scope`, `sport`, `selected`, `camera`, `needsStart`, and `eventSink`. The events are `ViewportChanged`, `PermissionResult`, `Select`, `Open`, `ScopeChanged`, `SportChanged`, `LoadMore`, `Retry` and `SearchEverywhere`. Verify that `composeStabilityCheck` passes on `:feature-segment-api`.
- [x] 5.2 Implement `SegmentBrowsePresenter` per design decisions 2, 3, 4 and 6. Area browse is debounced, keeps its content on a new area and is keyed by sport. Name results use `rememberRetainedPaging` with `KeepItems`, keyed by scope and sport. The start is a first location fix within 5 s, else the latest activity's start, else the prompt. It sorts runs by distance from the centre, keeps lifts on the map only, and opens `SegmentScreen` on `Open`. Verify with jvmTests using `presenterTestOf` and `SessionTestFixture`: the area loads and sorts nearest first; too wide makes no request; sport change clears; query refinement keeps results; This area becomes Everywhere on `SearchEverywhere`; no viewport means an Everywhere search with no `near`; a permission refusal falls back to the latest activity; and lifts are left out of the list.

## 6. Browse UI and the Search tab

- [x] 6.1 Implement `SegmentBrowseUi` with `ui/adaptive` pieces. Compact: the map with the list in a bottom sheet area. Medium and Expanded: the list beside the map. On top: the search box, scope switch (only with `hasArea`), sport chips, and the zoom-in, prompt and nothing-in-this-area states with the search-everywhere action. Rows show name, sport and length; the selected row is marked and scrolled to. Loading goes through `ContentStateLayout`, `pagedRefreshStatus` and `pagedFooter`. Web, without `interactiveMapSupported`, shows search and list only. Put new strings in `:ui-resources`. Verify with `SegmentBrowseUiTest` (jvm, `AppPreview`): loaded, too wide, name results, nothing in this area and nowhere to open. The web layout can't render on the JVM (`interactiveMapSupported` is true there), so 7.2 checks it in the web app.
- [x] 6.2 Add `SegmentBrowseSamples` to `:app-fixtures` (loaded area with lifts, too wide, name results, empty in this area, area loading, failure, web) and add the screen to `ContentLoadingConformanceTest`. Add `SegmentBrowseScreenshots.kt` and record its references with `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite`, after reviewing them. Verify that `ContentLoadingConformanceTest` and `ScreenshotCoverageTest` pass.
- [x] 6.3 Add the Athletes / Segments switch to the Search tab per design decision 7. `AthleteSearchState.mode` is saveable with a `ModeChanged` event; in Segments mode, render `CircuitContent(SegmentBrowseScreen)` with a navigator that forwards `goTo` and `pop` to the presenter as events (`GoTo`, `Back`), since Circuit's `NavEvent` is in circuit-foundation, which presenters and `-api` modules don't use. Add `:feature-segment-api` to `feature/search`'s modules. Verify with an `AthleteSearchPresenterTest` case for the mode change and nav forwarding, an updated `AthleteSearchUi` screenshot, and `./gradlew checkModuleBoundaries`.
- [x] 6.4 Request location permission from the UI with `rememberLocationPermissionRequest` when the presenter reports `needsStart` and the platform supports location, sending `PermissionResult`. Verify on Android: first open prompts; granted centres on the device location; denied centres on the latest activity.

## 7. Integration checks

- [x] 7.1 Add a composeApp jvmTest spanning features (`SearchTabSegmentsTest`): switch the Search tab to Segments, open a screen from the browse screen onto the tab's back stack, go back, and find the same retained browse screen. Switching to another tab and back needs the app shell, so 7.2 checks it in the running app. Verify that `./gradlew :composeApp:jvmTest` passes.
- [x] 7.2 Run end to end against the local stack (`scripts/dev.sh --seed alice` plus a seeded segment): browse the area on desktop and Android, search a name in both scopes, see a lift muted on the map, and search on web (`:webApp:wasmJsBrowserDevelopmentRun`) with the list only. Verify by screenshots of each.
- [x] 7.3 Run `./gradlew check`. Verify that it passes, including detekt, ktfmt, `composeStabilityCheck`, `checkModuleBoundaries` and the screenshot comparison.

## Workflow follow-up

- When archiving, add segment browse to the paged lists named in `ui/content-loading`'s Purpose.
- Archive the change after review.
