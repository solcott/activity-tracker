# Tasks

## 1. Probe (throwaway code, not merged)

- [x] 1.1 Throwaway Swift tests on an iPhone 17 (iOS 26.0) simulator over `FollowListSamples` and `FollowRequestsSamples` (design decision 2): `subject` on every follow list sample, `requests` and `declined` through `elements(as: ProfileViewBasic.self)` on every requests sample, `FollowRequestsTab` as a Swift enum (its cases, `CaseIterable` or not) and `SelectTab(tab:)` built from it, and `.navigationSubtitle` in an inline bar drawn to an image. Verify the tests pass and print what each read.
- [x] 1.2 Remove the throwaway tests. Verify `git status` shows only the change's own files.
- [x] 1.3 Write the results into `openspec/explorations/ios-native-ui.md` under "Phase 4 probes (follow lists)". Verify it records each result.

## 2. Kotlin rules (no visible change)

- [x] 2.1 `SignInAgainNotice` in `:app-common-api`; `signInAgainItem` asks it (decision 3). Verify a `commonTest` for each branch, and `:feature-profile-ui:jvmTest` and `:feature-invites-ui:jvmTest` pass.
- [x] 2.2 `FollowListTexts`, `FollowListState.loadErrorKind` and `isRemoving`; `FollowListUi` asks them. Verify a `commonTest` for each kind and the removal, and `:feature-profile-ui:jvmTest`.
- [x] 2.3 `FollowRequestsTexts` and `FollowRequestsState.accounts`, `offersDecline`, `canAnswer`, `showsDeclinedNote`, `showsTruncatedNote`, `notice`; `FollowRequestsUi` asks them. Verify a `commonTest` for each tab and condition, and `:feature-profile-ui:jvmTest`.
- [x] 2.4 `FollowListSamples.variants` and `FollowRequestsSamples.variants` (decision 6). Verify `./gradlew :composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"` and `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite` pass with no reference changed.

## 3. The native pages

- [x] 3.1 `pageTitle(_:subtitle:)` in the bar and in `PageBar`, `AppButton(small:)`, and the gallery's `pageBarSubtitle` and buttons entries (decisions 4 and 5). Verify `DesignSnapshotTests` records the new entry and the changed buttons entry, and every other reference compares equal.
- [x] 3.2 Type aliases and `Samples.followList`, `followListVariants`, `followRequests`, `followRequestsVariants`. Verify a Swift test reads them (`FollowListsTextTests`).
- [x] 3.3 `FollowListView`, registered (decision 5). Verify `FollowListsTextTests` resolves the titles, subtitles, rows and Remove's availability on the samples, and `ScreenRegistryTests` names `FollowListScreen`.
- [x] 3.4 `FollowRequestsView`, registered. Verify `FollowListsTextTests` covers the tabs' accounts, answers' availability, the notes, the notice and the empty texts, and `ScreenRegistryTests` names `FollowRequestsScreen`.
- [x] 3.5 Previews and `FollowListsSnapshotTests` (decision 6). Verify they record on a first run and pass on a second on an iOS 26.0 iPhone 17 simulator, and each image was looked at, light and dark.

## 4. The whole change together

- [x] 4.1 Run `./gradlew format`, `./gradlew check` and `./gradlew -p build-logic :convention:test`. Verify all pass, with no Android screenshot reference changed.
- [x] 4.2 Run the Swift tests (`xcodebuild test`) on the iPhone 17 iOS 26.0 simulator and on iOS 18.4. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [x] 4.3 Build the Release configuration for the simulator. Verify it succeeds and passes the stub check, and the stub report lists nothing new from `:feature-profile-api` or `:app-common-api`.

## 5. Walk the pages

- [x] 5.1 On the signed-in iPhone 17 Pro (iOS 26.0): the own profile's Followers and Following, natively, against the shared pages' words; an account opened from a list and back; another athlete's Followers from their profile; back by the button and the swipe; Follow requests and Remove only if the account already approves followers, without answering a request. Verify by noting what was seen, and that the account's follows and follow policy are as before.
- [x] 5.2 On the signed-in iPad Air 11-inch (iOS 26.0): the lists from a profile, upright and on its side, and back. Verify by noting what was seen.
- [x] 5.3 Launch the build on an iOS 18.4 simulator. Verify it reaches sign-in, and record what couldn't be seen there.

## 6. Docs

- [x] 6.1 The roadmap's phase 4 entry, a "Phase 4 findings (follow lists)" section, and the open items. Verify `openspec validate ios-native-follow-lists --strict` passes.
- [x] 6.2 `.agents/rules/compose-screens.md` (the native pages' list, `FollowListTexts`, `FollowRequestsTexts`, the states' getters, `SignInAgainNotice`) and `adaptive-ui.md` (the subtitle). Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 6.3 A "Not verified" section listing what wasn't seen and why. Verify it names every unchecked case from 5.

## Probe results (1.1)

- `FollowListState.subject` reads as `ProfileViewBasic?`: alice on every sample a page has answered, nil while loading and after a
  failed first page.
- `FollowRequestsState.requests` and `declined` read through `elements(as: ProfileViewBasic.self)` on every sample: bob and carol for
  loaded, refreshing and refresh failed; empty for loading, failed and empty. The view reads the tab's list from `accounts`, never the
  content's value.
- `FollowRequestsTab` is a Swift enum conforming to `CaseIterable`: `allCases` is `[Requests, Declined]`; `SelectTab(tab: .Declined)`
  builds and reads back `Declined`. The segmented picker iterates `allCases`.
- `.navigationSubtitle` in an inline bar on iOS 26.0 draws "@alice.test" centred under "Followers" and shrinks the title: about
  15 points semibold over about 12 points, secondary (measured against the 17-point headline in `pageBarBack`'s reference). The pre-26
  principal form and `PageBar` draw `.subheadline.weight(.semibold)` over `.caption`, `.secondary`, when there is a subtitle.

## Walk (iPhone 17 Pro and iPad Air 11-inch on iOS 26.0, signed in as the local test account; iPhone 16 Pro on iOS 18.4)

The account's policy is `open`, it follows bob and no one follows it (read from the local AppView before the walk), so its profile
shows no Follow requests entry and its Followers list offers no Remove.

- **5.1, iPhone.** You → "1 following": native Following pushed over the shared profile, "Following" over "@alice.test" in the
  system's bar (the navigation subtitle), bob with his initial, "bob" and "@bob.test" and the disclosure mark. bob opened his shared
  profile; "1 follower" there opened native Followers, "Followers" over "@bob.test", listing alice, no Remove. Back by the edge swipe
  to bob's profile, by its shared back to Following (as it was left), and by the system's back button to alice's profile. "0 followers"
  opened Followers reading "No followers yet." and back. The account's follows and follow policy are as before: nothing was tapped
  that writes.
- **5.2, iPad (this change's Debug build, already signed in).** Upright: You → "1 following" drew Following under the tab bar with
  the subtitle, the row across the window. Turned on its side: the same, wide; bob opened his shared profile on its side, and the edge
  swipe returned to Following. Turned upright again; the back button returned to alice's profile.
- **5.3, iOS 18.4 (iPhone 16 Pro).** The Debug build the 18.4 test run made launched to the native sign-in screen. A throwaway
  test (removed) drew the loaded Followers sample and the gallery's `pageBarSubtitle` there, light and dark: the pre-26 form puts
  "Followers" over "@alice.test" centred in the stack's bar, and the column header its two lines beside the back chevron.

## Not verified

- **Follow requests on a device.** The test account's policy is `open`, so its profile has no Follow requests entry; the walk didn't
  turn approval on, which writes an approval for every current follower that turning it off leaves behind. The page, its tabs, its
  answers, notes and notices rest on the `FollowListsSnapshotTests` references (every sample and variant) and `FollowListsTextTests`.
  Tapping a tab, Approve and Decline weren't tried on a device.
- **Remove on a device.** Offered only on an approving athlete's own Followers; rests on the `removable` snapshot and text tests.
- **A list with more than one page on a device.** The test account's lists hold one account; `PagedList` asking for a second page
  rests on its own tests (`PagedListTests`), as before.
- **Avatars from URLs.** The accounts have no pictures, so only initials were seen.
- **The pages on iOS 18, signed in.** The 18.4 simulator isn't signed in and signing in needs the account's password; the Swift tests
  ran there with the snapshots skipped, and the pre-26 subtitle was seen only in a throwaway image drawn there.
- **VoiceOver.** The subtitle is read with the title as one header in a column header, and by the system in its bar; not heard.

