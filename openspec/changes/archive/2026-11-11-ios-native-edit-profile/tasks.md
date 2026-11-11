# Tasks

## 1. Probe (throwaway code, not merged)

- [x] 1.1 Throwaway Swift tests and scratch `IosApp` entries on a cloned iPhone 17 (iOS 26.0) simulator (design decision 1): every `EditProfileSamples` state read, `DetailsVisibility` and `DetailGroup.visibility(visibility:)`, `VisibilityChanged` built with a Swift enum case, the companion's limits, `AvatarImage` cast both ways, `compressAvatar` over a UIKit-written JPEG, a HEIC and junk, and a plain `List` of a class passed both ways. Verify the tests pass and print what each read.
- [x] 1.2 Remove the throwaway code. Verify `git status` shows only the change's own files.
- [x] 1.3 Write the results into `openspec/explorations/ios-native-ui.md` under "Phase 4 probes (edit profile)", with any design change they force. Verify it records each result.

## 2. Kotlin rules (no visible change beyond the samples)

- [x] 2.1 `CountrySearch` and `CountryName` in `:feature-profile-api` with `Countries.codes` moved there (only edit profile offers countries); the Compose country field asks `sorted` and `matches` (decision 6). Verify `commonTest`s for nothing typed (sorted by name), a code first ("us"), accents folded by the caller ("cote"), and no match.
- [x] 2.2 `CalendarDate.toLocalDate` and `fromLocalDate` public in `:core-data`. Verify `commonTest`s for a real date, malformed and empty text, and a round trip.
- [x] 2.3 `EditProfileLimits`, `EditProfileTexts`, `GenderChoices`, the getters on `EditProfileState` and the `LocalDate` constructor on `BirthDateChanged` in `:feature-profile-api`; the new `edit_profile_height_in_row` string and the regenerated catalog; `EditProfileUi` asks all of it (decisions 3, 4, 7, 8). Verify `commonTest`s over every branch and sample, `:feature-profile-ui:jvmTest`, `:feature-profile-presenter:jvmTest`, and `checkIosResources`.
- [x] 2.4 `IosApp.compressAvatar` in `:iosShell` over `compressAvatar`, moved to `:app-common-ui` (decision 5). Verify the iOS framework builds and the stub check passes.
- [x] 2.5 The samples' visibilities and `EditProfileSamples.variants` (decision 9). Verify `ContentLoadingConformanceTest` passes, and `:androidApp:updateDebugScreenshotTestDefaultTestSuite` changes only the Edit profile references, each looked at.

## 3. The native form

- [x] 3.1 Type aliases (one block), `Samples.editProfile` and `editProfileVariants`, and `PhotoPick` made generic with `.gear` and `.avatar`. Verify the existing `PhotoPick` tests still pass and a Swift test reads the samples (`EditProfileTextTests`).
- [x] 3.2 `CountryList` sheet with its search through `CountrySearch` and Swift's folding. Verify its preview renders and `EditProfileLogicTests` cover "us" first, "cote" and Not set.
- [x] 3.3 `EditProfileView`, registered (decisions 2, 3, 5, 7, 8). Verify `EditProfileTextTests` resolve the spec's words, counters, labels and conditions on the samples and variants, `EditProfileLogicTests` the gender choice and the birthdate's day in two zones, and `ScreenRegistryTests` names `EditProfileScreen`.
- [x] 3.4 Previews and `EditProfileSnapshotTests` (decision 9). Verify they record on a first run and pass on a second on the cloned iOS 26.0 iPhone 17, and each image was looked at, light and dark.

## 4. The whole change together

- [x] 4.1 Run `./gradlew format`, `./gradlew check` and `./gradlew -p build-logic :convention:test`. Verify all pass, with only the Edit profile Android references changed.
- [x] 4.2 Run the Swift tests (`xcodebuild test`) on the cloned iPhone 17 (iOS 26.0) and iPhone 16 Pro (iOS 18.4). Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [ ] 4.3 Build the Release configuration for the simulator. Verify it succeeds and passes the stub check.

## 5. Walk the form (only when no other session is walking, or with the user's go-ahead)

- [x] 5.1 On the signed-in iPhone 17 Pro (iOS 26.0): note alice's profile, open Edit profile from her profile, see every section filled, change a field and leave by back; reopen and see it unchanged. Verify by noting what was seen against the shared form.
- [x] 5.2 On alice.test only: change the bio, the country through the search, the birthdate, the gender to Self-describe with words, a visibility from Public (seeing the warning), and the photo from the library; save; see the profile; then put every value back and save again. Verify the profile reads as before the walk.
- [ ] 5.3 On the signed-in iPad Air 11-inch (iOS 26.0), if free: the form pushed over the profile, the popover and the country sheet on an iPad, upright and on its side. Verify by noting what was seen.
- [x] 5.4 Launch the build on the cloned iOS 18.4 simulator. Verify it reaches sign-in, and record what couldn't be seen there.

## 6. Docs

- [x] 6.1 The roadmap's phase 4 bullet, a "Phase 4 findings (edit profile)" section, and the open items it settles or adds. Verify `openspec validate ios-native-edit-profile --strict` passes.
- [x] 6.2 `.agents/rules/compose-screens.md` (the native form, `IosApp.compressAvatar`, `PhotoPick`'s two shapes, `CountrySearch`, the new texts and getters). Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 6.3 A "Not verified" section listing what wasn't seen and why. Verify it names every unchecked case from 5.

## Walk (2026-11-10, iPhone 17 Pro, iOS 26.0, signed in as alice.test on the local stack)

- Before: display name "alice", no bio, photo, location, birthdate, gender, height or weight, every detail Only me, Approve followers off.
- 4.2: 228 tests in 41 suites passed on the cloned iPhone 17 (iOS 26.0), snapshots compared, and on the cloned iPhone 16 Pro
  (iOS 18.4), where the snapshot suites skip.
- 5.4: the Debug build launched on the cloned iOS 18.4 iPhone 16 Pro and reached sign-in.
- 5.1: Edit profile opened natively from the profile, every section filled from the saved profile; a changed bio left by back kept
  nothing, and the reopened form was unchanged.
- 5.2: the bio counter followed typing; the country search put United States first for "us"; the birthdate popover greyed out future
  days; the gender menu offered its five choices and Self-describe its field; the weight turned red and held Save back while invalid;
  the photo picker opened with no permission prompt and showed the compressed photo; a save updated the profile, and the reopened form
  showed the saved photo from its URL and the saved values. Narrowing the location after it was saved Public showed the public-until-now
  warning beside the Followers note, and unsetting the country removed the warning. Every value was then put back and saved, and the
  profile read as before.
- Found and fixed on the way: a reopened form read gender "Not set" over saved words, because the hidden words field's model was never
  seeded; the view now reads `state.gender.value` (roadmap findings, compose-screens.md).

## Not verified

- 5.3, the iPad Air: skipped so the signed-in simulators went back to the segment lists session sooner. The form pushed over the
  profile on an iPad, the birthdate popover and the country sheet at iPad size, upright and on its side, were not seen.
- A HEIC from the photo library through the picker: the probe compressed a HEIC, but whether the walk's library photo was one wasn't
  checked.
- The Sign in again button: tapping it signs out, so it was seen only in the `needsSignIn` snapshot.
- A failed save on the device: seen only in the `saveFailed` snapshot.
- 4.3, the Release build: it fails on `KotlinTypes.swift`'s `UploadQueueView` alias (`UploadActivityPackage` has no such member in
  Release), which came with the migration branch's upload queue and fails there too; the stub check passed before it. Whether this
  change's types build in Release is unseen until that alias is fixed.
- The form on iOS 18.4 signed in: not possible without signing in (the roadmap's Open items: the local stack needs HTTP/3 or another tunnel on iOS 18).
- Approve followers turned on and saved: the toggle was seen but not saved, since it changes who may follow alice.

## Workflow follow-up

- Commit in small commits by concern on this branch once the user asks; never push.
- Archive with `/opsx:archive` once the user has reviewed and committed the change.
- Merge into `claude/ios-swiftui-uikit-migration-df6ef6` beside the zones pages and the segment lists; the overlaps in `ScreenRegistry.swift`, `KotlinTypes.swift`, `Samples.swift` and the roadmap are adjacent blocks, and `Localizable.xcstrings` is regenerated rather than merged.
