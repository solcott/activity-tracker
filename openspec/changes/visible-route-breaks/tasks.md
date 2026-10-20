# Tasks

## 1. Break markers model

- [x] 1.1 Replace `RouteMarkers.breakEnds` with `breaks: ImmutableList<Break<T>>` (`Break(stop, resume)`), keep `shownAt` carrying breaks through unchanged, and update `RouteMarkersTest` (unbroken line, several breaks, a one-point piece, no points, given ends); verify `./gradlew :composeApp:jvmTest --tests "*.RouteMarkersTest"` passes
- [x] 1.2 Add `BREAK_COLOR` (amber, about `#FFA000`), set `BREAK_RADIUS` to `END_RADIUS`, and add the resume ring width and connector width and dash constants to `RouteMarkers`; verify `RouteMarkers` documents what each constant draws and the build compiles (`./gradlew :composeApp:compileKotlinJvm`)

## 2. Drawing them

- [x] 2.1 In `RoutePreview`, draw a dashed amber connector per break after the route path, then stops (solid amber, white ring), resumes (white, thick amber ring), start and end; render a ride with one break headlessly on desktop Skia (throwaway jvmTest, not kept) and verify the connector, both break kinds and their size relative to the start marker; record the finding in this task
  - Finding: a throwaway jvmTest (not kept) drew `RoutePreview` headlessly on desktop Skia from the `24785356441_ACTIVITY.fit` ride (every 10th point, both ends of its 288 m break kept), card-sized (360x160 dp) and large (900x600 dp), light and dark. The break shows an amber dashed connector between a solid amber stop dot and a white resume dot with a thick amber ring, both the start/end markers' size. On the dark card, where the break spans about 15 dp, the two amber markers touch but stand out clearly from the teal line, which the old route-coloured rings didn't.
- [x] 2.2 In `ActivityMap.nonWeb`, add a `route-break-connectors` `LineLayer` (MultiLineString of each break's stop and resume, dash array, butt caps) after the route layers and split `route-breaks` into `route-break-stops` and `route-break-resumes` `CircleLayer`s before the start and end; first confirm the pinned maplibre-compose `LineLayer` takes a dash array (else note the fallback chosen here). Verify on the desktop app (`./gradlew :desktopApp:hotRun --auto`, hot-reload MCP screenshot) with the `24785356441_ACTIVITY.fit` ride imported, on its detail map and the enlarged map, in light and dark themes; record the finding in this task
  - Finding: maplibre-compose 0.20.0's `LineLayer` takes `dasharray` (an `Expression<VectorValue<Number>>`, in line widths), so the dp dash and space are divided by `CONNECTOR_WIDTH`; no fallback needed. Reloaded into the running desktop app (explicit-mode hot reload) with the `24785356441_ACTIVITY.fit` ride open. On the enlarged map, a dashed amber connector joins a solid amber stop dot and a white resume dot with an amber ring at the 288 m break, both about twice the route line's width. On the detail page's small map, the two amber markers mark the break clearly against the teal line. Dark theme only: the app follows the macOS appearance, which wasn't changed for this check, so the light theme is covered only by 2.1's light Canvas renders.
- [x] 2.3 Check that the connector stays distinguishable from the base tiles' dashed footpaths on that ride's enlarged map (the trail-dense section near the break); adjust dash length or width in `RouteMarkers` if not, and record the finding in this task
  - Finding: on the enlarged map of that ride, the tiles' footpaths near the break are thin grey dashes. The connector is amber, thicker and has longer dashes, so it reads apart from them at a glance; no constant changed.

## 3. Checks

- [x] 3.1 Run `./gradlew check composeStabilityCheck` and verify it passes
