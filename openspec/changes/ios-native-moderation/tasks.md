# Tasks

## 1. Kotlin (no visible change)

- [x] 1.1 `ModerationSubjectTexts` in `:feature-moderation-api`; `ModerationUi` asks it. Verify a texts test (both visibilities and
  fallbacks, the author's fallback, `canOpen`) and the Android moderation screenshots unchanged.
- [x] 1.2 `ModerationSamples.variants` (comment, commentNoName, notUnderReview, opening, invalidUri, openFailed). Verify the texts test
  over them.

## 2. Check

- [x] 2.1 `checkIosScreenRegistry` in `activitytracker.swift.export`, wired into `check`, with a unit test of its scan. Verify it fails
  naming `ModerationScreen` before 3.1 and passes after.

## 3. Native

- [x] 3.1 `ModerationView` with aliases, samples, `#Preview`s, the registry entry and `ScreenRegistryTests`. Verify
  `ModerationSnapshotTests` on iOS 26.0, each image looked at.
- [x] 3.2 Swift Testing texts tests over `ModerationSubjectTexts` (`ModerationTextTests`). Verify they pass.

## 4. The whole change together

- [x] 4.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [x] 4.2 `xcodebuild test` on an iOS 26.0 clone and an iOS 18.4 clone (built for 18.4). Verify both pass (snapshots skipped on 18.4).
- [x] 4.3 The Release configuration for the simulator, ad-hoc signed. Verify it builds and passes the stub check.

## 5. Docs

- [x] 5.1 The roadmap: a Phases bullet, "Phase 4 findings (moderation)", the open item on what still goes through Compose. Verify
  `openspec validate ios-native-moderation --strict` passes.
- [x] 5.2 A "Not verified" section in this file listing what wasn't seen and why.

## Walk

- Not done. Nothing on iOS opens Moderation (Settings offers it only off mobile), and opening a subject needs an account on the labeler
  team with the moderator or admin role, which this session doesn't have on the local stack. Both clones (iOS 26.0 `196875C1`, iOS
  18.4 `CF04BA30`) held no session and are deleted.

## Not verified

- **The page on a device:** typing a URI, Go on the keyboard and Open sending `Open`, progress while `getSubject` answers, and each
  answer (an activity, a comment, not under review, a failed open). Each state was seen only in snapshots, at a phone's width. Needs a
  moderator account on the local stack and a way to reach the page (a debug push of `ModerationScreen`), neither of which exists.
- **VoiceOver** on the comment row (read as one element) and the field's hint; iPad widths.

## Checks

- `./gradlew check -Pkotlin.incremental=false`: passed (after formatting the new build-logic files; Android moderation screenshots
  unchanged). `checkIosScreenRegistry` failed naming `ModerationScreen` with the registry entry removed, and passes with it.
- `xcodebuild test` on an iOS 26.0 clone: 442 tests in 84 suites; the new references recorded, looked at and compared. One unrelated
  failure under the full run's load, `ImportSnapshotTests.wide` (iPad light), passed on its own rerun (5 of 5).
- `xcodebuild test` on an iOS 18.4 clone, built for it: 442 tests passed, snapshots skipped.
- Release for the simulator, ad-hoc signed: first failed at the new aliases (`ModerationScreen` is not a member of
  `AppPackage.moderation`), since only the samples imported `FeatureModerationApi`'s Swift module and Release leaves them out;
  `KotlinTypes.swift` now imports it. Then built, `checkSwiftExportStubs` passed, and the moderation suites passed again on 26.0.
