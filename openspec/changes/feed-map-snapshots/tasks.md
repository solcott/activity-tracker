# Tasks

## 1. Snapshot spike

- [x] 1.1 On desktop (`:desktopApp:hotRun`), throwaway code creates a `MapSnapshotter` on `DefaultMapRuntime.instance` with `MapStyles.LIGHT` and a `LineLayer` that reads a `mutableStateOf` route. It captures twice with different routes and writes both PNGs to the scratchpad. Verify that the two images show different routes. If they don't, record in design.md that we use a snapshotter per capture. Also note whether capture needs `ProvideMapPresentationHost`. Then remove the throwaway code.

## 2. Shared route layers

- [x] 2.1 Move the casing and route `LineLayer`s, `RouteMarkerLayers`, `lineJson` and `pointsJson` from `ActivityMap.nonWeb.kt` into an internal `@MaplibreComposable RouteLayers(routeJson, markers, color)` in `nonWebMain`, and have `ActivityMap` call it. Verify `./gradlew :composeApp:jvmTest` and that the detail map looks unchanged in a desktop hot-reload screenshot.

## 3. Camera fit and PNG encoding

- [x] 3.1 Add the pure `fitCamera(bounds, size, margin): CameraPosition` (Web Mercator centre, `log2` zoom against 512-dp tiles, clamped) in `nonWebMain`. Verify JVM tests: a route spanning 0.01° of longitude at the equator, a tall north–south route, a single-point route (clamped zoom) and a route near 60°N.
- [x] 3.2 Add `internal expect fun ImageBitmap.encodePng(): ByteArray`, with an Android actual (`Bitmap.compress`) and a `nonAndroidMain` actual (Skia). Verify with a JVM test that round-trips a small bitmap through `Image.makeFromEncoded` with the same size and pixels.

## 4. Snapshot rendering through Coil

- [x] 4.1 Add `RouteSnapshots` in `nonWebMain`, the one place that calls the snapshotter API: one lazy snapshotter per theme, and `suspend fun render(input, sizePx, density): ImageBitmap` behind a `Mutex`, using the approach task 1.1 chose. Errors surface as `MapSnapshotException`, logged once per process. Verify that the desktop smoke run in 6.2 renders an image.
- [x] 4.2 Add the `RouteSnapshot(polyline, gaps, dark, routeColor, density)` model, a `Keyer` (all model fields, `options.size`, the style URL and `ROUTE_IMAGE_VERSION`), and a `Fetcher` that renders, encodes to PNG and returns a `SourceFetchResult`. Verify JVM tests that the key changes with each input and is stable for equal inputs.
- [x] 4.3 Create the route-image `ImageLoader` in `App.kt`: its own memory cache, a disk cache in a `route-maps` directory, and the fetcher and keyer registered. Keep it in a process-wide `RouteImages` store. Clear both caches on sign-out and on a switch to another account, and the disk cache at startup when there is no session. Verify with a JVM test that a written entry is gone after the sign-out clear (spec: "Signing out drops cached maps").

## 5. Feed card

- [x] 5.1 Add `expect fun RouteMapTile(polyline, gaps, modifier, height)` in `commonMain` with two actuals:
  - **nonWeb:** `RoutePreview` underneath, then `AsyncImage` on top. Show the credit overlay only on `Success`. Under `LocalInspectionMode`, draw `RoutePreview` alone.
  - **wasmJs:** `RoutePreview`.
  - Add the credit text to string resources.

  Verify that `:composeApp:jvmTest`, including `ContentLoadingConformanceTest` and `AppPreviewUiTest`, passes.
- [x] 5.2 Use `RouteMapTile` for the map slot in `ActivityCard`. Verify that `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite` passes with unchanged references (spec: "Previews and tests draw the line preview").
- [x] 5.3 Update the "Feed vs detail" bullet in `BACKLOG.md` to describe device snapshots. Add a backlog item for the Android Vulkan runtime that notes the single-`.so` constraint. Update `RoutePreview`'s KDoc, which still calls it a placeholder, to say it's the fallback and web's map. Verify that the docs match the code.

## 6. Integration checks

- [x] 6.1 `./gradlew check composeStabilityCheck` passes.
- [x] 6.2 Desktop: run `:desktopApp:hotRun --auto` signed in to the local stack, and use compose-hot-reload's `take_screenshot` on the feed. Cards should show map images with the credit, matching the detail map's line and markers. Switch to dark mode and the images should redraw dark. Scroll away and back and the images should show at once.
- [ ] 6.3 Android emulator (`:androidApp:installDebug`) and iOS simulator: the feed shows map images. In airplane mode with the app's data cleared, cards show line previews and no errors.
- [x] 6.4 Privacy: as a follower, the feed shows no map tile for an activity whose Map category is Only me. As a mutual, a Mutuals-map ride shows the trimmed route. After sign-out the `route-maps` cache directory is empty.

## Workflow follow-up

- Archive the change once implemented and reviewed (`/opsx:archive`), which creates `openspec/specs/feed/feed-route-map/spec.md`.
