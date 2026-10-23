# Tasks

## 1. Contracts and shared helpers

- [x] 1.1 Add `app/screens/.../app/segment/SegmentMapScreen.kt`: `SegmentMapScreen(uri)` (`@CircuitSerializable(SessionScope::class)`), an `@Immutable` `SegmentMapState` with `content: ContentState<SegmentMapContent?>` and `eventSink`, an `@Immutable` `SegmentMapContent(polyline, name, credit: SegmentCredit?)`, and `SegmentMapEvent.Back` and `Retry`.
  - Also add `SegmentEvent.OpenMap` to `SegmentScreen.kt`.
  - Verify `./gradlew :app-screens:compileKotlinJvm` passes.
- [x] 1.2 Move `mapLine` out of `SegmentPresenter.kt` into a shared `internal` file in the segment package of `:app-presenters`, next to `creditOf`, so both presenters use them. Verify `./gradlew :app-presenters:jvmTest --tests "*.SegmentPresenterTest"` still passes.

## 2. Presenters

- [x] 2.1 Make `SegmentPresenter` handle `OpenMap` with `navigator.goTo(SegmentMapScreen(uri))`. Verify with a case in `SegmentPresenterTest` that the navigator gets `SegmentMapScreen(uri)`.
- [x] 2.2 Add `SegmentMapPresenter` (`@CircuitInject(SegmentMapScreen::class, SessionScope::class)`). It loads `segments.segmentOutcome(uri)` with `produceRetainedContentState` under a `loadKey`, and maps it to `SegmentMapContent` with `mapLine` and `creditOf`. `Back` pops and `Retry` reloads.
  - Verify with tests in `SegmentPresenterTest`, which shares its `getSegment` mock:
    - an OSM segment gives its precision-5 line, name and `OpenStreetMap` credit;
    - an athlete's segment gives no credit;
    - a failed load gives a `LoadSegment` error, and `Retry` loads it again;
    - `Back` pops.
  - Run `./gradlew :app-presenters:jvmTest`.

## 3. UI

- [x] 3.1 Move `Credit`, `CreditText` and `OSM_COPYRIGHT_URL` out of `SegmentUi.kt` into an `internal` file in `composeApp/.../app/segment/`, and pass `onExpand = { state.eventSink(SegmentEvent.OpenMap) }` to the segment screen's `ActivityMap`.
  - Verify on desktop (`./gradlew :desktopApp:hotRun --auto`, compose-hot-reload MCP) with a seeded OSM segment (`segments/segment-records` seed script):
    - the credit in the list looks as before;
    - a drag over the map scrolls the screen;
    - clicking the map or the expand button opens the segment map.
  - Finding (iOS simulator, iPhone 17, instead of desktop: this session's compose-hot-reload MCP was attached to another checkout's desktop app): on seeded OSM, agency and athlete segments the list credit looked as before ("© OpenStreetMap contributors" link plus ODbL; "From USDA Forest Service trail data" plus CC0; none for the athlete's). A drag up over the map left it unmoved (the page was too short to scroll). Tapping the map body and tapping the expand button each opened the segment map.
- [x] 3.2 Add `composeApp/.../app/segment/SegmentMapUi.kt` (`@CircuitInject(SegmentMapScreen::class, SessionScope::class)`): an `AppScaffold` with an `AppTopBar` (segment name, back), then `ContentStateLayout` with `UiError.Kind.LoadSegment` and `RefreshStatus`, and a `Box` holding `ActivityMap(interactive = true, height = Dp.Infinity)` with the credit aligned top-start on a rounded surface-coloured chip of capped width. Use only `ui/adaptive` wrappers.
  - Verify on desktop with the OSM segment and an agency segment:
    - the segment is framed with start and finish marked;
    - scroll-wheel zoom and drag pan work, and the map doesn't rotate;
    - recenter re-frames the segment;
    - the credit stays visible while zoomed, is clear of the logo, the tile attribution and recenter, and clicking the OSM credit opens OpenStreetMap's copyright page;
    - back returns to the segment screen.
  - Also check an athlete's segment shows no credit chip, and that the chip reads in both light and dark themes. Record findings in this task.
  - Finding (iOS simulator): name in the top bar; framed with start and finish marked; pinch zoomed in, a two-finger twist left it north-up, a one-finger drag panned it, recenter re-framed it; the OSM chip stayed while zoomed, cleared the logo, tile attribution and recenter, and its link opened openstreetmap.org/copyright; the athlete segment showed no chip; the chip read in light and dark; back and the left-edge swipe returned to the segment screen. The tab bar showed over the map at first, so `SegmentMapScreen` now hides navigation like `RouteMapScreen` (`TopLevelDestination.showsNavigation`, with a test case).
  - On the agency segment, whose line starts in the top-left corner, the chip covered the start marker; 3.3 fixes it.
- [x] 3.3 Add `overlayTop: Dp = 0.dp` to `ActivityMap` (expect, both actuals, KDoc). The nonWeb actual adds it to the top fit margin, for the first fit and for recenter; wasmJs ignores it. `SegmentMapUi` measures the credit chip with `onSizeChanged` and passes its height plus its margin, or 0 with no credit.
  - Verify on the iOS simulator: the agency segment's whole line, start marker included, shows below the chip, both when it opens and after panning and recentering; the activity route map frames as before.
  - Finding (iOS simulator): the agency segment's start marker now opens below the chip, and recenter after a pan frames it there again; the inline segment map, which passes nothing, framed as before. The activity route map wasn't reopened: it passes no `overlayTop`, so its margin is the same 24 dp. Seen in passing, not from this change: the finish marker at the bottom-right corner sits partly under MapLibre's attribution button, on every map that uses the 24 dp margin.

## 4. Integration checks

- [x] 4.1 Check on the Android emulator and the iOS simulator at phone width:
  - tapping the map opens it, pinch zoom and pan work, and a twist doesn't rotate;
  - recenter works, and system back or edge swipe returns;
  - a long agency attribution wraps inside the chip without covering the recenter button.
  - Record the findings.
  - Finding (iOS simulator): see 3.2.
  - Finding (Android emulator, Pixel 10 Pro): on the OSM segment, tapping the map body and the expand button each opened it full screen with no bottom nav and the line framed below the chip. A one-finger drag panned it, a double tap zoomed in, recenter re-framed it, and system back returned. Not checked here: pinch and twist (adb can't send multi-touch; same `MapInteractions` as iOS, where both were checked).
  - Not checked: a long agency attribution wrapping in the chip. The seed script only writes "USDA Forest Service", and adding a longer one means changing the seed script.
- [x] 4.2 Check offline: open the segment map with the AppView stopped, confirm a load error with retry, and that back returns to the segment screen.
  - Finding (Android emulator, airplane mode instead of stopping the AppView, which other sessions share): the map showed "Couldn't load segment. You're offline." with Retry in its place; with airplane mode off, Retry loaded the map; offline again, the top bar's back returned to the segment screen.
- [x] 4.3 Check that the web segment screen (`./gradlew :webApp:wasmJsBrowserDevelopmentRun`) still shows the static preview with no expand button, and that a click does nothing.
  - Finding: the OSM segment showed the canvas preview with start and finish marked, no expand button and the credit in the list; clicking the line did nothing.
- [x] 4.4 Run `./gradlew check composeStabilityCheck` and verify it passes.

## Workflow follow-up

- Archive the change once it is implemented and reviewed, which syncs `segments/segment-map-viewer` into the main specs.
