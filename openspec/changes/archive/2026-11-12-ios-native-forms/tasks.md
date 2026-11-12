# Tasks

## 1. Kotlin rules (no visible change)

- [x] 1.1 `GearFieldTexts` in `:app-common-api`; `GearPicker` resolves it. Verify a `GearFieldTextsTest`, `SharedTextNamesTest`, and the gear picker's UI tests and screenshots unchanged.
- [x] 1.2 `SportTypeGroups.matching` and `subSportChoices`/`choiceLabel`; `ActivityTypePicker` asks them. Verify a `SportTypeGroupsTest` and the record UI tests and screenshots unchanged.
- [x] 1.3 `ManualActivityTexts` and `ManualActivityState.durationEntered`, filled by the presenter. Verify `ManualActivityPresenterTest` asserts it follows typing, and the manual screenshots unchanged.
- [x] 1.4 `EditActivityTexts.coreChartsBody`; `CreateDiveSiteTexts` and `CreateDiveSiteState.routeMap`; the Compose forms resolve them. Verify texts tests in the features' `-ui` tests (names resolve) and screenshots unchanged.
- [x] 1.5 Fixture variants: edit (dive with site and photos, gear, leaderboard choice, saving, failed, each confirm), manual (gear, imperial, saving), create dive site (publishing, limit, failed). Verify the features' UI tests and `ContentLoadingConformanceTest` pass, and new screenshot references are looked at.

## 2. Native views

- [x] 2.1 `ActivityFormSections.swift`: details sections, gear row and sheet, type row and sheet. Verify they compile and draw in previews.
- [x] 2.2 `EditActivityView` with aliases, samples, `#Preview`s, the registry entry and `ScreenRegistryTests`. Verify `ActivityFormsSnapshotTests` on iOS 26.0, each image looked at.
- [x] 2.3 `ManualActivityView`, the same. Verify its snapshots on iOS 26.0, each image looked at.
- [x] 2.4 `CreateDiveSiteView`, the same. Verify its snapshots on iOS 26.0, each image looked at.
- [x] 2.5 Swift Testing texts tests over the new Kotlin texts (`ActivityFormsTextTests`). Verify they pass.

## 3. The whole change together

- [x] 3.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [x] 3.2 `xcodebuild test` on an iOS 26.0 clone and an iOS 18.4 clone. Verify both pass (snapshots skipped on 18.4); delete the clones.
- [x] 3.3 The Release configuration for the simulator. Verify it builds and passes the stub check.

## 4. See it

- [x] 4.1 On a signed-in simulator on the local stack: manual entry (type sheet, units, gear, visibility, save), edit that activity (title, discard, save), and Create dive site if a dive is at hand; delete what was written. Verify by noting what was seen; record what couldn't be seen under "Not verified".

## 5. Docs

- [x] 5.1 The roadmap: a Phases bullet, "Phase 4 findings (forms)", and the open items updated. Verify `openspec validate ios-native-forms --strict` passes.
- [x] 5.2 `.agents/rules/compose-screens.md` where a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 5.3 A "Not verified" section in this file listing what wasn't seen and why.

## Walk (iPad Air 11-inch (M3) `3F116204`, iOS 26.0, signed in as alice on the local stack; this change's Debug build)

- **Manual entry (4.1).** Feed > Add > Add manual activity opened the native form with Run chosen, its sub-sports as chips and Save
  held back. The type sheet opened with the search, Recent and common (Run checked) and the categories; "gravel" left only Cycling >
  Gravel Ride, and choosing it closed the sheet, showed Gravel Ride with its icon and Ride's sub-sports with Gravel Ride filled. The
  form was in Imperial (alice's units): Distance (mi), Temperature (°F). Typing a title, 5 and 30 enabled Save as the duration was
  typed, and the distance added Pace and speed to the categories. The gear sheet listed alice's five items with their kind and Only
  me; ticking the Edge 840 checked it, showed Remove all, named it on the row and added the Gear category. Save opened the new
  activity's page: Gravel Ride, 5.00 mi, 30:00, 10.0 mph, the Edge 840 under Gear.
- **Edit (4.1).** The page's menu > Edit opened the native form with Cancel and Save in the bar, the saved title and gear, 0 of 10
  photos with Add photos, and no leaderboard choice (no track). Typing a description and tapping Cancel asked "Discard your changes?";
  Cancel kept the text. Visible to Followers narrowed the categories with it, and Save asked "Make this activity less visible?";
  Change anyway saved and returned to the page with the Followers badge and the description.
- **Put back:** the page's menu > Delete deleted the activity; the feed shows alice's earlier activities as before. The iPad is left on
  the Feed tab running this change's Debug build. The iOS 26.0 and 18.4 clones (`FF37332F`, `DB7E9211`) are deleted.
- **Seen in passing:** a tap on the type row opens its sheet after a moment on this busy simulator; a second tap meanwhile lands outside
  the sheet and closes it.

## Not verified

- **Create dive site on a device:** alice has no dive with a position on the local stack, and making one needs a dive file import
  (another screen). The snapshots draw every state (loaded with the outline sketch, duplicates, inside a zone, no position, publishing,
  the allowance, a failed check); the live MapKit map, the slider moving it and Publish weren't seen.
- **Photos on a device:** the simulator's library wasn't used, so picking several photos, a HEIC retried as JPEG, Remove and saving
  them weren't seen; the snapshots draw saved and picked tiles and a full form.
- **Edit's core-charts warning and Leave out of leaderboards:** need an activity from before detail categories and one with a segment
  track; the snapshots draw both.
- **Add collection:** alice has no gear collections; the gear sheet snapshot draws the menu's bar.
- **iOS 18 signed in, iPhone layouts and dark mode on a device:** the iOS 18.4 run passed with snapshots skipped; snapshots cover the
  iPhone width in light and dark.

## Checks

- `./gradlew check -Pkotlin.incremental=false`: passed (9 min 20 s).
- `xcodebuild test` on an iOS 26.0 clone: 405 tests passed, the new snapshots compared after recording and being looked at.
- `xcodebuild test` on an iOS 18.4 clone, built for it: 405 tests passed, snapshots skipped. (Built for the 26.0 clone instead, the app
  stops at launch on 18.4: Xcode embeds `libswiftCompatibilitySpan.dylib` only for a destination that needs it.)
- Release for the simulator: built, the stub check passed.

## Workflow follow-up

- Archive with `/opsx:archive`, syncing the specs, once everything above is done and verified.
