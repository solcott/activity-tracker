# Tasks

## 1. Kotlin: the report sheet's words and the profile's way in

- [x] 1.1 `ReportTexts`, `ReportReasonChoice` and `ReportState.noteRequired`/`chose` in `:feature-report-api`; `ReportUi` and `ReportingHost` ask them (design decision 5). Verify a `commonTest` over each case, the names in a text-names test, and that the report's Android screenshot references are unchanged.
- [x] 1.2 `ProfileState.reportEvent(authorName)` in `:feature-profile-api`; `ProfileUi`'s menu asks it. Verify a test over own, other, blocking and no labeler (`ProfileReportTest`).
- [x] 1.3 Report sheet variants (`ReportSamples.variants`: Other, sending, failed, sent without the Block offer) in `:app-fixtures`, outside `all`.

## 2. Kotlin: the place-name sheet's words

- [x] 2.1 `ProposePlaceNameTexts` and `ProposePlaceNameState.showsInvalid`/`shareEnabled` in `:app-common-api`; `ProposePlaceNameUi` asks them. Verify a `commonTest`, the names in a text-names test, and unchanged Android references.
- [x] 2.2 Name sheet variants (`ProposePlaceNameSamples.variants`: sending, failed) in `:app-fixtures`, outside `all`.

## 3. Through the host

- [x] 3.1 `ReportThroughHostTest` (`composeApp` JVM): the real `ReportPresenter` over a mock labeler as the sub-screen of a stand-in parent carrying `rememberReporting`, through `sessionHostOf`: open, pick, send once, sent, Block reaching the parent's confirmation; close and reopen starting afresh; a failure keeping the reason. Verify it passes.

## 4. Swift

- [x] 4.1 `\.hostRecord` set by `NativeScreen` (design decision 1); type aliases in one added block of `KotlinTypes.swift`.
- [x] 4.2 `ReportSheet` and the `reporting(_:)` modifier (decisions 2 to 4, 6), previews over the samples (one added block in `Samples.swift`), a text test against the shared words, and `ReportSheetSnapshotTests` over every step and the failure.
- [x] 4.3 `ProposePlaceNameSheet` and the `placeNames(_:)` modifier, previews, a text test, and `ProposePlaceNameSnapshotTests`.
- [x] 4.4 Report in `ProfilePage`'s menu and the `reporting(_:)` modifier on the page. Verify the profile's snapshots are unchanged.

## 5. The whole change together

- [x] 5.1 `./gradlew format`, `./gradlew check` and `./gradlew -p build-logic :convention:test` pass, with no Android screenshot reference changed.
- [x] 5.2 Swift tests pass on cloned iOS 26.0 and iOS 18.4 simulators, snapshots compared on 26.0 (recorded on a first run and looked at, light and dark) and skipped on 18.4.
- [x] 5.3 Debug and Release simulator builds succeed and pass the stub check; the stub report lists nothing new from the report or names modules.
- [x] 5.4 Walk on the signed-in iPad Air 11-inch: bob's profile from alice's Following, Report, each step; Send only if the report goes to the local stack's labeler.

## 6. Docs

- [x] 6.1 The roadmap: a Phases item 4 bullet, "Phase 4 findings (report sheet)", open items (the post card wires Report, the name sheet waits for its pages). Verify `openspec validate ios-native-report-sheet --strict`.
- [x] 6.2 `.agents/rules/compose-screens.md` (a sheet's sub-screen in a native page: the modifier, the record from the environment). Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh`.
- [x] 6.3 A "Not verified" section.

## Found on the walk (5.4)

- **Send stayed off after a required note was typed.** `ReportState.canSend` reads the note live, which the Compose sheet follows, but
  the presenter's composition never read the note, so typing published no new state and the native Send didn't change. The presenter now
  reads it in composition into `ReportState.noteWritten`, so filling or emptying the note publishes a new state; `ReportPresenterTest`
  asserts it. Seen fixed on the iPad.
- **The type picker's snapshot varied behind its bar buttons** on the cloned iOS 26.0 simulator (336 pixels, rows 59 to 103, both runs).
  It now passes `Snapshots.barButtons` as the other screens with bar buttons do; not this change's code, but its Swift run.

## Results (5)

- `./gradlew check` passed (after `ProfileState.reportEvent` became one expression for detekt's `ReturnCount`), with no Android
  screenshot reference changed; `./gradlew -p build-logic :convention:test` passed.
- Debug and Release simulator builds succeeded and passed `checkSwiftExportStubs`; the stub report names nothing from
  `:feature-report-api`, `app.names` or `PresenterHost`.
- Swift tests (after every fix): 312 in 55 suites on `Report Sheet iPhone 17` (iOS 26.0, cloned from the snapshot simulator), snapshots compared once
  recorded and looked at, light and dark (the name sheet's re-recorded once its lead moved to a footer and its name became a row); and on
  `Report Sheet iPhone 16 Pro` (iOS 18.4), snapshots skipped.
- Walked on the signed-in iPad Air 11-inch (iOS 26.0) as alice: You, Following, bob's profile; the menu offers Mute, Block and Report;
  Report opened the native sheet as a form sheet; Other made the note required with Send off; a typed note turned Send on (after the
  fix above); Send reached the local stack's Ozone labeler (`createReport`, 200; the app's labeler DID is the one `scripts/dev.sh status`
  shows); the thanks offered Block bob; Block bob closed the sheet and then asked the confirmation, which was cancelled; Report reopened
  with no reason and no note; a pick then a swipe down closed the sheet, and it reopened fresh. Bob is still followed, not muted, not
  blocked (a stray tap muted him once mid-walk; unmuted at once).

## Not verified

- **Report on a profile the viewer blocks** (offered with no Block offer): alice doesn't block bob, and blocking him to see it would change
  the shared account. `ProfileReportTest` and the `sentNoBlock` snapshot cover it.
- **Taking the Block offer through to a block on a device**: the confirmation was cancelled so as not to block bob; `ReportThroughHostTest`
  covers the block reaching the carrier.
- **A failed send on a device**: the local labeler accepted the report; the `failed` snapshot and the host test cover the failure.
- **The place-name sheet in the app**: no native page places it (the segment and dive site pages are still the shared UI's), so it was
  seen in snapshots and previews only; its `placeNames(_:)` modifier and delete confirmation never ran.
- **The sheet on an iPhone, and on iOS 18 signed in**: the walk was on the iPad; the iPhone 17 Pro was the other session's.
- **VoiceOver** on the reason rows' selected trait.

## Open items

- **The post card wires Report** (`ios-native-post-card`): when the two branches merge, its page adds `.reporting(state.reporting)` and
  its menu sends a `ReportingEvent.Open` built by a Kotlin getter, as `ProfileState.reportEvent` builds the profile's.
- **The activity card has no Report** in either UI: the shared card has no menu; an activity is reported from its page, which is still
  the shared UI's.
- **The name sheet waits for a page**: a native segment or dive site page adds `.placeNames(state.names)`.
