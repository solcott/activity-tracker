# Design

## Context

What exists, from the code:

- **The contracts.** `GearListState` (`feature/gear/api`) holds `content: ContentState<GearLists>`: `collections` (`GearCollectionRow`: rkey, name, count), `active` (`GearGroup`: a type and its `GearRow`s) and `retired` (`GearRow`s), each row with `type`, `kind?`, `visibility`, `photoUrl?`, `mainTotal` (a `GearMainTotal` enum of `:core-lexicon`) and `totals?`; events `Back`, `Retry`, `Add`, `Open`, `OpenCollection`, `AddCollection`, `OpenIgnoredSensors`. `BlockListState` (`feature/profile/api`) holds `kind` (`BlockListKind`, Blocked or Muted), `accounts: PagedState<BlockListAccount, Unit>` (a `ProfileViewBasic`, `removable`, `onBluesky`), `removing: ImmutableSet<Did>`, `actionError: UiError?`; events `Back`, `LoadMore`, `Retry`, `Open(did)`, `Remove(did)`.
- **The shared UIs build text and decisions in composables.** `GearListUi` joins a row's kind (or type) and visibility with " · " and adds Retired where shown; `gearTypeLabel`, `gearKindLabel` and `gearMainTotalLabel` (`:app-common-ui` `GearLabels.kt`, used by five gear screens) look names up by token and format totals with `LocalUnitSystem`; a collection's count is a plural; the collections section shows unless the lists are empty, and explains itself while there are no collections. `BlockListUi` maps the kind to its title, empty text, remove label and load error in private extensions, shows the Bluesky note when `onBluesky && !removable`, and disables Remove while the account's did is in `removing`. `pagedRefreshStatus` and `pagedFooter` (`PagedList.kt`) decide what a paged list shows from `hasAnswer`, an extension of the uistate library.
- **Pictures.** Gear photos are the repo's blob URL for a Public item and `heldphoto://blob/<cid>` for any other (`GearRepository.photoUrl`), which the shared UI's Coil loader answers from the session's `HeldPhotos` (`HeldPhotoFetcher`). Avatars are the AppView's URLs. The shared avatar is a circle with the name's initial under the picture.
- **The iOS shell**: native pages title themselves with `pageTitle` (bar alone, `PageBar` beside the list); `ContentStateView` and `RefreshStatusView` draw a `ContentState` from `ContentShown`; `AppListRow` is the one list row; there is no image loading and no paged list.
- **What these pages open** (`GearScreen`, `GearCollectionScreen`, `EditGearScreen`, `EditGearCollectionScreen`, `IgnoredSensorsScreen`, `ProfileScreen`) are plain `Screen`s, not `SettingsPage`s, so the host already pushes them full width over Settings (`settingsPagesOnTop`).
- **Samples**: `GearListSamples.all` (the content-loading kinds and empty) and `BlockListSamples.all` (the nine paged kinds, all Blocked, no account removing, none on Bluesky).

## Goals / Non-Goals

**Goals:**

- The two pages, native, saying and offering exactly what the shared pages do, alone and beside the list.
- Three reusable pieces every later native list needs: remote images (with held photos), a paged list, and actions in a page's header.
- Every rule these pages follow written once in Kotlin.

**Non-Goals:**

- Image caching policy, prefetching, downsampling or blur for labelled media: Nuke's defaults, and avatars here carry no label.
- A Swift `PagedState` wrapper type: the list reads the Kotlin state through the rule below.
- Any other gear or profile screen.

## Decisions

### 1. Probe first

Throwaway Swift tests and a scratch Kotlin entry, run on the iPhone 17 simulator (iOS 26.0), and the generated Swift read; recorded in the roadmap under "Phase 4 probes (settings lists)":

- **Nuke 13.2.3 by Swift Package Manager** (swift-tools 6.0, iOS 15 minimum, no dependencies) resolves and builds in the app target under Swift 6 with main-actor default isolation, with no warning from it. `ImagePipeline.shared.image(for: ImageRequest(id:data:))` decoded an image from bytes given by a closure, which is how a `heldphoto:` URL is answered.
- **Held bytes reach Swift.** `IosApp.heldPhoto(url:)` returning a `ByteString?` exports as `ByteString?` (no stub); `toNSData` (kotlinx-io-bytestring, exported whole) gives Swift the bytes, which round-tripped equal. Signed out it returns nil.
- **A `PagedState` reads in Swift.** `items` is a Swift array whose elements cast to `BlockListAccount` (two of two in each sample with items; the library's `items` is a plain list, so the getter's array works); `refresh` and `append` are `LoadStatus` with `sealedType()`; `canLoadMore` and `endReached` are typed. `hasAnswer` is a library extension and isn't exported, as `ContentState`'s weren't: the rule moves into `:app-common-api` (decision 3).
- **`ProfileViewBasic`, `Did` and `Handle` are classes Swift reads** (`did.value`, `handle.value`, `displayName`, `avatar`); `shownName`, an extension property of `:app-common-api`, exports as `AppPackage.getShownName(_:)`. An `ImmutableSet<Did>` reads through `elements(as: Did.self)` as Swift `Did`s (bob's, in a new sample).

Neither page needs far more than its share: both use images, and only the block list pages. They stay one change.

### 2. Remote images: `RemoteImage` over NukeUI

`RemoteImage(url:)` in the design system wraps NukeUI's `LazyImage`: a `heldphoto:` URL becomes `ImageRequest(id: url, data:)` whose closure asks `IosApp.shared.heldPhoto(url:)` and throws when it has none; any other URL a plain request. The image fills its frame (`.scaledToFill`, clipped); a placeholder view the caller gives shows until it loads, on failure and for nil. `Avatar(url:name:size:)` is a circle with the name's initial on a tinted fill, the picture over it, as the shared `Avatar`; the gear photo is a 48-point rounded square on the system's secondary fill. Snapshots and previews have no URLs (the samples hold none), so they draw the placeholders, which is what they compare.

`AsyncImage` was set aside: it caches nothing beyond URLCache, can't be given bytes, and reloads as list rows are reused. The roadmap had already chosen Nuke. The held-photo entry is Kotlin's (`HeldPhotoBytes` in `:composeApp`'s iOS source, beside `IosHost`, through `IosApp`), so Swift never decides which photos are held.

### 3. `PagedShown`: one rule for what a paged list shows

`PagedShown.of(state, errorKind)` (and an overload over the parts, for lists that lay their own items, as `pagedFooter`'s) in `:app-common-api`, beside `ContentShown`, returns `above` (progress or failure, once answered), `showsEmpty`, `below` (the first page's or the next page's progress or failure), `firstPage` and `atEnd`: the branches of `pagedRefreshStatus` and `pagedFooter` as they are, which now ask it, keeping their test tags, so no screenshot or UI test changes. `PagedShown.Status` is a sealed interface (`Progress`, `Failure(error)`), which Swift switches over.

### 4. `PagedList` in the design system

`PagedList(state, of: Item.self, errorKind:, id:, retry:, loadMore:, top:, row:, empty:)` draws an `.insetGrouped` `List`:

- Before the first answer the whole page is the first page's progress or failure with Retry, centred as `ContentStateView` draws them, so a paged page and a loaded one look alike before they answer.
- Once answered: `top` (a screen's own rows, the block list's failed unblock), `above` as `ErrorMessage(layout: .row)` or a small progress row, the items in one section, `empty` when `showsEmpty`, and `below` (a small progress row, or the failure stacked with Retry). Status rows have no background or separator, so they read as part of the page, not as rows.
- **Load more**: each item row reports itself on screen (`onAppear`/`onDisappear` into a set of visible ids), and when the last item's id is visible and `canLoadMore`, `loadMore` runs (`onChange(of:initial:)`), the shared `LoadMoreAtEnd`'s rule. A flag that only remembered "the last row appeared" was rejected: after a page lands, the old last row is still on screen and would ask again at once. The presenter ignores a `LoadMore` while one is in flight, so a repeat costs nothing.
- Items are read with `state.items.compactMap { $0 as? Item }` once per body, as `ContentStateView` casts its value.

### 5. Actions beside a page's title

`pageTitle(_ title: Text, actions:)` gains an optional `@ViewBuilder actions`: alone they go in the stack's bar (`ToolbarItemGroup(placement: .topBarTrailing)`); in a column `PageBar` lays them at its trailing end, the title still centred over the column. The existing `pageTitle(_:)` is the same with no actions.

### 6. `AppItemRow`: a row with a picture and lines under its title

The design system's second row type, for lists of things rather than settings: a leading view (a photo or an avatar), the title, secondary lines under it, an optional summary and disclosure mark when it opens something, and an optional trailing control outside the row's own button (so Unblock takes its own taps; both use explicit button styles, since a `List` row with default-styled buttons fires them all). `AppListRow` stays for settings' rows. `AppSecondaryButton` gains `small:` (the list row's size) and an initialiser from Kotlin's text.

### 7. Text and conditions in Kotlin

- `:app-common-api` `GearTexts` (package `app.gear`): `type`, `kind` (falls back to the type), `mainTotal(main, totals, units)`, `distance`, `days`, `dives`, `hours`. `GearLabels.kt`'s seven composables resolve them, so every gear screen keeps its words.
- `:feature-gear-api` `GearListTexts.detail(row, showRetired)`, `total(row, units)`, `collectionCount(row)`; `GearLists.showsCollections` and `explainsCollections`. `GearRowItem` and the collections section ask them.
- `:feature-profile-api` `BlockListTexts.title(kind)`, `empty(kind)`, `remove(kind)`, `note(account)` (the Bluesky note, or null); `BlockListState.loadErrorKind` and `isRemoving(account)`. `BlockListUi` asks them and loses its private extensions.
- `:app-common-api` `avatarInitial(name)` beside `shownName`: what an avatar shows without its picture, which the Compose `AvatarFrame` now asks too.
- Tests for each branch in the `-api` modules' `commonTest`; `SharedTextNamesTest` gains the gear type names. A failed unblock is already `ErrorTexts.of(actionError)`.

### 8. The two views

- **`GearListView`**: `ContentStateView(state.content, as: GearLists.self, errorKind: .LoadGear)`, then a `List` (`.insetGrouped`): `RefreshStatusView`; the empty message with `AppButton(.gearAdd)` when `isEmpty`; Collections (when `showsCollections`): a row per collection (`AppListRow` with the count as its summary), a New collection row, and the help as the section's footer when `explainsCollections`; a section per active group headed by `GearTexts.type`; Retired. Items are `AppItemRow`s with the photo, `detail(row, showRetired: false)` and the total. `pageTitle(.gearTitle)` with Add (`AppIconButton(.add)`) and a More menu holding Ignored devices.
- **`BlockListView`**: `PagedList` over `accounts` with `top` = the action error, rows `AppItemRow` with `Avatar`, name (`getShownName`), handle (`LabelText.handle`), the note, and, when `removable`, `AppSecondaryButton(remove(kind), small: true)` disabled while `isRemoving`; `empty` = `empty(kind)` as secondary text. `pageTitle(title(kind))`.
- Both registered in `ScreenRegistry.app`; `KotlinTypes.swift` aliases their types.

### 9. Samples, previews, snapshots

- `BlockListSamples.variants` (`muted`, `mutedEmpty`, `actions`: bob removing, dave blocked on Bluesky, a failed unblock), apart from `all`, whose keys are `ContentLoadingConformanceTest`'s and the Android screenshot set's. Gear's samples are enough as they are.
- Previews: `SamplePages` over each page's samples and variants, and each page beside the list.
- `SettingsListsSnapshotTests`: every gear sample alone (the loaded list at its whole height), every block list sample and variant alone, and both loaded pages beside the list at 1,032 by 800, light and dark, with `everySampleHasASnapshot`. `DesignGallery` gains images (placeholder, avatar initials), item rows and the paged list's states (from the block list samples), and a header with actions; `DesignSnapshotTests` their references.
- Swift tests: `SettingsListsTextTests` (the texts and conditions on the samples, as the spec words them); `PagedListTests` (load more: asked for when the end of two items is on screen and more remain, not when the end was reached, and not with a hundred items until scrolled), using `PagedStates` from the fixtures.

### 10. Walking the pages

On the signed-in iPhone 17 Pro and iPad Air 11-inch (iOS 26.0): both lists alone and beside Settings' list (Gear beside, then Blocked, then Muted), each opened screen full width with back returning to the page beside the list, Add and More, unblocking or unmuting only what the walk itself created and putting it back. The iOS 18.4 build is launched to sign-in only (its simulators can't reach the local stack).

## Risks / Trade-offs

- **[A second page and a held photo depend on the test account]** → `PagedListTests` holds the load-more rule; if the account has no non-public gear photo, the held path rests on the probe and is listed as not verified.
- **[Nuke is a new dependency]** → Exact version, no transitive dependencies, only in the app target; phase 5 drops Coil from iOS, leaving one loader.
- **[Status rows without backgrounds in an inset grouped list]** → Looked at in the snapshots, light and dark, and on the iPad.
- **[iOS 18 not walked signed in]** → Swift tests on 18.4 and a launch; listed as not verified.

## Migration Plan

None: the shared pages stay on every other platform and as the fallback on iOS for anything not registered.
