# Design

## Context

- `ActivityRecorder` (`core/location`, `AppScope`) owns recording. It collects `LocationTracker.updates()` in its own scope and keeps everything in a `MutableStateFlow<RecordingState>`, the whole `samples` list included. `RecordPresenter` (`SessionScope`) drives it and, on Finish, calls `recorder.stop()` (which clears the state) before `ActivityRepository.publish`.
- `AndroidLocationTracker` requests fused updates at 1 s on the main looper, with no service. `IosLocationTracker` builds its `CLLocationManager` with "when in use" authorization and no background settings. `iosApp/Info.plist` has no `UIBackgroundModes`, and setting `allowsBackgroundLocationUpdates` without it crashes the app.
- `core/location` has no Room. `core/data`'s `AppDatabase` is `AppScope`, at version 4, with hand-written migrations (`addAppMigrations`; Kotlin/Native can't lower `AutoMigration`).
- Android components reach the Metro graph through `ActivityTrackerApplication.graph`. Sign-out runs through `AuthRepository.signOut`, after which `App.kt` drops the `SessionGraph`.
- `feed/map-breaks` already breaks a map line at any gap over the limit and 200 m, and `RouteBreaks` applies it to a recorded route at publish.

## Goals / Non-Goals

**Goals:**
- Location keeps flowing with the screen off on Android and iOS, with "while in use" permission only.
- No accepted sample is lost to a killed process, a refused publish or a sign-out.
- Totals are computed incrementally from data the journal can restore, so recovery rebuilds exactly what the athlete saw.

**Non-Goals:**
- Restarting a recording on its own after the process dies (it needs background-location permission, see Decisions).
- Sensors (heart rate, cadence) and auto-pause. The journal's shape leaves room for them, but nothing records them yet.
- Desktop and web, which have no GPS recording (`UnsupportedLocationTracker`).
- Changing what a recorded activity publishes: `ActivityDraft.fromRecording` keeps its output.

## Decisions

### 1. The journal is an interface in `core/location`, implemented with Room in `core/data`

`ActivityRecorder` depends on a `RecordingJournal` interface:
- `begin(did, startedAt)` returns the recording's id
- `append(id, event)` saves each `RecordingEvent`: an accepted sample, a pause or a resume
- `unfinished(did)` returns the account's recording with its events in order
- `delete(id)`

`core/data` binds `RoomRecordingJournal` in `AppScope` on every platform; desktop and web never start a recording, so no separate no-op binding is needed. It has two tables, added in migration `4 → 5`:
- `recording`: id, did, started-at.
- `recording_event`: recording id, sequence, kind (sample, paused, resumed), time, and a sample's position fields.

Totals aren't stored. A recovery replays the events through the same pure fold the recorder uses (Decision 3), so it rebuilds exactly what the athlete saw.

- *Why not a file in `core/location`:* the app already has one durable store with migrations, per-account keys and tests (`inMemoryDatabase()`). A second, hand-rolled format would need its own crash-safety story.
- *Why not putting Room in `core/location`:* it would make the location module depend on the data layer's database and its KSP setup.

### 2. Write every sample as it's accepted, off the collector

Each accepted sample is appended in its own small insert on the recorder's dispatcher. At 1 Hz that's trivial for SQLite. A sample is in `RecordingState` only after its append returns, so what the screen shows is never ahead of what recovery restores.

- *Alternative:* batch every N seconds. It loses up to N seconds on a kill, and it complicates the "screen never ahead of disk" rule for little gain.

### 3. `RecordingState` is a pure fold of events, without the list

`RecordingState.apply(event)` folds one `RecordingEvent` into the state, which holds:
- the current segment number
- the segment's last accepted sample
- distance, moving seconds, gain, top speed
- the climb base (`lastAltitude`, moved into state)
- elapsed before the current segment
- the count of samples

The recorder runs every command (sample, pause, resume, finish) one at a time, in order, on a single-lane dispatcher under a mutex, and saves each event before applying it. The accepted samples live in an append-only list inside the recorder, handed out only in the finish result, so nothing copies a growing list per sample (R4). There is no live map today, so nothing else reads them.

`climbTo` becomes part of the pure fold, so nothing runs a side effect inside a retried update (R3).

### 4. A resume starts a new segment

`resume()` increments the segment and clears the segment's last sample. `onSample` measures step, seconds and speed only against a previous sample in the same segment, so the first sample after a resume adds nothing. This fixes R2.

Recovery's resume is the same call: the time since the last saved sample is already outside `activeBeforeSegment`, since the restored state is paused. `ActivityDraft.fromRecording` still gets every sample in order. The route keeps the gap, and `RouteBreaks` breaks it per `feed/map-breaks` when it's far and long enough, so a recovered café stop joins and a recovered drive breaks.

### 5. Android: a foreground service only for lifetime and the notification

`RecordingService` lives in `androidApp`, which has the app's icon, strings and `MainActivity`. It's declared there with `foregroundServiceType="location"` and a package-only intent filter.
- **Starting it:** `AndroidRecordingForeground` (`core/location` `androidMain`, the `RecordingForeground` binding) starts it when a recording leaves Idle. That's always the athlete's tap, as Android 14 requires for a location service started without background-location permission.
- **What it does:** it collects nothing itself; `ActivityRecorder` still owns collection. It follows the recorder's state from the app graph and stops itself once the state is Idle, so nothing has to start or stop it from the background.
- **The notification:** an ongoing one on the channel "Recording", showing elapsed time and distance in the device region's units (the athlete's own choice lives in their session, which the service can't see). It updates about every 5 s while recording, and on every status change.
- **Notification actions:** Pause or Resume are sent back to the service, which calls the recorder. Tapping the notification opens `MainActivity` with an action that `RecordScreenRequests` turns into navigation to the record screen.

There's no Finish or Discard on the notification: both need the record screen (type, title, visibility, confirmation).

- *Why not collect inside the service:* it would move the recorder's lifetime into Android code and split the common logic. The process stays alive through the service either way.

`POST_NOTIFICATIONS` is requested with the location permission on Android 13+, through the existing `LocationPermission` expect UI. A denial changes nothing but the notification's visibility.

### 6. iOS: background updates on the existing manager

- Info.plist: `UIBackgroundModes: [location]`
- On the manager: `allowsBackgroundLocationUpdates = true`, `pausesLocationUpdatesAutomatically = false`, `activityType = CLActivityTypeFitness`, `showsBackgroundLocationIndicator = true`

All are set in `updates()` before `startUpdatingLocation()`, and the first is reset when the flow closes. Authorization stays `requestWhenInUseAuthorization`. Background updates are allowed because recording always starts in the foreground.

### 7. Recovery lives in `RecordPresenter`, gated by the account

At session start (`App`, beside `resolveSpacesSupport`), the recorder checks `journal.unfinished(did)` when it's Idle. If it finds one, it restores the state as Paused, marked recovered, without starting the tracker, and `App` opens the record screen.

`RecordScreen` shows that state:
- **Resume:** service, tracker, new segment.
- **Finish:** the normal finish flow.
- **Discard:** delete.

Start is replaced while a recovered recording is shown. The feed's Record entry opens the same screen, so it's offered wherever recording starts.

A recording whose DID isn't the session's is never loaded. `recording.did` comes from `RecordPresenter` passing `clients.did` to `start()`.

### 8. Finish publishes what was saved, then deletes

Finish runs in this order:
1. `recorder.finish()`: pause and stop the tracker. The service stays up while the recording is Paused.
2. Build the draft from the recorder's samples, which are exactly the journal's.
3. `publish`.
4. Only on success, `recorder.finished()`, which deletes the journal and goes Idle (the service then stops itself).

`publish` queues to the outbox and drains without throwing for network failures, so offline finishes delete the journal once queued, and the outbox holds the activity from then on. A thrown refusal (scope, spaces, visibility) leaves the recording Paused with `RecordState.error`, so the athlete can change the visibility and finish again, or discard.

### 9. Sign-out stops the recording and keeps it

`AuthRepository.signOut` calls `recorder.suspendForSignOut()`, which stops the tracker and the service and leaves the journal row as unfinished. The same account's next session recovers it (Decision 7), and other accounts never see it.

## Risks / Trade-offs

- [OEM battery killers stop foreground services anyway] → The journal makes it a recovery, not a loss. Nothing more without asking for background location.
- [The journal grows during very long recordings (a 24 h ultra is ~86k rows)] → Indexed by `(recording, sequence)`, read once at finish. Bounded by deletion on finish or discard.
- [Location pauses while the iOS app is suspended under memory pressure] → `pausesLocationUpdatesAutomatically = false` keeps updates on. A termination is recovered like Android's.
- [Room write failure (disk full)] → The sample isn't applied, and the recorder surfaces a recording error state instead of continuing silently.
- [The Android 14 foreground-service rules change again] → The service is isolated behind `RecordingForeground`. A manual test checklist in the tasks covers lock, background, kill and reboot on API 26, 33 and 34+.

## Migration Plan

Room `4 → 5` creates the two tables. Nothing existing changes. A recording in progress during the app update is lost once, since it was only in memory. There's no server side to roll out.

## Open Questions

- The notification's exact refresh cadence (5 s is the starting point) can be tuned on device without changing the specs.
