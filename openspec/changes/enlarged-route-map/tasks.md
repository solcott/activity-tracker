# Tasks

## 1. Contracts and route selection

- [x] 1.1 Add `app/screens/.../app/map/RouteMapScreen.kt`: `RouteMapScreen(uri)` (`@CircuitSerializable(SessionScope::class)`), an `@Immutable` `RouteMapState` and `RouteMapEvent.Back`.
  - `RouteMapState` holds polyline, gaps, ends, title or activity type, a loading flag, `error` and `eventSink`.
  - Also add `ActivityDetailEvent.OpenMap`.
  - Verify `./gradlew :app-screens:compileKotlinJvm` passes.
- [x] 1.2 Move the shown-route selection out of `PageMedia` (`ActivityDetailUi.kt`) into a presenter-side helper in `:app-presenters`, and have `ActivityDetailState` carry its result.
  - The helper picks the owner's untrimmed line and its gaps when loaded, otherwise the served polyline and `polylineGaps`, with ends only on the owner's line.
  - `PageMedia` then only renders.
  - Verify `./gradlew :app-presenters:jvmTest --tests "*.ActivityDetailRouteTest"` still passes, with a case added for the fallback to the served polyline.

## 2. Presenters

- [x] 2.1 Make `ActivityDetailPresenter` handle `OpenMap` with `goTo(RouteMapScreen(uri))`. Verify with a test in `ActivityDetailPresenterTest` that the navigator gets `RouteMapScreen(uri)`.
- [x] 2.2 Add `RouteMapPresenter` (`@CircuitInject(RouteMapScreen::class, SessionScope::class)`). It loads the activity with `feed.activity(uri)` and its rewrites, uses the effective record, calls `rememberOwnRoute` for the owner, and uses the helper from 1.2. `Back` calls `navigator.pop()`.
  - Verify with a new `RouteMapPresenterTest`:
    - a viewer gets the trimmed polyline and no ends;
    - the owner gets the untrimmed line, gaps and ends (reuse the `ActivityDetailRouteTest` fixtures);
    - a failed load gives a `LoadActivity` error;
    - `Back` pops.
  - Run `./gradlew :app-presenters:jvmTest`.

## 3. Map UI

- [x] 3.1 Add `onExpand: (() -> Unit)? = null` and `interactive: Boolean = false` to `ActivityMap` (expect plus both actuals), and update its KDoc.
  - nonWeb: when `onExpand` is set, add a tap on the map, using a MapLibre click callback or a clickable layer as a fallback, and an expand `AppIconButton` in the top-end corner.
  - nonWeb: when `interactive` is true, use `MapInteractions(Standard)` with rotate and pitch off, and add a recenter button that re-fits the route's bounds.
  - wasmJs: ignore both parameters.
  - Add string resources for the expand and recenter content descriptions.
  - Verify `./gradlew :composeApp:compileKotlinJvm :composeApp:compileKotlinWasmJs` passes.
- [x] 3.2 Pass `onExpand = { eventSink(ActivityDetailEvent.OpenMap) }` from `PageMedia`. Verify on desktop (`./gradlew :desktopApp:hotRun --auto`, compose-hot-reload MCP):
  - a drag over the map scrolls the page;
  - clicking the map or the expand button opens the route map.
  - Finding (Android emulator instead of desktop: the running desktop app belonged to a parallel session): on MTB run 2's detail page, a drag up over the map scrolled the page without moving the map, and tapping the map body or the expand button (top-end, clear of the logo and attribution) each opened the route map.
- [x] 3.3 Add `composeApp/.../app/map/RouteMapUi.kt` (`@CircuitInject`): an `AppScaffold` and an `AppTopBar` with back and title, then a loading indicator, `errorText`, or `ActivityMap(interactive = true)` filling the body. Use only `ui/adaptive` wrappers.
  - Verify on desktop:
    - the route is framed;
    - scroll-wheel zoom and drag pan work and the map doesn't rotate;
    - recenter re-frames;
    - back returns to the detail page.
  - Record screenshots in this task.
  - Finding (Android emulator): the route map opened framed on the whole route with the title in the top bar and no bottom nav; a one-finger drag panned it; recenter re-framed the route; system back and the top bar's back each returned to the detail page; no crash in logcat. On desktop (wide layout, dark theme) the expand button on the side pane's map opened it, scroll-wheel zoomed it in, a right-button drag (rotate/tilt elsewhere) left it north-up and flat, recenter re-framed it, and back returned to the detail page with the sidebar.

## 4. Docs

- [x] 4.1 Update `BACKLOG.md`'s "Show activities on real maps" renderer note to say the detail map opens a full-screen interactive map. Verify the note no longer lists it as future work.

## 5. Integration checks

- [x] 5.1 Check on the Android emulator and the iOS simulator: tap opens the map, pinch zoom and pan work, a twist doesn't rotate, recenter works, and system back or edge swipe returns. Record any edge-swipe conflict on iOS.
  - Finding (iOS simulator, iPhone 17): the expand button (Cupertino arrows) and a tap on the map each opened the route map under the Compose iOS bar with the tab bar hidden; a two-finger pinch zoomed in, a two-finger twist left it north-up, recenter re-framed the route, and a left-edge swipe went back to the detail page without panning the map. Android (emulator): tap opens, pan, recenter and system back work (see 3.2, 3.3); pinch wasn't injected there (adb can't send multi-touch on the unrooted image), but it's the same `MapInteractions` that zooms on iOS and desktop.
- [x] 5.2 Check that the web detail page (`./gradlew :webApp:wasmJsBrowserDevelopmentRun`) still shows the static preview with no expand button, and that a tap does nothing.
  - Finding: the web detail page showed the canvas preview with no expand button, and clicking the route did nothing.
- [x] 5.3 Run `./gradlew check composeStabilityCheck` and verify it passes.

## Workflow follow-up

- Archive the change once it is implemented and reviewed, which syncs `feed/route-map-viewer` and `feed/route-markers` into the main specs.
