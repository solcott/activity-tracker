# Tasks

## 1. Probe (throwaway code, not merged)

- [x] 1.1 Throwaway Swift tests on a cloned iPhone 17 (iOS 26.0) simulator over `StarredSegmentsSamples`, `SegmentLeaderboardSamples`, `SegmentMayorsSamples` and `DiveSiteRegularsSamples` (design decision 2): the items cast to their entry types with their fields read, `viewerEntry`, `boards` through `elements(as: String.self)`, an event built with a nil value, and `hasAnswer` on each sample. Verify the tests pass and print what each read.
- [x] 1.2 Remove the throwaway tests. Verify `git status` shows only the change's own files.
- [x] 1.3 Write the results into `openspec/explorations/ios-native-ui.md` under "Phase 4 probes (segment lists)". Verify it records each result.

## 2. Kotlin rules (no visible change but grouped counts)

- [x] 2.1 `DateText` and `SegmentTexts` in `:app-common-api`; `longDate`, `effortDateLabel`, `segmentSportLabel` and `segmentTypeLabel` resolve them (decision 3). Verify `commonTest`s for each branch, the new names in `SharedTextNamesTest`, and `:app-common-ui:jvmTest` and `:feature-settings-ui:jvmTest` pass.
- [x] 2.2 `BoardOption`, `BoardChoice` and `RankBoardTexts` in `:app-common-api`; `BoardChoiceChip` and `BoardSegments` replace `ChoiceChip` and `ScopeControl` in `RankBoards.kt`. Verify `commonTest`s for the label, `offersMenu`, `narrows` and the scope.
- [x] 2.3 `SegmentBoardTexts`, `StarredSegmentsTexts` (with `starred_segments_summary`) and the getters on the three segment states; `StarredSegmentsUi`, `SegmentLeaderboardUi`, `SegmentMayorsUi`, `BoardParts.kt` and the segment page's Mayor card ask them. Verify `commonTest`s for every period, bracket (pounds included), type, accent, day, count and star condition, a names test in `:feature-segment-ui`, and `:feature-segment-ui:jvmTest`.
- [x] 2.4 `DiveSiteRegularsTexts` and the getters on `DiveSiteRegularsState`; `DiveSiteRegularsUi` and the site page's Regulars card ask them. Verify `commonTest`s and `:feature-divesite-ui:jvmTest`.
- [x] 2.5 The four samples' `variants` (decision 7). Verify `./gradlew :composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"` and `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite` pass with no reference changed.
- [x] 2.6 `./gradlew generateIosResources` for the new string. Verify `checkIosResources` passes.

## 3. The native pages

- [x] 3.1 `AppMenuChip` with the gallery's `menuChips` entry (decision 4), and `PagedList`'s `after:` and `keepsTop:` (decision 5). Verify `DesignSnapshotTests` records the new `menuChips` and `pagedWithRows` entries (both additions), `PagedListTests` still pass, and every existing reference compares equal.
- [x] 3.2 Type aliases, imports and `Samples` entries for the four pages. Verify a Swift test reads them (`SegmentListsTextTests`).
- [x] 3.3 `StarredSegmentsView`, registered (decision 6). Verify `SegmentListsTextTests` resolves the rows, the unavailable star, the unstar's availability, the sign-in notice and the empty text, and `ScreenRegistryTests` names `StarredSegmentsScreen`.
- [x] 3.4 `RankRow`, `BoardMenu` and the board parts, and `SegmentLeaderboardView`, registered. Verify `SegmentListsTextTests` covers the choices, the type label or menu, accents, times, days, pounds and the viewer's place, and `ScreenRegistryTests` names `SegmentLeaderboardScreen`.
- [x] 3.5 `SegmentMayorsView` and `DiveSiteRegularsView`, registered. Verify `SegmentListsTextTests` covers their windows, counts and "Reached" lines, and `ScreenRegistryTests` names both screens.
- [x] 3.6 Previews and `SegmentListsSnapshotTests` (decision 7). Verify they record on a first run and pass on a second on an iOS 26.0 iPhone 17 simulator, and each image was looked at, light and dark.

## 4. The whole change together

- [x] 4.1 Run `./gradlew format`, `./gradlew check` and `./gradlew -p build-logic :convention:test`. Verify all pass, with no Android screenshot reference changed.
- [x] 4.2 Run the Swift tests (`xcodebuild test`) on cloned iPhone simulators on iOS 26.0 and iOS 18.4. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [ ] 4.3 Build the Release configuration for the simulator. Verify it succeeds and passes the stub check, and the stub report lists nothing new from the segment, divesite or common contract modules.

## 5. Walk the pages

- [x] 5.1 On the signed-in iPhone 17 Pro (iOS 26.0), once no other session is walking: Starred segments from the own profile; a segment's leaderboard and Mayors with each filter tried; a dive site's Regulars; back by the button and the swipe. Verify by noting what was seen, and that nothing was written.
- [x] 5.2 On the signed-in iPad Air 11-inch (iOS 26.0): the pages reached in 5.1, upright and on its side. Verify by noting what was seen.
- [x] 5.3 Launch the build on an iOS 18.4 simulator. Verify it reaches sign-in, and record what couldn't be seen there.

## 6. Docs

- [x] 6.1 The roadmap's phase 4 bullet, a "Phase 4 findings (segment lists)" section, and the open items. Verify `openspec validate ios-native-segment-lists --strict` passes.
- [x] 6.2 `.agents/rules/compose-screens.md` (the native pages' list, the new texts and getters, `AppMenuChip`, `PagedList`'s additions). Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 6.3 A "Not verified" section listing what wasn't seen and why. Verify it names every unchecked case from 5.

## Probe results (1.1)

- The paged items cast to `StarredSegment`, `LeaderboardEntry`, `MayorEntry` and `DiveSiteRegularEntry` on every sample, with their
  fields typed (`rank`, `elapsed`, `startedOn`, `activity`, `efforts`, `visits`, `reachedOn`, `currentName`, `distanceMeters`,
  `record.sport`, `record.kind` nil). No sample had a viewer entry, a gone star or a type list.
- `boards` (`persistentListOf()`) read as `[]` through `elements(as: String.self)`, and `removing` (`persistentSetOf()`) as empty.
- `SegmentLeaderboardEvent.AgeGroupChanged(ageGroup: nil)` builds and reads back nil.
- The loading and failed samples are `PagedShown.firstPage`; the presenters key their paging on the filters, so a choice shows a first
  page (hence `keepsTop`).

## Walk (iPhone 17 Pro and iPad Air 11-inch on iOS 26.0, signed in as the test account; my iPhone 16 Pro clone on iOS 18.4)

The account's units are imperial; it has one star, on an active ride segment where it is first on the leaderboard (8:15) and Mayor (2
efforts in the last 90 days, 3 all time). Nothing was tapped that writes: no star or unstar, no choice that saves.

- **5.1, iPhone.** You → Starred segments: native, "Rush › Exit Stage Left → Canyon Hollow" with "2.29 mi · Ride" and the filled star.
  The row opened the shared segment page; Leaderboard opened the native board: All time and Everyone chosen, "Ride" as a label (one
  type), the three brackets as plain menus, alice first in the accent colour with "Oct 19, 2026" and 8:15. This month restarted the
  board under the choices, which stayed, and read "No one is on this leaderboard yet". The weight menu listed "All weights" (checked),
  "Under 121 lb" … "254 lb and over"; choosing 121–142 lb filled its chip. The edge swipe went back to the segment page; its Mayor card
  opened native Mayors ("Mayors of …", truncated by the bar), "Last 90 days" filled, alice "Reached Oct 19, 2026" and "2 efforts" on
  one line. The window menu listed the eight windows with Last 90 days checked; All time showed 3 efforts. alice's entry opened her
  shared profile, and its back button returned to Mayors with All time kept.
- **5.2, iPad (this change's Debug build, replacing the zones pages').** Upright: You → Starred segments, the row across the window;
  its segment and Leaderboard. On its side: the leaderboard across the width, the menus on one line. Turned upright again; left on the
  leaderboard.
- **5.3, iOS 18.4.** The Debug build from the 18.4 test run launched to the native sign-in screen.

## Not verified

- **Regulars on a device.** A dive site's page opens only from a dive activity or a site link; the test account's activities are runs
  and rides, and the walk created none. `DiveSiteRegularsView` rests on `SegmentListsSnapshotTests` (every sample and `lastYear`) and
  `SegmentListsTextTests`.
- **Unstarring, a gone star, a failed unstar and sign in again on a device.** Unstarring writes; the account has no gone star and its
  session has the grant. They rest on the `unavailable`, `unstarFailed` and `needsSignIn` snapshots and the text tests.
- **The viewer's own place, a type menu, a failed page and a second page on a device.** alice leads every board she is on and the
  segment lists one type; they rest on the `filtered`, `firstPage`, `pageFailed` and `loadingMore` snapshots.
- **The Release build (4.3).** It fails at `KotlinTypes.swift`'s upload queue alias: `UploadQueueView` has a Swift type only through
  `:app-fixtures`, which Release builds leave out. That alias came with `ios-native-uploads` (already on the branch at 319aded4) and its
  fix belongs in `feature/uploads`, which this change doesn't touch. The stub check passed, the report lists only the allowed
  serializer stubs for the segment, dive-site and common modules, and it was the only Swift error, so this change's Swift compiles in
  Release; a whole Release build wasn't seen.
- **The pages on iOS 18 signed in**, and VoiceOver on the filter menus.
