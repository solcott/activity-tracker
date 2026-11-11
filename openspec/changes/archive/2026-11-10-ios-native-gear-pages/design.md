# Design

## Context

What exists, from the code:

- **The contracts** (`feature/gear/api`). `GearCollectionState` holds `content: ContentState<GearCollectionDetail>` (`name`, `notes?`, `members: ImmutableList<GearRow>`), `confirmingDelete`, `deleting`, `error: UiError?`; events `Back`, `Retry`, `Open(rkey)`, `Edit`, `Delete`, `ConfirmDelete`, `DismissDelete`. The presenter clears `confirmingDelete` on `ConfirmDelete`, derives `deleting` from its single flight, and pops once the delete succeeds. `IgnoredSensorsState` holds `content: ContentState<ImmutableList<LinkedSensor>>` (`id`, `manufacturer?`, `model?`, `serialTail?`, `weak`) and `error: UiError?`; events `Back`, `Retry`, `Unignore(id)`. Both screens are plain `Screen`s, not `SettingsPage`s, so the host already pushes them full width over Settings (`settingsPagesOnTop`).
- **The shared UIs build text and decisions in composables.** `GearCollectionUi` titles the page with the name or "Collections" while it is empty, shows Edit (`AppIcon.ManualEntry`) and Delete (`AppIcon.Delete`) only once the name is known, disables both while `deleting`, shows `error` under the refresh status, then the notes, then "No items yet" or the members as `GearRowItem(showRetired = true)`, and asks before deleting in `AppAlertDialog`. `IgnoredSensorsUi` lists `LinkedSensorRow`s with Un-ignore. `LinkedSensorRow` names a device with `deviceName` (a plain function in `:app-common-ui` `GearLabels.kt`, also used by the import's `NewDevicesForm` in `:feature-record-ui`), and picks "Serial …2071" or the weak note in the composable; its action labels ("Un-ignore Garmin Edge 840", "Unlink SRAM 1052") are built by the two callers. `GearUi`'s linked devices use the same row.
- **The native pieces this reuses** (`ios-native-settings-lists`, `ios-native-account`): `ContentStateView`/`RefreshStatusView`, `AppItemRow` with `Thumbnail`, `GearListTexts.detail(row:showRetired:)` and `total(row:units:)`, `pageTitle(_:actions:)`, `AppIconButton`, `AppSecondaryButton(small:)`, `ErrorMessage(layout: .row)`, `.listStatusRow()`, and `.alert(_:isPresented:presenting:)` over a state's question (Account's ending the session).
- **Samples**: `GearCollectionSamples.all` (the content-loading kinds over "Dive kit" with a regulator, a retired fullsuit and fins, and an empty "Winter training") and `IgnoredSensorsSamples.all` (the content-loading kinds over a Garmin Edge 840 and a weak Polar H10). Neither has a deleting, confirming or failed-action state.

## Goals / Non-Goals

**Goals:**

- The two pages, native, saying and offering exactly what the shared pages do.
- Every rule they follow written once in Kotlin, including the device row's, which the gear item page will reuse when it goes native.

**Non-Goals:**

- New design-system pieces: both pages are built from what exists.
- A progress mark while deleting or un-ignoring: the shared pages show none (they disable the actions, or nothing), and the native ones follow them.
- iOS-convention changes to where Delete sits (a red row at the end of the list): the shared bar's two buttons are kept, as the Gear list kept its Add and More.

## Decisions

### 1. The gear item page waits for the native activity card

Asked of the user before proposing. `GearScreen` lists its counted activities with `activityItems`, the feed's `ActivityCard` (author header, moderation notice and blur, a `RouteMapTile` drawn by MapLibre, media, likes, the stats table). The options were to pull the native card forward (most of the feed change, and the card's map), to draw iOS-only compact rows until the card exists (a look the other platforms don't have, thrown away later, and moderation notices to carry), or to wait. It waits: the roadmap's open items record that the item page, its components, mount sheet and SwiftUI unmount date picker go with or right after the native card. This change's device row (decision 3) is the piece of it that can land now.

### 2. Probe first

Throwaway Swift tests on the iPhone 17 simulator (iOS 26.0), and the generated Swift read, recorded in the roadmap under "Phase 4 probes (gear pages)":

- `ContentState<ImmutableList<LinkedSensor>>.data` reads in Swift as the Kotlin immutable list, and `elements(as: LinkedSensor.self)` gives the two sample devices in order. (The Gear list's content was a class holding lists; this is the first content that is a list.) **Found:** it doesn't. Through the erased `data` a list of devices arrives as a Swift `Array<Any>`, and the empty list (the loading sample's, and a loaded page with nothing ignored) throws an Objective-C exception when read. So `IgnoredSensorsState` gains `sensors`, a getter typed `ImmutableList<LinkedSensor>` returning the content's value, which reads through `elements(as:)` on every sample, and the native page never reads `data` (decision 5). The content itself is unchanged.
- `LinkedSensor.weak` read from the app's code. `weak` is a Swift declaration modifier, but only a contextual keyword: the generated `FeatureGearApi.swift` already declares `public var weak: Swift.Bool` and builds (read while proposing). The probe confirms `sensor.weak` reads from a test, so the contract keeps its name, unlike `PlaceNameFields.self` in phase 1.
- `GearCollectionDetail.notes` as a Swift optional, and `members` through `elements(as: GearRow.self)`.
- The stub report for `:feature-gear-api` and `:app-common-api` after decisions 3 and 4, read once they land.

### 3. Device text in Kotlin

- `deviceName(manufacturer, model)` moves from `:app-common-ui` `GearLabels.kt` to `:app-common-api` beside `GearTexts` (same package, `app.gear`), unchanged, so `NewDevicesForm` and the gear UIs compile without an import change. It stays a top-level function (Swift calls it as `GearPackage`'s static function) rather than joining `GearTexts`, since it returns plain text, not an `AppText`.
- `LinkedSensorTexts` in `:feature-gear-api`: `name(sensor)` (`AppText.written(deviceName(...))`), `detail(sensor)` ("Serial …2071", the weak note, or null), `unignore(sensor)` and `unlink(sensor)` (the spoken labels). `LinkedSensorRow` takes the sensor and resolves `name` and `detail` itself; its callers pass `unignore` or `unlink` resolved. So `GearUi` keeps its words, and the native item page will ask the same functions.
- Tests in `:feature-gear-api` `commonTest`: a model naming its maker, a maker and model, a model alone, the serial, the weak note, neither, and both labels.

### 4. The collection's conditions on its state

`GearCollectionState.title: AppText` (the name, or `gear_collections` while it is empty), `showsActions` (the name is known) and `actionsEnabled` (`!deleting`). `GearCollectionUi` asks them. A test over every sample kind and the new variants.

### 5. The two views

- **`GearCollectionView`**: `ContentStateView(state.content, as: GearCollectionDetail.self, errorKind: .LoadGearCollection)`, then an `.insetGrouped` `List`: `RefreshStatusView`; the action error as `ErrorMessage(layout: .row)` in a status row; the notes as a section of their own (plain text, selectable, not a button); "No items yet" as a secondary status row, or the members in one section as `AppItemRow`s with `Thumbnail`, `GearListTexts.detail(row, showRetired: true)` and `total(row, units)`, opening `Open(rkey)`. `pageTitle(Text(state.title))` with, when `showsActions`, `AppIconButton(.manualEntry, label: .gearCollectionEdit)` and `AppIconButton(.delete, label: .gearCollectionDelete)`, disabled unless `actionsEnabled`. The alert is `.alert(Text(.gearCollectionDeleteTitle), isPresented:)` bound to `confirmingDelete` (setting it false sends `DismissDelete`), with a destructive Delete collection sending `ConfirmDelete`, Cancel, and the message. The notes section was chosen over the members section's header, which the inset grouped style shrinks and capitalises.
- **`IgnoredSensorsView`**: `ContentStateView(state.content, errorKind: .LoadIgnoredSensors)` in a form that lays out the progress and the failure without reading the value (a small addition to `ContentStateView`), with the devices read from `state.sensors.elements(as: LinkedSensor.self)` (decision 2); an `.insetGrouped` `List`: `RefreshStatusView`, the action error, the empty explanation as a secondary status row, or one section of `AppItemRow`s with the name as title, the detail as its line, no picture and no disclosure, and `AppSecondaryButton(.gearUnignore, small: true)` trailing with `unignore(sensor)` as its accessibility label. `pageTitle(Text(.gearIgnoredSensors))`. If `AppItemRow` can't go without its leading view, it gains that option, with a gallery entry; that is the one design-system change allowed.
- Both registered in `ScreenRegistry.app`; `KotlinTypes.swift` aliases `GearCollectionScreen`, `GearCollectionState`, `GearCollectionEvent`, `GearCollectionDetail`, `IgnoredSensorsScreen`, `IgnoredSensorsState`, `IgnoredSensorsEvent`, `LinkedSensor`, `LinkedSensorTexts`.
- Both draw in the stack's bar only (they never show in the column), through `pageTitle`, so a later move into the column needs nothing new.

### 6. Samples, previews, snapshots

- `GearCollectionSamples.variants` (`deleting`, `deleteFailed` with an unreachable server, `confirmingDelete`) and `IgnoredSensorsSamples.variants` (`nothingIgnored`, loaded and empty, since `contentSamples` has no empty kind; `unignoreFailed`), apart from `all`, whose keys are `ContentLoadingConformanceTest`'s and the Android screenshot set's.
- Previews: `SamplePages` over each page's samples and variants inside a `NavigationStack`.
- `GearPagesSnapshotTests`: every sample and variant of both pages on an iPhone, light and dark, with `everySampleHasASnapshot`; the bar's buttons take `Snapshots.barButtons`. The alert isn't drawn by a snapshot (`.alert` presents over the page); its words are held by `GearPagesTextTests`.
- `GearPagesTextTests`: the spec's scenarios on the samples (the rows' lines, the title before and after loading, `showsActions` and `actionsEnabled`, the devices' names, details and labels, the empty texts, the failures through `ErrorTexts`).

### 7. Walking the pages

On the signed-in iPhone 17 Pro and iPad Air 11-inch (iOS 26.0). The test account has no collection and no ignored device, so the walk makes a collection through the shared editor (New collection on Gear), opens it, opens an item from it and comes back, cancels a delete, then deletes it, which is the walk's own; on the iPad it opens the collection from Gear beside the list and comes back to the list beside Gear, upright and on its side. Ignored devices is seen empty; its rows rest on the snapshots. The iOS 18.4 build is launched to sign-in only.

## Risks / Trade-offs

- **[A Swift keyword as a property name]** → `weak` is generated and builds; if the probe finds a call site that won't take it, a rename touches only the contract, the presenter, the fixtures and the shared row.
- **[A collection on a device is the walk's own]** → Created and deleted by the walk, as earlier walks muted and unmuted bob; no other data changes.
- **[An ignored device on a device]** → Needs an import that ignores one; the rows rest on the snapshots and text tests and are listed as not verified.
- **[iOS 18 not walked signed in]** → Swift tests on 18.4 and a launch; listed as not verified.

## Migration Plan

None: the shared pages stay on every other platform and as the fallback on iOS for anything not registered.
