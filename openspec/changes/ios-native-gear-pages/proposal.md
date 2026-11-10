# Proposal

## Why

Phase 4 of the native iOS UI (`openspec/explorations/ios-native-ui.md`) moves features one change at a time, cheapest infrastructure first. The native Gear list (`ios-native-settings-lists`, archived) opens three gear screens that are still the shared UI's: an item, a collection and Ignored devices. A collection and Ignored devices reuse what that change built (`GearTexts`, `AppItemRow`, `Thumbnail`, `ContentStateView`) and add only a confirmation alert and a device row, so they are the cheapest native pages left in Settings.

The item's page (`GearScreen`) waits. It ends with the activities its usage counts, drawn as full feed cards: a route map snapshot, media with moderation blur, likes, the stats table. A native page would need the native activity card, which is the feed's work (and the card's map is the maps phase), or an iOS-only stand-in that would be thrown away. It moves with or right after the native activity card, together with its components, the mount sheet and the unmount date picker (SwiftUI's own).

## What Changes

- **A gear collection is native on iOS.** Its name as the title ("Collections" until it loads), its notes, its items in the athlete's order, each with its photo, name, kind, who may see it and Retired where it is, and opening the item's page; "No items yet" when it holds none; Edit and Delete in the bar once it has loaded, unavailable while it is being deleted; Delete asks first in the system's alert ("Delete this collection?", with what stays as it is), and a failed delete shows above the items. The content-loading states of `ui/content-loading`. Same words as the shared page.
- **Ignored devices is native on iOS.** Each ignored device by its maker and model, with the end of its serial or that it may match other units, and Un-ignore (read to VoiceOver with the device's name); a failed un-ignore above the devices; the empty page's explanation; the content-loading states. Same words as the shared page.
- **Both open full width, as today**: they aren't settings pages, so from Gear beside Settings' list they take the whole window, and back returns to Settings with Gear beside it. What they open (the item's page, the collection editor) stays the shared UI.
- **Kotlin chooses the text and conditions, both UIs ask.** A device's name moves from `:app-common-ui` to `:app-common-api` beside `GearTexts`, unchanged for the import's new-device form; a device's detail line and its Un-ignore and Unlink labels go to the gear contract (`LinkedSensorTexts`), which `LinkedSensorRow` and the gear page's linked devices resolve; a collection's title, and whether its Edit and Delete show and can be used, go to `GearCollectionState`. No visible change on Android, desktop or web.
- **Samples, previews and snapshots** of both pages in every shared sample, plus new variants beside `all` (a collection being deleted, a failed delete, the delete question; a failed un-ignore), so the shared screenshot set and the content-loading conformance keys are unchanged. Registry entries and their test.

Out of scope: the item's page (above), the item and collection editors (`EditGearScreen`, `EditGearCollectionScreen`, forms with photo picking; a change of their own), the "left on another device" screen (`LeftElsewhereScreen`), any new design-system piece beyond what the two pages need, and pull to refresh (the shared pages have none).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui/settings-layout`: a gear collection and Ignored devices are drawn natively on iOS and open full width over Settings.
- `dev/shared-display-text`: a device's name and detail, its actions' labels, and a collection's title and actions are chosen in shared code.
- `dev/ios-swift-interop`: a loaded list of contract objects (Ignored devices' content) reaches Swift as its own type.

## Impact

- **`:app-common-api`:** `deviceName` (moved from `GearLabels.kt`, same package, so its callers in `:feature-record-ui` and `:feature-gear-ui` are unchanged); tests.
- **`:feature-gear-api`:** `LinkedSensorTexts`; `GearCollectionState.title`, `showsActions`, `actionsEnabled`; tests. **`:feature-gear-ui`:** `GearCollectionUi`, `IgnoredSensorsUi`, `LinkedSensorRow` and `GearUi`'s linked devices ask them.
- **`:app-fixtures`:** `GearCollectionSamples.variants`, `IgnoredSensorsSamples.variants`.
- **`iosApp`:** `GearCollectionView`, `IgnoredSensorsView`, type aliases, samples, previews, snapshot and text tests, registry entries.
- **Docs:** the roadmap's phase 4 entry, a "Phase 4 probes (gear pages)" and findings section, its open items (the item page's dependency on the native card); `.agents/rules/compose-screens.md` and `adaptive-ui.md`.
- **Risk carried:** iOS 18 can't be walked signed in (its simulators can't reach the local stack); snapshots compare only on iOS 26.0; a collection exists on the test account only if the walk makes one, and deleting it is the walk's own.
