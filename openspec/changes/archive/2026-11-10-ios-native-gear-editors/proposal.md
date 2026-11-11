# Proposal

## Why

Phase 4 of the native iOS UI (`openspec/explorations/ios-native-ui.md`) moves features one change at a time. The native Gear list and collection page (`ios-native-settings-lists`, `ios-native-gear-pages`, archived) open the two gear editors, `EditGearScreen` and `EditGearCollectionScreen`, which are still the shared UI's. They are the first native forms built on text fields beyond sign-in's one, so they settle what every later native form needs: fields inside the system's grouped form, digits-only and maximum-length rules on a device, the first native photo pick, the first native date choice, multi-select sheets and reordering. The gear item page waits for the native activity card; the editors don't.

## What Changes

- **The gear editor is native on iOS.** "Add gear" or "Edit gear", in the system's grouped form: name (required, with the shared message when missing), type and kind as menus with the choice trailing on one line, brand and model, the sports it is for and the activity types it is the default for (each chosen in a sheet, by category, sub-sports under their sport), its collections ticked in place, weight (digits only), acquired date (a calendar of past days, clearable), starting distance in the athlete's units and starting time, the photo (picked from the photo library, compressed and stripped of metadata as on every platform, changed or removed), notes, and who may see it with the Followers note. Save in the bar; a failed save shows at the top. A saved item also shows what it is mounted on (opening that item), when it was retired, Retire or Put back in use, and Delete, which asks in the system's alert, offering Retire instead. Same words and limits as the shared form.
- **The collection editor is native on iOS.** "New collection" or "Edit collection": name (required) and notes with the collections help, its items in order, reordered by dragging and removed in place (each also with Move up, Move down and Remove for VoiceOver, in the shared labels), Add items in a sheet that ticks the athlete's other items and adds them in the order ticked, the full note at 50, and Save in the bar.
- **Both open full width**, as today: from Gear or a collection, alone or beside Settings' list; back and a finished save return to where they were opened from.
- **Kotlin chooses the text and conditions, both UIs ask.** The editors' titles, the kind, sports and default-for summaries (a list's names joined, or the none text), the starting distance's label by units, the photo button, the mounted-on and retired lines, the field limits, the sport and type groups the sheets offer and which type nests under which, and getters for what each form enables, shows and has chosen (`EditGearState`, `EditGearCollectionState`, `EditGearTexts`, `SportTypeGroups`). The Compose forms ask the same. No visible change on Android, desktop or web.
- **A photo's bytes reach the presenter through Kotlin's compression.** Swift hands the picked image to a Kotlin entry (`IosApp.compressPhoto`) that runs the shared `compressPhoto`, and sends the result as the form's existing `PhotoPicked` event. No contract change.
- **Design system:** a text field laid out as a form row (label leading, text trailing, or several lines under the label), and the menu choice row Activity defaults has, moved into the design system for every form.
- **Samples, previews and snapshots** of both forms in every shared sample, plus variants beside `all` (a mounted, retired item with a photo; saving; a failed save; the delete question; a collection at its limit with nothing to add), so the shared screenshot set and the content-loading conformance keys are unchanged. Registry entries and their test.

Out of scope: the gear item page (`GearScreen`, with the native activity card), mounting from the editor's own flow beyond what `EditGearScreen` already does, the profile editor and the activity forms (their own changes, though they can reuse the photo pick and form rows), and a native image cropper.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui/settings-layout`: the gear editor and the collection editor are drawn natively on iOS and open full width over Settings.
- `dev/shared-display-text`: the editors' titles, summaries, labels, limits, choice groups and conditions are chosen in shared code.
- `dev/ios-swift-interop`: a photo picked natively reaches a presenter compressed by shared code, and a calendar date crosses between the two languages.
- `ui/ios-design-system`: text fields and menu choices laid out as rows of the system's grouped form.

## Impact

- **`:app-common-api`:** `SportTypeGroups` (the gear sheets' groups and the nesting rule `sportTypeList` asks too); a joined-list text; tests.
- **`:feature-gear-api`:** `EditGearTexts`, `EditGearLimits`, getters on `EditGearState` and `EditGearCollectionState`; tests. **`:feature-gear-ui`:** `EditGearUi`, `EditGearCollectionUi` ask them. **`:app-common-ui`:** `sportTypeList` asks the nesting rule.
- **`:ui-resources`:** one string (the joined list) and the regenerated iOS catalog.
- **`:iosShell`:** `IosApp.compressPhoto`.
- **`:app-fixtures`:** `EditGearSamples.variants`, `EditGearCollectionSamples.variants`.
- **`iosApp`:** `EditGearView`, `EditGearCollectionView`, their sheets and small logic types, `AppTextField`'s row layouts, `AppPickerRow`, type aliases, samples, previews, snapshot and text tests, registry entries.
- **Docs:** the roadmap's phase 4 entry, "Phase 4 probes (gear editors)" and findings, open items; `.agents/rules/compose-screens.md` and `adaptive-ui.md`.
- **Risk carried:** iOS 18 can't be walked signed in; snapshots compare only on iOS 26.0; another session's build shares the signed-in simulators, so the walk waits for theirs; a walk creates gear on the test account and must not delete real gear without the user's go-ahead.
