# Tasks

## 1. Probes that gate the design (throwaway code, not merged)

- [x] 1.1 Two stacks in a pushed destination (design decision 1.1): a scratch root view with a `TabView`, a `NavigationStack`, and a destination that hides the outer bar and draws an `HStack` of two `NavigationStack`s, the second with a path. Run it on an iPad simulator on iOS 26.0 and on 18.4. Verify by screenshots that two bars show side by side, the inner stack pushes and pops, the list column's back button leaves the destination and the edge swipe pops the outer stack. If it fails, record the fallback layout in `design.md`. **It failed** (the destination never shows); the fallback, two plain columns under the outer bar, was probed on both versions and is design decision 2.
- [x] 1.2 `NavigationSplitView` as a pushed destination (1.2), in the same scratch view. Verify by a screenshot what it draws, and record why it isn't used.
- [x] 1.3 Immutable collections from Swift (1.3): in a scratch test, read the settings page samples' `ImmutableList<String>`, `ImmutableList<Int>`, list of a class, nullable list and `ImmutableSet` of an enum through the Kotlin collection's iterator. Verify each cast's result and record what an enum element does.
- [ ] 1.4 Rotation (1.4): rotate the iPad simulators with the scratch app open. Verify whether the window turns, on both iOS versions, and record it. **Not done:** nothing here can turn a simulator (see Not verified).
- [x] 1.5 Remove the throwaway code and write the results into `openspec/explorations/ios-native-ui.md` under a "Phase 4 probes" heading, updating any design decision they changed. Verify `git status` shows only the change's own files and the roadmap.

## 2. Text and the marked row, chosen in Kotlin (no behaviour change)

- [x] 2.1 `LabelText.visibility` in `:app-common-api`, and `visibilityLabel` in `:app-common-ui` resolving it. Verify a `commonTest` for each known value and an unknown one, and that `SharedTextNamesTest` covers the names.
- [x] 2.2 `SettingsTexts` in `:feature-settings-api` (design decision 8): `privacyZones`, `segmentMatching`, `defaultVisibility`, `unitsNote`. Verify `commonTest`s: unread gives null; none, one and two zones; ends hidden; matching on, off and unknown; saving and each unit system.
- [x] 2.3 `SettingsPane.marks(page)` in `:app-common-api` (design decision 6). Verify a `commonTest`: the shown page in two panes, another page, the blocked list against the muted one, and a single pane.
- [x] 2.4 `SettingsUi` resolves `SettingsTexts` and asks `marks`. Verify `:feature-settings-ui:jvmTest` passes unchanged and `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite` passes with no reference changed.

## 3. The host decides the split

- [x] 3.1 `settingsPagesOnTop` in `:app-host` (design decision 3), and `SettingsListDetailDecoration` calling it in place of its own rule. Verify a `commonTest` (Settings alone, pages above it, another screen on top, no Settings, a page with no Settings beneath) and that `SettingsListDetailDecorationTest` passes unchanged.
- [x] 3.2 `HostRecord.paneDefault` (design decision 4): a Settings record gets an Activity defaults record beside it; the host keeps its state while Settings is on the stack and runs it while reported visible. Verify `SessionHostTest` cases: it publishes a state only once reported visible; it isn't in `records` and back from Settings leaves Settings; a second Settings gets a new one with nothing retained.
- [x] 3.3 `TabStack.setWidth` (now `setSize`, 8.2), `SettingsSplit` and `ShownRecords.split` and `pushed`. Verify `TabStackTest` cases for each scenario of the `dev/presenter-host` delta: a page open, a deeper page, another screen on top, 839 against 840 points, width not changing `records`, and the marked page.
- [x] 3.4 `HostRecord.paneRoot` (design decision 5), provided as `LocalIsPaneRoot` by the iOS fallback host. Verify a `TabStackTest` case (true for the pane's first page while wide, false after narrowing and for a deeper page) and that `:composeApp` compiles for iOS.
- [x] 3.5 Verify the Swift export carries the new host API with no stub: the iOS build passes its stub check, and the generated `PresenterHost` module has `split`, `pushed`, `setWidth` and `SettingsSplit`'s members.

## 4. Swift pieces the screen needs

- [x] 4.1 The collection helper (design decision 9), `elements(as:)`, in `iosApp/iosApp/Kotlin`, with its limits in its doc comment. Verify Swift tests on the settings page samples for each scenario of the `dev/ios-swift-interop` delta.
- [x] 4.2 `AppListRow` gains `selected` (design decision 6), and `DesignGallery.rows` a selected row. Verify the rows snapshot is rerecorded and looked at, light and dark.
- [x] 4.3 Type aliases for the settings contracts and `Samples.settings`. Verify the app builds in Debug and Release.

## 5. The native Settings list

- [x] 5.1 `SettingsView` (design decision 7) over `SettingsState`, with `\.settingsLayout` in the environment, registered in `ScreenRegistry.app`. Verify the registry tests name Settings and that a Swift test reads each summary through `SettingsTexts` as the spec's scenario words it.
- [x] 5.2 Previews and snapshot tests of the list in each sample (design decision 10), light and dark. Verify the tests record on a first run and pass on a second on the iOS 26.0 simulator, and that each reference image was looked at.
- [x] 5.3 Walk Settings on the signed-in iPhone 17 Pro simulator: open it from the profile, read each row against the shared list, change units both ways, open each row's page and come back by the back button and by the edge swipe. Verify by noting what was seen.

## 6. The list beside its page

- [x] 6.1 The shell draws the split (design decisions 2 and 3): the stack reports its width, the outer path follows `pushed`, the destination of a record with a `paneDefault` is the two-column layout (`SettingsColumns`), which draws the split's page beside the list when `split` is set, and a fallback page in the column leaves the outer bar alone. Verify the app builds, the existing `StackPathTests` pass, and a `FallbackControllersTests` case keeps a pane default's controller while its Settings record is on the stack.
- [x] 6.2 Back from the split is the outer stack's own back button and edge swipe, which pop Settings and its pages together. Verify in 6.4's walk.
- [x] 6.3 A snapshot of the list beside a page at an iPad's size with a row marked (design decision 10). Verify it records, passes on a second run, and was looked at, light and dark.
- [x] 6.4 Walk the split signed in on the iPhone simulator with the width rule and list width lowered in a scratch build (design decision 11): Activity defaults beside the list with its row marked, each row replacing the page, a deeper page and its back, a profile from the blocked list full width and back, the list's back button and the edge swipe leaving Settings. Verify by noting what was seen, then revert the scratch values and verify `git diff` shows none of them.

## 7. Docs and the whole change together

- [x] 7.1 Update `.agents/rules/compose-screens.md` (a native screen in a stack, the collection helper, `SettingsTexts` as the feature-text example), `adaptive-ui.md` (the iOS split and who decides it) and `AGENTS.md`'s host paragraph. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 7.2 Run `./gradlew format`, `./gradlew check`, `./gradlew -p build-logic :convention:test` and the iOS `xcodebuild test` on the iOS 26.0 simulator. Verify all pass, with no Android screenshot reference changed.
- [x] 7.3 Rewrite the roadmap's phase 4 section to what this change built, add a "Phase 4 findings (settings)" section, and update its open items (the immutable-collection decision made, the Settings line gone, portrait-only on iPad added). Verify `openspec validate ios-native-settings --strict` passes.

## 8. Landscape and an iOS rule for the split (added after the first apply)

- [x] 8.1 Allow portrait and both landscapes on iPhone and all four orientations on iPad (design decision 13), in both build configurations. Verify the built app's `Info.plist` lists them per device.
- [x] 8.2 `settingsSplitFits` in `:app-host` (700 points wide, 500 tall) and `TabStack.setSize(width, height)` in place of `setWidth`; the shell reports its stack's size. Verify `SettingsSplitFitsTest` (every iPad at full screen either way up, an iPad sharing its screen, an iPhone either way up, the least window) and the `TabStackTest` cases for a phone on its side and a tablet turned.
- [x] 8.3 Turn the signed-in iPhone 17 Pro simulator and walk the feed, the profile, Settings and a settings page (Account) on its side. Verify by noting what was seen: each lays out across the wider screen with the tabs floating over it, Settings stays one page at a time, and turning upright keeps Account on screen with back returning to the list.
- [x] 8.4 Rerun `./gradlew check` and the iOS `xcodebuild test`, update the roadmap, `adaptive-ui.md` and the shared notes. Verify all pass and `openspec validate ios-native-settings --strict`.
- [x] 8.5 Walk the split on an iPad with an account (iPad Air 11-inch, iOS 26.0, signed in by the user). Seen, on its side (1,180 by 820 points): the list beside Activity defaults with its row marked; Training zones replacing it with no back button and its row marked; Default zones opened from it with its own back button, Training zones still marked. Turned upright (820 by 1,180) with Default zones open: the list and the same page stay, nothing loads again. Its back button returns to Training zones; Muted accounts shows beside the list with only its row marked, not Blocked accounts; a swipe from the leading edge leaves Settings, page and all, for the profile.

## 9. What the iPad walk showed (added after 8.5)

- [x] 9.1 Beside a page, Settings' title goes next to the back button, over the list, and the bar's centre is left empty: centred in the window it sat over the page column, above the page's own title. Verify on the signed-in iPad, upright and on its side, and on the rerecorded snapshot of the list beside a page.
- [x] 9.2 A row's summary gets all the room its title leaves, and its disclosure mark sits 6 points from it: "Matching on" wrapped beside "Segments & groups" in the 360-point list, a point short. Verify on the iPad and that the rows and Settings snapshots are rerecorded, pass on a second run and were looked at.
- [x] 9.3 Look at an activity on its side on the iPad and the iPhone. Seen: on the iPad it lays out as on any wide window. On the iPhone (874 by 402 points) the shared UI takes its `Expanded` layout, a 244-point column beside a pane that is empty for an activity with no map, so the page is cramped. Not fixed here: it is `WindowWidth`'s rule, by width alone, on every platform (see the roadmap's open items).

## Not verified

- **The list beside its page on iOS 18, signed in.** Seen on iOS 26.0 only (8.5). The iOS 18.4 simulators can't reach the local
  stack, so there the layout rests on the probe's screenshots (1.1) and the snapshot (6.3).
- **Crossing the split's size with Settings open.** An iPhone never has room for the split and an iPad at full screen always does
  (8.5), so Settings never moved between the two layouts on screen: that needs an iPad window resized beside another app. That the list keeps its scroll position rests on its being one view in both layouts, and that the
  page keeps its place on the fallback screens' cached view controllers; neither was seen. In the walk, a screen pushed over the
  split and popped again left the list at another offset, because at the scratch width its rows change height between the two
  layouts.
- **Most screens on a phone on its side.** Only the feed, the profile, Settings, the Account page and an activity with no map
  were looked at turned. A map, the charts, Record, the forms and sign-in were not; nor was any screen on iOS 18.
- **1.1's back swipe and taps on the iPad probes:** the simulator tool had no access to an iPad, so the probes were driven by launch
  arguments and read from screenshots. The edge swipe and the back button were exercised on the iPhone in 6.4 instead.
- **A profile opened from the blocked list** (the spec's example of a screen that opens full width): the test account has blocked
  no one. A gear item opened from the gear list stood in for it: full width, and back restored the list beside Gear.
- **"Saving…" under the units choice** was not caught on screen: the save finishes within a frame or two against the local stack.
  The text is tested (`SettingsTextsTest`, `SettingsTextTests`); a failed save's message was not provoked.
- **VoiceOver reading the marked row as selected** (ui/ios-design-system): the trait is set in `AppListRow`, and nothing reads a
  SwiftUI view's accessibility tree from a unit test.
- **`Following`'s fix** (design decision 12) has no regression test; it was verified by Settings showing on the simulator.
- **5.2, 6.3:** the previews compile in Debug and are left out of Release; they were not opened in Xcode's canvas.
