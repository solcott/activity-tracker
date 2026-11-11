# Design

## Context

What exists, from the code:

- **The contracts** (`feature/gear/api`). `EditGearState` holds `content: ContentState<Unit>`, `isNew`, seven `TextFieldState`s (name, brand, model, weight, starting distance, starting time, notes), `units`, `type` with `types`, `kind?` with `kinds`, `sports` (sport tokens), `defaultFor` (`ActivityType`s), `collections` (`GearCollectionChoice`: rkey, name, member), `visibility`, `acquiredOn`/`retiredOn` (`LocalDate?`), `mountedOn` (`GearMountedOn`: uri, name, since), `photo` (`GearPhoto.Saved(url)` or `Picked(PickedPhoto)`), `nameMissing`, `saving`, `confirmingDelete`, `approvesFollowers`, `error`. Its events cover every field (`TypeChanged`, `KindChanged`, `ToggleSport`, `ToggleDefault`, `ToggleCollection`, `VisibilityChanged`, `AcquiredChanged`, `PhotoPicked`, `RemovePhoto`) and `Save`, `Retire`, `Unretire`, `Delete`, `ConfirmDelete`, `DismissDelete`, `OpenMountedOn`, `Back`, `Retry`. `EditGearCollectionState` holds `content`, `isNew`, `name`, `notes`, `members` and `others` (`GearRow`s), `full`, `nameMissing`, `saving`, `error`; events `Add(rkeys)` (with a Swift `List` constructor already), `Remove(rkey)`, `Move(from, to)` (indexes into `members`, `to` the final index), `Save`, `Back`, `Retry`. Both presenters pop on a successful save, so a native form needs nothing for closing. Neither screen is a `SettingsPage`, so the host pushes them full width over Settings.
- **The shared forms build text and rules in composables.** `EditGearUi` picks its title, the kind shown (or None), the sports and default-for summaries (labels joined with ", ", or the none string), the starting distance's label by units, Add or Change photo, the mounted-on and retired lines (dates as `LocalDate.toString()`), and holds the field limits (`GearItemRecord.MAX_NAME`, `MAX_BRAND`, `MAX_NOTES`, and two private UI constants, `MAX_WEIGHT_DIGITS = 7` and `MAX_STARTING_CHARS = 9`) and the two type groups (`SPORT_GROUPS`, bare sports by category; `TYPE_GROUPS`, every type) over `SportCatalog`. `sportTypeList` (`:app-common-ui`, shared with the activity type pickers) decides which type nests under its sport. The photo is picked with FileKit's launcher and compressed with `compressPhoto` (`:app-common-ui`: WebP through Skia off Android, scaled to 2,048 px, stepping down under 1.5 MB, metadata dropped) before `PhotoPicked`. `EditGearCollectionUi` reorders with up and down buttons and adds through a sheet that keeps the order ticked.
- **The native pieces this reuses**: `AppTextField` with a `TextFieldModel` per field (sign-in), `TextFieldRules` (digits only, maximum length; never on a device yet), the menu choice row in `ActivityDefaultsView.picker`, `pageTitle(_:actions:)`, `AppItemRow`, `Thumbnail` and `RemoteImage`, `ContentStateView`'s form without a value, `ErrorMessage(layout: .row)`, `.listStatusRow()`, `.alert` over a state's question, `VisibilityTexts.followersNote`, `LabelText.sport`/`activityType`/`sportCategory`/`visibility`, `GearTexts.type`/`kind`, `GearListTexts.detail`.
- **Samples**: `EditGearSamples.all` (the content-loading kinds over a gravel bike, an `invalid` one with no name, a `new` one) and `mounted` outside `all`; `EditGearCollectionSamples.all` (the same kinds over "Race kit"). No saving, failed-save, delete-question or full state.

## Goals / Non-Goals

**Goals:**

- Both forms native, saying, limiting and offering what the shared forms do, laid out the way an iOS edit form is.
- Every rule they follow written once in Kotlin, so the profile editor and the activity forms can reuse the photo pick, the form rows and the type groups.

**Non-Goals:**

- A contract change for iOS: the events and fields are enough.
- Unsaved-changes prompts, a progress mark while saving or while a photo compresses: the shared forms have none.
- A native image cropper or a camera capture: the shared pick has neither.

## Decisions

### 1. Probe first

Throwaway code on the iPhone 17 simulator (iOS 26.0), recorded in the roadmap under "Phase 4 probes (gear editors)" before the views are built on the answers:

- **A sheet with a `NavigationStack` of its own, presented from a stack's destination, shows its bar and title.** The settings probes found a stack inside a destination never shows; a sheet is its own presentation, so it should. If it doesn't, the sheets draw their own header row.
- **Edit mode on a `Form` that also holds text fields.** The collection form keeps edit mode on for its items (decision 7); the probe checks that drag handles and delete controls show only on the items' rows, that the text fields still take focus and typing, and that buttons in other rows still fire.
- **`LocalDate` from Swift**: which properties a `LocalDate` reads as (`year`, `month` as Swift's enum or a number, `day`) and that `LocalDate(year:month:day:)` builds one an event accepts.
- **The photo's way in**: a Kotlin `suspend fun IosApp.compressPhoto(bytes: ByteString): PickedPhoto?` awaited from a Swift test with a JPEG and a HEIC that UIKit wrote, returning WebP within the size limits (Skia may not decode HEIC; see decision 5); and `GearPhoto` cast with `as?` in Swift.
- **Equality and membership**: whether Swift's `==` on two equal Kotlin data objects (`ActivityType`) is Kotlin's `equals`. Decision 9 doesn't rely on it either way: membership is asked of Kotlin.
- **Refused edits on a device**: `AppTextField` redraws a refused keystroke (the open item), seen on the weight field in the walk, not in the probe.

### 2. Two grouped forms, Save in the bar

Each form is a `Form` (the system's grouped look, as Activity defaults) inside `ContentStateView`'s form without a value (`content` is `ContentState<Unit>`), titled with `pageTitle(Text(state.title))` and Save as the page's bar action, disabled while saving. That is where an iOS edit form keeps Save, and it stays reachable as the form scrolls; the shared form's Save at its end is Compose's convention. A failed save shows in a status row at the top of the form, where it is seen without scrolling, in place of the shared form's place above its Save. The name's own error is under the name field, as shared. Back is the stack's (`Back` is sent by the stack's pop like every native page).

Rejected: Save as the last row (the shared layout), which leaves it under the keyboard and off screen on a long form; a `.toolbar` of the view's own, which `pageTitle` already places correctly alone and beside the list.

### 3. Text fields as form rows

`AppTextField` gains a `layout`: `.stacked` (sign-in's, the default and unchanged), `.row` (the label leading and the text trailing on one line, the way the system's forms edit a value) and `.multiline` (the label above a field that grows to several lines, for notes). Supporting text and the error colour stay under the field in every layout. Each view holds one `TextFieldModel` per field in `@State`, as sign-in does, built with its `TextFieldRules` from Kotlin's limits (decision 9): the name, brand, model and notes at their record's maximum length, the weight digits only and at most 7 digits with the number pad, the starting distance and time at most 9 characters with the decimal pad (no digits rule, as shared). Brand and model, side by side in the shared form, are two rows.

Rejected: a new field type beside `AppTextField` (a second model binding to keep in step), and the system's bare `TextField` in a row (it would skip `TextFieldModel`, so a presenter's seed after load would not show: the late `values` open item).

### 4. Menu choices as `AppPickerRow`

`ActivityDefaultsView.picker` moves into the design system as `AppPickerRow(label, name:, selection:, options:, optionLabel:)`, generic over the value (a `String`, or a `String?` for the kind's None), unchanged in look: a `Menu` holding an inline `Picker`, the choice and the system's up-down chevron trailing on one line, the label wrapping, VoiceOver reading the name and value. Activity defaults uses it, and its snapshots must compare equal. Type, kind and visibility are `AppPickerRow`s; the Followers note and the visibility help are the section's footer.

### 5. The photo: `PhotosPicker`, compressed in Kotlin

The pick is SwiftUI's `PhotosPicker(matching: .images, preferredItemEncoding: .compatible)`, not a document picker: gear photos are in the photo library, the picker runs out of process with no permission prompt, and `.compatible` asks Photos to hand over a format every decoder reads (JPEG for a HEIC). A document picker (`.fileImporter`) is for files, as activity import will use it.

The bytes reach the presenter through Kotlin, not Swift's own encoder: Swift loads the item's `Data`, converts it (`toByteString`, kotlinx-io-bytestring is exposed) and awaits `IosApp.compressPhoto`, which runs the shared `compressPhoto` and returns a `PickedPhoto`, or nil when the bytes aren't a picture; Swift sends `EditGearEvent.PhotoPicked(photo:)`. So the size, the format, the metadata stripping and the turning upright are the same rule on every platform, and the contract is unchanged. Encoding in Swift was rejected: iOS has no system WebP encoder (the open item) and the rule would be written twice. If the probe finds Skia can't decode what `.compatible` gives, Swift hands over a JPEG it re-encodes with `UIImage` first, keeping the scaling and stripping in Kotlin.

The sequence (load, convert, compress, send, and ignore a nil) is `PhotoPick`, a small type beside the view with its load and compress injected, tested with Swift Testing like `ArchiveShare`. The row shows a 72-point picture (`RemoteImage` for `GearPhoto.Saved`, the picked bytes through `UIImage(data:)` for `Picked`, cast with `as?`), the picker labelled Add or Change photo from Kotlin, and Remove photo when there is one.

### 6. Dates: a calendar in a popover

The acquired row shows its date as Kotlin words it (ISO, as the shared form shows it) and opens a graphical `DatePicker` limited to today and earlier in a popover (`presentationCompactAdaptation(.popover)`, so a phone doesn't take a sheet for it); choosing a day sends `AcquiredChanged` and closes. Unset, the row shows a calendar button named "Choose the date you got it"; set, a clear button named "Clear the acquired date". The conversion is `CalendarDay`, a Swift type beside the view mapping a `LocalDate` to the `Date` at the start of that day in the device's calendar and back from the chosen `Date`'s year, month and day in that calendar, so no time zone shifts the day; tested in a zone west and east of UTC.

Rejected: a compact `DatePicker` in the row (it can't show "no date"), and a toggle revealing an inline calendar (Reminders' pattern, but a third control for one optional date).

### 7. Choosing in sheets, ticking in place, reordering by drag

- **Sports and default-for** each open a `.sheet` holding its own `NavigationStack` (the probe), a `List` with a section per category from Kotlin's groups (decision 9), a checkmark per ticked row, and Done in the bar. A tap sends `ToggleSport` or `ToggleDefault` at once, as the shared sheet does; the shared sheet's Cancel does the same as its Done, so the native one offers Done only, which is what it does. In the default-for sheet a sport with sub-sports is a `DisclosureGroup`: the sport's own row ticks, the disclosure shows its sub-sports, which are collapsed at first as shared. The sheet's content is a view of its own (`TypeChoiceList`), so it can be previewed and snapshotted.
- **Collections** are a section of rows ticked in place (a checkmark, the selected trait), hidden when the athlete has none. The shared chips and "Add to" menu are Compose's take on the same choice.
- **The collection's items** are one section with the system's edit mode held on for the form (`\.editMode`), so each row has a drag handle and a delete control: a move sends `Move(from, to)` and a delete `Remove(rkey)`. SwiftUI's move gives the index to insert before in the old order; Kotlin's is the final index. `MemberMove` converts (one less when moving down) and is tested. Each row also carries the shared labels as named accessibility actions ("Move Aero helmet up", "Move Aero helmet down", "Remove Aero helmet"), only the moves its place allows. If the probe finds edit mode breaks the form's other rows, the rows fall back to the shared form's three buttons.
- **Add items** opens a sheet (`AddItemsList`, its own view) with Cancel and Add in its bar, the other items with a checkmark each, and "All your gear is in this collection." when there are none; the order ticked is kept by `TickedItems` (tested: ticking, unticking and ticking again puts an item last), and Add sends `Add(rkeys:)` with that order.

### 8. Management of a saved item

Under the form for a saved item (`showsManagement`): the mounted-on line as a button sending `OpenMountedOn`, the retired line, Retire or Put back in use, and Delete in the destructive role. The alert is `.alert(Text(.gearDeleteTitle), isPresented:)` over `confirmingDelete` (closing sends `DismissDelete`), with Retire (`Retire`), Delete (destructive, `ConfirmDelete`) and Cancel, as Account and the collection page do.

### 9. Kotlin rules (no visible change)

- **`:feature-gear-api`, `EditGearTexts`** (an object, under detekt's eleven functions): `title(isNew)` and `collectionTitle(isNew)`, `kind(state)` (the kind's name, or None when it has none or isn't among its type's), `sports(sports)` and `defaultFor(types)` (names joined, or "Any sport" / "No activity types"), `startingDistanceLabel(units)`, `photoAction(photo)`, `mountedOn(mounted)`, `retiredOn(date)` and `date(date)` (ISO, as shared). **`EditGearLimits`**: each field's maximum (from `GearItemRecord` and `GearCollectionRecord`, plus the two UI constants moved here) and the weight's digits-only flag. **Getters**: `EditGearState.enabled`, `showsKind`, `showsManagement`, `isRetired`, `visibilityOptions`, `isForSport(sport)`, `isDefaultFor(type)`, `kindOptions` (null first, then `kinds`); `EditGearCollectionState.title`, `enabled`, `canAdd`. Swift never reads a set or list to answer a membership question (the `ImmutableSet` lesson of `BlockListState.isRemoving`).
- **`:app-common-api`, `SportTypeGroups`**: `gearSports` and `allTypes`, lists of `SportTypeGroup(category, label, types)` from `SportCatalog`, and `rows(group)`, the group's top rows each with the types nested under it (`SportTypeRow(type, label, nested)`), from one rule, `nestsIn(types)`, which `sportTypeList` now asks too. A group's lists are `ImmutableList`s of `ActivityType`, a class Swift has a type for since `ToggleDefault` names it.
- **A joined list**: one new string, `text_list_pair` ("%1$s, %2$s"), and `AppText.joined(texts)` folding it, which `EditGearTexts` uses; the catalog is regenerated (`generateIosResources`) and `SharedTextNamesTest` covers the names.
- `EditGearUi` and `EditGearCollectionUi` ask all of it; their screenshots must compare equal. Tests in `:feature-gear-api` and `:app-common-api` `commonTest` over each branch and every sample.

### 10. Registry, aliases, samples, snapshots

- `EditGearScreen`/`State`/`Event`, `EditGearCollectionScreen`/`State`/`Event`, `GearPhoto`, `GearMountedOn`, `GearCollectionChoice`, `EditGearTexts`, `EditGearLimits`, `PickedPhoto`, `SportTypeGroups` and its two classes, aliased in one block of `KotlinTypes.swift` headed for the gear editors; the two registrations together at the end of `ScreenRegistry.app`, so merging beside the follow lists' additions is append-only.
- Variants beside `all`, so the shared screenshot set and `ContentLoadingConformanceTest` keys stay: `EditGearSamples.variants` (`mountedRetired`: the chain, retired, with a saved photo URL; `imperial`; `saving`; `saveFailed`; `confirmingDelete`) and `EditGearCollectionSamples.variants` (`full`, `nothingToAdd`, `saving`, `saveFailed`).
- `#Preview`s with `SamplePages` over each, and `GearEditorsSnapshotTests` over every sample and variant, light and dark, recorded tall enough for the whole form, plus `TypeChoiceList` and `AddItemsList` and the two new field layouts in the design gallery's snapshots. `GearEditorsTextTests` hold the words and conditions; `PhotoPickTests`, `CalendarDayTests`, `MemberMoveTests`, `TickedItemsTests` the logic.

## Risks / Trade-offs

- [Edit mode on a whole form could make other rows behave as in a list being edited] → probed first (decision 1); the fallback is the shared form's buttons.
- [`PhotosPicker` can't be driven by snapshots or tests] → `PhotoPick` is tested with its load and compress injected; the picker itself is seen in the walk, with the simulator's sample photos.
- [Skia may not decode a HEIC] → `.compatible` encoding, probed with a HEIC; a `UIImage` JPEG step before Kotlin if needed.
- [Save in the bar differs from the shared form's last-row Save] → the words, the condition and the event are the same; only where it sits is iOS's, as the Gear list's bar buttons were.
- [A walk on the shared simulators replaces the follow lists session's build] → build and test in parallel, walk only after their walk is done or with the user's word; the walk creates and edits its own item and collection and deletes only those, never existing gear without the user's go-ahead.
- [iOS 18 can't be walked signed in] → the Swift tests run there with snapshots skipped; recorded under "Not verified".
