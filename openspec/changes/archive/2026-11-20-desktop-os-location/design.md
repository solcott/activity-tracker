# Design

## Context

Map browse (`MapBrowse`, `app/common/presenter/.../browse/MapBrowse.kt`) decides where Trails & roads and Dive sites open. `SegmentBrowsePresenter` and `DiveSiteBrowsePresenter` inject `LocationTracker`, start `MapBrowse(MapBrowse.initialPermission(tracker))`, and run `browse.locate(tracker, feed)` once permission is known. `locate` waits up to `LOCATION_TIMEOUT` (5 s) for `tracker.updates().first()`, then falls back to `feed.latestOwnStart()`, then to `BrowseStart.Nowhere`. Desktop and web bind `UnsupportedLocationTracker` (`core/location/src/nonMobileMain`). Its `isSupported = false` is also what hides Record in the feed, and `initialPermission` turns it into `false`, so desktop goes straight to the fallback.

The desktop app is a plain JVM process, usually run unbundled through Gradle (`:desktopApp:hotRun`). It has no native packaging and no native-interop dependency yet. It is the only JVM app graph (`DesktopAppGraph`); `core/location`'s `jvmMain` is also the target the tests run on.

## Goals / Non-Goals

**Goals:**
- One location read per browse opening on desktop, from each OS's own service, with the OS's own permission UI.
- Keep `MapBrowse` platform-agnostic: it asks one contract, and mobile behaviour stays as it is apart from the late-fix rule.
- Every failure (no service, refused, timeout, helper missing, odd output) ends as "no location" and never surfaces as an error.

**Non-Goals:**
- Recording, continuous updates or any other desktop use of location.
- An in-app permission prompt or a setting on desktop.
- Location on web (it has no map).
- IP-based geolocation.

## Decisions

### A one-shot `PlaceLocator`, not a desktop `LocationTracker`
Add `interface PlaceLocator` in `core/location` commonMain:
- `permission(): Boolean?`: false where there's none, true when it may be read, null until the app has asked (mobile only).
- `suspend fun here(): GeoPoint?`: one fix or null; it never throws except on cancellation, and the caller bounds the wait.
- `val hold: Duration`: how long browse holds its first view for `here()` before opening at the fallback.

Bindings:
- Everywhere by default: `TrackerPlaceLocator(tracker)`, contributed from commonMain, wraps `LocationTracker`. `permission` is what `MapBrowse.initialPermission` was, `here` is `updates().first()`, and `hold` is 5 s, today's wait. Over `UnsupportedLocationTracker` (web) its permission is false, so web needs no binding of its own.
- Desktop: `DesktopPlaceLocator`, contributed from `desktopApp` with `replaces = [TrackerPlaceLocator::class]`. Its `permission` is true when this OS has a backend (the OS asks inside `here()`), and `hold` is 2 s: a granted location answers well inside that, and beyond it the athlete is likely reading the prompt.

*Alternative:* a real desktop `LocationTracker`. Rejected: `isSupported` would turn Record on, and a stream API doesn't suit a single OS query that may sit behind a prompt. Contributing the desktop binding from `core/location/jvmMain` was also rejected: that source set is the test target, and the OS bridges belong with the app.

The mobile in-app ask still goes through `MapBrowseState.askLocation` and `PermissionResult`, and the presenter still reads `LocationTracker.hasPermission()` there through `TrackerPlaceLocator.permission()`. No UI changes.

### Late fix moves only an untouched map
`MapBrowse.locate(locator, fallback)` takes the fallback as a function (the presenters pass `feed.latestOwnStart()`), so it can be unit-tested on virtual time. It starts `here()`, bounded by `LATE_LOCATION_LIMIT` (30 s), and waits `hold` for it. An answer within the hold opens there. Otherwise browse opens at the fallback (or `Nowhere`) at once and marks the placement *provisional*. A later answer within the limit re-centres the map only while the placement is still provisional.

A viewport centred within 0.001 degrees of where the fallback sent the camera means the map has *arrived*. A viewport elsewhere before that is the map's own first view, not the athlete. One elsewhere after it means the athlete moved the map, and clears the flag. When browse opened nowhere, the athlete zooming in somewhere (a viewport that isn't too wide) clears it. A name search doesn't, since it doesn't move the map.

This applies on mobile too: a slow GPS fix now lands instead of being dropped. That's within the spec's wording and is a harmless improvement.

*Alternative:* hold the "Locating" spinner for up to 30 s on desktop. Rejected: a first run with a permission prompt would show a spinner for as long as the athlete reads it.

### macOS: a bundled Swift helper launched through LaunchServices
`CLLocationManager` grants location per bundle with an `NSLocationUsageDescription`. An unbundled `java` has neither, so a direct JNA call from the JVM is denied or never prompts. So:
- `desktopApp/src/macosHelper/` holds `main.swift` (request when-in-use authorization, `requestLocation()`, write `{"lat":…,"lng":…}` or `{"error":"…"}` to the `--out` path, and exit) and `Info.plist` (its own bundle id, `LSUIElement`, the usage string).
- A Gradle task (`buildMacLocationHelper`), registered only on macOS hosts, runs `swiftc` and assembles `ActivityTrackerLocation.app` under `build/`, then ad-hoc signs it (`codesign -s -`). `run` and `hotRun` get its path as a system property.
- `DesktopPlaceLocator` runs `open -W -g -n <app> --args --out <tmp>` and reads the file. `-W` waits, `-g` keeps it in the background, and `-n` starts a fresh instance.

*Alternatives:* JNA plus the Objective-C runtime (needs a runtime delegate class and a run loop, and is still denied without a bundle), or `CoreLocationCLI`-style unbundled tools (the same TCC problem).

### Windows: PowerShell and WinRT
Run `powershell.exe -NoProfile -NonInteractive -Command <script>`. The script loads `Windows.Devices.Geolocation.Geolocator` through the WinRT type projection, awaits `GetGeopositionAsync()` through `AsTask()`, and prints `lat,lng` in invariant culture. Windows PowerShell 5.1 ships with Windows 10 and 11, so there's nothing to bundle. If location is off for desktop apps the result is an error, which reads as null.

*Alternative:* JNA calling WinRT COM directly. That's far more code for the same call.

### Linux: GeoClue2 over D-Bus with `dbus-java`
`org.freedesktop.GeoClue2.Manager.GetClient` → set `DesktopId` (the app id) and `RequestedAccuracyLevel` (city, 4) → `Start` → the first `LocationUpdated(old, new)` signal → read `Latitude` and `Longitude` from the new `Location` object → `Stop`. `dbus-java` (`com.github.hypfvieh`, MIT, maintained, Maven Central) with its native-unixsocket transport, added to `gradle/libs.versions.toml`.

*Alternative:* shelling out to GeoClue's `where-am-i` demo, which isn't installed on most distros.

### Library check
Before writing the bridges, look for an existing JVM desktop geolocation library on klibs.io and Maven Central (CLAUDE.md "Prefer libraries"). The KMP options known today (compass, moko-geo) target Android, iOS and the browser only. If nothing covers all three OSes, the commit says so.

Result (checked 2026-11-20): Compass 3.0.2 lists a JVM target, but its locators are only `compass-geolocation-mobile` (Android, iOS) and `compass-geolocation-browser`; the JVM artifact carries the interface alone. Geolocation-KMP's desktop location is an IP lookup, which is out of scope. moko-geo targets Android and iOS. No library covers OS location on JVM desktop, so the bridges are ours.

Spike result (macOS 27.2): the helper prompts and answers when it runs an `NSApplication` with the `.accessory` policy, launched with `open -W -n` (no `-g`). A bare `RunLoop` never showed the prompt. The grant survived an ad-hoc re-sign of an unchanged binary, and a later run answered in under a second.

### Parsing kept apart from processes
Each backend has a pure parse function (helper JSON, PowerShell output line, GeoClue variant map → `GeoPoint?`) that rejects NaN, out-of-range coordinates and errors. Unit tests cover those; process and D-Bus plumbing stays thin.

## Risks / Trade-offs

- [macOS TCC may still credit the responsible process (Terminal or the IDE) when run under `hotRun`] → Launching through `open` makes LaunchServices the parent, so the helper is credited to its own bundle. Verify this early as the first macOS task. If it fails, document that location works only when the helper is approved under System Settings › Privacy › Location.
- [Ad-hoc signed helper rebuilt with a new code signature may lose its grant] → Keep the bundle id stable and rebuild only when the Swift source changes (Gradle up-to-date checks).
- [Windows and Linux can't be tested on the dev machine] → The parse functions are unit-tested on every host; the OS calls are marked unverified until run on a real machine or VM.
- [GeoClue often has no provider since Mozilla Location Service shut down] → It returns null and the fallback stands, which is the specified behaviour.
- [Graphs in `composeApp` JVM tests may now need a `PlaceLocator` binding] → Provide a fake in the test graph or test fixtures, as is done for the tracker.
- [Late re-centre could surprise an athlete reading the list] → It happens only before they touch the map, within 30 s, and the spec states it.
