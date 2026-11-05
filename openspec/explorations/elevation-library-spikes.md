# Elevation library spikes

Report-only spikes for the `segment-elevation-profile` change, run on 2026-11-05. No production code changed. The lines used are real
Corner Canyon (Draper, UT) segments from a `us-utah` importer run. The terrain checks read USGS 3DEP data straight from `prd-tnm.s3.amazonaws.com`.

## 1. Line resampling with spatial-k turf

**Question.** Could `org.maplibre.spatialk:turf` replace or back `SegmentGeometry.resample` and `distanceMeters` in `core/location`?

**Version and targets.** maplibre-compose 0.20.0 requires spatial-k `geojson` and `units` **0.8.0**, and turf 0.8.0 is the newest
release. Its Gradle module publishes `jvm` (which Android uses, since there is no separate Android variant), `iosArm64`,
`iosSimulatorArm64`, `iosX64`, `wasmJs`, `js` and the other native targets. It needs Kotlin stdlib 2.4.10, and we're on 2.4.21. Licence MIT.

**Size.** JVM jars: turf 126 KB, geojson 217 KB and units 57 KB, about 400 KB in all, plus kotlinx-serialization, which we already have.
The wasmJs and iOS klibs are each about 130 KB for turf alone. maplibre-compose is in `nonWebMain` only, so today spatial-k reaches no
web build and not the AppView. Putting it in `core/location` would add all three modules to the web app and the AppView.

**APIs that fit.**
- `Geometry.length()` is a haversine sum over `Length` value classes.
- `LineString.locateAlong(distance)` gives one point at a distance along a line.
- `LineString.slice(start: Length, stop: Length)`, the `lineSliceAlong` equivalent, gives a sub-line.

There's no even-spacing or chunking function: resampling means calling `locateAlong` once per sample. Each call walks the line from its
first point, so resampling costs O(points × samples) where ours is one pass. Every call also means converting `GeoPoint` to and from
GeoJSON `Position`, `Point` and `LineString`.

**Numeric agreement.** The scripts are Python ports of `SegmentGeometry.resample` (haversine, R = 6,371,000 m, linear lat/lng
interpolation) and of turf's `length` and `locateAlong` (haversine, R = 6,371,008.8 m, great-circle `offset` along the bearing). Results
on the Corner Canyon lines:

| Line | Points | Length, ours (m) | turf − ours | Samples | Max point gap | Time, ours / turf (Python) |
|---|---:|---:|---:|---:|---:|---:|
| Canyon Hollow | 18 | 204.72 | +0.000 m | 22 | 0.3 mm | 0.02 / 0.1 ms |
| Bobsled | 29 | 332.12 | +0.000 m | 35 | 0.5 mm | 0.02 / 0.3 ms |
| Maple Hollow Downhill Bike | 86 | 996.80 | +0.001 m | 101 | 1.4 mm | 0.06 / 2.2 ms |
| Rush – Through the Trees | 130 | 1723.52 | +0.002 m | 174 | 2.4 mm | 0.09 / 5.8 ms |
| Maple Hollow – CCC | 308 | 7887.82 | +0.011 m | 790 | 10.9 mm | 0.30 / 68 ms |
| Grand View (24.8 km, not Corner Canyon) | 679 | 24760.98 | +0.034 m | 2478 | 34 mm | 0.8 / 411 ms |

The two agree to about 1.4 mm per km. That is just the difference between the two Earth radii (1.4 ppm), and lerp against great circle
adds well under a millimetre at 10 m spacing. The timings show turf's quadratic resampling.

**Verdict: don't adopt.** Turf gives the same numbers, but it doesn't make resampling simpler or faster, and the conversions would be a
cost on every call. It would also bring spatial-k into the web app and the AppView, which don't have it today, and it would shift the
radius under existing matching thresholds and stored lengths, however slightly. Keep `resample` and `distanceMeters` hand-written. Turf is
worth another look only if we want its other features (`nearestPointTo`, `bezierSpline`, `RTree`) in client map code that already has
maplibre-compose.

## 2. Terrain source beyond Terrarium

**Question.** Should the AppView read USGS 3DEP (1 m lidar, 1/3 arc-second) or Utah UGRC lidar GeoTIFFs directly, or should
`scripts/terrain-fetch.sh` convert them offline to Terrarium PNGs so `TerrainTiles` stays as it is?

**The source format decides it.** Both 3DEP products are Cloud-Optimized GeoTIFFs with Float32 samples, 512×512 tiles, **LZW and
PREDICTOR=3** (the floating-point predictor), and NoData −999999. The 1/3 arc-second tile `USGS_13_n41w112` is 403 MB in NAD83
geographic. The 1 m tile `USGS_1M_12_x42y449_UT_2023SaltLakeCo_C24` is 256 MB in NAD83 / UTM 12N, with internal overviews. To test the
JVM readers I cut 512×512 windows out of both tiles with GDAL, keeping their encoding, and also wrote deflate copies with predictors 1, 2
and 3.

| Reader | Weight | Licence | Result |
|---|---|---|---|
| JDK `javax.imageio` TIFF plugin (JDK 25) | none | GPLv2+CE (JDK) | Reads deflate with predictor 1. Fails on predictor 3 ("Illegal value for Predictor") and on predictor 2 with 32-bit samples. It doesn't read georeferencing or project from UTM, so we'd write both. |
| Apache SIS 1.7 `sis-geotiff` | 10 jars, 6.1 MB | Apache-2.0 (the EPSG database is an optional non-free add-on) | Reads predictor 1, projects UTM 12N correctly and evaluates the right height (1319.633 m). **Fails on predictor 3** ("Differencing predictor FLOAT is unsupported"), both locally and over HTTP range requests on the real COG. |
| GeoTools 35.2 `gt-geotiff` (+ `gt-epsg-hsql` for EPSG codes) | 92 jars, 20.7 MB (94 jars, 25.8 MB with EPSG). Not on Maven Central: needs `repo.osgeo.org` | LGPL-2.1, with ImageN, imageio-ext, JTS and Guava; EPSG data under the EPSG terms of use | Reads predictor 3 through imageio-ext and matches GDAL exactly on the 1 m window (1319.633 m). Opens files only: remote COG range reads need the separate `gt-cog` plugin and its range readers. First read takes about 1 s while the stack warms up. |

So the only JVM option that reads the published files today is GeoTools, which adds about 21 MB of LGPL dependencies and a second Maven
repository. It also brings a CRS stack and a different tile layout (UTM per project, NAD83 geographic per degree) into the AppView,
alongside `TerrainTiles`' Web Mercator grid.

**Offline conversion (proof of concept).** For Web Mercator z15 tile 6204/12344, over Corner Canyon:
1. `gdalwarp -t_srs EPSG:3857 -te <tile bounds> -ts 256 256 -r average -ot Float32 /vsicurl/<1 m COG>` (3 s, reading only the byte ranges it needs).
2. `gdal_calc.py` with `--calc="floor((A+32768)/256)" --calc="floor(A+32768)%256" --calc="floor(((A+32768)%1)*256)"` and `--type=Byte`
   encodes the Terrarium bands.
3. `gdal_translate -of PNG` writes the tile.

I decoded the result with `TerrainTiles`' own Terrarium formula and bilinear sampling, ported to a scratch Java file:

| Point | 1 m lidar (GDAL bilinear) | Converted z15 tile | AWS Terrarium z15 |
|---|---:|---:|---:|
| −111.8395, 40.5130 | 1429.02 | 1429.04 | 1426.90 |
| −111.8370, 40.5100 | 1507.41 | 1507.36 | 1506.38 |
| −111.8340, 40.5080 | 1582.71 | 1582.68 | 1582.28 |
| −111.8310, 40.5060 | 1669.27 | 1669.21 | 1668.08 |
| −111.8355, 40.5115 | 1539.78 | 1539.73 | 1539.80 |

The converted tile is within 6 cm of the lidar, and the AppView reads it unchanged. GDAL reads predictor 3 and fetches over HTTP range
requests, so no full tile downloads are needed.

`rio-rgbify` produces Mapbox Terrain-RGB by default. It can write Terrarium with `--base-val -32768 --interval 0.00390625`, since
Terrarium is the same 24-bit base-256 encoding with that offset and step. But it adds a Python and rasterio toolchain, and its last
release was years ago. GDAL alone is enough.

**Caveats for the script.**
- `TerrainTiles` reads one zoom, so a lidar region has to fill that zoom's whole folder. Lidar tiles go where lidar covers the area and
  AWS Terrarium tiles go everywhere else, at the same zoom, and `TERRAIN_TILES_ZOOM` has to be set to match.
- Lidar is project-based: pick the newest project per area, fall back to 1/3 arc-second, and mask NoData so a project's edge never
  encodes −999999. `gdalbuildvrt` with `-srcnodata -999999` in priority order handles both.
- For a whole region, warp once to a 3857 grid aligned to the zoom's pixels (`-tr <res> -tap`), encode, then cut tiles with
  `gdal raster tile --resampling nearest`. Nearest only, because resampling encoded RGB corrupts the heights.
- Utah UGRC lidar wasn't tested. Its rasters come as downloads by area rather than as COGs on S3, so they'd join the VRT the same way
  once fetched. Check its terms before redistributing tiles.
- Data licences: 3DEP is US public domain. AWS Terrain Tiles need the attribution listed on their registry page.

**Recommendation: convert offline.** Have `terrain-fetch.sh`, given `--source 3dep`, build Terrarium PNGs with GDAL from 3DEP COGs read
over `/vsicurl/`, at z15 for the lidar's areas. Keep `TerrainTiles` and the AppView free of raster libraries. Of the in-process readers,
only GeoTools can read 3DEP as published, and it isn't worth 21 MB of LGPL dependencies and a CRS stack. Look at SIS again if a later
release adds the floating-point predictor.

## 3. Smoothing and resampling helpers

**Question.** Is there a maintained library worth using for `ElevationProfile.smooth`, a 50 m centred moving average that keeps the ends,
and for `ElevationProfile.even`, resampling the profile to evenly spaced points, in `core/location` (commonMain)? On the JVM, is Apache
Commons Math worth using?

| Library | Latest | Targets | What it offers here | Notes |
|---|---|---|---|---|
| kmath (`space.kscience`) 0.6.0 | 2026-10 | jvm, js, wasmJs, iOS, … | `kmath-functions`: `LinearInterpolator` and `SplineInterpolator` give a `PiecewisePolynomial` over `XYColumnarData`, which `even` could sample. No moving average or convolution anywhere in `kmath-core`, `-functions` or `-stat`. | `kmath-core` jar 803 KB plus `-functions` 109 KB, and `attributes-kt`. Its API is generic over `Field<T>`/`Buffer`, and 13 declarations in `-functions` are `@UnstableKMathAPI`. The project calls itself experimental, and its API changes between minors. |
| multik (`org.jetbrains.kotlinx`) 0.3.1 | 2026-04 | jvm, js, wasmJs, iOS, … | n-d arrays and linear algebra. Neither a moving window nor interpolation. | `multik-core` jar 542 KB. Solves a different problem. |
| Apache Commons Math 3.6.1 | 2016 | JVM only | `DescriptiveStatistics(window)` is a trailing rolling window, not a centred one, and doesn't keep the ends. `LinearInterpolator` gives a `PolynomialSplineFunction`. | 2.1 MB, unmaintained in 3.x, and 4.0 is still `4.0-beta1` (2022). Being JVM-only, it would make the AppView's profile differ from the clients'. |
| Commons Statistics 1.3 | 2026-05 | JVM only | Descriptive statistics (`Mean` and so on), no windowing. | Doesn't fit. |

The Kotlin stdlib already covers most of it. Both functions are short, unlike the matching code: `smooth` is a centred window over a
`DoubleArray`, using the half-window that fits at each end, or keeping the end samples as they are. With 10 m samples and a 50 m window,
that's 5 samples, about 15 lines. `even` walks the cumulative distance once with linear interpolation, also about 15 lines, the same
shape as `SegmentGeometry.resample`. Both have to behave the same on the AppView, Android, iOS and web, so they belong in commonMain,
which rules out Commons Math. kmath could only do the interpolation half, through a generic API of its own that costs more code than it
saves.

**Verdict: confirmed, write them by hand** in `core/location`, unit-tested in commonTest next to `ElevationGainTest`. No library is
justified for a single moving average and a linear resample.

## Reproducing

I ran the spikes from a scratch folder, so none of the scripts are committed:
- Spike 1: a Python port of both resamplers over `build/segment-import/us-utah.ndjson`.
- Spike 2: GDAL 3.13 `gdal_translate`, `gdalwarp`, `gdal_calc.py` and `gdallocationinfo` on the URLs above. The JDK reader was a
  single-file `java` program. SIS and GeoTools ran from a throwaway Gradle project through the repo's wrapper (`sis-geotiff:1.7`,
  `gt-geotiff:35.2`).
- Spike 3: Maven Central metadata and the published sources jars.
