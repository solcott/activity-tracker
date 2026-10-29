# Design

## Context

- **The card's map slot.** `ActivityCard` passes `MediaRow` a `map` slot that draws `RoutePreview(record.polyline, …, gaps)` over `effectiveRecord()`. That is the polyline and breaks this viewer was served, already trimmed and gated by tier.
- **The live map.** `ActivityMap.nonWeb.kt` draws the same route on a live MapLibre map, using:
  - a white casing and the route `LineLayer`;
  - `RouteMarkerLayers`, which draws the dashed connectors and the stop, resume, start and end circles;
  - `RouteCurve.smooth` for the line's curves;
  - `MapStyles.LIGHT`/`DARK`, the OpenFreeMap styles.
- **The snapshotter.** maplibre-compose 0.20.0 has a common `MapSnapshotter`, built by `DefaultMapRuntime.instance.createSnapshotter(baseStyle) { …style DSL… }`. Its `capture(DpSize) { cameraPosition; density }` returns an `ImageBitmap`.
  - Offscreen backends: Android uses OpenGL (the runtime we ship), iOS uses Metal, and desktop uses Metal on macOS and Vulkan elsewhere, matching `:desktopApp`'s runtimes.
  - Captures run one at a time, in order.
  - A capture fails as a whole if any tile fails.
  - Style content is evaluated in a fresh `Composition` for each capture (`DefaultStyleCompositionEvaluator`).
- **Coil.** Coil 3.6.3 is the app's image loader (`App.kt`: a singleton with `HeldPhotoFetcher`). PNG and WebP encoding already exists for photos: Skia in `nonAndroidMain` and `Bitmap` on Android (`PhotoCompression.*`).
- **Web.** MapLibre has no wasmJs target. `ActivityMap.wasmJs.kt` already falls back to `RoutePreview`.

## Goals / Non-Goals

**Goals:**
- One rendering path for Android, iOS and desktop, in `nonWebMain`.
- The image draws exactly what `ActivityMap` draws, from shared layer code.
- Scrolling costs no GL surfaces. Every capture is cancellable and cached.

**Non-Goals:**
- An Android Vulkan runtime. An APK can carry only one `libmaplibre-native-c.so`, so that switch is a separate follow-up.
- Server-side images.
- Snapshots anywhere other than the feed card. The detail page and segment pages keep their live maps.
- The owner's 1 m full-track line. Cards draw the served polyline, as they do today.

## Decisions

### Snapshots through Coil, not a hand-rolled cache
Add a model `RouteSnapshot(polyline, gaps, dark, routeColor, density)` plus a Coil `Fetcher` and `Keyer`, all in `nonWebMain`.
- The fetcher takes its pixel size from Coil's resolved `options.size`, which is the card tile's laid-out size.
- It captures the image, encodes it as PNG and returns a `SourceFetchResult`.
- Coil then provides memory and disk caching, cancels a request when its card leaves the composition, and dedupes requests.
- The `Keyer` hashes the polyline, gaps, theme, density, size, the style URL and a `ROUTE_IMAGE_VERSION` constant. Changing the layers bumps that constant, which invalidates old images.

*Alternatives considered:*
- An `ImageFetchResult` (memory only) loses images on restart, which the spec's "Drawn maps are reused" forbids.
- A custom LRU on disk would duplicate what Coil does.

### A dedicated ImageLoader for route images
Route images use their own `ImageLoader`, with a disk cache in its own directory (`route-maps`, beside Coil's own in the system temporary directory) and its own memory cache. `RouteImageStore` (`RouteImages`, process-wide, like the photo loader) builds it on first use, and `RouteMapTile` reads it from there; no CompositionLocal is needed, since nothing else ever supplies another.
- Coil leaves disk caching to fetchers, so `RouteSnapshotFetcher` reads and writes the loader's disk cache itself, under the keyer's key.
- `App` tells the store who is signed in (`onRouteImageSession`) once the session is restored and whenever it changes. It clears both caches whole when signed out (a sign-out, or starting signed out in case the app was killed mid sign-out) and on a switch from one account to another. It doesn't clear on the `key(did)` session disposal itself, which an Android configuration change also causes.

This loader exists only so these images can be cleared without touching photos. Coil's `DiskCache` can't list or clear entries by kind.

*Alternative considered:* sharing the singleton loader. That would leave non-public routes on disk after sign-out, or force clearing photos as well.

### One snapshotter per theme; per-capture route via state
A `RouteSnapshots` object, in `nonWebMain`, lazily keeps two `MapSnapshotter`s on `DefaultMapRuntime.instance`, one for `MapStyles.LIGHT` and one for `MapStyles.DARK`, so each style loads once. Each snapshotter's style content reads a `mutableStateOf<RouteLayersInput?>`. A capture:
1. takes a `Mutex`;
2. sets the input;
3. captures with the fitted `CameraPosition` and the request's density;
4. releases the mutex.

The snapshotter serialises captures anyway, so the mutex costs no throughput.
- **Spike result (desktop, macOS Metal):** confirmed. One snapshotter captured two routes in a row, each set through the state before its capture, and each image showed its own route. No `ProvideMapPresentationHost` was needed. The first capture took about 3 s (style, glyphs and tiles); the second, in the same area, took 169 ms. Outside an app, as in a unit test, `DefaultMapRuntime.instance` can't infer an application id, so a test would need its own `createMapRuntime { cacheFile = … }`; the app itself is fine.
- **Fallback, had it failed:** create a snapshotter for each capture and close it afterwards. The style JSON comes from MapLibre's cache, so this costs a style parse per image.

### Shared route layers
Move the casing and route `LineLayer`s and `RouteMarkerLayers` out of `ActivityMap.nonWeb.kt` into an internal `@MaplibreComposable RouteLayers(routeJson, markers, color)` in `nonWebMain`, along with `lineJson` and `pointsJson`. `ActivityMap` and the snapshotter both call it.
- The highlight layer stays in `ActivityMap`; cards don't use it.
- The route colour comes in as a parameter: the snapshotter has no `MaterialTheme`, so the composable reads `colorScheme.primary` and puts it in the model and the key.

### Camera fitted by Web Mercator math
`capture` takes a `CameraPosition`, not bounds. A pure `fitCamera(bounds, size, margin): CameraPosition` works out the centre as the midpoint in Mercator space, and the zoom as `log2` of the tighter of the width and height ratios against 512-dp world tiles, less the 24 dp `FIT_MARGIN` on each side. It's clamped to the style's zoom range, so a near-zero-length route doesn't zoom past it.

It's tested on JVM against known bounds. *Alternative considered:* rendering once to read `cameraForBounds`. The snapshotter has no `MapState`, so that's not possible.

### The card composable and its fallback
`expect fun RouteMapTile(polyline, gaps, modifier, height)` in `commonMain`:
- **nonWeb actual:** `AsyncImage(RouteSnapshot(…), imageLoader = RouteImages.loader(context))`. `RoutePreview` is drawn beneath it, so it shows through until the image arrives and stays when the request fails. The credit `Text` (© OpenFreeMap © OpenStreetMap, `labelSmall`, on a translucent surface in the bottom-end corner) is drawn only once the state is `Success`. Under `LocalInspectionMode` the actual returns `RoutePreview` alone.
- **wasmJs actual:** `RoutePreview`.
- `ActivityCard` swaps its `map` slot to `RouteMapTile`.

The credit is drawn in Compose rather than burned into the PNG, so it stays crisp, localisable and the same size at every density.

### PNG encoding
`internal expect fun ImageBitmap.encodePng(): ByteArray`, with these actuals:
- `androidMain`: `asAndroidBitmap().compress(PNG)`.
- `nonAndroidMain`: Skia, `Image.makeFromBitmap(asSkiaBitmap()).encodeToData(PNG)`, following `PhotoCompression.nonAndroid.kt`.

PNG is used rather than WebP or JPEG because it's lossless, keeping lines and text crisp, and every platform can encode it.

## Risks / Trade-offs

- **[The snapshot content may not re-read state between captures]** → The first task is a spike, and the fallback is a snapshotter per capture.
- **[The first feed load renders several images in a row, each fetching tiles]** → Coil cancels off-screen requests. MapLibre's ambient cache shares tiles across captures, and the line preview shows meanwhile.
- **[An offscreen renderer is missing or broken on some Android GL drivers or VMs]** → The capture throws `MapSnapshotException`, the line preview stays, and the error is logged once rather than per card.
- **[More traffic to OpenFreeMap]** → It has no key and no limits, the requests are cached, and moving tile providers is still a URL swap in `MapStyles`.
- **[maplibre-compose's pre-1.0 API churn]** → Keep snapshotter calls inside `RouteSnapshots` and the fetcher, as `ActivityMap` keeps its own.
- **[Coil threads]** → The fetcher runs off the main thread. `capture` dispatches to the runtime's own dispatcher, and the input state is written under a `Snapshot.withMutableSnapshot`.
- **[The snapshotter on desktop may need the AWT presentation host that maps use]** → The desktop smoke test covers this. If it does, the snapshotter is created inside `ProvideMapPresentationHost`'s scope.

## Migration Plan

This is client-only, with nothing stored remotely. Rolling back means reverting the card back to `RoutePreview`. The `route-maps` cache directory is then orphaned, and the next sign-out or a reinstall clears it.
