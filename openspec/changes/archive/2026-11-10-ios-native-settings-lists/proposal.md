# Proposal

## Why

Phase 4 of the native iOS UI (`openspec/explorations/ios-native-ui.md`) moves Settings' pages one change at a time; Activity defaults and Segments & groups are native (`ios-native-settings-pages`, archived). The next two, the Gear list and the Blocked and Muted accounts lists, are the first native pages that show pictures from the network and the first that load a list page by page, which every later list (followers, search, the feed) needs too. Drawing them now builds that infrastructure on two small, well-sampled pages.

## What Changes

- **The Gear list is native on iOS.** A grouped list of the athlete's collections (with New collection, or what collections are when there are none), then their active gear by type, then the retired gear: each item with its photo, name, kind and who may see it, and its one usage total; Add and Ignored devices in the page's bar; the empty list's message with Add; the content-loading states of `ui/content-loading`. Same words and figures as the shared page.
- **Blocked accounts and Muted accounts are native on iOS.** Each account with its avatar, name and handle, Unblock or Unmute (unavailable while that account's change is being written), and "Blocked on Bluesky. Unblock them there." for a block this app can't undo; a failed unblock or unmute above the list; the list's empty message; and the paged loading of `ui/content-loading`: progress or the failure before the first page, a refresh's status over the accounts, the next page asked for once the end of the list is on screen, with its progress or failure below.
- **What opens from them opens full width over Settings, as it does today**: an item, a collection, a new item or collection, Ignored devices and a profile aren't settings pages, so none opens in the column.
- **Remote images in the iOS design system** (the probe in `design.md`): photos and avatars from a URL with a placeholder until they load or when they don't, through Nuke and NukeUI (Swift Package Manager, exact version), including `heldphoto:` URLs, which a non-Public gear item's photo is, answered from the signed-in session's held bytes. An avatar shows the athlete's initial until its picture loads.
- **A native paged list in the design system**: the paged states laid out as the shared UI's paged list does, deciding what shows from one Kotlin rule (`PagedShown` in `:app-common-api`) that the shared `pagedRefreshStatus` and `pagedFooter` now ask too, with no visible change; and a page header with actions beside its title, in the column and in the stack's bar.
- **Kotlin chooses the text and conditions, both UIs ask.** The gear names and totals move from `:app-common-ui`'s composables to `GearTexts` in `:app-common-api`, which those composables resolve; the gear row's detail line, a collection's count and when the collections section shows and explains itself go to the gear contract; a list's title, empty text, remove label and Bluesky note, its load error, and whether an account's change is being written go to the block list's contract. No visible change on Android, desktop or web.
- **Samples, previews and snapshots** of both pages in every shared sample, and the block list's muted list and its in-flight, Bluesky and failed-unblock states (new samples beside `all`, so the shared screenshot set is unchanged); design-system gallery entries for the new pieces. Every other screen keeps the fallback.

Out of scope: the other gear screens (an item, a collection, editing, Ignored devices), profiles and every other paged list, pull to refresh (the shared page has none here), a back button in the column header (none of these pages is deeper than the pane's first).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui/settings-layout`: the Gear list and the Blocked and Muted lists are drawn natively on iOS, alone and beside the list, and what opens from them opens full width.
- `ui/ios-design-system`: images from a URL with a placeholder, held photos among them; a paged list that follows the loading rules; actions in a page's header.
- `dev/shared-display-text`: gear names, totals and row lines, the block lists' words, and what a paged list shows around its items are chosen in shared code.
- `dev/ios-swift-interop`: a paged list's items, and a set of account ids, reach Swift as their own types; a held photo's bytes reach Swift.

## Impact

- **`:app-common-api`:** `PagedShown`; `GearTexts`; `avatarInitial`; tests. **`:app-common-ui`:** `PagedList.kt` and `GearLabels.kt` ask them; no screenshot reference changes.
- **`:feature-gear-api`:** `GearListTexts`, `GearLists.showsCollections` and `explainsCollections`; **`:feature-gear-ui`:** `GearListUi` asks them. **`:feature-profile-api`:** `BlockListTexts`, `BlockListState.loadErrorKind` and `isRemoving`; **`:feature-profile-ui`:** `BlockListUi` asks them. Tests.
- **`:app-fixtures`:** `BlockListSamples.variants`. **`:composeApp`/`:iosShell`:** `IosApp.heldPhoto`, the held bytes for the native image loader.
- **`iosApp`:** Nuke 13.2.3 (Nuke, NukeUI) by Swift Package Manager; in `Design`: `RemoteImage`, `Avatar`, `PagedList`, `AppItemRow`, actions in `pageTitle`/`PageBar`; `GearListView`, `BlockListView`, type aliases, samples, previews, snapshot and text tests, registry entries.
- **Docs:** the roadmap's phase 4 entry, a "Phase 4 probes (settings lists)" and findings section, its open items; `.agents/rules/compose-screens.md` and `adaptive-ui.md`.
- **Risk carried:** iOS 18 can't be walked signed in (its simulators can't reach the local stack); the snapshots compare only on iOS 26.0; a real held gear photo and a second page of accounts depend on what the test account holds.
