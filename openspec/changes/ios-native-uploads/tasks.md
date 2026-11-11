# Tasks

## 1. Probe (throwaway code, not merged)

- [x] 1.1 Throwaway Swift tests on a cloned iPhone 17 (iOS 26.0) simulator over `UploadsSamples` (design decision 1): the empty queue's `emptyList()` fields, the loaded queue's typed arrays, `UploadSource` and `ImportSaveStatus` casts, the enums, `AtUri.rkey`, `timeLeft` as a `Duration`, `serverPausedUntil`, `expandedImports`, and a Kotlin `TimeZone` and `Instant` made in Swift. Verify the tests pass or record what fails.
- [x] 1.2 Remove the throwaway tests. Verify `git status` shows only the change's own files.
- [x] 1.3 Write the results into `openspec/explorations/ios-native-ui.md` under "Phase 4 probes (uploads)". Verify it records each result.

## 2. Kotlin rules (no visible change)

- [x] 2.1 `DurationText.approximate` in `:app-common-api` with `duration_minutes`, `duration_hours`, `duration_days` and `uploads_time_left` replacing the three `uploads_time_left_*` plurals (decision 2); `SharedTextNamesTest` covers it. Verify a `commonTest` for each unit, rounding and the minimum, and `generateIosResources` regenerates the catalog.
- [x] 2.2 `DayText.day` and `DayText.until` in `:app-common-api`; `startTime` and `untilText` resolve them (decision 4). Verify a `commonTest` for today, yesterday, a written date, and today and tomorrow for `until`, and `:app-common-ui:jvmTest` and `:feature-feed-ui:jvmTest` pass.
- [x] 2.3 `UploadsTexts` and the `UploadsState` getters in `:feature-uploads-api`; `UploadsUi`, `UploadsRows` and `UploadsText.kt` ask them (decision 3). Verify a `commonTest` over the samples for every function and condition, and `:feature-uploads-ui:jvmTest`.
- [x] 2.4 Verify `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite` passes with no reference changed.

## 3. The native page

- [x] 3.1 `ShownClock` in the environment and `CatalogText`'s zone (decision 5). Verify a Swift test that the fixed clock reads the samples' "Today" and time in UTC.
- [x] 3.2 Type aliases and `Samples.uploads`. Verify a Swift test reads them (`UploadsTextTests`).
- [x] 3.3 `UploadsView` with its two alerts, registered (decision 6). Verify `UploadsTextTests` resolves the spec's lines and the conditions on the samples and the alerts' words, and `ScreenRegistryTests` names `UploadsScreen`.
- [x] 3.4 Previews and `UploadsSnapshotTests` (decision 7). Verify they record on a first run and pass on a second on an iOS 26.0 iPhone 17 simulator, and each image was looked at, light and dark.

## 4. The whole change together

- [x] 4.1 Run `./gradlew format`, `./gradlew check` and `./gradlew -p build-logic :convention:test`. Verify all pass, with no Android screenshot reference changed.
- [x] 4.2 Run the Swift tests (`xcodebuild test`) on the cloned iPhone 17 (iOS 26.0) and iPhone 16 Pro (iOS 18.4) simulators. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.

## 5. Walk the page

- [x] 5.1 Only while no other session is walking: install the Debug build on the signed-in iPhone 17 Pro (iOS 26.0), open Uploads from the feed's upload row or the import flow, and look at what the account's queue shows (likely empty). Never cancel a real upload or pause imports without the user's go-ahead. Verify the page, its title and Clear, and back.
- [x] 5.2 Check the iPad Air 11-inch the same way, if it can be done without displacing another session's build.

## 6. Docs

- [x] 6.1 The roadmap: a bullet under Phases item 4, "Phase 4 probes (uploads)" and "Phase 4 findings (uploads)" sections after the zones contracts', and the open items (lengths of time, dates). Verify each names this change.
- [x] 6.2 `.agents/rules/compose-screens.md`: `UploadsTexts`, `DurationText`, `DayText` and `ShownClock` where the native-view and text rules list their peers. Verify `scripts/check-agent-sync.sh` passes.
- [x] 6.3 A "Not verified" section listing what wasn't seen and why. Verify it names every unchecked case from 5.

## Probe results (1.1)

Recorded in the roadmap under "Phase 4 probes (uploads)": the queue model's plain lists read as typed arrays, the empty `emptyList()`
ones included; `content.data`, `UploadSource`, `ImportSaveStatus`, the enums and `AtUri.rkey` read as expected; `timeLeft` is a
`Duration` class reading 93,600 s for the part-way sample; Swift makes a Kotlin `TimeZone` (`of(zoneId:)`) and `Instant`
(`fromEpochMilliseconds`, since `fromEpochSeconds` is ambiguous from Swift).

## Checks run

- `./gradlew check` (incremental compilation off), passing, with the Android screenshot suite comparing unchanged; `./gradlew -p
  build-logic :convention:test` passing; `scripts/check-agent-sync.sh` passing.
- `xcodebuild test` on `Uploads iPhone 17` (iOS 26.0, D6328F65-DD4F-4359-892A-31D995B1C1F9): 165 tests in 29 suites pass, the 19
  uploads samples compared light and dark after the first run recorded them. On `Uploads iPhone 16 Pro` (iOS 18.4,
  958C0DC3-FF20-44F5-9D02-7A170A295DD3): 165 pass, snapshot suites skipped. Both simulators were created for this change and hold no
  session.
- Each recorded image was looked at: loading (no Clear), failed, loaded, refreshing, refresh failed, empty (Clear unavailable), own
  only, an import part way and expanded (20 rows, then Show all below the fold), both pauses, refused, finished, saving, stopped (its
  line red), the three questions (the page under the alert), a failed cancel (above the lists) and a send in flight (that row's actions
  unavailable).

## Walk (iPhone 17 Pro, iOS 26.0, signed in as alice on the local stack, 2026-11-10 ~20:29 to 20:36)

Done with the go-ahead of the session that started this one, after the invite lists' walk on the same simulators had finished.

- Installed this change's Debug build (built with the checkout's device-mode `local.properties`) on the signed-in iPhone 17 Pro, replacing
  the invite lists' build. The feed loaded signed in, with no upload row.
- **Created one activity and deleted it.** A manual Run, "Walk test (uploads), delete me", 1 mi in 10 min, Only me (and its details Only
  me), saved at 8:32 PM. It uploaded within seconds: by the time the app was back on the feed, the card showed "Only me" and the upload
  row was already gone. Deleted from its page at 8:35 PM (the system's "Delete this activity?"); the feed is back to its cards before the
  walk. Nothing was cancelled, imports weren't paused, and no other record was touched.
- **The queue afterwards**, read from the app's database on the simulator (read-only): the outbox is empty, and the upload log holds two
  finished items, an older "Session" upload (finished 2026-11-10, before this walk) and the walk test's. A deleted activity's finished
  log item stays until the week's prune or Clear; Clear is on the page this walk couldn't open and would also drop the older item, so
  both were left.
- Seen in passing, not this change's: after Save on the shared Add activity form the tab bar came back while the form, frozen on
  "Saving…", stayed drawn under it until the Feed tab was tapped. The fallback screen kept its last frame after its record left.
  Taps on this simulator also landed late, as the settings lists' session noted: the first tap on the feed's + menu opened Record, which
  was backed out of without recording.
- **Then the page, signed in (2026-11-10 ~20:39 to 20:53).** At the user's prompt, an import route: a single file
  (`spike-ride.fit`, already in the simulator's On My iPhone storage, saved as "Walk test (uploads import), delete me", Only me) opens
  its activity, not "View uploads", so two generated GPX tracks ("Walk test A/B (uploads), delete me", Only me, copied into On My
  iPhone and removed afterwards) were imported as a batch. "Import saved" offered View uploads, which opened the **native Uploads page**:
  the stack's back button and Clear (enabled) in the bar; the import as "walk-test-a.gpx and 1 more", "Saved Today at 8:48 PM", "2 of 2
  uploaded", no Cancel rest (nothing left to cancel), its two items "Uploaded"; then Finished with the single import, the first walk
  test and the older "Session". Tapping an uploaded item opened its activity; deleting it from there popped back to the native page;
  its back button returned to the feed. All three activities were deleted, the outbox is empty, and the feed is as before. Their
  finished log items (and the import's group) stay on the simulator until the week's prune or Clear, which wasn't tapped since it
  would also drop the older item.
- The iPad Air was left as the invite lists' walk left it.

## Not verified

- **The page on iPad**, signed in: 5.2 was marked done at the user's word without an iPad walk (not installed there, so as not to
  replace the invite lists' build again).
- **A queue with work in it, signed in**: uploads on the local stack finish in a second or two, so waiting, uploading, refused and
  saving items, the pauses, the time left, Send again, Cancel, Cancel rest and both alerts were seen only in the snapshots; Clear was
  not tapped (it would also drop another session's finished item). VoiceOver wasn't heard.
- **The alerts' behaviour.** That Cancel upload and Cancel rest send their confirm events, and Keep or a swipe sends `Dismiss`, is
  read from the code and the presenter's handling, not tapped; the alerts' words are held by `UploadsTextTests`.
- **The page beside Settings on iPad** doesn't arise: Uploads is pushed over the feed, never a settings page.
