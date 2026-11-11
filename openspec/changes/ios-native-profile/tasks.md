# Tasks

## 1. Probe (throwaway code, not merged)

- [ ] 1.1 Read the generated Swift for `ProfileState` and `ProfileView` (design decision 2): which members reach Swift typed (`profile`, `viewerDid`, `reporting`, `revealed`, `ProfileView.details`, `viewer`, `labels`), and that nothing the page needs is dropped. Verify by noting each in the roadmap's probes.
- [ ] 1.2 Write the results into `openspec/explorations/ios-native-ui.md` under "Phase 4 probes (profile)". Verify it records each result.

## 2. Kotlin rules (no visible change)

- [ ] 2.1 `ProfileTexts`, `ProfileEntry`, `ProfileActions`, `FollowButton` and the `ProfileState` getters in `:feature-profile-api` (decision 2). Verify a `commonTest` over every branch.
- [ ] 2.2 `ProfileUi` asks them. Verify `:feature-profile-ui:jvmTest` and the shared text names test.
- [ ] 2.3 `ProfileSamples.variants` in `:app-fixtures` (decision 4). Verify `./gradlew :composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"` and the Android screenshot tests pass with no reference changed.

## 3. The native page

- [ ] 3.1 Type aliases (one appended block) and the samples (appended) in `Samples.swift`. Verify `ProfileTextTests` reads every sample.
- [ ] 3.2 `ProfileView` (decision 3), registered (appended entry). Verify `ScreenRegistryTests` names `ProfileScreen`.
- [ ] 3.3 Previews and `ProfileSnapshotTests` (decision 5). Verify they record on a first run and pass on a second on an iOS 26.0 simulator, and each image was looked at, light and dark.

## 4. The whole change together

- [ ] 4.1 Run `./gradlew format`, `./gradlew check` and `./gradlew -p build-logic :convention:test`. Verify all pass, with no Android screenshot reference changed.
- [ ] 4.2 Run the Swift tests (`xcodebuild test`) on an iOS 26.0 and an iOS 18.4 simulator. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [ ] 4.3 Build the Release configuration for the simulator. Verify it succeeds and passes the stub check, and the stub report lists nothing new from `:feature-profile-api`.

## 5. See the page (only when no other session is using the signed-in simulators)

- [ ] 5.1 On a signed-in iPhone 17 Pro (iOS 26.0), as alice on the local stack: her own profile (header, entries, Settings, activities), Followers and Edit profile pushed from it and back, another athlete's profile (follow and unfollow, mute and unmute, block after the confirmation and unblock), each undone. Verify by noting what was seen. If the simulators are taken, rely on the snapshots and record the walk under "Not verified".
- [ ] 5.2 Launch the build on an iOS 18.4 simulator. Verify it reaches sign-in, and record what couldn't be seen there.

## 6. Docs

- [ ] 6.1 The roadmap: a Phases item 4 bullet, "Phase 4 findings (profile)", the open items (the profile's line updated; Report and Posts on the native profile). Verify `openspec validate ios-native-profile --strict` passes.
- [ ] 6.2 `.agents/rules/compose-screens.md` if a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [ ] 6.3 A "Not verified" section listing what wasn't seen and why.
