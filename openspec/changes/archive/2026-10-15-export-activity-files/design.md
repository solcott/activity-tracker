# Design

## Context

See proposal.md for why, and `specs/feed/activity-export/spec.md` for what the export does. What exists and is reused:

- **Track.** `ActivityTrackStore.read(activity)` (`core/data`) reads the owner's `feed.activityTrack` from `self` and parses its GPX with `readGpxTrack` into `LocationSample`s (lat, lon, altitude, speed, time). `ActivityRepository.ownRoute` wraps it, and the detail screen already calls it for the owner (`OwnRoute.kt`). It returns null when there's no track, it isn't sent, or it can't be read.
- **Streams.** `FeedRepository.streams(uri)` reads `getActivityStreams` from the AppView: an `ActivityStreamData` whose arrays line up with `time` (seconds since `startedAt`), in lexicon units (cm, mm/s, tenths of °C, bpm, W, rpm). The detail screen loads it for charts (`rememberCharts`).
- **Record.** `feed.activity(uri)` gives the `ActivityView`, whose record has `sport`, `subSport`, `startedAt`, `elapsedTime`, `distance`, `avgHeartRate`, `maxHeartRate` and `title`.
- **Writers.** The only writer is `Gpx.write` in `core/location`, a minimal GPX 1.1 track for the track blob (position, elevation, time). `:core-activityfile` reads every format and holds `StreamBuilder` (file samples to lexicon units).
- **Files.** `composeApp` already depends on FileKit (`filekit-dialogs-compose` 0.16.0) for the import file picker. FileKit 0.16 has a file saver launcher on desktop/native, a download on the web, and `shareFile` on Android and iOS.
- **Outbox.** `db.outbox().pending(did)` lists queued writes with their collection and rkey; `PdsActivityTrackStore.has` already checks it per rkey.

## Goals / Non-Goals

**Goals:**
- Pure, common-code writers for GPX and TCX that unit-test on the JVM without a session.
- One join of track and streams shared by both formats.
- Reuse what the detail screen already loaded where it can, but read fresh at export time so the file is never built from a stale screen.

**Non-Goals:**
- FIT export and UDDF for dives (see Open Questions).
- Exporting several activities at once, or the whole account.
- Re-adding laps, sub-second timing or any field the app doesn't store.
- Changing `Gpx.write`, the track blob's format, or anything stored.

## Decisions

### Writers in `:core-activityfile`, behind a format-neutral `ExportActivity`
Add `ExportActivity` (title, sport for GPX `<type>`, TCX sport, `startedAt`, elapsed time, distance, heart-rate summary, and `points: List<ExportPoint>`) and `ExportPoint` (time, optional lat/lon, and each channel in SI units: altitude m, distance m, speed m/s, heart rate, cadence, power, temperature °C, depth m). `GpxWriter.write(ExportActivity): String` and `TcxWriter.write(ExportActivity): String` serialize it. `:core-activityfile` already knows every format's elements and namespaces from its parsers, and the writers can be checked by round-tripping through `ActivityFileReader` in tests.

Alternatives: extend `Gpx` in `core/location`. That module holds location primitives used by the recorder; extensions for heart rate and power don't belong there, and `Gpx.write` must stay byte-stable for the track blob. Keep it as is.

The writers build strings with `buildString` and escape text, as `Gpx.write` does; xmlutil's writer adds little for flat documents and its serialization isn't needed.

### Joining track and streams: `ExportPoints.join`
Also in `:core-activityfile`, pure: `join(startedAt, track: List<LocationSample>?, streams: ActivityStreamData?): List<ExportPoint>`. Each track point's second offset is `(time - startedAt)` rounded to whole seconds. Stream samples are indexed by `time`; a track point takes the sample at its second. A stream sample whose second no track point has becomes a point of its own with no position. Points are sorted by time. Units are converted back from the lexicon's integers (the reverse of `StreamBuilder`'s `CENTI`/`MILLI`/`DECI`). Altitude comes from the stream when it has one at that second, else the track's GPS altitude. Gear changes are dropped: neither format has them.

Multiple track points in one second each take that second's sample, so no position is lost.

### GPX extensions
GPX 1.1 with Garmin's `TrackPointExtension` v2 (`gpxtpx`, `http://www.garmin.com/xmlschemas/TrackPointExtension/v2`) for `hr`, `cad`, `wtemp` (dives) or `atemp` (everything else), `depth` and `speed`; power as a bare `<power>` element in `<extensions>`, the convention Strava writes and reads. GPX is offered only with positions (`trkpt` requires lat/lon); points without a position are left out of GPX.

### TCX shape
`TrainingCenterDatabase` v2 with one `Activity` (`Sport` `Running`, `Biking` or `Other`, `Id` = `startedAt`) and one `Lap` (`StartTime`, `TotalTimeSeconds` = moving time, else elapsed time, since readers take it as timer time, `DistanceMeters`, `AverageHeartRateBpm`, `MaximumHeartRateBpm`, `Intensity` Active, `TriggerMethod` Manual), and one `Track` of `Trackpoint`s. Speed and power go in the `ActivityExtension` v2 `TPX` (`Speed`, `Watts`). The TCX sport comes from `SportCatalog`'s `SportCategory` (Running → `Running`, Cycling → `Biking`, the rest `Other`), mapped in `core/data` because `:core-activityfile` can't see the catalog.

### `ActivityExporter` in `core/data`
Session-scoped, injected into the presenter:
- `formats(activity, hasTrack, hasStreams): Set<ExportFormat>` is a pure rule (GPX needs a track of ≥ 2 points, TCX a track or streams), used by the presenter with what it already loaded.
- `suspend fun export(view: ActivityView, format: ExportFormat): ExportedFile` refuses with `NotSentYetException` when the outbox holds any write for `feed.activity`, `feed.activityStreams` or `feed.activityTrack` under the activity's rkey; then reads the track (`ActivityTrackStore.read`) and streams (`FeedRepository.streams`) fresh, joins them, writes the file and returns `ExportedFile(name, mimeType, bytes)`. A track read that returns null where the screen had one, or a streams read that throws, fails the export rather than writing a thinner file.
- File name: the title (or the sport's display name) and the local start date, e.g. `Morning Run 2026-10-15.gpx`, with characters invalid on any platform (`/\:*?"<>|`) replaced and the length capped.
- MIME types: `application/gpx+xml` and `application/vnd.garmin.tcx+xml`.

Alternative: build the file in the presenter from the state it holds. The presenter's route is the trimmed-for-display list and its charts are already transformed; reading fresh keeps the exporter testable on its own with the fake PDS and AppView the data tests use.

### Detail screen: event, state and a one-shot hand-off
- `ActivityDetailState.Loaded.export: ActivityExportState?` — null on someone else's activity or when no format applies; else `formats`, `busy`, `error`, and `file: ExportedFile?` waiting for the UI to hand off.
- Events `Export(format)` and `ExportHandedOff` (the UI consumed `file`, whether shared, saved or cancelled).
- The presenter runs `Export` in `flights` (single flight, as Delete does), so a double tap doesn't produce two files. It uses `ownRoute`'s loaded track and `charts` presence only to decide `formats`.
- `ExportedFile` is `@Immutable` with `equals` by name and content identity, so the stability check passes.

The UI adds "Export" to the owner's overflow menu, opening an `AppAlertDialog` (or the adaptive sheet the menu uses) with GPX/TCX choices and the rebuilt-file note. When `file` arrives the UI calls a `rememberFileHandOff()` and sends `ExportHandedOff`.

### Platform hand-off: `rememberFileHandOff` in `composeApp`
An expect/actual composable returning `(ExportedFile) -> Unit`:
- `androidMain` / `iosMain`: write the bytes to FileKit's cache dir under the file's name and share it with `rememberShareFileLauncher` (`FileKit.shareFile`). Checked in 0.16.0: `filekit-dialogs` merges its own `FileKitFileProvider` (authority `{applicationId}.FileKitFileProvider`, serving `cache-path`) and `filekit-core` initializes through androidx.startup, so the app's manifest needs nothing.
- `jvmMain` (desktop): `rememberFileSaverLauncher` with the suggested name (without extension) and `defaultExtension`, then write the bytes to the chosen file; a cancel writes nothing.
- `wasmJsMain`: FileKit's web download of the bytes under the name.

Alternative: a share sheet everywhere through Calf. Calf has no desktop/web share, and FileKit is already in the build.

## Risks / Trade-offs

- [Streams not indexed yet: the AppView serves streams a little after the activity, so an export right after sending may miss channels] → the outbox check covers unsent writes; for indexing lag, `formats` comes from what the screen loaded, and a TCX/GPX with a track but no streams is still correct, just thinner. Accept.
- [Large multi-day activities: tens of thousands of points held as strings in memory] → files of this size are a few MB; build once in `Dispatchers.Default`, as charts are.
- [Untrimmed track leaves the device: the file holds positions inside privacy zones] → it's the owner's own data; the spec says so, and the dialog's note mentions it.
- [GPX extension support varies by tool] → `gpxtpx` v2 and bare `<power>` are what Garmin Connect and Strava import; tests pin the element names.
- [FileKit's share/save API differs from what's assumed on some target] → the first task checks each platform's API in 0.16.0 before the UI work; fall back to a per-platform implementation (Android `Intent.ACTION_SEND` with a `FileProvider`, `UIActivityViewController` on iOS) only where needed.

## Open Questions

- **FIT export.** Its own change, `export-fit-files`. Garmin's Java, JS and Swift SDKs all encode, so a `FitWriter` takes the same `ExportActivity` behind a `FitEncoder` interface.
- **UDDF for dives.** Its own change, `export-dives-as-uddf`. A dive exports as GPX/TCX until then (GPX carries depth and water temperature); a `UddfWriter` adds another `ExportFormat` without changing the join or the hand-off.
