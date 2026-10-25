# Proposal

## Why

GPS recording only works while the app is on screen. Android collects fused location with no foreground service, and iOS has neither `allowsBackgroundLocationUpdates` nor the `location` background mode. So a run recorded with the phone locked in a pocket comes back with a handful of points. Samples live only in `ActivityRecorder`'s memory, so a process the system kills loses the whole recording, and so does a Finish whose publish is refused: `recorder.stop()` clears the state before `publish` runs. Recording is the app's core feature, and the codebase review (R1–R4) found all of this.

## What Changes

- **Background recording on Android.** A foreground service of type `location` runs while a recording is in progress, started from the Start tap. Its notification shows elapsed time and distance. The manifest gains the foreground-service and notification permissions, and the app asks for notification permission on Android 13+ when recording starts. The location permission stays "while in use".
- **Background recording on iOS.** The `location` background mode in Info.plist, and on the location manager `allowsBackgroundLocationUpdates`, `pausesLocationUpdatesAutomatically = false` and the fitness activity type. The permission stays "when in use".
- **A recording journal.** Every accepted sample, and every start, pause, resume and stop, is saved as it happens, for the account that started the recording. The in-memory list of samples is replaced by the journal plus running totals, which also removes the O(n²) copy (R4).
- **Recovery after the app is killed.** On the next launch, an unfinished recording is offered to the athlete who started it: resume, save, or discard. Time and distance between the last saved sample and a resume count as paused.
- **Finish never loses a recording.** The recording stays in the journal until its activity is queued for publishing. If publishing is refused, the athlete can retry or discard.
- **Pausing leaves out the pause (R2).** Distance, moving time and speed after a resume are measured from the first new sample, not from the last one before the pause.
- **Elevation gain is counted once per sample (R3)**, even when a pause or resume happens during the update.
- When implemented, R1–R4 are deleted from `docs/reviews/2026-10-23-codebase-review.md`.

## Capabilities

### New Capabilities
- `record/gps-recording`: recording a GPS activity on a phone: it keeps recording with the screen off or the app in the background, pause and resume, the journal and recovery after the app is killed, and finishing into a published activity without losing the recording.

### Modified Capabilities
None. Where a resumed or recovered recording's map line breaks follows `feed/map-breaks` as it stands.

## Impact

- **`core/location`:** `ActivityRecorder` (state from the journal, segments per resume, the climb kept in state), a new `RecordingJournal` interface, `IosLocationTracker` (background settings), and the Android foreground service with its notification (`androidMain`).
- **`core/data`:** a Room implementation of the journal (new entities, an `AppDatabase` version bump and migration, regenerated schema JSON), bound in `AppScope`.
- **`app/presenters` / `app/screens` / `composeApp`:** `RecordPresenter` (keeps the recording until publish, recovery state and events, notification permission), new strings, `:app-fixtures` samples, UI and screenshot tests.
- **Platform apps:** `androidApp` manifest (service declaration, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`), `iosApp/Info.plist` (`UIBackgroundModes`).
- **No lexicon, AppView or record format changes.** A recorded activity publishes exactly as today.
