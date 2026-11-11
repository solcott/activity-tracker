# Tasks

## 1. Probe (throwaway code, not merged)

- [x] 1.1 Read the generated Swift for `ProfileState` and `ProfileView` (design decision 2): which members reach Swift typed (`profile`, `viewerDid`, `reporting`, `revealed`, `ProfileView.details`, `viewer`, `labels`), and that nothing the page needs is dropped. Verify by noting each in the roadmap's probes.
- [x] 1.2 Write the results into `openspec/explorations/ios-native-ui.md` under "Phase 4 probes (profile)". Verify it records each result.

## 2. Kotlin rules (no visible change)

- [x] 2.1 `ProfileTexts`, `ProfileEntry`, `ProfileActions`, `FollowButton` and the `ProfileState` getters in `:feature-profile-api` (decision 2). Verify a `commonTest` over every branch.
- [x] 2.2 `ProfileUi` asks them. Verify `:feature-profile-ui:jvmTest` and the shared text names test.
- [x] 2.3 `ProfileSamples.variants` in `:app-fixtures` (decision 4). Verify `./gradlew :composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"` and the Android screenshot tests pass with no reference changed.

## 3. The native page

- [x] 3.1 Type aliases (one appended block) and the samples (appended) in `Samples.swift`. Verify `ProfileTextTests` reads every sample.
- [x] 3.2 `ProfileView` (decision 3), registered (appended entry). Verify `ScreenRegistryTests` names `ProfileScreen`.
- [x] 3.3 Previews and `ProfileSnapshotTests` (decision 5). Verify they record on a first run and pass on a second on an iOS 26.0 simulator, and each image was looked at, light and dark.

## 4. The whole change together

- [x] 4.1 Run `./gradlew format`, `./gradlew check` and `./gradlew -p build-logic :convention:test`. Verify all pass, with no Android screenshot reference changed.
- [x] 4.2 Run the Swift tests (`xcodebuild test`) on an iOS 26.0 and an iOS 18.4 simulator. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [x] 4.3 Build the Release configuration for the simulator. Verify it succeeds and passes the stub check, and the stub report lists nothing new from `:feature-profile-api`.

## 5. See the page (only when no other session is using the signed-in simulators)

- [x] 5.1 On a signed-in iPhone 17 Pro (iOS 26.0), as alice on the local stack: her own profile (header, entries, Settings, activities), Followers and Edit profile pushed from it and back, another athlete's profile (follow and unfollow, mute and unmute, block after the confirmation and unblock), each undone. Verify by noting what was seen. If the simulators are taken, rely on the snapshots and record the walk under "Not verified".
- [x] 5.2 Launch the build on an iOS 18.4 simulator. Verify it reaches sign-in, and record what couldn't be seen there.

## 6. Docs

- [x] 6.1 The roadmap: a Phases item 4 bullet, "Phase 4 findings (profile)", the open items (the profile's line updated; Report and Posts on the native profile). Verify `openspec validate ios-native-profile --strict` passes.
- [x] 6.2 `.agents/rules/compose-screens.md` if a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 6.3 A "Not verified" section listing what wasn't seen and why.

## Walk (iPad Air 11-inch (M3), iOS 26.0, signed in as alice on the local stack; this change's Debug build)

The gear item session held the signed-in iPhone 17 Pro (its claim in the shared notes left the iPad alone), so the walk was on the iPad,
upright. The stack answered.

- **5.1.** The You tab drew alice's profile natively: "@alice.test", 16 activities, 0 followers, 1 following, Edit profile and Starred
  segments as navigation rows (no invites or requests: her policy is `open` and she has no invites), Settings in the bar, and her
  activities as native cards with their badges and starts. Edit profile pushed the native form, filled from her profile, and back
  returned. "1 following" pushed the native Following list (bob), and bob pushed his native profile: "bob" in the bar, "@bob.test", 10
  activities, 1 follower, Following as the tinted button, cards with Like, and the menu. The menu offered Mute and Block; Mute turned it
  to Unmute, and Unmute back. Block asked "Block bob?" with the shared message, Cancel and a red Block; confirming showed Unblock in place
  of Following and no activities; Unblock brought both back. Following unfollowed (filled Follow, 0 followers), and Follow followed again
  (Following, 1 follower). A card opened the shared activity page, and back went through bob's profile and Following to alice's. Every
  write was put back: alice follows bob, and neither blocks nor mutes the other.
- **5.2, iOS 18.4** (`Native Profile iPhone 16 Pro`, made for this change). The Swift tests ran there (snapshots skipped); the app wasn't
  walked signed in, which iOS 18 can't be (roadmap open items).

## Not verified

- On an iPhone: the walk was on the iPad, since the gear session held the signed-in iPhone 17 Pro. The snapshots are iPhone-sized.
- Requested, Follow requests (N) and Activity invites (N) on a device: alice's policy is `open` and the local network has no invites or
  pending requests; drawn in the `requested` and `ownApproving` snapshots.
- The Bluesky-only block note, a labelled avatar, a warning, a hidden athlete and a failed action on a device: no such accounts or
  failures on the local stack; drawn in snapshots.
- A tab root's title: at the You tab's root on the iPad (tabs at the top) no title shows, where the shared page showed the athlete's
  name; recorded as an open item, since the shell has no notion of a native tab root yet.
- VoiceOver, the largest text sizes, and the page beside another column.
- Report and Posts on a profile on iOS, which this change leaves out (open items).
