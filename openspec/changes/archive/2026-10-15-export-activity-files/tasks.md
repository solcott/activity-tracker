# Tasks

## 1. Writers and join in `:core-activityfile`

- [x] 1.1 Add `ExportActivity` and `ExportPoint` (SI units, optional position and channels) and `ExportPoints.join(startedAt, track, streams)`: track points take the stream sample at their second, unmatched samples become position-less points, sorted by time, lexicon units converted back (the reverse of `StreamBuilder`), stream altitude preferred over GPS altitude, gears dropped; verify with `commonTest` cases for track + heart rate, streams without a track, a track without streams, two points in one second, and a stream sample past the track's end (`./gradlew :core-activityfile:jvmTest`)
- [x] 1.2 Add `GpxWriter.write(ExportActivity)`: GPX 1.1, one `trk` with name and type, `trkpt`s only for points with a position, `ele` and `time`, Garmin `TrackPointExtension` v2 (`hr`, `cad`, `atemp`/`wtemp` for dives, `depth`, `speed`) and a bare `<power>`, text escaped; verify tests pin the element names and namespaces and that reading the output back with `ActivityFileReader` gives the same positions, times, heart rate, cadence and power
- [x] 1.3 Add `TcxWriter.write(ExportActivity)`: one `Activity` (`Running`/`Biking`/`Other`, `Id` = start), one `Lap` with elapsed time, distance and heart-rate summary, `Trackpoint`s with time and whichever of position, altitude, distance, heart rate and cadence exist, `TPX` `Speed`/`Watts` extension; verify tests for a run, an indoor ride without positions and a swim (`Other`), and that `ActivityFileReader` reads each back with matching samples
- [x] 1.4 Update `.agents/rules/activity-files.md` to say `:core-activityfile` also writes GPX/TCX for export (`ExportPoints`, `GpxWriter`, `TcxWriter`) and that `Gpx.write` in `core/location` stays the track blob's writer; verify `scripts/check-agent-sync.sh` passes

## 2. Exporter in `core/data`

- [x] 2.1 Add `ExportFormat` (GPX, TCX with extension and MIME type), `ExportedFile(name, mimeType, bytes)` (`@Immutable`) and `ActivityExporter.formats(hasTrack, hasStreams)`; verify jvm tests: track ≥ 2 points → GPX and TCX, streams only → TCX, neither → empty
- [x] 2.2 Implement `ActivityExporter.export(view, format)`: refuse with `NotSentYetException` while the outbox holds any write for `feed.activity`, `feed.activityStreams` or `feed.activityTrack` under the rkey; read the track and streams fresh; fail rather than write a thinner file when either read fails; map `SportCategory` to the TCX sport; build the file name from title (or sport name) and local start date with invalid characters replaced; verify jvm tests with the fake PDS and AppView for each case (unsent, offline track read, GPS run, indoor ride, file name sanitizing) (`./gradlew :core-data:jvmTest`)

## 3. Detail screen state and presenter

- [x] 3.1 Add `ActivityExportState` (`formats`, `busy`, `error`, `file`) to `ActivityDetailState.Loaded.export` and the `Export(format)` and `ExportHandedOff` events in `:app-screens`; verify `./gradlew composeStabilityCheck` passes
- [x] 3.2 Wire the presenter: `export` is null on someone else's activity or when no format applies, formats from the loaded own route and streams, `Export` runs once at a time in `flights` and sets `file` or `error` (a not-sent error distinct from a failed read), `ExportHandedOff` clears `file`; verify with a new `ActivityDetailExportTest` (`ActivityDetailTestBase`): owner sees formats, non-owner sees none, manual entry sees none, double `Export` produces one file, unsent and offline errors, hand-off clears (`./gradlew :app-presenters:jvmTest`)

## 4. UI and platform hand-off in `composeApp`

- [x] 4.1 Check FileKit 0.16.0's APIs on each target (`shareFile` and cache dir on Android/iOS, the file saver launcher on desktop, the download on wasmJs) and whether Android needs a `FileProvider` manifest entry; record the outcome in design.md's hand-off decision if it differs, and verify `./gradlew :composeApp:compileKotlinJvm :composeApp:compileKotlinWasmJs` with a stub use
- [x] 4.2 Add the expect/actual `rememberFileHandOff(): (ExportedFile) -> Unit`: share sheet on Android and iOS, save dialog on desktop, download on web; verify each target compiles (Android `:androidApp:assembleDebug`, iOS `xcodebuild` as in CLAUDE.md, desktop, wasmJs)
- [x] 4.3 Add Export to the owner's detail menu and a dialog with the offered formats, a busy state, the rebuilt-file note (laps, sub-second timing and other fields not kept; the full untrimmed route), and the error; on `file`, hand it off and send `ExportHandedOff`; use only `ui/adaptive` components; verify detekt passes and, on desktop with Hot Reload, the dialog shows GPX and TCX for an own GPS activity and nothing on another athlete's (screenshot via the hot-reload MCP)
- [x] 4.4 Add the strings for the menu item, dialog, note and errors to the shared string resources; verify `./gradlew :composeApp:jvmTest` passes

## 5. Integration

- [x] 5.1 With `scripts/dev.sh --seed alice`, record or import a GPS activity, export it as GPX and TCX on desktop, and re-import both files through Import: the activity's distance, duration and heart rate match within rounding; export an Only me activity too
- [x] 5.2 Export on the Android emulator and iOS simulator and confirm the share sheet opens with the named file; export on the web and confirm the download
- [x] 5.3 Run `./gradlew check` and verify it passes

## Workflow follow-up

- Update BACKLOG.md's "order of the open changes" entry once this change is archived.
- Archive the change with `/opsx:archive` after review.
