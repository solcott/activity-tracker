# Tasks

## 1. Spikes and dependencies

- [x] 1.1 Add `no.synth:kmp-zip`, `no.synth:kmp-zip-kotlinx` and `kotlinx-serialization-json-io` to `gradle/libs.versions.toml` and `core/data`; verify the `android`, `jvm`, `iosSimulatorArm64` and `wasmJs` compilations of `:core-data` resolve kmp-zip (`./gradlew :core-data:compileAndroidMain :core-data:compileKotlinWasmJs :core-data:compileKotlinIosSimulatorArm64`). If the Android target can't resolve its JVM variant, switch to design decision 5's fallback (`expect` zip sink, `java.util.zip` on Android) and record that in design.md
- [x] 1.2 Spike the web OPFS path in `wasmJsMain`: a `RawSink` over `FileSystemWritableFileStream` writing 1 GB in chunks, then `getFile()` plus an object-URL download, run in `:webApp:wasmJsBrowserDevelopmentRun`. Confirm wasm memory stays flat in the browser's dev tools. If it can't stream, adopt the in-memory capped fallback from design.md's risks and update the auth/data-export spec's web scenario before going on
- [x] 1.3 Run `./gradlew kotlinWasmUpgradeYarnLock` if the wasm npm lock changes, and `./gradlew :core-data:jvmTest` stays green

## 2. What to walk

- [x] 2.1 Add `AppCollections.records` in `core/lexicon`: every `app.activitytracker` record NSID. Add a jvm test that parses every `lexicons/app/activitytracker/**.json` with `"type": "record"` and asserts the lists match; verify with `./gradlew :core-lexicon:jvmTest`
- [x] 2.2 Add a blob-ref walker (`{"$type":"blob","ref":{"$link":cid}}` anywhere in a record's JSON) with tests on an activity streams record, a source record, a profile with an avatar and a record with none

## 3. Activity files from the PDS

- [x] 3.1 Give `ActivityExporter` an entry point that builds from a `StoredActivity` (from `ActivityPlaces.read`), the full track and the source info, without the AppView: the original when there is one, else FIT, else none. Add `ActivityExporterTest` cases for an imported ride (original bytes back), a recorded run split across tiers (FIT holds every tier's channels and the untrimmed track), and a manual entry (none); verify with `./gradlew :core-data:jvmTest --tests "*ActivityExporterTest"`
- [x] 3.2 Add archive file naming (`<yyyy-mm-dd>-<title>[-n].<ext>`, the original's name kept after the date, unique within an export) with tests for clashing titles and characters unsafe in file names

## 4. The export job

- [x] 4.1 Add the `data_exports` and `data_export_items` Room entities, a DAO and a schema version with an auto-migration; verify the generated schema JSON and `./gradlew :core-data:jvmTest`
- [x] 4.2 Add a `DataExportStore` (start, per-collection list cursors, pending/done/skipped items, counts, state) with tests for resuming a listing from its cursor and skipping done items
- [x] 4.3 Add a `DataExporter` runner:
  - list the repo (`AppCollections.records`) and, when `canUseSpaces`, each of the four spaces (`AthleteSpaceDeclaration`);
  - stage each record's JSON;
  - queue blobs (originals routed to activity files) and one activity file per `feed.activity` rkey;
  - fetch with bounded concurrency and backoff, honoring `ratelimit-reset`;
  - skip items with reasons.

  Cover each auth/data-export scenario against `FakeAthletePds`: only-me run, private settings, interactions, missing mutuals space, split tiers, shared blob, AppView unreachable (no AppView calls made), unreadable blob, offline at start, no space scope, Bluesky records ignored
- [x] 4.4 Write `manifest.json` (`"format": 1`, account, PDS, time, counts, unsent changes from the outbox, `spacesIncluded`, activity URI to file, source CID to file, problems) and `README.txt` from `:ui-resources` strings. Test the manifest for the split-tier and problem cases
- [x] 4.5 Add the zip pass (staging tree → `ZipOutputStream(Sink)`; JSON deflated, blobs and activity files at a low level or stored on wasm). Add a jvm round-trip test that unzips with `java.util.zip` and compares every entry to staging, plus a ZIP64 test with more than 65,535 entries
- [x] 4.6 Add the free-space check (`expect fun freeBytes`) before starting and the mid-run pause on a write that fails for lack of space, resuming on retry; test with a fake that reports low space
- [x] 4.7 Add `DataExports` (app-scoped like `ImportSaves`): a per-account lock, start, cancel (stop and delete `exports/<id>/`), `signedOut(did)`, deletion after 7 days at session start, and a progress `Flow`. Test single-runner, cancel and delete, sign-out, expiry and resume after a simulated kill (a new runner over the same database)

## 5. Running in the background

- [x] 5.1 Add `DataExportLauncher` and the in-process default, wired in `AppGraph` beside `ImportSaveLauncher`, reusing `backgroundGraph` from `BackgroundUploads.kt`; verify an in-process export runs to `ready` in a `composeApp` jvm test
- [x] 5.2 Android: a `DataExportWorker` (foreground, `dataSync` type, progress notification, rerun after process death), its manifest entries, and a ready notification opening the Account page; a Robolectric/WorkManager-testing test that it's scheduled once per account and resumes
- [x] 5.3 iOS: a `BGProcessingTask` identifier in `iosApp/Info.plist`, an `IosDataExports` registering and scheduling it, `beginBackgroundTask` while backgrounding, resume on launch, and a local notification when ready; build with the `xcodebuild` command in CLAUDE.md and run one export end to end in the simulator against the local stack

## 6. Handing the archive over

- [x] 6.1 Add `rememberArchiveHandOff` in `:app-common-ui` per design decision 7: Android and desktop `openFileSaver` then a zip pass into its sink; iOS a cache file then the share sheet; web OPFS then a download. Dismissing keeps the export ready, and a completed save deletes it. Run it manually on desktop (`./gradlew :desktopApp:run`) and on the web, and check that the unzipped archive matches the staging tree

## 7. Account page and sign-out

- [x] 7.1 Add an `export` sub-state (none / running / ready / paused / failed) and Export, Cancel and Save events to `AccountScreen` in `feature/settings/api`; `./gradlew composeStabilityCheck` passes
- [x] 7.2 Wire `AccountPresenter` to `DataExports`: start, cancel and save through `rememberSingleFlight`, unsent-change and no-spaces notes. Add presenter tests for each auth/account-page export scenario and a double tap starting one export
- [x] 7.3 Add the export row to `AccountUi` (description, progress, Cancel, Save, problems count, paused for space, web keep-tab-open note), with strings and plurals in `:ui-resources`
- [x] 7.4 Add `exportPending` to `AuthRepository`'s pending-work summary and the sign-out dialog line, and have sign-out and Sign in again call `DataExports.signedOut`. Test the new "Export not yet saved" scenario in `AccountPresenterTest` and `AuthRepositoryTest`
- [x] 7.5 Add `AccountSamples` for each export state, extend `AccountUiTest`, add screenshots in `AccountScreenshots.kt`, regenerate references with `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite` and review them

## 8. Integration

- [x] 8.1 Run `./gradlew check`, `./gradlew checkModuleBoundaries` and `scripts/check-public-safety.sh`, and all pass
- [x] 8.2 With `scripts/dev.sh --seed alice`, export on Android with Only me, Followers and Mutuals activities, gear and interactions. Kill the app mid-export, let it resume, save the zip, and confirm its records, blobs and activity files against the PDS (the original FIT byte for byte, the rebuilt FIT importing back)

## Workflow follow-up

- Sync the specs and archive the change once implemented and reviewed (`/opsx:archive`).
- The leave-activity-tracker change can then point at this export before deleting anything.
