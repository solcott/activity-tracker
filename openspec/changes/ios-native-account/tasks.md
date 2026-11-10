# Tasks

## 1. Probe (throwaway code, not merged)

- [x] 1.1 Run throwaway Swift tests on an iPhone 17 (iOS 26.0) simulator, and read the generated Swift for stubs (design decision 1). They cover:
  - the Account and Leave samples' `GrantStatus`, `DataExportStatus` (with `Running.phase`), `EndingSession`, `PendingWork`, `SharedPlacesSummary`, `LeaveRun` and `LeaveStep`;
  - whether `ExportArchive`, `Outdated.missing` and `pausedUntil` arrive;
  - a scratch `suspend` entry on `IosApp`, awaited from Swift;
  - a `List<AppText>` getter.

  Verify the tests pass and print what each read.
- [x] 1.2 Remove the throwaway tests. Verify `git status` shows only the change's own files.
- [x] 1.3 Write the results into `openspec/explorations/ios-native-ui.md` under "Phase 4 probes (account)", and update `design.md` where a probe changes a decision. Verify the roadmap records each result.

## 2. Kotlin rules (no visible change)

- [x] 2.1 Add `AppText.Time`, resolved by the Compose `TextSource` with `timeFormat()` and handled by every `when` over `AppText` (decision 3). Verify a `commonTest` and `:app-common-ui:jvmTest` with a 12- and a 24-hour clock.
- [x] 2.2 Add `AccountTexts` and the `AccountState` getters; `AccountUi` asks them (decision 2). Verify `commonTest`s covering:
  - each grant, and the missing features' order;
  - each export status's lines, including no records yet and the paused megabytes rounded up;
  - both end-session questions.

  Also verify `:feature-settings-ui:jvmTest`.
- [x] 2.3 Add `LeaveTexts` and the `LeaveState` getters; `LeaveUi` asks them. Verify `commonTest`s (places none, loaded and failed; device lines; each step and none; records deleted; paused at a fixed time) and `:feature-settings-ui:jvmTest`.
- [x] 2.4 Add `AccountSamples.variants` (ending, a failed save) and `IosApp.writeArchive` (decision 4). Verify an `:iosShell:iosSimulatorArm64Test` writes a sample archive to a temporary path, and that a failure leaves no file. Verify `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite` passes with no reference changed.

## 3. Design system

- [x] 3.1 Add `pageTitle(_:back:actions:)`, `\.paneRoot` from `ScreenView`, and `PageBar`'s back button (decision 5). Verify:
  - a gallery entry with and without back, with and without actions;
  - the title stays centred in each;
  - VoiceOver reads Back.
- [x] 3.2 Add `DesignSnapshotTests` for the header with back. Verify they record, pass on a second run and were looked at, light and dark.

## 4. The native pages

- [x] 4.1 Add the type aliases and `Samples.account`, `Samples.accountVariants` and `Samples.leave`. Verify a Swift test reads every sample.
- [x] 4.2 Add `AccountView` with Copy and the two alerts, registered (decision 6). Verify:
  - an `AccountTextTests` resolves the spec's lines on the outdated, export and end-session samples;
  - the registry test names it.
- [x] 4.3 Add `ArchiveShare`: write, share sheet, delete, events (decision 4). Verify a Swift test over a fake presenter:
  - a completed activity sends `ExportShared`;
  - a dismissal sends nothing;
  - a failed write sends `ExportSaveFailed`;
  - the file is gone after each.
- [x] 4.4 Add `LeaveView`: summary, confirmation and progress, with no back while a leave runs, alone and in the column; registered. Verify:
  - `LeaveTextTests` cover the places, device, step, records and paused lines;
  - `canLeave` gates the button for "@Alice.test" and "alice";
  - the registry test names it.
- [x] 4.5 Add previews and `AccountSnapshotTests`: Account and Leave at a phone's width, Leave's progress, Leave as a deeper page beside the list at an iPad's size, and Account beside the list. Verify they record on a first run and pass on a second on an iOS 26.0 iPhone 17 simulator, and each image was looked at.

## 5. The whole change together

- [x] 5.1 Run `./gradlew format`, `./gradlew check` and `./gradlew -p build-logic :convention:test`. Verify all pass, with no Android screenshot reference changed.
- [x] 5.2 Run the Swift tests (`xcodebuild test`) on the iPhone 17 iOS 26.0 simulator and on iOS 18.4. Verify they pass, with snapshots compared on 26.0 and skipped on 18.4.
- [x] 5.3 Build the Release configuration for the simulator. Verify it succeeds and passes the stub check.

## 6. Walk the pages

Never confirm a leave on a device.

- [x] 6.1 On the signed-in iPhone 17 Pro (iOS 26.0), walk:
  - Account from Settings, with each line's words against the shared page;
  - Copy, and paste in another field;
  - Sign out up to its alert, cancelled (never confirmed);
  - an export started, cancelled, started again and saved through the share sheet to Files, then the kept question answered both ways;
  - Leave opened, a handle typed both ways, then Cancel and back by the button and the swipe.

  Verify by noting what was seen. (Sign out's alert and cancelling a running export weren't seen: see Not verified.)
- [x] 6.2 On the signed-in iPad Air 11-inch (iOS 26.0), walk:
  - Account beside the list with no back;
  - Leave beside it, with a back button returning to Account while the list keeps Account marked;
  - the share sheet's popover;
  - all of it upright and on its side.

  Verify by noting what was seen.
- [x] 6.3 Launch the build on an iOS 18.4 simulator. Verify it reaches sign-in, and record what couldn't be seen there.

## 7. Docs

- [x] 7.1 Update the roadmap's phase 4 entry, add a "Phase 4 findings (account)" section, and update the open items: close the column back button item, and add `LeftElsewhereScreen` and the remaining time composables. Verify `openspec validate ios-native-account --strict` passes.
- [x] 7.2 Update `.agents/rules/compose-screens.md` (`AppText.Time`, `AccountTexts`, `LeaveTexts`, the `suspend` entry) and `adaptive-ui.md` (the native pages and the header's back button). Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 7.3 Add a "Not verified" section listing what wasn't seen and why, at least a running leave on a device and iOS 18 signed in. Verify it names every unchecked case from 6.

## Walk (iPhone 17 Pro and iPad Air 11-inch on iOS 26.0, signed in as the local test account; iPhone 16 Pro on iOS 18.4)

- **6.1, iPhone.** Settings → Account, native, with the account's real handle, DID and server. Copy put the DID on the clipboard (read
  back with `simctl pbpaste`) and the button read "DID copied". An export started, ran ("Downloading files: 5 of 29") and finished. Save
  turned itself off while the 2.5 MB zip was written and the share sheet offered it. Dismissing the sheet asked nothing, left the export
  ready and left no zip in the app's caches (seen twice). Save to Files → On My iPhone asked "Did you keep the export?" once the sheet had
  gone, and Keep it left the export ready. The sheet's Copy action asked again, and "Delete the app's copy" returned the page to Export
  your data; so did Delete export.
- **The iPhone was signed out by mistake mid-walk.** A tap meant for the Leave row landed on Sign out as the list bounced back to its
  top, and with nothing pending Sign out ends the session without asking, as the shared page does. It was signed back in, not by this
  session, and the walk went on, waiting for each scroll to settle before tapping.
- **6.1, Leave on the iPhone.** Native, titled in the stack's bar with the system back button. The places summary loaded ("4 segments
  others use stay… Your other 2 segments are deleted."). "alice.tes" left Leave disabled; "alice.test" turned it red and enabled; the
  text was deleted again at once and Leave never tapped. Cancel returned to Account at its scroll position; Leave opened again and the
  bar's back button returned; Leave opened again and the edge swipe returned; Leave opened once more, so Kotlin's stack stayed in step.
- **6.2, iPad (this change's Debug build installed over the old one, still signed in).** Upright: Account beside the list with its own
  header and no back, the list marking alice.test. An export ran and Save offered the share sheet as a popover centred over the window,
  with no arrow; its Copy action closed it and the kept question followed, and "Delete the app's copy" returned the page to Export your
  data. Leave beside the list: a back chevron at the header's leading end, the list still marking alice.test, the places line loading then
  filled; the chevron returned to Account, whose header had no back. On its side (landscape left): Account beside the list with no back,
  Leave beside it with the chevron, and the chevron back to Account. Turned upright again.
- **6.3, iOS 18.4 (iPhone 16 Pro).** The same build installed and launched to the native sign-in screen. Nothing past sign-in was seen
  there (see Not verified).

## Not verified

- **A running leave on a device.** Never started: a real leave deletes the account's data. Its progress, pause and stop rest on the
  samples' snapshots and text tests.
- **Sign out's question, and Sign in again.** The question only comes with work pending, and the test account had none, so Sign out
  wasn't tapped on purpose (it would end the session); no outdated grant either. The alerts' words rest on `AccountTextTests`, and an
  alert isn't in the snapshots.
- **Cancel export while running.** The local export finished in a few seconds, before a tap could be checked against a screenshot
  (taps there land about one screenshot late), so both tries landed on Save instead. The failed-save line is only in the
  `exportSaveFailed` snapshot.
- **VoiceOver reading the header's back button as Back.** Set with `accessibilityLabel(.actionBack)`; not heard.
- **The pages on iOS 18, signed in.** The 18.4 simulator isn't signed in and signing in needs the account's password; the Swift tests
  run there with the snapshots skipped (122 passed).
