# Tasks

## 1. Live map frame

- [x] 1.1 `ActivityMap.nonWeb.kt`: measure the map's dp size and set its camera from `RouteMap.frame(width, height, overlayTop)` when the route's key, `overlayTop` or (static maps only) the size changes; drop `cameraForBounds` and `CameraFit`. Verify: `:app-common-ui:compileKotlinJvm` passes.
- [x] 1.2 Recenter flies to the same frame for the current size with `animateCamera`. Verify: compiles on Android and JVM; tapping it on a device was not possible here (see the exploration's findings, Not verified).

## 2. RouteCamera cleanup

- [x] 2.1 Replace `fitCamera`, `FIT_MARGIN` and `MAX_FIT_ZOOM` with `MapFrame.toCameraPosition()`; `RouteSnapshots` asks `RouteMap.frame`. Verify: `grep -rn "fitCamera\|FIT_MARGIN\|MAX_FIT_ZOOM"` finds nothing.
- [x] 2.2 Delete `RouteCameraTest` (its cases are `RouteMapTest`'s). Verify: `:app-common-api:jvmTest --tests "*.RouteMapTest"` passes.

## 3. Look and record

- [x] 3.1 Desktop (`:desktopApp:hotRun`, hot-reload MCP screenshots): a long route and a very short one on the activity page and the enlarged map, compared with the feed image and the iOS map; Android emulator if one is available.
- [x] 3.2 Android screenshot tests: run `:androidApp:testDebugScreenshotTestDefaultTestSuite`; update and look at any reference that changes.
- [x] 3.3 Record findings in `openspec/explorations/ios-native-ui.md` and tick its open item about the live map's `cameraForBounds`.
- [x] 3.4 `./gradlew check -Pkotlin.incremental=false` passes.

## Workflow follow-up

- Archive with `/opsx:archive` (syncs `dev/shared-display-text`).
- Commit in small commits by concern; don't push or merge.
