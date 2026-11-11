# Tasks

## 1. Probe (throwaway code, not merged)

- [x] 1.1 Throwaway Swift tests and a scratch `IosApp.compressPhoto` on an iPhone 17 (iOS 26.0) simulator (design decision 1): a `LocalDate` read and built, `GearPhoto` cast, `ActivityType` equality, `compressPhoto` over a UIKit-written JPEG and HEIC. Verify the tests pass and print what each read.
- [x] 1.2 A throwaway view in a snapshot test: a sheet holding a `NavigationStack` presented from a stack's destination, and a `Form` in edit mode with text fields beside a movable section. Verify by looking at the recorded images, then delete them.
- [x] 1.3 Remove the throwaway code. Verify `git status` shows only the change's own files.
- [x] 1.4 Write the results into `openspec/explorations/ios-native-ui.md` under "Phase 4 probes (gear editors)", with any fallback taken. Verify it records each result.

## 2. Kotlin rules (no visible change)

- [x] 2.1 `text_list_pair` in `strings.xml`, `AppText.joined`, the iOS catalog regenerated, and `SportTypeGroups` with `nestsIn` in `:app-common-api`; `sportTypeList` asks `nestsIn` (decision 9). Verify `commonTest`s for the joins (none, one, three) and the groups (Gravel Ride under Ride in all types; no sub-sport in gear sports), `SharedTextNamesTest`, and `checkIosResources`.
- [x] 2.2 `EditGearTexts`, `EditGearLimits` and the getters on `EditGearState` and `EditGearCollectionState` in `:feature-gear-api`; `EditGearUi` and `EditGearCollectionUi` ask them. Verify `commonTest`s over every branch and sample, and `:feature-gear-ui:jvmTest`.
- [x] 2.3 `IosApp.compressPhoto` in `:iosShell` (decision 5). Verify the iOS framework builds and the stub check passes.
- [x] 2.4 `EditGearSamples.variants` and `EditGearCollectionSamples.variants` (decision 10). Verify `./gradlew :composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"` and `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite` pass with no reference changed.

## 3. Design system

- [x] 3.1 `AppTextField` layouts `.row` and `.multiline` (decision 3), with gallery entries and their references. Verify `DesignSnapshotTests` record and then compare, and `TextFieldModelTests` still pass.
- [x] 3.2 `AppPickerRow` from `ActivityDefaultsView.picker` (decision 4), used by Activity defaults, with a gallery entry. Verify `SettingsPagesSnapshotTests` compare equal with no reference changed.

## 4. The native forms

- [x] 4.1 Type aliases (one block) and `Samples.editGear`, `editGearVariants`, `editGearCollection`, `editGearCollectionVariants`. Verify a Swift test reads them (`GearEditorsTextTests`).
- [x] 4.2 `CalendarDay`, `PhotoPick`, `MemberMove`, `TickedItems` (decisions 5 to 7). Verify `CalendarDayTests`, `PhotoPickTests`, `MemberMoveTests` and `TickedItemsTests` pass.
- [x] 4.3 `TypeChoiceList` and `AddItemsList` sheets. Verify their previews render and `GearEditorsTextTests` cover their groups, nesting and the nothing-to-add text.
- [x] 4.4 `EditGearView`, registered (decisions 2, 3, 5, 6, 8). Verify `GearEditorsTextTests` resolve the spec's words, limits and conditions on the samples, and `ScreenRegistryTests` names `EditGearScreen`.
- [x] 4.5 `EditGearCollectionView`, registered (decision 7). Verify `GearEditorsTextTests` cover its title, help, empty, full and accessibility labels, and `ScreenRegistryTests` names `EditGearCollectionScreen`.
- [x] 4.6 Previews and `GearEditorsSnapshotTests` (decision 10). Verify they record on a first run and pass on a second on an iOS 26.0 iPhone 17 simulator, and each image was looked at, light and dark.

## 5. The whole change together

- [x] 5.1 Run `./gradlew format`, `./gradlew check` and `./gradlew -p build-logic :convention:test`. Verify all pass, with no Android screenshot reference changed.
- [x] 5.2 Run the Swift tests (`xcodebuild test`) on an iPhone 17 iOS 26.0 simulator and on iOS 18.4. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [x] 5.3 Build the Release configuration for the simulator. Verify it succeeds and passes the stub check, and the stub report lists nothing new from `:feature-gear-api`, `:app-common-api` or `:iosShell`.

## 6. Walk the forms (once the follow lists session's walk is done, or with the user's go-ahead)

- [x] 6.1 On the signed-in iPhone 17 Pro (iOS 26.0): Add gear, saved without a name (the error), then named "Walk bike" with a kind, sports, a default type, a collection, weight typed with a letter (refused), an acquired date, starting distance, a photo from the library, notes and Followers; saved, listed on Gear; reopened and edited; a change cancelled by back; Retire and Put back in use; Delete asked and cancelled. Verify by noting what was seen against the shared form.
- [x] 6.2 On the same iPhone: New collection "Walk kit" with Walk bike and another item added in the order ticked, one dragged, one removed, saved; Edit opened from the collection and left by back. Verify by noting what was seen.
- [x] 6.3 On the signed-in iPad Air 11-inch (iOS 26.0): both forms from Gear beside Settings' list take the whole window, back returns to where they were opened, upright and on its side; the date popover and the sheets on an iPad. Verify by noting what was seen.
- [x] 6.4 Clean up only what the walk made, deleting Walk kit and Walk bike, with the user's go-ahead if anything else would go. Verify Gear lists what it listed before the walk.
- [x] 6.5 Launch the build on an iOS 18.4 simulator. Verify it reaches sign-in, and record what couldn't be seen there.

## 7. Docs

- [x] 7.1 The roadmap's phase 4 bullet, a "Phase 4 findings (gear editors)" section, and the open items it settles or adds (refused edits on a device, late `values`, the first native photo pick). Verify `openspec validate ios-native-gear-editors --strict` passes.
- [x] 7.2 `.agents/rules/compose-screens.md` (the native forms, `AppTextField` layouts, `AppPickerRow`, `PhotoPick`, `CalendarDay`, the new texts and getters) and `adaptive-ui.md`. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 7.3 A "Not verified" section listing what wasn't seen and why. Verify it names every unchecked case from 6.

## Probe results (1.1, 1.2)

- `LocalDate`: `year` and `day` are `Int32`, `month` is kotlinx-datetime's `Month` as a Swift enum whose `rawValue` is its ordinal;
  `LocalDate(year:month:day:)` built 2026-07-02 and `AcquiredChanged(date:)` took it. `GearPhoto.Saved` casts back with `as?`. Swift's
  `==` on two equal `ActivityType`s is Kotlin's `equals`.
- `IosApp.compressPhoto`: a 4,032 × 3,024 JPEG came back WebP at 2,048 × 1,536; a HEIC of it and four bytes of junk came back nil. So
  the picker asks for a compatible encoding and `PhotoPick` retries as UIKit's JPEG (design decision 5's fallback).
- A sheet holding its own `NavigationStack`, presented from a pushed destination, shows its inline title and Done. Edit mode held on for
  a `Form` gives only the `onMove`/`onDelete` section its handles and delete controls.

## Walk (iPhone 17 Pro and iPad Air 11-inch on iOS 26.0, signed in as the local test account; this change's Debug build)

Before: Gear listed the SRAM X0 Derailleur and Power Meter, the Garmin HRM 200, Edge 840 and Fenix 7s, and no collection. The
follow-lists session's walk was done (its notes); the tunnel was started with `scripts/dev.sh`.

- **6.1, iPhone.** Gear → + opened the native "Add gear". Save with no name: "Name" turned red with "Give your gear a name" under it,
  nothing saved. Typed "Walk bike" (the error went). Kind's menu offered None (checked) and the ten bike kinds; Gravel showed on one line
  at the trailing edge. Sports opened "Choose sports" with Done; Ride and Run ticked in place; the row read "Ride, Run". Default-for
  opened "Use by default for"; Ride's disclosure showed its sub-sports collapsed at first; Gravel Ride ticked; the row read "Gravel
  Ride". Weight: "9a5" typed in one burst read "9" (the letter refused, and the 5 after it lost); a 5 typed alone then read "95". The
  calendar opened in a popover with days after today greyed; 5 November read "2026-11-05" with the clear button. "Starting distance
  (mi)" (imperial account) took 1200. Add photo opened the system's photo picker with no permission prompt; a library photo came back
  through Kotlin's compression and showed with Change photo and Remove photo. Notes took a line. Visible to started at Only me; Followers
  showed the Followers note. Save closed onto Gear, listing "Walk bike / Gravel · Followers, 1,200 mi" with its photo (a held photo).
  The gear page's Edit opened "Edit gear" with every value seeded, the saved photo loaded; a brand typed and left by back wasn't saved.
  Retire saved and closed the form; Edit then read "Retired 2026-11-10" with Put back in use; Delete asked "Delete this gear?" with the
  shared message, Retire, Delete and Cancel; Cancel kept it. Put back in use saved and closed; the shared gear page and the Gear list
  showed the change only some seconds later (the AppView catching up, not the form).
- **6.2, iPhone.** New collection opened natively; "Walk kit" typed into the name with the form in edit mode. Add items listed the six
  other items with Add unavailable until one was ticked; Walk bike then the Edge 840 ticked; Add put them in that order. The Edge dragged
  by its handle above Walk bike, then removed with its delete control. Save closed onto Gear: "Walk kit, 1 item". Walk kit (native) →
  Edit opened "Edit collection" filled in; left by the edge swipe.
- **6.3, iPad.** Settings with Activity defaults beside it (now through `AppPickerRow`, unchanged); Gear beside the list; + opened "Add
  gear" over the whole window, its Collections section offering Walk kit; the calendar popover drew over the form (its arrow points at
  the middle of the row, not at the button); back returned to Settings with Gear beside the list and marked. On its side: Walk kit and
  its "Edit collection" took the whole window; back twice returned to the list beside Gear. Turned upright again.
- **6.4, iPad.** Walk kit → Delete → "Delete collection"; then Walk bike → Edit → Delete → Delete. Gear lists what it listed before.
- **6.5, iOS 18.4** (`Gear Editors iPhone 16 Pro`, a simulator made for this change). The iOS 18 build launched to the native sign-in.

## Not verified

- **A keystroke right after a refused one.** Seen lost on the weight field when typed in one burst by the simulator tool; a human's pace
  wasn't tried. `TextFieldModel` refuses an edit whole, and the field's own text is put back on the next draw, so a character arriving
  before then is part of a refused edit too. Recorded in the roadmap's open items.
- **A HEIC from the library.** The simulator's photos are JPEGs; the HEIC path (`.compatible`, then UIKit's JPEG) rests on the probe and
  `PhotoPickTests`. That the saved photo carries no location wasn't read back from the PDS; it rests on `compressPhoto` re-encoding.
- **A failed save, the saving state and the delete question on a device**: the local stack answered every write at once. They rest on
  the `saveFailed`, `saving` and `confirmingDelete` snapshots and the text tests (the delete alert was seen).
- **A mounted item's line and Open, and a full collection, on a device**: the test account has no mount and no 50-item collection; they
  rest on the `mountedRetired` and `full` snapshots.
- **VoiceOver**: the Move up, Move down and Remove actions and the pickers' spoken names weren't heard.
- **The forms on iOS 18, signed in**: the 18.4 simulator isn't signed in and signing in needs the account's password; the Swift tests ran
  there with the snapshots skipped.

## Workflow follow-up

- Archive with `/opsx:archive` once the user has reviewed and committed the change.
- Merge this branch into `claude/ios-swiftui-uikit-migration-df6ef6` beside the follow lists; the overlaps in `ScreenRegistry.swift`, `KotlinTypes.swift` and the roadmap are append-only blocks.
