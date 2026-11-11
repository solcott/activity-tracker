# Tasks

## 1. Probe (throwaway code, not merged)

- [x] 1.1 Throwaway Swift tests on a cloned iPhone 17 (iOS 26.0) simulator over `ActivityInvitesSamples` and `AddPeopleSamples`, and the generated Swift read (design decision 2): whether `InviteView` and `InviteeView` have Swift types, the content's lists read through the erased value or not, `ActivityInvitesTab.allCases`, and `Open(invite:)` and `Remove(invitee:)` built from Swift. Verify the tests run and print what each read.
- [x] 1.2 Remove the throwaway tests. Verify `git status` shows only the change's own files.
- [x] 1.3 Write the results into `openspec/explorations/ios-native-ui.md` under "Phase 4 probes (invite lists)". Verify it records each result.

## 2. Kotlin rules (no visible change)

- [x] 2.1 `ActivityInvitesTexts` and the `activity_invites_summary` string; `ActivityInvitesState.pending`, `declined`, `invites`, `offersDecline`, `canAnswer`, `showsDeclinedNote`, `notice`; `ActivityInvitesUi` and `InviteSummary` ask them (decision 3). Verify a `commonTest` for each tab and condition, the untitled and distance-less summary, and `:feature-invites-ui:jvmTest`.
- [x] 2.2 `AddPeopleTexts` and `AddPeopleState.invitees`, `followers`, `full`, `canAdd`, `canRemove`, `notice`; `AddPeopleUi` asks them. Verify a `commonTest` for each status and condition, and `:feature-invites-ui:jvmTest`.
- [x] 2.3 `ActivityInvitesSamples.variants` and `AddPeopleSamples.variants` (decision 5). Verify `./gradlew :composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"` and `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite` pass with no reference changed, and `checkIosResources` passes after regenerating the catalog.

## 3. The native pages

- [x] 3.1 Type aliases and `Samples.activityInvites`, `activityInvitesVariants`, `addPeople`, `addPeopleVariants`. Verify a Swift test reads them (`InviteListsTextTests`).
- [x] 3.2 `ActivityInvitesView`, registered (decision 4). Verify `InviteListsTextTests` resolves the tabs' invites, titles, lines and summaries, the buttons' availability, the note, the notice and the empty texts, and `ScreenRegistryTests` names `ActivityInvitesScreen`.
- [x] 3.3 `AddPeopleView`, registered. Verify `InviteListsTextTests` covers statuses, buttons' words and availability, the notes and the notice, and `ScreenRegistryTests` names `AddPeopleScreen`.
- [x] 3.4 Previews and `InviteListsSnapshotTests` (decision 5). Verify they record on a first run and pass on a second on an iOS 26.0 iPhone 17 simulator, and each image was looked at, light and dark.

## 4. The whole change together

- [x] 4.1 Run `./gradlew format`, `./gradlew check` and `./gradlew -p build-logic :convention:test`. Verify all pass, with no Android screenshot reference changed.
- [x] 4.2 Run the Swift tests (`xcodebuild test`) on an iOS 26.0 iPhone 17 simulator and on iOS 18.4. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [x] 4.3 Build the Release configuration for the simulator. Verify it succeeds and passes the stub check, and the stub report lists nothing new from `:feature-invites-api`.

## 5. Walk the pages

- [x] 5.1 On the signed-in iPhone 17 Pro (iOS 26.0), when no other session is walking: Activity invites from the own profile if it has invites (the tabs, View to the shared accept page and back), and Add people from an own activity, against the shared pages' words, back by the button and the swipe. Nothing that writes is tapped without the user's go-ahead. Verify by noting what was seen, and that nothing was written.
- [x] 5.2 On the signed-in iPad Air 11-inch (iOS 26.0): the same pages upright and on its side, and back. Verify by noting what was seen.
- [x] 5.3 Launch the build on an iOS 18.4 simulator. Verify it reaches sign-in, and record what couldn't be seen there.

## 6. Docs

- [x] 6.1 The roadmap's phase 4 entry, a "Phase 4 findings (invite lists)" section, and the open items. Verify `openspec validate ios-native-invite-lists --strict` passes.
- [x] 6.2 `.agents/rules/compose-screens.md` (the native pages' list, `ActivityInvitesTexts`, `AddPeopleTexts`, the states' getters). Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 6.3 A "Not verified" section listing what wasn't seen and why. Verify it names every unchecked case from 5.

## Probe results (1.1)

Recorded in `openspec/explorations/ios-native-ui.md` under "Phase 4 probes (invite lists)": both contents cast from the erased value
as their classes and their lists read as `InviteView` and `InviteeView`, empty ones included; `ActivityInvitesTab.allCases` is
`[Pending, Declined]`; `InviteeView` has a Swift type through `AddPeopleEvent.Remove(invitee:)`, and `InviteView` only through
`:app-fixtures` until `ActivityInvitesTexts` named it (the Release build of 4.3 has it).

## Walk (iPhone 17 Pro and iPad Air 11-inch on iOS 26.0, signed in as the local test account; a cloned iPhone 16e on iOS 18.4)

Read from the local AppView before the walk: no activity invites exist on the local network, and the test account has 18 activities
and no followers. So its profile has no Activity invites entry, and Add people has no one to add.

- **The build needs device mode.** The first install, from this worktree with no `local.properties`, wasn't in device mode
  (`activitytracker.deviceHost`), so it called the local hosts while the signed-in session belongs to the public dev ones: the feed
  and the profile couldn't load, and the one write it tried at start (creating the profile record) failed. Copying the main checkout's
  `local.properties` (ignored by git) and rebuilding fixed it.
- **5.1, iPhone.** Feed → the account's "Session" run, the shared activity page, which offers "Add people" → native Add people pushed
  over it in the Feed tab's stack, "Add people" in the system's bar, the note on what people added can see, then "Your followers"
  reading "Only people who follow you can be added." Back by the edge swipe to the activity, opened again, and back by the bar's
  button. You → the account's profile: Edit profile and Starred segments, and no Activity invites entry. Nothing that writes was
  tapped.
- **5.2, iPad.** Upright: the same page from the same activity, the note on one line across the window. Turned on its side: the same,
  wide; the bar's back button returned to the shared activity page, also on its side. Turned upright again.
- **5.3, iOS 18.4.** The Debug build the 18.4 test run made reached the native sign-in screen. A build made for the 26.0 destination
  stopped at launch there (`libswiftCompatibilitySpan.dylib` not loaded: only a build for an older destination embeds it), which isn't
  this change's.

## Not verified

- **Activity invites on a device.** The local network has no invites, so the account's profile offers no entry; making one writes an
  invite from another account, which the walk didn't do. The page, its tabs, Decline, View, the Declined note and the notices rest on
  the `InviteListsSnapshotTests` references (every sample and variant) and `InviteListsTextTests`.
- **Add people with people in it.** The account has no followers, so neither Add, Cancel nor Remove showed; the invitees, the full note,
  and busy and failed states rest on the snapshots and text tests.
- **View opening the shared accept page** from the native list, on a device. It sends `Open(invite)`, which `InviteListsTextTests`
  builds; the presenter's navigation is unchanged.
- **Avatars from URLs.** The samples hold no pictures, so only initials were seen.
- **The pages on iOS 18, signed in.** The 18.4 simulator isn't signed in; the Swift tests ran there with snapshots skipped.
- **VoiceOver.** Not heard.
