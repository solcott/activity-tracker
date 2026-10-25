# Tasks

## 1. Recorder state without the sample list (R2, R3, R4)

- [x] 1.1 Replace `RecordingState.samples` with a segment number, the segment's last sample, the climb base and a sample count, and move `climbTo` into a pure function of the state. Verify with `:core-location:jvmTest`: a test feeds samples while racing `pause()` and checks gain is counted once.
- [x] 1.2 Make `resume()` start a new segment, and measure step, seconds and speed only within a segment. Verify with tests for the spec's "Walking to a cafe while paused" and "Top speed across a pause" scenarios in `ActivityRecorderTest`.
- [x] 1.3 Move anything reading `state.samples` off it (there is no live map: only `ActivityDraft.fromRecording` read it, now from `RecordingResult.samples`). Verify the record screen still draws the route with `:composeApp:jvmTest` (`RecordUiTest`) and the existing record screenshots, `:androidApp:testDebugScreenshotTestDefaultTestSuite`.

## 2. Recording journal

- [x] 2.1 Add the `RecordingJournal` interface to `core/location` (`begin`, `append`, `mark`, `unfinished`, `load`, `delete`), plus `RecordingJournal.None` and an in-memory fake for tests (the Room journal binds on every platform, so no `nonMobileMain` no-op is needed). Verify `./gradlew :core-location:compileKotlinJvm` and the desktop/web graphs still build (`:desktopApp:compileKotlin`).
- [x] 2.2 Add the Room `recording` and `recording_sample` entities and DAOs, `MIGRATION_4_5` in `addAppMigrations`, `AppDatabase.version = 5`, and commit the regenerated schema JSON. Verify with a `core-data` migration test that opens a version-4 database and migrates it.
- [x] 2.3 Implement `RoomRecordingJournal` in `core/data`, bound in `AppScope`, keyed by DID. Verify with `core-data` jvm tests: append and load round-trip, `unfinished(did)` ignores other accounts' recordings, `delete` leaves no rows.
- [x] 2.4 Make `ActivityRecorder` write through the journal: `begin` on start, `append` before a sample is applied, `mark` on pause, resume and stop, and a recording error state when a write fails. Verify with recorder tests using the fake journal, including the spec's "Battery dies" scenario (rebuild the state from the journal and compare totals).

## 3. Finish, recovery and sign-out

- [x] 3.1 Change `RecordPresenter` Finish to pause collection, build the draft from the journal, `publish`, and delete the journal only on success, keeping the recording Paused with `error` on a refusal. Verify with `RecordPresenterTest` cases for "Private recording on a session without Spaces" and "Offline finish".
- [x] 3.2 Restore an unfinished recording for the session's DID at session start (`App`, beside `resolveSpacesSupport`) as Paused and recovered. Add `RecordState.recovered` and the Resume, Save and Discard events, with strings, `:app-fixtures` samples (`RecordSamples.recovered`) and a UI test. Verify with `RecordPresenterTest` ("App killed mid-ride", "Another account") and `ContentLoadingConformanceTest`/`ScreenshotCoverageTest` if `State` gains fields they check.
- [x] 3.3 Make resuming a recovered recording count the gap as paused and start a new segment. Verify with a recorder test for the spec's "Resumed after a crash" scenario, and a `core-data` test that its published polyline breaks per `RouteBreaks`.
- [x] 3.4 Call `recorder.suspendForSignOut()` from `AuthRepository.signOut`. Verify with an `AuthCoordinatorTest`/recorder test for "Sign out and back in".

## 4. Android background recording

- [x] 4.1 Add `RecordingService` (`core/location` `androidMain`, `foregroundServiceType="location"`), its notification channel, and the ongoing notification with elapsed time, distance and a Pause/Resume action that opens the record screen when tapped. Declare it and `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` and `POST_NOTIFICATIONS` in the module manifest. Verify `./gradlew :androidApp:assembleDebug` and that the merged manifest lists the service.
- [x] 4.2 Add the `RecordingForeground` hook that the recorder calls on status changes (Android starts and stops the service, other platforms do nothing). Verify with a recorder test that the hook sees start, pause, resume and stop in order.
- [x] 4.3 Request `POST_NOTIFICATIONS` with location on Android 13+ in `LocationPermission`, so a denial still records. Verify on an API 34 emulator: deny notifications, record with the screen off for 5 minutes, and the route has samples throughout.
- [x] 4.4 Device checks on API 29 (the minSdk), 33 and 34+ (API 29 not testable on its emulator, see results below): screen locked for 10 minutes, another app in front, Pause/Resume from the notification, and killing the process (`adb shell am kill`) followed by recovery on relaunch. Record the results in the task's notes.

## 5. iOS background recording

- [x] 5.1 Add `UIBackgroundModes: location` to `iosApp/Info.plist`, and set `allowsBackgroundLocationUpdates`, `pausesLocationUpdatesAutomatically = false`, `activityType = fitness` and `showsBackgroundLocationIndicator` in `IosLocationTracker.updates()`, resetting the first on close. Verify the `xcodebuild` simulator build from AGENTS.md and `:core-location:iosSimulatorArm64Test`.
- [x] 5.2 Simulator and device checks: City Run or Freeway Drive simulated location with the app in the background for 10 minutes keeps receiving samples, the blue location indicator shows, and a terminated app recovers on relaunch.

## 6. Integration

- [x] 6.1 Run `./gradlew check` and `./gradlew composeStabilityCheck`, and fix what they report.
- [x] 6.2 Delete R1–R4 from `docs/reviews/2026-10-23-codebase-review.md`, and fix any "Suggested order" items that reference them. Verify `grep -n "R[1-4]" docs/reviews/2026-10-23-codebase-review.md` returns nothing.

## Device check results (2026-10-24)

- **Android, Pixel 9 Pro XL, API 37** (4.3, 4.4 on 34+):
  - Location was asked "while using the app" only, with no "all the time"; notification permission denied.
  - Screen off (dozing) for 5 min 26 s: 325 samples, about 1 Hz, and the service in the foreground (type location).
  - Clock app in front for 2 min 14 s: 135 samples.
  - With notifications allowed, the notification read "Recording · m:ss · 0.1 mi". Pause made it "Recording paused" and offered Resume, and Resume went back to Pause, both journaled. Tapping it opened the record screen from the feed.
  - `kill -9` of the process: the service didn't restart, 992 samples were kept, and relaunching opened the recovered recording (16:25 elapsed, the 13 s pause left out) with its notice and Resume/Finish/Discard. Finish published it, emptied the journal and stopped the service.
- **Android, emulator, API 33** (4.4):
  - Location was asked "while using the app" only; the notification prompt was left unanswered at first.
  - Screen asleep for 5 min: 340 samples.
  - Settings in front for 2 min 5 s: 138 samples.
  - With notifications allowed, Pause and Resume from the notification were journaled, and tapping it opened the record screen.
  - Killing the process as root: the service stayed down and 746 samples were kept. Relaunching offered the recording back (11:24, the 5 s pause left out), and Discard emptied the journal and stopped the service.
  - Pace shows 0:00 there because the emulator's `geo fix` reports a speed of 0 m/s.
- **Android API 29** (the minSdk, raised from 26 because current Chrome needs 29): not checked. The emulator image's Chrome 91 renders the local PDS's sign-in page blank, so the app can't sign in there. Handing the sign-in to another browser fails because the PDS binds a sign-in request to the first browser that opens it. API 26's image (Chrome 58 WebView only) hit the same wall before the minSdk change.
- **iOS, iPhone 17 simulator, iOS 26** (5.2):
  - Location was asked "while using" only.
  - With a simulated 5 m/s route: 190 samples in 190 s with Safari in front, and the location arrow in the status bar.
  - Terminated mid-recording: relaunch opened the recovered recording (0.78 mi) on the record screen.
  - These checks found and fixed three bugs:
    - The location delegate wasn't kept alive (no positions on iOS at all).
    - Navigating to the record screen on the first frame crashed Compose's insets.
    - Recovery didn't save its implicit pause, so a second crash counted the first gap as active time.
  - Not checked on a physical iPhone, which can't sign in to the local stack.

## Workflow follow-up

- Archive the change with `/opsx:archive` once the device checks pass, which creates the `record/gps-recording` main spec.
