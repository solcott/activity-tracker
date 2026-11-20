# Tasks

## 1. Spike: macOS permission from an unbundled JVM

- [x] 1.1 Build a throwaway `ActivityTrackerLocation.app` (Swift `CLLocationManager` one-shot plus an `Info.plist` with `NSLocationUsageDescription`, ad-hoc signed), launch it with `open -W -g -n … --args --out <tmp>` from a `java` process started by Gradle, and verify macOS prompts for the helper's own bundle and the out file holds a lat/lng. If it doesn't, stop and revisit the design with the user.
- [x] 1.2 Search klibs.io (`kotlin-multiplatform-libraries-expert`) and Maven Central for a JVM desktop geolocation library covering macOS, Windows and Linux, and record the result for the commit message. If one fits, revisit the design with the user before group 4.

## 2. One-shot location contract

- [x] 2.1 Add `PlaceLocator` (`permission()`, `suspend here()`, `hold`) in `core/location` commonMain, `TrackerPlaceLocator` over `LocationTracker` contributed from commonMain (over web's unsupported tracker it has no location). Verify with `TrackerPlaceLocator` tests over `FakeLocationTracker` (`./gradlew :core-location:jvmTest`): a fix, no fix, an error, and permission mapping.
- [x] 2.2 Bind a `PlaceLocator` wherever a graph or test fixture builds one (`SessionTestFixture` and composeApp test graphs), and verify `./gradlew :composeApp:jvmTest` and `:app-common-presenter:jvmTest` compile and pass.

## 3. Map browse uses it, with the late fix

- [x] 3.1 Change `MapBrowse.locate` to take `PlaceLocator` and a fallback function, wait the locator's `hold` for a fix, place provisionally at the fallback, and re-centre on a fix within `LATE_LOCATION_LIMIT` only while the placement is untouched (`viewportChanged` from the athlete clears it; the camera's own first viewport doesn't). Move `initialPermission` to the locator. Verify with `MapBrowse` unit tests in `app-common-presenter` jvmTest: fix first, fallback then a late fix, a late fix after a pan, a late fix after the limit, no fix and no activity.
- [x] 3.2 Inject `PlaceLocator` in `SegmentBrowsePresenter` and `DiveSiteBrowsePresenter` in place of the direct `LocationTracker` use, keeping the mobile permission ask. Verify `SegmentBrowsePresenterTest` and the dive site browse presenter test pass with a fake locator, including a gated late fix that moves an untouched map's camera and leaves a panned one.
- [x] 3.3 Update the segment browse sample states or fixtures only if the state's shape changed, and verify `./gradlew :composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"` passes.

## 4. Desktop OS bridges

- [x] 4.1 Add `DesktopPlaceLocator` in `desktopApp` (contributed to `AppScope`), choosing a backend by `os.name`, with its backends running on `Dispatchers.IO` and cut off by the caller, and returning null on any failure. Verify with a unit test using a fake backend: a fix, a failure, a hang cut off by the timeout, and an unknown OS.
- [x] 4.2 macOS: move the spike into `desktopApp/src/macosHelper/` (Swift and `Info.plist`) and register a `buildMacLocationHelper` task on macOS hosts only (`swiftc`, bundle, `codesign -s -`, up to date when the sources haven't changed), passing the bundle's path to `run` and `hotRun` as a system property. The backend launches it through `open` and parses its JSON with a pure parser. Verify with parser tests (fix, error, malformed, out of range) and `./gradlew :desktopApp:buildMacLocationHelper` producing the bundle; on a Linux host the task must not be registered.
- [x] 4.3 Windows: a backend running the PowerShell WinRT `Geolocator` script and a pure parser of its `lat,lng` line in invariant culture. Verify with parser tests (fix, comma-decimal locale guard, error text, empty). The OS call itself is marked unverified on this machine.
- [x] 4.4 Linux: add `dbus-java` (core and native-unixsocket transport) to `gradle/libs.versions.toml` and `desktopApp`, and a GeoClue2 backend (GetClient, DesktopId, city accuracy, Start, the first LocationUpdated, Stop) with a pure reader of the Location properties. Verify with reader tests (fix, missing properties, out of range); the OS call is marked unverified on this machine.

## 5. Docs

- [x] 5.1 Update CLAUDE.md (the Architecture DI paragraph on desktop's `UnsupportedLocationTracker`, and the Commands block if `buildMacLocationHelper` needs running by hand) and `.agents/rules/adaptive-ui.md` or `data-and-auth.md` only where they describe desktop as having no location. Verify `scripts/check-agent-sync.sh` passes.

## 6. Integration

- [x] 6.1 On macOS, run `./gradlew :desktopApp:hotRun --auto`, open Search › Trails & roads, and verify with the compose-hot-reload MCP screenshots: the OS prompt names the helper, allowing it centres the map on the current location (moving from the latest activity if it opened there first), denying it leaves the map at the latest activity, and the feed still shows no Record.
- [x] 6.2 Run `./gradlew check` and verify it passes (detekt, module boundaries, stability, presenter and desktop tests).

## Workflow follow-up

- Run the Windows and Linux bridges on a real machine or VM before archiving, or note them as unverified in the archive.
- Archive the change with `/opsx:archive` after review, syncing the delta into `segments/segment-browse`.
