# Tasks

## 1. Break markers model

- [ ] 1.1 Replace `RouteMarkers.breakEnds` with `breaks: ImmutableList<Break<T>>` (`Break(stop, resume)`), keep `shownAt` carrying breaks through unchanged, and update `RouteMarkersTest` (unbroken line, several breaks, a one-point piece, no points, given ends); verify `./gradlew :composeApp:jvmTest --tests "*.RouteMarkersTest"` passes
- [ ] 1.2 Add `BREAK_COLOR` (amber, about `#FFA000`), set `BREAK_RADIUS` to `END_RADIUS`, and add the resume ring width and connector width and dash constants to `RouteMarkers`; verify `RouteMarkers` documents what each constant draws and the build compiles (`./gradlew :composeApp:compileKotlinJvm`)

## 2. Drawing them

- [ ] 2.1 In `RoutePreview`, draw a dashed amber connector per break after the route path, then stops (solid amber, white ring), resumes (white, thick amber ring), start and end; render a ride with one break headlessly on desktop Skia (throwaway jvmTest, not kept) and verify the connector, both break kinds and their size relative to the start marker; record the finding in this task
- [ ] 2.2 In `ActivityMap.nonWeb`, add a `route-break-connectors` `LineLayer` (MultiLineString of each break's stop and resume, dash array, butt caps) after the route layers and split `route-breaks` into `route-break-stops` and `route-break-resumes` `CircleLayer`s before the start and end; first confirm the pinned maplibre-compose `LineLayer` takes a dash array (else note the fallback chosen here). Verify on the desktop app (`./gradlew :desktopApp:hotRun --auto`, hot-reload MCP screenshot) with the `24785356441_ACTIVITY.fit` ride imported, on its detail map and the enlarged map, in light and dark themes; record the finding in this task
- [ ] 2.3 Check that the connector stays distinguishable from the base tiles' dashed footpaths on that ride's enlarged map (the trail-dense section near the break); adjust dash length or width in `RouteMarkers` if not, and record the finding in this task

## 3. Checks

- [ ] 3.1 Run `./gradlew check composeStabilityCheck` and verify it passes
