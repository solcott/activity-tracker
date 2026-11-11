# Proposal

## Why

Phase 4 of the native iOS UI (`openspec/explorations/ios-native-ui.md`) moves features one change at a time. The gear item page
(`GearScreen`) is the last gear screen still drawn by the shared UI on iOS: it waited only for a native activity card, since it lists
the activities its usage counts, and `ActivityCardView` exists now (`ios-native-activity-card`). Opened from the native Gear list, a
collection or an activity, it drops the athlete from native pages into a Compose one and back.

The Compose page (`GearUi.kt`, `GearComponentsUi.kt`) also still builds much of what it says inside composables: the kind line, the brand
and model, the visibility and Retired notes, which totals apply to the item's type, the starting usage, the breakdown rows, a component's
line and mount dates, the picker's title, which errors show by Components, and the unmount date's bounds. A native page can't ask a
composable, so these move to shared code both UIs ask.

## What Changes

- **The gear item page is native on iOS.** A SwiftUI inset grouped list drawn from `GearState`: the photo, name, kind, brand and model
  and the owner's visibility and Retired notes; the owner's collections, ticked in place; Components (each with its main total and
  since when, and the owner's Replace and Unmount in a menu), Mount a component, Previous and Mounted on; the linked devices and
  activity profiles with Unlink; the totals that apply to its type with any starting usage; the breakdowns by activity type and by year;
  then the activities counted, newest first, page by page, as native activity cards. Edit is in the bar on the owner's page; a missing
  item says so instead of offering Retry.
- **The mount sheet and the unmount date are native.** Mount a component and Replace open a sheet of the athlete's mountable items with
  New item and Cancel in its bar; Unmount opens a SwiftUI calendar starting the day after the mount, today by default, with Cancel and
  Unmount.
- **Kotlin chooses the page's text and conditions, both UIs ask.** Every line above that the Compose page built in a composable, which
  sections show, whether Edit, the collections and component management are offered, whether mounting can be used, which error shows by
  Components and which over the activities, and the unmount date's first and initial day. The Compose page asks the same, with no
  visible change on Android, desktop or web.
- **Samples, previews and snapshots** of the page in every shared sample and in the variants (linked devices, a follower's view,
  components, a chain's mount history, choosing a replacement, choosing an unmount date, a failed mount, not found, the next page
  loading); a registry entry and its test.
- **The roadmap** records the probes, the findings, and closes the open item on `GearScreen`.

Out of scope: the gear editors (already native), the profile and feed (other changes), and any change to `ActivityCardView` or its texts.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `gear/gear-usage`: the gear page is drawn natively on iOS.
- `gear/gear-components`: the mount sheet and the unmount date are native on iOS.
- `dev/shared-display-text`: the gear page's lines, which sections show and the unmount date's bounds are chosen in shared code.

## Impact

- **`:feature-gear-api`:** `GearPageTexts` and `GearFigure`; getters on `GearState` (title, `showsEdit`, `showsCollections`,
  `showsComponents`, `managesComponents`, `mountEnabled`, `componentsError`, `activitiesError`, the unmount days); tests.
- **`:feature-gear-ui`:** `GearUi.kt` and `GearComponentsUi.kt` resolve the shared choices; `MOUNT_ERRORS` moves to the contract.
- **`:app-fixtures`:** `GearSamples.variants` (and an unmounting and a failed-mount sample).
- **`iosApp`:** `GearItemView`, its sheets, type aliases, samples, previews, snapshot and text tests, the registry entry and its test.
- **Docs:** the roadmap's phase 4 entry, probes, findings and open items.
- **Risk carried:** snapshots compare only on iOS 26.0; iOS 18 can't be walked signed in; other sessions share the signed-in simulators
  and edit `KotlinTypes.swift`, `Samples.swift`, `ScreenRegistry.swift` and its test.
