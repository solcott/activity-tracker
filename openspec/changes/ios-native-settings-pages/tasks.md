# Tasks

## 1. Probe the detail column's bar (throwaway code, not merged)

- [x] 1.1 A scratch root view by launch argument on iPad Pro 13-inch simulators with iOS 26.0 and 18.4: `SettingsColumns` in a pushed destination with a form page that sets `.navigationTitle`; with a header in a top safe-area inset (bar material and hairline); with `.safeAreaBar` (26.0); and `NavigationSplitView` with column visibility held at `.all` and no sidebar button. Verify by screenshots which shows the page's title where (design decision 1).
- [x] 1.2 Remove the throwaway code. Verify `git status` shows only the change's own files.
- [x] 1.3 Write the results into `openspec/explorations/ios-native-ui.md` under "Phase 4 probes (settings pages)", and close the open item on the bar. Verify the open item names the decision.

## 2. Text and conditions chosen in Kotlin (no visible change)

- [x] 2.1 `LabelText.detailCategory` and `VisibilityTexts` (`categoryName`, `categoryHelp`, `followersNote`, `lockedNote`) and `VisibilityFields.activityOptions` in `:app-common-api` (design decision 3). Verify `commonTest`s: each known category and an unknown one, Cadence with and without running form, the four helps and none, Followers with and without approval and another visibility, unsupported and sign in again; and `SharedTextNamesTest` covers the category names.
- [x] 2.2 `:app-common-ui`'s `VisibilityPicker`, `DetailVisibilityPicker`, `ActivityVisibilityForm` and `detailCategoryLabel` resolve them; `lockedVisibilityNote` removed. Verify `:app-common-ui:jvmTest` passes.
- [x] 2.3 `SettingsTexts.defaultsNote`, `segmentsNote`, `groupsNote`; `ActivityDefaultsState.canChange`; `SegmentsAndGroupsState`'s `canChange…`, `offers…`, `shownGroupChoice` and `groupOptions` with `GroupOption` (decisions 3 and 4). Verify `commonTest`s for every branch of each, and the three options in order with their labels.
- [x] 2.4 `ActivityDefaultsUi` and `SegmentsAndGroupsUi` ask them. Verify `:feature-settings-ui:jvmTest` passes unchanged and `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite` passes with no reference changed.

## 3. The column header

- [x] 3.1 `PageBar` and `pageTitle(_:)` in `iosApp/iosApp/Design`, `\.inPane` in the environment, set by `ScreenView(inPane:)` for a native screen (decision 2). Verify the app builds and a gallery entry shows the header.
- [x] 3.2 The header in `DesignGallery` and its design snapshot. Verify it records, passes on a second run and was looked at, light and dark.

## 4. The native pages

- [x] 4.1 Type aliases, `Samples.activityDefaults` and `Samples.segmentsAndGroups`. Verify a Swift test reads the grouping options as three `GroupOption`s whose `choice` is the Swift enum, Off, Connected and Anyone in order.
- [x] 4.2 `ActivityDefaultsView` (decision 5), registered in `ScreenRegistry.app`. Verify a Swift test resolves its texts on the samples as the spec's scenarios word them (the capped options, the Followers note, the locked note) and the registry test names it.
- [x] 4.3 `SegmentsAndGroupsView` (decision 6), registered. Verify a Swift test of its notes and conditions on the samples (sign in again, never asked as Off) and the registry test names it.
- [x] 4.4 Previews and `SettingsPagesSnapshotTests` (decision 7): every sample alone, and both pages beside the list at an iPad's size. Verify the tests record on a first run and pass on a second on an iOS 26.0 simulator, and each image was looked at.

## 5. The whole change together

- [x] 5.1 Run `./gradlew format`, `./gradlew check` and `./gradlew -p build-logic :convention:test`. Verify all pass, with no Android screenshot reference changed.
- [x] 5.2 Run the Swift tests (`xcodebuild test`) on the iOS 26.0 simulator and on iOS 18.4. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [x] 5.3 Build the Release configuration for the simulator. Verify it succeeds (no `Samples` outside `#if DEBUG`) and passes the stub check.

## 6. Walk the pages

- [x] 6.1 On the signed-in iPhone 17 Pro (iOS 26.0): open both pages from Settings, change each choice and change it back, read every note against the shared page's words, return by the back button and the edge swipe. Verify by noting what was seen.
- [x] 6.2 On the signed-in iPad Air 11-inch (iOS 26.0), upright and on its side: Activity defaults beside the list on opening with its header and no back button, scrolled under its header; Segments & groups chosen; a fallback page chosen after and back to a native one. Verify by noting what was seen.
- [x] 6.3 Launch the build on an iOS 18.4 simulator. Verify it reaches sign-in, and record what couldn't be seen there.

## 7. Docs

- [x] 7.1 The roadmap's phase 4 entry, a "Phase 4 findings (settings pages)" section and the open items (the bar decided, enum collections answered, the `:core-data` element item left open). Verify `openspec validate ios-native-settings-pages --strict` passes.
- [x] 7.2 `.agents/rules/compose-screens.md` (a native page's `pageTitle`, enum choices as objects, `VisibilityTexts`) and `adaptive-ui.md` (native pages in the column). Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 7.3 A "Not verified" section listing what wasn't seen and why. Verify it names every unchecked case from 6.

## 8. What the walk showed (added during 6.1)

- [x] 8.1 A category with its help (Physiology, Dive data, Gear) had its choice moved under the label by the form's own picker row; the user asked for it on one line at the right. Each row is now a label beside a `Menu` holding an inline `Picker` (design decision 5), after a throwaway snapshot compared it with a picker in an `HStack` (rows 12 points taller) and `LabeledContent` (wraps the same way). Verify on the signed-in iPhone and iPad that every choice is on the right at the form's row height, the menu opens checked and saves, and the rerecorded Activity defaults references pass on a second run.

## Not verified

- **The pages on iOS 18, signed in.** The iOS 18.4 simulators can't reach the local stack (roadmap), so there the build was only launched to sign-in (6.3). The pages rest on the Swift tests, which pass on iOS 18.4, and on the probe's screenshots of the column header before iOS 26 (bar material and hairline, 1.1); the snapshots compare only on iOS 26.0.
- **Turning segment matching off on the account.** That deletes the account's route copies on the AppView, so the walk didn't do it. That the leaderboard and bracket switches go with it is held by `SettingsPagesTest` and `SettingsPagesTextTests` and by the shared page's tests.
- **"Saving…" and a failed save on screen.** Every save finished within a frame or two against the local stack, and none failed. The texts are tested (Kotlin and Swift), and the failure's place is in the `saveFailed` snapshots.
- **The locked and "server doesn't support private activities" states on a device.** The account's session can choose; these are in the `locked` snapshot and the text tests.
- **A session that can't use segments or grouping**, on a device: the `signInAgain` snapshot and the text tests only.
- **VoiceOver** reading each choice by its row's name and value: set in `ActivityDefaultsView.picker`, not heard.
- **A window resized across the split's size with a native page open.** As for Settings' list (`ios-native-settings`), an iPad at full screen always has room and an iPhone never does; turning the iPad kept the page and its place.
- **The previews** compile in Debug and are left out of Release; they were not opened in Xcode's canvas.

## Workflow follow-up

- Archive with `/opsx:archive` once the user has reviewed and committed the change.
