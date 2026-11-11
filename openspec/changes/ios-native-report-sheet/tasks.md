# Tasks

## 1. Kotlin: the report sheet's words and the profile's way in

- [ ] 1.1 `ReportTexts`, `ReportReasonChoice` and `ReportState.noteRequired`/`chose` in `:feature-report-api`; `ReportUi` and `ReportingHost` ask them (design decision 5). Verify a `commonTest` over each case, the names in a text-names test, and that the report's Android screenshot references are unchanged.
- [ ] 1.2 `ProfileState.reportEvent(authorName)` in `:feature-profile-api`; `ProfileUi`'s menu asks it. Verify a test over own, other, blocking and no labeler.
- [ ] 1.3 Report sheet variants (`ReportSamples.variants`: Other, sending, failed, sent without the Block offer) in `:app-fixtures`, outside `all`.

## 2. Kotlin: the place-name sheet's words

- [ ] 2.1 `ProposePlaceNameTexts` and `ProposePlaceNameState.showsInvalid`/`shareEnabled` in `:app-common-api`; `ProposePlaceNameUi` asks them. Verify a `commonTest`, the names in a text-names test, and unchanged Android references.
- [ ] 2.2 Name sheet variants (`ProposePlaceNameSamples.variants`: sending, failed) in `:app-fixtures`, outside `all`.

## 3. Through the host

- [ ] 3.1 `ReportThroughHostTest` (`composeApp` JVM): the real `ReportPresenter` over a mock labeler as the sub-screen of a stand-in parent carrying `rememberReporting`, through `sessionHostOf`: open, pick, send once, sent, Block reaching the parent's confirmation; close and reopen starting afresh; a failure keeping the reason. Verify it passes.

## 4. Swift

- [ ] 4.1 `\.hostRecord` set by `NativeScreen` (design decision 1); type aliases in one added block of `KotlinTypes.swift`.
- [ ] 4.2 `ReportSheet` and the `reporting(_:)` modifier (decisions 2 to 4, 6), previews over the samples (one added block in `Samples.swift`), a text test against the shared words, and `ReportSheetSnapshotTests` over every step and the failure.
- [ ] 4.3 `ProposePlaceNameSheet` and the `placeNames(_:)` modifier, previews, a text test, and `ProposePlaceNameSnapshotTests`.
- [ ] 4.4 Report in `ProfilePage`'s menu and the `reporting(_:)` modifier on the page. Verify the profile's snapshots are unchanged.

## 5. The whole change together

- [ ] 5.1 `./gradlew format`, `./gradlew check` and `./gradlew -p build-logic :convention:test` pass, with no Android screenshot reference changed.
- [ ] 5.2 Swift tests pass on cloned iOS 26.0 and iOS 18.4 simulators, snapshots compared on 26.0 (recorded on a first run and looked at, light and dark) and skipped on 18.4.
- [ ] 5.3 Debug and Release simulator builds succeed and pass the stub check; the stub report lists nothing new from the report or names modules.
- [ ] 5.4 Walk on the signed-in iPad Air 11-inch: bob's profile from alice's Following, Report, each step; Send only if the report goes to the local stack's labeler.

## 6. Docs

- [ ] 6.1 The roadmap: a Phases item 4 bullet, "Phase 4 findings (report sheet)", open items (the post card wires Report, the name sheet waits for its pages). Verify `openspec validate ios-native-report-sheet --strict`.
- [ ] 6.2 `.agents/rules/compose-screens.md` (a sheet's sub-screen in a native page: the modifier, the record from the environment). Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh`.
- [ ] 6.3 A "Not verified" section.
