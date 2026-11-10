# Tasks

## 1. Probe (throwaway code, not merged)

- [x] 1.1 Throwaway Swift tests on an iPhone 17 (iOS 26.0) simulator: Nuke 13.2.3 by Swift Package Manager decoding bytes from a closure, a `ByteString` from Kotlin turned into `Data`, `IosApp.heldPhoto` signed out, the block list samples' `PagedState` items, statuses and flags, `ProfileViewBasic` and `shownName`, and an `ImmutableSet<Did>`; and read the generated Swift for stubs (design decision 1). Verify the tests pass and print what each read.
- [x] 1.2 Remove the throwaway tests. Verify `git status` shows only the change's own files.
- [x] 1.3 Write the results into `openspec/explorations/ios-native-ui.md` under "Phase 4 probes (settings lists)". Verify it records each result and the decision to keep both pages in one change.

## 2. Kotlin rules (no visible change)

- [x] 2.1 `PagedShown` in `:app-common-api`, and `pagedRefreshStatus`/`pagedFooter` asking it (decision 3). Verify a `commonTest` over every paged sample kind, and `:app-common-ui:jvmTest` passes.
- [x] 2.2 `GearTexts` in `:app-common-api`; `GearLabels.kt` resolves it (decision 7). Verify `commonTest`s: a known and an unknown type and kind, each main total in both unit systems and "No activities yet"; `SharedTextNamesTest` covers the type names.
- [x] 2.3 `GearListTexts`, `GearLists.showsCollections` and `explainsCollections`; `GearListUi` asks them. Verify `commonTest`s and `:feature-gear-ui:jvmTest`.
- [x] 2.4 `BlockListTexts`, `BlockListState.loadErrorKind` and `isRemoving`; `BlockListUi` asks them. Verify `commonTest`s and `:feature-profile-ui:jvmTest`.
- [x] 2.5 `BlockListSamples.variants`; `IosApp.heldPhoto` (decision 2). Verify `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite` passes with no reference changed.

## 3. Design system

- [x] 3.1 Nuke and NukeUI in the app target; `RemoteImage`, `Avatar` and the gear photo frame (decision 2). Verify the app builds and a gallery entry shows the placeholders and an initial.
- [x] 3.2 `PagedList` (decision 4). Verify `PagedListTests`: load more asked for with two items and more to come, not at the end, and not with a hundred items until the last is scrolled to.
- [x] 3.3 `pageTitle(_:actions:)` and `PageBar`'s actions (decision 5), `AppItemRow` and `AppSecondaryButton(small:)` (decision 6). Verify gallery entries for each.
- [x] 3.4 Gallery entries and `DesignSnapshotTests` for images, item rows, the paged list's states and the header with actions. Verify they record, pass on a second run and were looked at, light and dark.

## 4. The native pages

- [x] 4.1 Type aliases and `Samples.gearList`, `Samples.blockList`, `Samples.blockListVariants`. Verify a Swift test reads the samples (`SettingsListsTextTests`).
- [x] 4.2 `GearListView`, registered (decision 8). Verify `SettingsListsTextTests` resolves the spec's figures and lines on the loaded sample, and the registry test names it.
- [x] 4.3 `BlockListView`, registered. Verify `SettingsListsTextTests` covers both kinds' words, the Bluesky note, `isRemoving` and the empty texts, and the registry test names it.
- [x] 4.4 Previews and `SettingsListsSnapshotTests` (decision 9). Verify they record on a first run and pass on a second on an iOS 26.0 iPhone 17 simulator, and each image was looked at.

## 5. The whole change together

- [x] 5.1 Run `./gradlew format`, `./gradlew check` and `./gradlew -p build-logic :convention:test`. Verify all pass, with no Android screenshot reference changed.
- [x] 5.2 Run the Swift tests (`xcodebuild test`) on the iPhone 17 iOS 26.0 simulator and on iOS 18.4. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [x] 5.3 Build the Release configuration for the simulator. Verify it succeeds and passes the stub check.

## 6. Walk the pages

- [x] 6.1 On the signed-in iPhone 17 Pro (iOS 26.0): Gear, Blocked accounts and Muted accounts from Settings; each row's words against the shared page; Add, More, an item and a collection opened and back; a profile opened from a list and back; back by the button and the swipe. Verify by noting what was seen.
- [x] 6.2 On the signed-in iPad Air 11-inch (iOS 26.0): each list beside Settings' list with its header and actions; an item and a profile opened full width and back to the list beside its page; upright and on its side. Verify by noting what was seen.
- [x] 6.3 Launch the build on an iOS 18.4 simulator. Verify it reaches sign-in, and record what couldn't be seen there.

## 7. Docs

- [x] 7.1 The roadmap's phase 4 entry, a "Phase 4 findings (settings lists)" section and the open items. Verify `openspec validate ios-native-settings-lists --strict` passes.
- [x] 7.2 `.agents/rules/compose-screens.md` (images, `PagedList`, `PagedShown`, `GearTexts`, page actions) and `adaptive-ui.md` if it names the native pages. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 7.3 A "Not verified" section listing what wasn't seen and why. Verify it names every unchecked case from 6.

## 8. What the snapshots and `check` showed (added during 4.4 and 5.1)

- [x] 8.1 The first recording wrapped a gear total ("No activities yet", "14 days · 41,000 m") under itself beside the name, where the shared row keeps it on one line; `AppItemRow`'s summary keeps its line and the title column takes the rest. Verify the gear references were rerecorded, looked at and pass on a second run.
- [x] 8.2 The gear list alone failed one comparison on 610 pixels, off by at most 3 levels, inside the glass behind its bar's buttons; its snapshots allow one pixel in a thousand to differ (`Snapshots.barButtons`). Verify two consecutive full runs pass on iOS 26.0.
- [x] 8.3 detekt refused `LabelText` with a twelfth function and a test helper with six parameters; the avatar's initial is `avatarInitial` in `ShownName.kt` (Kotlin, Compose and Swift updated) and the helper takes four. Verify `./gradlew check` passes.

## Not verified

- **The pages on iOS 18, signed in.** The iOS 18.4 simulators can't reach the local stack (roadmap), so there the build was only launched (6.3). The pages rest on the Swift tests, which run on iOS 18.4 with the snapshots skipped; the column header before iOS 26 rests on the earlier change's probe.
- **A held photo (`heldphoto:`) and a real avatar on screen.** The test account's gear has no photos and its accounts no avatars, so the walk saw only placeholders and initials. The held path rests on the probe (bytes from Kotlin through `toNSData`, decoded by Nuke from a data request) and on reading the code path; a URL avatar rests on NukeUI.
- **A second page of accounts on a device.** The walk's lists held one account at most. Asking for more is held by `PagedListTests` (two items with more ask once, the last page and a long unscrolled list ask nothing), and the next page's progress and failure by the `loadingMore` and `pageFailed` snapshots.
- **A failed unblock or unmute, and an unblock in flight, on a device.** Every write finished at once against the local stack. They are in the `actions` snapshots and the text tests.
- **A block made only on Bluesky, on a device.** The test account has none; the `actions` sample and the text tests cover the note.
- **New collection and a collection opened, on a device.** The account has no collection, and New collection wasn't saved (it would create one); Add was opened and left without saving.
- **VoiceOver** reading a row as one element with its control apart: set in `AppItemRow`, not heard.
- **The previews** compile in Debug and are left out of Release; they were not opened in Xcode's canvas.

## Workflow follow-up

- Archive with `/opsx:archive` once the user has reviewed and committed the change.
