# Tasks

## 1. Kotlin rules (no visible change beyond a grouped count)

- [x] 1.1 `AthleteSearchTexts` in `:feature-search-api` and `LeftElsewhereTexts` in `:feature-settings-api` (decision 1). Verify their tests.
- [x] 1.2 `AthleteSearchUi` and `LeftElsewhereUi` ask them. Verify both modules' `jvmTest` and the Android screenshot tests unchanged.
- [x] 1.3 Sample variants where missing (Segments mode, a failed search). Verify `ContentLoadingConformanceTest`.
- [x] 1.4 `SegmentBrowseViewController(record)` and `IosApp.segmentBrowseViewController` (decision 5). Verify the iOS build and the stub check.

## 2. The native views

- [x] 2.1 Type aliases and samples (appended blocks). Verify a text test reads every sample's texts.
- [x] 2.2 `AthleteSearchView` and `SegmentBrowseEmbed` (decisions 2 to 5). Verify their previews and `SearchSnapshotTests`, light and dark, each image looked at.
- [x] 2.3 `LeftElsewhereView` and the stack in `SessionView` (decision 6). Verify its previews and `LeftElsewhereSnapshotTests`, each image looked at.
- [x] 2.4 The registry entries (decision 7). Verify `ScreenRegistryTests` names both screens as native.

## 3. The whole change together

- [x] 3.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [x] 3.2 The Swift tests on the iOS 26.0 and 18.4 snapshot simulators. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [x] 3.3 The Release configuration for the simulator. Verify it builds and passes the stub check.

## 4. See it

- [x] 4.1 On the signed-in iPhone 17 Pro `E426A46D`, as alice on the local stack: the Search tab's title, the prompt, a search, no match, a profile opened and back, Segments with the browse screen (its field, its map, a segment opened and back), the mode kept across tabs. Verify by noting what was seen; record what the stack can't show under "Not verified".

## 5. Docs

- [x] 5.1 The roadmap: a phase 4 bullet, "Phase 4 findings (search)", the open items (left elsewhere closed; native segment browse; the leave in progress without a stack). Verify `openspec validate ios-native-search --strict`.
- [x] 5.2 `.agents/rules/compose-screens.md` and `adaptive-ui.md` where a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh`.
- [x] 5.3 A "Not verified" section listing what wasn't seen and why.

## Walk (iPhone 17 Pro `E426A46D`, iOS 26.0, signed in as alice on the local stack; this change's Debug build)

- **4.1.** The Search tab opened on the native page: the large title "Find athletes", the system's search field under it, the
  Athletes and Segments choice as the list's first row, and the prompt. "bo" listed bob with his initial and @bob.test once typing
  paused; tapping him opened his profile on the Search tab, and back returned to the results with "bo" still in the field. "zzz" said
  No athletes match “zzz”. Cancel cleared the query and brought the prompt back. Segments turned the title to "Search" and showed the
  shared browse screen under the choice: its name field, sport chips and map, which located, drew the area's segments and listed them
  nearest first. Typing in its field raised the keyboard, which the browse screen padded for itself. A segment from the list opened
  its page on the Search tab, and back returned to the map and list as they were. Feed and back kept Segments with its map, and so
  did Athletes and back. Left on Athletes with a blank query.
- **Put back:** nothing was written; the mode and queries are session state, left at Athletes and blank.
- **iOS 18.4:** the Swift tests ran on `958C0DC3` (snapshots skipped); not walked signed in, which iOS 18 can't be (roadmap open items).

## Not verified

- `LeftElsewhereView` on a device: it needs the account to leave on another device while this one holds work, which the local stack
  has no second signed-in device for; the snapshots draw every sample, and its text test reads them.
- A failed search and a next page on a device: the local stack has a handful of athletes and never fails a search; the snapshots draw
  both.
- The browse screen's location prompt (the simulator had already granted it), an iPad, VoiceOver, and the largest text sizes.
