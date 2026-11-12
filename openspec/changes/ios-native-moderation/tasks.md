# Tasks

## 1. Kotlin (no visible change)

- [ ] 1.1 `ModerationSubjectTexts` in `:feature-moderation-api`; `ModerationUi` asks it. Verify a texts test (both visibilities and
  fallbacks, the author's fallback, `canOpen`) and the Android moderation screenshots unchanged.
- [ ] 1.2 `ModerationSamples.variants` (comment, commentNoName, notUnderReview, opening, invalidUri, openFailed). Verify the texts test
  over them.

## 2. Check

- [ ] 2.1 `checkIosScreenRegistry` in `activitytracker.swift.export`, wired into `check`, with a unit test of its scan. Verify it fails
  naming `ModerationScreen` before 3.1 and passes after.

## 3. Native

- [ ] 3.1 `ModerationView` with aliases, samples, `#Preview`s, the registry entry and `ScreenRegistryTests`. Verify
  `ModerationSnapshotTests` on iOS 26.0, each image looked at.
- [ ] 3.2 Swift Testing texts tests over `ModerationSubjectTexts` (`ModerationTextTests`). Verify they pass.

## 4. The whole change together

- [ ] 4.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [ ] 4.2 `xcodebuild test` on an iOS 26.0 clone and an iOS 18.4 clone (built for 18.4). Verify both pass (snapshots skipped on 18.4).
- [ ] 4.3 The Release configuration for the simulator, ad-hoc signed. Verify it builds and passes the stub check.

## 5. Docs

- [ ] 5.1 The roadmap: a Phases bullet, "Phase 4 findings (moderation)", the open item on what still goes through Compose. Verify
  `openspec validate ios-native-moderation --strict` passes.
- [ ] 5.2 A "Not verified" section in this file listing what wasn't seen and why.
