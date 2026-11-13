# Native iOS UI on the existing Circuit presenters

Exploration from 2026-11-09. No code changed. It records why the iOS app moves from Compose Multiplatform to SwiftUI, the decisions made,
the target architecture and the phases. Each phase becomes its own change; the first is `ios-swift-export-spike`.

## Where the app is today

- The iOS app is one `ComposeUIViewController` inside a three-file SwiftUI shell (`iosApp/iosApp/`). The only Swift class Kotlin calls
  into is `SwiftFitDecoder`. Location, auth, the Keychain, the database, background tasks and sharing are Kotlin.
- `:composeApp` builds a static Objective-C framework that exports only `:core-activityfile` and `calf-ui`. Nothing uses SKIE, Swift
  export or KMP-NativeCoroutines. The deployment target is iOS 16.0. CI has no iOS job.
- The shared UI imitates iOS: a Compose-drawn navigation bar with Haze, Calf for the tab bar, sheets and alerts, and `*.ios.kt` actuals
  in `:ui-designsystem`.
- There are 51 screens and 3 SubCircuit sub-screens. iOS would stop using about 28,600 lines of Compose UI (`feature/*/ui` 14.0k,
  `:app-common-ui` 10.1k, `:ui-designsystem` 4.5k). Presenters are 13.3k lines with 22.6k lines of tests, all `commonMain`.
- Navigation is one `SaveableBackStack`; tabs switch with `resetRoot(save, restore)`. No Circuit overlays and no result passing: sheets,
  alerts and menus are driven by fields in each screen's `State`.
- Presenters read no composition locals. They do rely on Circuit's retained state, `rememberSaveable` and `rememberTextFieldState`.

## Decisions

| Decision | Choice |
|---|---|
| Kotlin to Swift interop | Swift export on Kotlin 2.5, where it is Beta. Not SKIE |
| Contract edits | Accepted where the spike proves them necessary. Presenter edits stay at imports and renamed calls |
| iOS minimum | iOS 18, from 16.0 |
| End state | Fully native: Compose renders nothing on iOS |
| UI framework | SwiftUI. UIKit only through representables (the map view, a zoomable image, the Compose fallback host) |
| Architecture on iOS | Circuit stays. Swift views are the `Ui` half only: no Swift view models, no TCA |

**Why not SKIE.** Its latest release (0.10.15) supports Kotlin 2.4.20 and the repo is on 2.4.21. It cannot take 2.5, it waits on
Touchlab for every Kotlin bump, and every Swift screen would be rewritten again on a later move to Swift export (`onEnum(of:)` to
`sealedType()`, different names for every type). Its advantage is real: the Objective-C bridge tolerates `TextFieldState`, sealed
interfaces and `ImmutableList` as they are, so it needs no contract edits. It is the fallback if the spike fails.

**What Swift export costs.** The kmp-dataresult library already guards its modules against three constructs Swift export could not
handle on Kotlin 2.4.21: `sealed interface` (generic subtypes of an erased parent protocol are rejected), `kotlinx.collections.immutable`
types (types inheriting `List`, `Set` or `Map` are ignored, KT-80416) and any Compose type in the reachable API, which breaks the iOS
build with no warning. The app's contracts have all three: 83 sealed interfaces, `ImmutableList` in 42 files, `TextFieldState` in 25.
Kotlin 2.4.20 mapped sealed hierarchies to Swift enums and the app's sealed interfaces are not generic, so some of this may no longer
apply. The spike measures it rather than assuming.

Other facts about Swift export on 2.5.0-Beta2, from JetBrains' docs and one published migration:
- The Gradle DSL is `export { swift { } }`. `api` dependencies are exported automatically, so an umbrella module with no code of its own
  keeps the exported surface small.
- Generic type parameters are erased to their upper bounds. `ContentState<T>` and `PagedState<T, M>` reach Swift untyped.
- A function returning a type from a hidden dependency compiles to a silent `fatalError()` in the generated Swift.
- Its bundled coroutine support needed an iOS 18 deployment target.
- Swift can implement Kotlin interfaces (2.4.20), which `SwiftFitDecoder` needs.
- Kotlin 2.5.0 stable is planned for December 2026.

## Target architecture

```
 Swift (iosApp)                               Kotlin
+-------------------------------+            +--------------------------------------------+
| RootView (login / session)    | <--flows-- | :app-host  (new, commonMain, JVM-tested)   |
| TabView                       |            |   AppHost: root state, SessionEffects      |
|   NavigationStack(path) x3    | <--path--> |   NavStack x3: implements Circuit Navigator|
|     ScreenView(record)        |            |   one Molecule composition per session:    |
|       registry[screen type]   | <--state-- |     key(record) { presenter.present() }    |
|         FeedView(state)       | --events-> |     per-record retained + saveable state   |
|         ... or fallback:      |            |   child hosts: SubCircuit, nested screens  |
|       ComposeScreen(record)   | ---------> | composeApp iosMain: one screen's Compose UI|
+-------------------------------+            +--------------------------------------------+
        ^ Swift export umbrella  :ios-export (no code; api -> contracts + :app-host)
```

- **Presenters run headless.** `:app-host` runs each visible record's presenter in a Molecule composition and publishes its state as a
  flow. Swift activates a record when its view appears and deactivates it when it disappears, so a covered screen's presenter leaves
  composition while its retained and saveable state survive until the record is popped. That is what `NavigableCircuitContent` does
  today, and what presenters are written and tested against. Molecule is already resolved transitively and publishes every client target.
- **Kotlin owns navigation.** Each tab has a Kotlin stack that implements Circuit's `Navigator`, so presenters call `goTo`, `pop` and
  `popUntil` unchanged. SwiftUI binds `NavigationStack(path:)` to it, and a swipe back writes the shorter path to Kotlin. Tabs become
  three real stacks. Sheets, alerts and menus stay driven by `State`.
- **Presenter factories without `Circuit`.** The `Circuit` class is in `circuit-foundation`, which depends on Compose UI, as do
  `circuit-backstack` and `circuitx-subcircuit`. The host resolves presenters from Metro's multibound `Set<Presenter.Factory>` and keeps
  its own back stack. `circuit-retained` and `circuit-runtime-presenter` need only the Compose runtime.
- **Coexistence.** A record with no Swift view yet renders through a per-screen `ComposeUIViewController` running `CircuitContent` for
  that one screen. The app is complete at every commit and screens move one at a time, in any order.

The alternative, keeping the Compose shell and embedding SwiftUI views in it, was rejected: the Compose-drawn bar and transitions would
stay until a final big-bang cutover, and native views inside Compose have z-order and gesture problems the design system already works
around.

## TextFieldState

`TextFieldState` could not stay in exported contracts. What phase 1 built keeps the presenters' code as it was:

- An app-owned type with the same simple name, in its own module `:core-textfield` (package `textfield`): an `expect class
  TextFieldState`, with `expect` versions of `rememberTextFieldState`, `setTextAndPlaceCursorAtEnd` and `clearText`, and a
  `textFieldStateOf(initial)` factory. Its own module because both the contracts and the design system's `AppTextField` take it, and
  neither may depend on the other.
- On Android, desktop and web it is an `actual typealias` to Compose's `TextFieldState`. Behaviour is unchanged, `AppTextField` takes it
  as before, and JVM tests keep editing it with Compose's API.
- On iOS it is an `actual class` that wraps a Compose `TextFieldState` until Compose leaves iOS, not a second copy of the text: the
  Compose field on iOS edits the same object as before, so typing, the cursor, selection and restoring behave as they did. Swift reads
  and sets `value`, a `String`. `composeState`, typed `Any`, is what the iOS `AppTextField` casts back; it goes in phase 5, when the
  class's inside becomes plain snapshot state. A two-way copy between two text states on every keystroke, the first idea, is how
  characters get dropped and cursors jump.
- Presenters, contracts and fixtures changed imports only, plus `TextFieldState()` to `textFieldStateOf()`. Compose foundation left the
  contract modules. detekt's `UseAppTextFieldState` refuses Compose's four names outside test files, since a missed import compiles
  everywhere but iOS.
- Still to do in phase 2: what Swift needs to observe a field (a flow of Kotlin-side changes such as a seed, a clear or a restore after a
  failed send, behind an `@Observable` model and a `TextField` binding), and SwiftUI equivalents of the rules that live only in
  Compose UI today (`DigitsOnly`, maximum length, keyboard type).

Rejected: an `XChanged(String)` event per field (51 fields, new presenter logic, and the reason the `UseTextFieldState` rule exists),
and hiding the Compose type behind per-field accessor functions (51 shims, and it depends on hidden-type behaviour that fails silently).

## Phases

Each phase leaves a shippable app.

0. **Toolchain and spike** (`ios-swift-export-spike`). Kotlin 2.5.0-Beta2; Swift export replaces the Objective-C framework for the
   existing three Swift files with the UI unchanged; three real contracts are probed from Swift. The exit is a running app and a written
   list of which contract constructs must change. If Swift export fails the gate, the fallback is SKIE on Kotlin 2.4.20, and everything
   from phase 2 on is unchanged.
1. **Swift-clean contracts** (`ios-swift-clean-contracts`). The `TextFieldState` type in `:core-textfield`; `PlaceNameFields.self`
   renamed; `LocalSettingsPane` out of `:app-common-api`; every contract module exported to Swift, with a second constructor on the four
   events that took an immutable collection; two guards, `checkSwiftExportSources` in `check` and `checkSwiftExportStubs` in Xcode's
   build; a macOS CI job. No visible change. Left for the phase that needs them: `ImmutableList` and `ImmutableSet` fields, which Swift
   reads as Kotlin collection protocols with untyped elements, become `List` and `Set` or get one Swift wrapper (decide on the first
   native list screen); and moving model types out of `:core-data`, which is about the size of the export, not about making it work.
2. **Headless host, native shell, first native screen** (`ios-native-shell`). `:app-host` runs presenters in Molecule with no UI and
   owns a `Navigator` stack per tab; `SessionRunner` holds what a session shows and its background work for both shells; the iOS app's
   root is SwiftUI (`RootView`, a `TabView`, a `NavigationStack` per tab bound to the Kotlin stack), with a screen registry keyed by
   screen name and every unregistered screen drawn by its Compose UI in a view controller of its own; `BackSwipe` keeps the edge swipe
   on those; Login is native, over a Swift `AppTextField` and `TextFieldModel`; a 20-string catalog held to `strings.xml` by
   `checkIosStrings`; Swift 6 mode and a Swift Testing target the `ios` CI job runs. Settings shows one page at a time on iPad until
   it is native. Left for phase 3: the generated catalog, and the design system the two pieces made here (`AppTextField`,
   `AppButton`) start.
3. **Native foundations** (`ios-native-foundations`). The iOS String Catalog (2,104 entries), `AppIcon.swift` and an asset catalog
   of the 82 vendored drawables are generated from `strings.xml`, `AppIcon.kt` and the drawables by `generateIosResources`,
   committed, and held to their sources by `checkIosResources` in `check`; Swift names a string by Xcode's generated symbol. Text
   that follows a rule is an `AppText` chosen in `:app-common-api` (`StatText`, `LabelText`, `ErrorTexts`, `SportIconNames`,
   `ContentShown`, and login's `LoginTexts` in its own `-api`), which Compose resolves in a composable and Swift through a
   `TextSource` over the catalog. The Swift design system (`iosApp/iosApp/Design`), previews and snapshot tests from `:app-fixtures`,
   which debug builds export. Left for phase 4: the other composables that build text (dates and times, the dive and stat tables),
   each moved onto `AppText` with the feature that shows it.
4. **Features, one change each**, cheapest infrastructure first: lists and forms (settings, profile lists, gear, invites, moderation,
   uploads, search, post pages); the feed (card, comments, posts, the sub-screens); maps; charts; record and import.
   - **Settings' list** (`ios-native-settings`). `SettingsView` is the first native screen in a tab's stack: the system's bar and back
     button, a grouped `List` of `AppListRow`s, row summaries and the units note chosen in Kotlin (`SettingsTexts`,
     `LabelText.visibility`). In a window at least 700 points wide and 500 tall (every iPad at full screen, either way up; never an iPhone) the list shows
     beside its page again, so
     ui/settings-layout's iOS exception is gone. The presenter host decides it: a UI reports its stack's size, and `TabStack.shown`
     carries a `SettingsSplit` (which record is Settings, which page shows beside it, which row is marked) and the shortened list of
     records to push; the page shown until a row is chosen is a record beside the stack (`HostRecord.paneDefault`), never on it. The
     Swift side is two plain columns in Settings' destination (`SettingsColumns`). Settings' pages are still the shared UI's, in the
     second column when wide. Swift reads a contract's immutable collections through `elements(as:)`. Left for later changes: each
     settings page's native view, and with the first of them how a native page gets a bar of its own in the column.
   - **The first settings pages** (`ios-native-settings-pages`). Activity defaults and Segments & groups are SwiftUI forms
     (`ActivityDefaultsView`, `SegmentsAndGroupsView`): menu pickers of visibilities, switches and a segmented choice, in the system's
     grouped form. A native page titles itself with `pageTitle`: the stack's bar when alone, and a column header (`PageBar`) when
     beside the list, where a navigation title never shows (the probe below). What they say and when each choice is offered or can be
     changed is Kotlin's (`VisibilityTexts`, `LabelText.detailCategory`, `SettingsTexts`' page notes, getters on the two states), which
     the Compose pages ask too. A choice among an enum's values reaches Swift as a list of contract objects holding the value
     (`GroupOption`). Left for later changes: the other settings pages (zones and threshold history with charts, privacy zones with a
     map, Account, Leave), and a back button in `PageBar` with the first native page that is deeper than the pane's first.
   - **The settings lists** (`ios-native-settings-lists`). Gear and the Blocked and Muted accounts lists are inset grouped lists
     (`GearListView`, `BlockListView`), the first native pages with pictures and the first loaded page by page. The design system gained
     `RemoteImage`, `Avatar` and `Thumbnail` over Nuke and NukeUI (Swift Package Manager, 13.2.3), which answer a `heldphoto:` URL from
     the session's held bytes (`IosApp.heldPhoto`); `PagedList` over a `PagedState`, laid out from `PagedShown` in `:app-common-api`, the
     rule the shared `pagedRefreshStatus` and `pagedFooter` now ask too, which asks for the next page once the last item is on screen;
     `AppItemRow` for a thing with a picture and a trailing control; and actions beside a page's title (`pageTitle(_:actions:)`). What the
     pages say and decide is Kotlin's (`GearTexts`, which `GearLabels.kt` resolves for every gear screen, `GearListTexts`,
     `BlockListTexts`, `avatarInitial`, getters on the states). Left for later changes: the other gear screens, profiles and the
     other paged lists, which can build on `PagedList` and `AppItemRow`.
   - **Account and Leave** (`ios-native-account`). The Account page and Leave Activity Tracker are inset grouped lists (`AccountView`,
     `LeaveView`), with the first system alerts (ending the session with work pending, and whether a shared export was kept), the first
     file handed to another app (`ArchiveShare`: Kotlin zips the export to a file through `IosApp.writeArchive`, a `suspend` entry Swift
     awaits, and `UIActivityViewController` offers it), and the first time of day (`AppText.Time`, which each UI formats on the device's
     clock). Leave opened from Account beside Settings' list is the first deeper native page in the column: `pageTitle(_:back:canGoBack:)`
     draws a back button in `PageBar` from `HostRecord.paneRoot` (`\.paneRoot`), and a running leave has no way back, alone or beside the
     list. What the pages say and decide is Kotlin's (`AccountTexts`, `LeaveTexts`, `offersSignInAgain`, `canLeave(typed)`,
     `canGoBack`, `showsDeviceWork`), and an outdated grant's missing features reach Swift as text, since its set of enums can't. Left for
     later changes: the "left on another device" screen (`LeftElsewhereScreen`), Training zones, threshold history and Privacy zones.
   - **The gear pages** (`ios-native-gear-pages`). A gear collection and Ignored devices are inset grouped lists (`GearCollectionView`,
     `IgnoredSensorsView`), opened full width over Settings from Gear. The collection has Edit and Delete in its bar and asks before
     deleting in the system's alert; Ignored devices is the first loaded content that is itself a list, which Swift reads from a typed
     getter (`IgnoredSensorsState.sensors`) and `ContentStateView`'s form without a value, since the erased value can't carry a list (the
     probe below). What the pages say and decide is Kotlin's (`LinkedSensorTexts` over `deviceName`, now in `:app-common-api`, which the
     gear page's linked devices ask too; `GearCollectionState.title`, `showsActions`, `actionsEnabled`). Left for later changes: the gear
     item page (`GearScreen`), with or right after the native activity card it lists its activities with, and its components, mount
     sheet and unmount date picker; and the gear editors (`EditGearScreen`, `EditGearCollectionScreen`).
   - **The follow lists** (`ios-native-follow-lists`). Followers, Following and Follow requests are inset grouped lists
     (`FollowListView`, `FollowRequestsView`), the first native pages pushed over a shared page that isn't Settings (the profile, still
     the shared UI's) and the first with a subtitle under the title (`pageTitle(_:subtitle:)`: the system's navigation subtitle on iOS
     26, a two-line principal title before it, a second line in `PageBar`), a segmented choice (SwiftUI's `Picker` over the contract
     enum's `allCases`) and a row with two answers (`AppButton(small:)` beside `AppSecondaryButton(small:)`). What the pages say and
     decide is Kotlin's (`FollowListTexts`, `FollowRequestsTexts`, `FollowListState.isRemoving`, `FollowRequestsState.accounts`,
     `canAnswer`, `offersDecline`, the notes' `shows…`), and so is the notice over a list that its action failed or the session must
     sign in again (`SignInAgainNotice` in `:app-common-api`, which the shared `signInAgainItem` asks for Follow requests, Activity
     invites and Add people). Left for later changes: the profile itself, Search with the segment browse screen, and the invites pages,
     which can build on `SignInAgainNotice`.
   - **The zones contracts** (`ios-zones-contracts`). No page goes native. (It said Training zones and Privacy zones wait for native
     charts and a map; they don't, and are native since `ios-native-zones-pages`: only threshold history draws a chart.) Their contracts now read from Swift without a crash or an opaque element: the zone kinds are a list of
     `ZoneKindOption` (with `offers(kind)`, which the shared pages ask), `Zone` and `PrivacyZone` have Swift types because `ZoneTexts`'
     functions name them (a zone's label and a privacy zone's name, which the shared pages ask too), and `shownRows` is a copy.
     `ZonesContractsTests` reads every blocker typed over the samples. Left for later changes: the pages themselves, and with them a
     zone row's name and range as `AppText`.
   - **The gear editors** (`ios-native-gear-editors`). The gear and collection forms are grouped `Form`s (`EditGearView`,
     `EditGearCollectionView`), the first native forms built on text fields: `AppTextField` gained a row layout (label leading, text
     trailing) and a several-line one, with each field's limits from Kotlin (`EditGearLimits`); menu choices are `AppPickerRow`, Activity
     defaults' row moved into the design system. Save is in the bar and a failed save at the top. The first native photo pick is
     `PhotosPicker` with a compatible encoding: Swift hands the bytes to `IosApp.compressPhoto`, the shared `compressPhoto`, and sends the
     result in the form's own `PhotoPicked` (a HEIC Skia can't read is tried again as UIKit's JPEG). The first native date is a calendar
     of past days in a popover, over `CalendarDay`. Sports and default-for are chosen in sheets with a stack of their own, by Kotlin's
     groups (`SportTypeGroups`, whose nesting rule `sportTypeList` now asks too), collections are ticked in place, and a collection's items
     are reordered by dragging with the form held in edit mode, VoiceOver keeping the shared Move up, Move down and Remove. What the forms
     say and decide is Kotlin's (`EditGearTexts`, getters on both states, a joined list as `AppText.joined`). Left for later changes: the
     gear item page, the profile editor and the activity forms, which can reuse the photo pick, the form rows and the type sheets.
   - **The invite lists** (`ios-native-invite-lists`). Activity invites and Add people are inset grouped lists (`ActivityInvitesView`,
     pushed over the shared profile, and `AddPeopleView`, pushed over the shared activity page), built as Follow requests is: a
     segmented choice over the contract enum's `allCases`, `SignInAgainNotice` over the rows, `AppItemRow`s with two small buttons, the
     Declined note as the section's footer, and Add people's two lists as headed sections. The first native row to show an activity's
     distance and time, from the environment's units. What the pages say and decide is Kotlin's (`ActivityInvitesTexts`: an invite's
     title or type, who added the athlete, its distance and time joined by a shared string; `AddPeopleTexts`; typed `invites`,
     `invitees` and `followers` and `canAnswer`, `offersDecline`, `canAdd`, `canRemove` and `full` on the states), which the shared
     pages and `InviteSummary` ask too. Left for later changes: accepting an invite (`AcceptInviteScreen`), which waits for the native
     route preview and visibility form, and the profile and activity page that open these.
   - **The upload queue** (`ios-native-uploads`). Uploads is an inset grouped list (`UploadsView`) pushed over the feed, the first native
     page whose loaded content is a `:core-data` model of plain lists (read directly: an empty `emptyList()` is safe, probe below), with
     two system alerts bound to the state's questions (cancel, cancel the rest of an import). The first shared length of time:
     `DurationText.approximate` in `:app-common-api`, a plural rounded to the largest unit ("40 min", "3 h", "1 day"), not a new
     `AppText` case, since no device setting changes it. The first day words a native page shows ("Today at 7:00 AM", "until tomorrow
     at"): `DayText`, which `startTime` and `untilText` now ask too, decides them from the current time and zone each UI passes, and
     Swift's are a `ShownClock` in the environment (`\.clock`), which also formats the time of day in its zone, so previews and
     snapshots fix both (`Samples.clock`). What the page says and decides is Kotlin's (`UploadsTexts`, `UploadImportTexts`, getters on
     `UploadsState`), which the Compose page asks. Left for later changes: the feed's upload row, and a general date `AppText` with the
     feed's start times, which read on the activity's own UTC offset.
   - **The segment lists** (`ios-native-segment-lists`). Starred segments, a segment's leaderboard and Mayors board and a dive site's
     Regulars are inset grouped lists (`StarredSegmentsView`, over the profile; `SegmentLeaderboardView` and `SegmentMayorsView`, over the
     segment page; `DiveSiteRegularsView`, over the site page; all three of whose own pages, with their maps and charts, stay the shared
     UI's), the first native pages with filters over a list: segmented choices and filter menus (`AppMenuChip`, a capsule opening an
     inline picker, filled while it narrows the list) that wrap onto lines (`FlowRow`, a small `Layout`, as a list cell clips a row that
     scrolls sideways). A new choice starts its board again, so `PagedList` keeps a screen's top rows over a first page (`keepsTop:`) and
     shows the viewer's own place after the items (`after:`). A board's choices are Kotlin's `BoardChoice` (options with labels, which is
     chosen, `offersMenu`, `narrows`; `RankBoardTexts`, `SegmentBoardTexts`, `LeaderboardBracketTexts` with the weight classes in the viewer's units,
     `DiveSiteRegularsTexts`), and so are an entry's rank, accent, day and count, a starred segment's line, and two shared texts the
     composables built: a calendar date written out (`DateText`, which `longDate` and `effortDateLabel` now resolve) and a segment's type
     (`SegmentTexts`, which `segmentTypeLabel` resolves). The leaderboard's time is `Stats.duration`, StatText's clock form, so no new
     length of time was needed. Left for later changes: the segment page, its map, segment browse and the dive site page, which wait for
     native maps and charts.
   - **Edit profile** (`ios-native-edit-profile`). Edit profile is a grouped `Form` (`EditProfileView`) pushed over the shared profile,
     the largest native form yet: the photo, the display name and bio with the shared counters, Approve followers, and a section per
     detail group ending in who may see it (`AppPickerRow` labelled with what the group covers), its Followers note and, under the group
     it is about, the now-hidden warning. Save is in the bar and the shared `SignInAgainNotice` at the top. The photo pick is the gear
     form's, made generic (`PhotoPickOf`): `AvatarPick` hands the bytes to `IosApp.compressAvatar`, the shared 512 px JPEG. The country is
     chosen in a searchable sheet whose order and match are Kotlin's (`CountrySearch`, the codes moved into `:feature-profile-api`), over
     names and folded keys each UI makes with its platform; the birthdate's text reaches the gear form's calendar popover as a `LocalDate`
     (`birthDay`, `BirthDateChanged(date:)`); gender is a menu over `GenderChoices`' tokens, Self-describe showing the athlete's words.
     `nowHidden`, an enum list, is asked per group (`isNowHidden(group)`), and `description`, which Swift export leaves out, is also
     `bio`. Limits count and hold Save back without refusing typing, as shared. What the form says and decides is Kotlin's
     (`EditProfileTexts`, `EditProfileLimits`, getters on the state), which the Compose form asks. Left for later changes: the profile
     itself, with the feed's card.
   - **The zones pages** (`ios-native-zones-pages`). Training zones is an inset grouped list (`TrainingZonesView`): who sees the zones,
     threshold history (still the shared page, the only one of them with a chart), each set of zones under its heading with Edit, its
     threshold lines and its zones, and Add a sport as a menu. The default and a sport's zones are grouped forms (`ZonesView`,
     `SportZonesView`) with Save in the bar, each threshold a form row from the `ZoneThreshold` enum (its token, digits-only rule and
     length), a sport's blank one hinting its default (`AppTextField`'s new `hint`), each model an `AppPickerRow` over its zones; beside
     Settings' list they are the column's second page, with a back button to Training zones. Privacy zones is a grouped form
     (`PrivacyZonesView`): each zone's radius as a menu labelled by its name, removed by a swipe, a zone added where a phone is, and the
     hidden start and end; no centre is shown, so no map is needed. A digits-only field now keeps an edit's digits rather than refusing
     it whole, so a digit typed right after a letter is kept (the gear weight's open item). What the pages say and decide is Kotlin's
     (`ZoneTexts`' name, range, heading, model, basis and missing input, `TrainingZonesTexts` with its `ZoneThresholdLine`s,
     `SportZonesTexts`, `PrivacyZonesTexts`, `ZoneNameText` in `:app-common-api`, which the activity charts' time in zone asks too, and
     getters on the four states), which the Compose pages ask too. Left for later changes: threshold history, with the native charts.
   - **The activity card** (`ios-native-activity-card`). A native card (`ActivityCardView`, a list row in a section of its own) draws
     an activity as the shared `ActivityCard` does: the author line with a non-public tier's badge, the start on the activity's own
     clock and whom it was done with, the labels' notices, the icon and title, a dive's site, two lines of description, the figures, the
     owner's locks, the route and photos in one sideways row (`MediaStrip`: blurred under a label until revealed, placeholders when taken
     down) and Like with the counts. Its route is a line on a `Canvas` (`RouteSketch`), placed by Kotlin (`RouteShape`: the served line
     smoothed and fitted to the tile, with its breaks and markers), never a map. What the card says and decides is Kotlin's
     (`ActivityCardContent`, over `StartText`, an `AppText.Time` that can carry a UTC offset, `CardStatsText`, `ModerationTexts`,
     `VisibilityTexts.badge` and `lock`), which the Compose card asks too. The first page drawn with it is a batch import's
     (`ImportBatchView`, over the shared feed), its months Kotlin's (`ImportBatchTexts`), laid out by `PagedList`'s new `sections:`. The
     feed itself isn't native: its filter bar is a SubCircuit, whose presenter the presenter host can't run, and its posts, import cards,
     upload rows, prompts and sheets aren't native either. Left for later changes: the feed (with SubCircuits in the host), and the
     profile, the gear item page, a dive site's page and Moderation, which can list activities with this card now.
   - **Sub-screens in the host** (`ios-host-subcircuits`). The presenter host runs SubCircuit sub-screens: a native view asks a shown
     record for the sub-screen its state embeds (`HostRecord.sub(screen, outerEventSink)`, the screen as an object since Swift has no
     `SubScreen` type) and draws the `HostSubRecord`'s state through `SubScreenView`, mapping outer events to the screen's events as the
     shared UI does. The sub-screen's presenter comes from the session graph's `SubPresenterFactory` set and runs inside its record's,
     so its state is kept and dropped with the record's; a dismissed sheet closes its sub-record, and an unequal screen of its class
     replaces it. The Compose UIs keep `SubCircuitContent`. The first native sub-screen is the feed's filter bar (`FeedFilterBar`: the
     chips, the date menu, Clear, a plain type sheet and a custom range sheet), its words and marks Kotlin's (`FeedFilterTexts`,
     `TypePickerState.selectionOf`), which the shared bar asks too; no page places it, since the feed is still the shared UI's. Left for
     later changes: the feed, with the bar, and the report and place-name sheets, which can run through the host now.
   - **The gear item page** (`ios-native-gear-item`). The gear item page is an inset grouped list (`GearItemView`): the photo beside the
     name, kind, brand and model and the owner's notes; the owner's collections ticked in place (the shared page's chips and Add menu);
     Components with each component's line and since when, the owner's Replace and Unmount in a `Menu` on its row and Mount a component
     under them, Previous and Mounted on; the linked devices and profiles with Unlink; the totals with the starting usage as the
     section's footer; the breakdowns; then the activities as native cards through `PagedList` with `keepsTop`, so the item stays while
     its first page of activities loads. Edit is in the bar. The mount sheet and the unmount calendar are sheets with stacks of their own
     (`MountSheet`, `UnmountSheet`, a graphical `DatePicker` from the day after the mount). What the page says and decides is Kotlin's
     (`GearPageTexts` with its `GearFigure` rows, getters on `GearState`: `showsEdit`, `showsCollections`, `showsComponents`,
     `managesComponents`, `mountEnabled`, `componentsError`, `activitiesError`, `authorClickable`, `unmountFirstDay`,
     `unmountInitialDay`), which the Compose page asks too.
   - **The profile** (`ios-native-profile`). The profile (`ProfilePage`, over `ProfileScreen`'s state) is a grouped list: the header
     (avatar blurred under its label until tapped, handle, the labels' notices, bio, the location and facts lines, the counts opening
     the follow lists), one row of actions (the own entries as navigation rows, Follow, Following or Requested with progress while
     written, Unblock, or the Bluesky note), Settings or a menu with Mute and Block (after the shared confirmation) in the bar, and the
     activities as native cards. What it says and decides is Kotlin's (`ProfileTexts`, getters on `ProfileState`), which `ProfileUi`
     asks too. Left out natively until the host and a post card allow them: Report (its sheet is a SubCircuit) and the Posts tab
     (posts have no native card).
   - **Posts** (`ios-native-post-card`). A native post card (`PostCardView`, drawn from Kotlin's `PostCardContent`, which the shared
     `PostCard` asks too: reposted by, author and audience, the proposal with its votes, the quoted post one level deep or "Post
     unavailable", Like, Repost, Quote and the counts, Delete on the viewer's own quote), the post page (`PostPage`, with the likers,
     reposters and Show quotes, and the first native comment list, `CommentsSection`, from `CommentsTexts.rows`, whose rule the shared
     list asks for each comment's actions), the quotes list (`PostQuotesView`), the quote sheet and delete confirmation (`.quoteSheet`,
     a SwiftUI sheet over `QuoteFields`, a plain state, not a sub-screen), and the profile's Posts tab again (`ProfilePosts`). Report is
     still left out of the card, the comments and the quotes list, waiting for the native report sheet (`ios-native-report-sheet`).
   - **The report sheet** (`ios-native-report-sheet`). The first sheet run through the host's sub-screens: `ReportSheet`, a form in
     its own stack (the reasons as checked rows, the note, what moderators may see, Cancel and Send, the failure; then the thanks,
     Block and Done), shown from any page's `Reporting` by the `reporting(_:)` modifier, which reads the page's record from the
     environment (`\.hostRecord`) and closes the sub-record once the sheet has gone. The profile's menu offers Report
     (`ProfileState.reportEvent`). The place-name sheet (`ProposePlaceNameSheet`, `placeNames(_:)`) is native the same way and placed
     in no page yet. Their words are Kotlin's (`ReportTexts`, `ProposePlaceNameTexts`), which the Compose sheets ask too.
   - **Report on posts** (`ios-native-post-report`). The native post card offers Report on someone else's post (`canReport:`,
     sending the shared `PostEvent.Report`) on the post page, in the quotes list and on the profile's Posts tab, and the native
     comment list on others' comments (`CommentsSection(state:reporting:)`, sending `Reporting.reportComment`, which the Compose list
     asks too); the post page and quotes list show the native report sheet with `reporting(_:)`.
   - **The feed** (`ios-native-feed`). The Feed tab's root is an inset grouped list (`FeedPage`) under a large title naming the scope,
     which is the scopes' menu (`toolbarTitleMenu`), with Add in the bar: the filter bar as its first row (the host's sub-screen,
     `FeedFilterBar`), the upload rows (`FeedUploadRows`), a refresh's failure and a failed action, the two prompts, and the timeline as
     native activity cards, import cards (`FeedImportCard`) and post cards in its order, or the empty message with Clear filters. The
     first load's progress or failure shows under the bar, the list pulls to refresh (`pullToRefresh`, the system's spinner held until
     the state's refresh ends), and the quote and report sheets are the posts'. What it says is Kotlin's (`FeedTexts`,
     `FeedUploadTexts`, `ImportCardTexts`, `FeedState.showsEmpty` and `keyOf`), which the Compose feed asks too. Left for later changes: paging the
     timeline (one page on every platform) and the bar's type sheet's search and collapsed sports. A feed post's card offers Report
     (`canReport:`) since the post-report merge.
     A large title shows no title menu, so the scopes are also a chip leading the filters.
   - **Search** (`ios-native-search`). The Search tab's root is native (`AthleteSearchView`): the large title by mode ("Find athletes",
     "Search"), Athletes and Segments as a segmented choice (the list's first row, over the embed in Segments mode), and in Athletes
     mode the system's search field (`searchable`, bound to a `TextFieldModel` over the presenter's query) over the accounts as
     `AppItemRow`s through `PagedList` with `keepsTop`, the prompt while nothing is typed and the no-match message. Segments mode embeds
     the shared segment browse screen under the choice: a Compose view controller (`IosApp.segmentBrowseViewController(record:)`, the
     browse presenter running in its composition as `AthleteSearchUi` runs it) whose navigation goes to the Search record's latest state
     as `GoTo`, kept with the page so the map and query survive switching modes. The choice after leaving on another device is native
     too (`LeftElsewhereView`), in a stack of its own `SessionView` gives it so its title shows. What they say is Kotlin's
     (`AthleteSearchTexts`, `LeftElsewhereTexts`), which the Compose screens ask too. Left for later changes: native segment browse, with
     the native maps.
   - **The route map and the dive site page** (`ios-native-map`). A native map (`RouteMapView`, MapKit's SwiftUI `Map`) draws a route
     as the shared map does: each piece smoothed, in the accent colour on a white casing, the breaks' dashed connectors and markers, and
     the start and end where the viewer's map shows them, framed north-up as Kotlin frames it. What it shows is Kotlin's (`RouteMap` in
     `:app-common-api`: the pieces, the markers with the ends others see, the bounds, and `MapFrame`, the Web Mercator camera with the
     shared margin and closest zoom 16), which the Compose map, its line preview and the feed's map images ask too (the feed's images
     its framing as well); `MapCamera` turns the frame into MapKit's rect. It is static by default (a drag scrolls the page), opens an
     enlarged map with `expand:`, and pans and zooms north-up with Recenter when `interactive`. Outside a session (`\.liveMaps`, set by
     `SessionView`) it draws `RouteSketch` from `RouteMap.sketch`, so previews and snapshots fetch nothing; `RouteShape`'s ends are
     optional so the stand-in honours hidden ends. The first page with it is the dive site page (`DiveSitePage`): the outline on the map,
     the about lines, the Regulars card, the conditions, the names (`PlaceNamesSection`, ready for the segment page) with the native
     name sheet (`placeNames(_:)`), and the dives as native cards with no Like (`ActivityCardView(likeable:)`). What it says and decides is
     Kotlin's (`DiveSiteTexts`; `PlaceTexts` and `PlaceNameTexts` in `:app-common-api`, shared with the segment page; `DiveSiteState`'s
     `routeMap`, `proposals`, `history`, `viewerDid` and `revealed`; `PlaceNameFields.isOwn`), which the Compose pages ask too. Left for
     later changes: the segment page (its elevation chart), the enlarged maps and segment browse (interactive and many-segment maps), the
     activity page and the invite's route preview, and the highlighted stretch a new segment shows.
   - **The interactive maps** (`ios-native-interactive-maps`). A segment's enlarged map is native (`SegmentMapView`): the line on
     `RouteMapView(height: nil, interactive: true)` filling the page, pan and zoom north-up with Recenter, the data credit over the
     top-leading corner (`CreditBox`, `SegmentCreditTexts`) with the line framed below it. Segment browse is native (`SegmentBrowseView`):
     the name field, This area and Everywhere while a name is searched over an area, the sport menu chip, and the list beside the map
     from 600 points wide and under it on a phone, on a MapKit map of many segments (`SegmentsMapView`) drawing Kotlin's `SegmentsMap`
     (the lines in drawing order, lifts dashed under the runs and the selected run on top, `nearest` for a tap, since SwiftUI's `Map`
     has no line hit test, `sketch` for the stand-in, and `MapViewport.ofDegrees` for what it reports). The Search tab shows it in
     Segments mode as a screen its record embeds (`HostRecord.embed`, drawn by `EmbeddedScreenView`): the browse presenter runs inside
     the Search record's and navigates its stack, so the Compose embed (`SegmentBrowseViewController`) is gone. Accept invite is native
     (`AcceptInviteView`): the summary, the route as `RouteSketch`, the intro and overlap warning, the title, the visibility sections
     Activity defaults also places (`VisibilityFormSections`), sign in again, Decline and Accept. What they say and decide is Kotlin's
     (`SegmentBrowseTexts` with `SegmentBrowseState.listShows`, `offersScope`, `offersSearchEverywhere`; `SegmentCreditTexts`;
     `AcceptInviteTexts` with `AcceptInviteState.busy`, `offersSignInAgain`, `routeMap`; `SegmentMapContent.routeMap`), which the Compose
     pages ask too. Left for later changes: the create-segment page and its highlighted stretch, the activity's route viewer and page.
   - **Charts and the segment page** (`ios-native-charts`). A native line chart (`LineChartView`, Swift Charts) draws Kotlin's
     `LineChart` (`:app-common-api`, `app.chart`): the points, filled beneath and broken at gaps, the y axis with a labelled gridline
     per step, the x labels, the band stops it is tinted by (`GradeColors`, the grade bands' light and dark colours), a readout per
     point and the description VoiceOver reads; a tap or sideways drag shows the nearest point's readout and leaves it, and a mostly
     vertical drag scrolls the page (`ChartScrub`). The generic rules moved out of Compose into `:core-data` (`data.chart`:
     `ChartAxis`, the y axis's round steps, and `BandStop.runs`, the grade bands' runs), which `TimeSeriesChart` and `GradeBands` ask
     too, and a page's choices into its `-api` (`SegmentProfile.chart(units)`, which the Compose profile chart now draws). The first page
     with it is the segment page (`SegmentPage`): Star in the bar, the static map expanding to the shared enlarged map, the about lines,
     the profile, the credit, the status, Leaderboard and the Mayor card (`LeaderCardView`, shared with the dive site's Regulars), the
     names, and the viewer's efforts (`EffortRowView`). What it says is Kotlin's (`SegmentPageTexts`; `EffortTexts` in
     `:app-common-api`, which the shared effort row asks too; `SegmentTexts.kindIcon`; `SegmentState`'s `routeMap`, `chart`, `isLift`
     and `offersLeaderboard`); a `<string-array>` item is `AppText.named("<array>_<index>")` in both UIs. Left for later changes:
     threshold history (a step chart with dots and value labels, and sparklines), the activity page's charts and the import preview's.
   - **The activity page** (`ios-native-activity-page`, archived). `ActivityPage` draws `ActivityDetailScreen` in inset grouped sections: the
     header (the labels' notices, the author and tier badge, the start and its lock, the title and type, the dive site, the
     description, the other recordings and members, the combine banner, errors and notices), the route on the static native map
     expanding to the shared enlarged map with the map's lock and the photos (`MediaStrip(showsRoute: false)`), the hero, likes and who
     liked it, Together with, the training load, a section per metric card (its headline, each line as a native `LineChartView` with
     its average dashed and a pace line inverted, time in zones as bars or Set thresholds, its rows, the laps table), the segment
     efforts (indented by nesting) and lifts, the stats list with gear rows opening their gear, and the comments (`CommentsSection`).
     From a regular width of 840 points a 400-point pane (map, header, hero, likes, group, avg/max table, training load, stats, comments)
     sits beside the cards, each scrolling on its own. The bar holds the owner's menu, built from Kotlin's `ActivityPageTexts.ownerMenu`
     (Split and Delete in alerts, Export in a sheet whose built file goes to the share sheet through `IosApp.writeExport`), or Report.
     The combine prompt is an alert; a hidden activity waits behind its warning. What it says moved out of Compose: `FigureTexts`,
     `StreamUnits`, `DiveText`, `LabelText.sensor`, `CardLines`, `ZoneRows`, `LapTableTexts` and `RecordingTexts` (`:app-common-api`),
     `ActivityPageTexts` (`:feature-detail-api`), which the Compose page asks too; `LineChart` gained `average`. Left for later
     changes: the charts `LineChart` can't draw (the elevation behind a line, a pair's second line, several tanks, a dive's markers and
     ceiling, e-bike assist steps, the power curve, gears), the legs, lengths and sets tables, the edit-combination sheet, chart source
     captions, nested efforts' guides and span bars, and the screens it opens that are still shared (media gallery, enlarged route map,
     Edit, combine picker, create segment).
   - **The leftovers** (`ios-native-leftovers`). Threshold history is native (`ThresholdHistoryView`), the last settings page: an inset
     grouped list with Expand all or Collapse all in the bar, a section per group whose header row expands or collapses it, each
     threshold in full (its value now and change, its step chart with a dot and value over each dated value, filled on the current one,
     run on to today, its first date, Today and the undated count under it, its values with date, source, Current and a remove menu,
     Show all) or a tile per threshold with its sparkline. `LineChart` grew a step style and dots (`steps`, `ChartDot`, both defaulted),
     which `LineChartView` draws, and `interactive: false` for a sparkline. What it says and charts is Kotlin's (`ThresholdHistoryTexts`
     with `chart(section, detailed)`; `ThresholdHistoryState.isRemoving(row)` and `canRemove`), which the Compose page asks too. The
     feed's native type sheet searches (`searchable`, the rule `SportTypeSearch` in `:app-common-api`, the sheet's groups
     `TypePickerState.groups(query, source)` with `CatalogText` as the source, and `selectionOf(category)`) and discloses each sport's
     sub-sports, collapsed when it opens. A snapshot is drawn until two draws agree (`Snapshotting.settledImage`), so a full run
     compares as a single suite does. `UploadsPresenter` uses `adding`.
   - **The rest of the activity page** (`ios-native-activity-rest`). Every chart the shared card draws is native: `LineChart` grew a
     backdrop, more lines, marks and a fill above (all defaulted), which `LineChartView` draws, and `CardLines.of` builds each card's
     charts (the elevation behind effort lines with its height in the readout, a pair's two legs, every tank on one chart, a dive's depth
     with its switches, warnings and ceiling, assist mode in steps, the power curve and the gears). The legs, lengths and sets tables are
     `StructureTableTexts` `DetailTable`s, drawn by the Compose card and the native one; a combined activity's cards say where their data
     came from (`RecordingTexts.sourceCaption`); the owner edits a combination in a native sheet (`EditCombinationSheet`, `CombinationTexts`:
     members removed after a confirmation, each kind's source menu and switch); nested efforts draw their guide lines and span bars. The
     screens the page opens are native: the enlarged route map (`ActivityRouteMapView`, `RouteMapTexts`), the media gallery
     (`MediaGalleryView`, `MediaGalleryTexts`: paged, a tap zooming in a `UIScrollView`, the counter, Report, a label's cover and the
     owner's taken-down placeholders) and the combine picker (`CombinePickerView`, `CombinePickerTexts`). Left for later: Add a recording
     and Create segment (Edit and Create dive site came with the forms).
   - **The forms** (`ios-native-forms`). Edit activity (`EditActivityView`), manual entry (`ManualActivityView`) and Create dive site
     (`CreateDiveSiteView`) are native grouped forms. Edit and manual entry share `ActivityFormSections.swift`: the title, a dive's site,
     the description and the photos (`ActivityDetailsSections`, several picked at once through `PhotoPick` and sent as one
     `PhotosPicked`), the gear row and its sheet (`GearFieldRow`, `GearFieldSheet`: tick items, Add collection, Remove all), and the type
     row with the chosen sport's sub-sports as chips and a searchable single-choice sheet (`ActivityTypeRow`, `ActivityTypeSheet`); both
     place `VisibilityFormSections`. Edit leads its bar with Cancel and hides the stack's back, since leaving asks first when something
     changed, and asks before narrowing a Public activity or widening its core charts; manual entry's Save waits for a duration; the dive
     site form draws its outline on `RouteMapView` with a radius slider, its duplicates as rows to open, and why no site can be made.
     What they say is Kotlin's (`GearFieldTexts`, `DetailsFormTexts`, `SportTypeGroups.matching` and `subSportChoices`,
     `ManualActivityTexts`, `EditActivityTexts`, `CreateDiveSiteTexts`, `CreateDiveSiteState.routeMap`, `GearField.isSelected` and
     `offersClear`), which the Compose forms ask too. The place-name sheet and the feed's filter bar were already native sub-screens.
   - **Create segment** (`ios-native-create-segment`). `CreateSegmentView`, over the activity page, is a grouped form shaped like Create
     dive site's: the route as the zones leave it on `RouteMapView` with the chosen stretch highlighted (`RouteMap.highlight`, drawn on
     MapKit and in `RouteSketch`, kept out of `RouteMap.key` so a moved stretch never reframes), a two-thumb `RangeSlider` (`Design/`,
     each thumb an adjustable element named and read in Kotlin's words), the selection's line (red when it fails), the name with the
     public note under it, duplicates to open, the allowance, a failed check, and Publish in the bar; or why no segment can be made.
     What it says is Kotlin's (`CreateSegmentTexts`, `CreateSegmentState.routeMap`), which the Compose form asks too.
   - **Import** (`ios-native-import`). The import screen is native (`ImportActivityView`, `ImportBatchSections.swift`): files picked
     with `fileImporter` (several, any type) reach Kotlin as the same range-read sources FileKit's picker gives
     (`IosApp.importSources`, `FilesPicked`), so an archive is never held whole and a batch save keeps them as bookmarks; reading's
     progress with Cancel, a batch save under way and stopped imports with Dismiss; one activity's preview, drawn with the activity
     page's pieces (hero, `RouteMapView`, avg/max beside the form, training load, `MetricCardSection`, stats), then the forms' sections,
     the threshold changes, gear conflicts, new devices and the profile link, with Save in the bar, the preview and form side by side
     from 840 points; a batch's summary, zones (a searchable sheet), duplicate banner with Retry, filters, rows by month with a tick and
     an opened row in a sheet; the saved panel with its combine suggestions and the after-import panel. Opened with `combineInto` it
     names the activity and has no visibility choices. What it says is Kotlin's (`ImportActivityTexts`, `ImportActivityState.canSave`,
     `canSaveBatch`, `saveEvent` and `saveBatchEvent` with the default titles worded through a `TextSource`,
     `RecordingTexts.suggestedActivity`), which the Compose screen asks too.
   - **Record** (`ios-native-record`). Record is native (`RecordView`), a grouped form over the feed: a failed finish and the
     recording's notices (offered back after the app stopped, not saved on the device, location off with Open Settings), the elapsed
     time large with its figures as the card's `CardStats`, then the forms' type row (outdoor types only, fixed once recording starts),
     title, gear and visibility sections, with the controls in a bottom bar (`safeAreaInset`): Start; Pause; Finish & save on a row of
     its own over Resume and Discard, which asks first; progress in their place while finishing. Start without access asks Core Location
     itself (`LocationAuthorization`, while in use) and sends `PermissionResult` with the answer. Recording stays Kotlin's
     (`ActivityRecorder`, `IosLocationTracker`). What it says and offers is Kotlin's (`RecordTexts`: the figures in the athlete's units,
     the notices, `controls` as `RecordControl`s, `typeChangeable`; `ActivityTypeField.offeredQuickTypes`), which the Compose screen asks
     too. With it every screen of the athlete's own is native; only Moderation, a moderator's page from Settings, is still the shared UI.
   - **Moderation** (`ios-native-moderation`). The moderator's page is native (`ModerationView`), an inset grouped list: the subject's
     AT-URI as a field (no capitals or corrections, Go sends `Open`) with the help under it, Open held while opening with a spinner
     beside it, a URI that isn't a record address or a failed open as the shared error row, then the subject headed "Visible to:" with
     its tier: the activity as the native card, inert as the shared page's is, or the comment's author and text, or "Not under review".
     What follows a rule is Kotlin's (`ModerationSubjectTexts`: the visibility line, the author's name or `@handle`, `canOpen`), which
     the Compose page asks too. Settings still offers it only off mobile, so nothing on iOS opens it; it is native so that every screen
     the host can show has a view, which `checkIosScreenRegistry` (in `check`) now holds for every `@CircuitSerializable` screen of a
     contract module. With it phase 4 is done: `FallbackScreen` has nothing left to draw.
5. **Remove Compose rendering from iOS** (`ios-drop-compose`, done). `FallbackScreen`, `BackSwipe`, the Compose bridge
   (`ScreenViewController`, `composeState`) and the UI modules' `*.ios.kt` actuals are gone; `activitytracker.kmp.compose` declares no iOS
   targets, so the feature `-ui`s, `:app-common-ui`, `:ui-designsystem`, `:ui-resources` and `:composeApp` build for Android, desktop and
   web only. `AppGraph` and `SessionGraph` moved to `:app-graph`, which names no UI and holds the iOS graph and host in its `iosMain`;
   `:composeApp` contributes `AppCircuits` and `SessionCircuits`, so the iOS graph binds no `Ui.Factory` or `SubUiFactory`. The iOS text
   field state is plain snapshot state. SubCircuit's published Compose UI dependencies are excluded on iOS, and `checkIosDependencies` (in
   `check`) fails on Compose UI, Skia or a Compose-only UI library reaching the iOS export. Haze left the build; Calf, Vico, Coil and
   MapLibre Compose stay on the other platforms only. `ui/ios-navigation-bar` is retired. Findings below.

## Recommended iOS stack

| Need | Use | Replaces |
|---|---|---|
| State observation | Observation (`@Observable`), `AsyncSequence` from exported flows | |
| Navigation | `TabView`, `NavigationStack(path:)`, `NavigationSplitView` for Settings on iPad | Calf's tab bar, the nav decoration |
| Sheets, alerts, menus, dates | `.sheet`, `.alert`, `.confirmationDialog`, `Menu`, `DatePicker` | Calf's adaptives |
| Maps | MapKit's SwiftUI `Map` (`RouteMapView`, `ios-native-map`), the line preview in previews and snapshots | MapLibre Compose |
| Charts | Swift Charts (`LineChartView`, `ios-native-charts`) | Vico |
| Images | Nuke with NukeUI, and a custom loader for `heldphoto:` URLs | Coil |
| Pickers and sharing | `PhotosPicker`, `.fileImporter`, `ShareLink` | FileKit's Compose launchers |
| Icons and text | SF Symbols, a String Catalog, Dynamic Type | Compose resources |
| Tests | Swift Testing, swift-snapshot-testing, Xcode Previews, a small XCUITest smoke run | |
| Lint and format | swift-format and SwiftLint | |
| Concurrency | Swift 6 mode with default main-actor isolation | |
| Project layout | One app target with a synchronized folder per feature; Swift Package Manager for dependencies only | |

MapKit was first set aside for maps because it can't render the OpenFreeMap styles the other platforms use. `ios-native-map` took it
anyway: it is the system's own (no package, binary framework or style URL), follows the appearance, carries Apple's credit and legal
link itself, and its `MapPolyline` and `Annotation` draw every mark a route map needs; the marks are the same everywhere and only the
tiles look like Apple Maps. Feed thumbnails stay the line preview on the native card.

## Spike findings

Filled in by `ios-swift-export-spike` as it runs.

### Toolchain for Kotlin 2.5.0-Beta2 (2026-11-09)

2.5.0-Beta2 is the newest Kotlin on Maven Central; there is no RC yet.

| Dependency | Version | Why it works with the beta |
|---|---|---|
| Metro | 1.5.2 (unchanged) | Its compatibility table lists 2.5.0-Beta2 for Metro 1.5.0 and later |
| KSP | 2.3.13 (unchanged) | KSP2 runs on its own Analysis API, not on the build's compiler, so it has no per-Kotlin release |
| Room's processor | 3.0.3 (unchanged) | A KSP processor, so the same applies |
| Compose compiler, serialization | 2.5.0-Beta2 | Both ship with Kotlin and take `version.ref = "kotlin"` |
| Compose Multiplatform | 1.12.2 (unchanged) | Its plugin accepts the beta |

### What the beta broke (2026-11-09)

`./gradlew check` passes on every target with one source change, and running the app found a second break that `check` can't see:

- `FitRoundTripTest` (`:core-activityfile` jvmTest) no longer compiled: an `assertEquals` over two nested lambdas failed with "the
  value of the type parameter 'T' must be mentioned in input types". The type argument is now written out. Nothing else needed a change:
  explicit backing fields, Metro's generated graphs, Room's KSP output and the Compose compiler all build as before.
- The signed-in app died on its first frame on iOS with `IrLinkageError: No function found for symbol
  'com.slack.circuit.foundation.navstack/ProvidedValues.provideValues|provideValues(){}[0]'`. `App.kt` passed Circuit a
  `ProvidedValues { … }` lambda: a lambda converted to a library's `fun interface` whose method is composable. The beta links it against
  the method's signature from before the Compose compiler adds its parameters. Nothing warns at build or link time, the JVM targets are
  unaffected, and no iOS test draws the signed-in shell. The lambda is now a small class. `SessionPreviewSubUis` and `AppPreview` convert
  lambdas to SubCircuit's `SubUi` the same way; they run only on the JVM (tests and screenshots), so they are left alone. The web app
  shares `App.kt` and so had the same break; it wasn't run to confirm.
- The wasm yarn lock is unchanged, and the web app's development bundle builds.
- Detekt's type-resolution tasks print "compiler errors found during analysis" in a dozen modules. That isn't the beta: detekt reads
  every source set of a module as one module and runs no compiler plugins, so it reports expect and actual declared together and every
  serialization `serializer()` as unresolved. Neither error comes from the compiler the build uses (not compared with a 2.4.21 run,
  since CI's detekt tasks came from the build cache).

### The app on Swift export (2026-11-09)

The app runs on Swift export. On an iOS 18.4 simulator it installs, launches and shows the sign-in screen with its strings, the handle
field takes text, and Sign in reaches the system's web sign-in prompt. The signed-in pass was done on an iOS 26.0 simulator, for the
reason below:

| Check | Result |
|---|---|
| Sign in and see the feed | Signed in as `alice.test`; the feed, a profile and an activity page load |
| Import a FIT file | `mtb-ride.fit` from `WriteWorkoutSamples` shows 7.83 mi, 45:00, 886 ft, 193 W and 152/167 bpm, the figures the file was written with (12.6 km, 2,700 s, 270 m, 193 W, a 140 to 168 bpm ramp), with its map and chart |
| Import a truncated file | The first half of the same file gives "Couldn't read that file" and the app carries on: the exception the Swift decoder throws reaches Kotlin |
| Background and return | Going to the background calls all three schedule functions; the same process comes back. The simulator refuses every `BGTaskScheduler` request (error 1), so no background task ran |
| Calf's native pieces | The tab bar switches tabs, the activity-type sheet opens with its grabber, the delete alert shows and cancels, the date wheels turn; so do the native menus |

Not checked: a background task actually running (it needs a device), and the signed-in app on iOS 18.

The iOS 18.4 simulator can't be used for the signed-in pass against the tunnel. Its first requests go over HTTP/2 and work; once it has
learned the host speaks HTTP/3, each QUIC connection is closed as soon as it is established (`quic_conn_retire_dcid unable to find
DCID` in the system log) and the request hangs for its 60 s timeout, which the app shows as "Couldn't reach the server". That is the
simulator's network stack, not the app: the same host answers `curl` in 0.2 s and the iOS 26.0 simulator reaches it.

How the build ended up:

- `:iosExport` names one `api` dependency, `:iosShell`. A module the umbrella names is exported whole. A module reached through
  another's signatures (`:core-activityfile`, kotlinx-datetime) is exported only as far as those signatures name its types. That is what
  keeps the surface small, so the two modules stay two.
- Exporting `:core-activityfile` whole didn't build. `PerSecond.hold(samples: List<Pair<Double, Int>>)` became a Kotlin bridge that
  read the argument as `List<Pair<Any?, Any?>>`, which the compiler rejects. So `:iosShell` has a `FitSupport` object that repeats the
  seven helper calls the Swift decoder makes on `FitDives`, `FitHeart` and `FitUnits`, and makes the exception it throws. That was our
  API, not a Swift export bug to wait out: `Pair` and `Triple` say nothing about what their values are, and no non-private signature
  in any module may name one now (detekt's `NoPairInApi`, in `:detekt-rules`; `hold` takes `List<TimedValue>`). That removes the one
  known reason `:core-activityfile` can't be exported whole. Exporting it whole, and dropping `FitSupport`, hasn't been tried.
- Hiding a dependency is not safe by default. With kotlinx-datetime hidden, `DecodedActivity`'s constructor (it takes a
  `LocalDateTime?`) was generated as `fatalError()`, with no warning. Left unconfigured it exports as far as it is named (1,892 lines);
  marked `EXPOSED` it exports whole (8,250 lines). Only `:core-location` and kotlinx-serialization-core are hidden.
- The Compose Multiplatform plugin copies resources into the app from the module Xcode's task runs in. Without it on `:iosExport` the
  build succeeds and the app bundle has no `compose-resources`. It needs the Compose compiler plugin beside it, which is why the
  stability check gained an iOS-only fallback.
- A Swift class implements a Kotlin interface by extending `KotlinBase` (`final class SwiftFitDecoder: KotlinBase, FitDecoder`). A
  Kotlin exception is a Swift `Error`: the decoder throws an `ActivityFileException` and Kotlin receives that same object, so
  `FitDecoder.decode` keeps throwing (design decision 4's first option).
- Nullable numbers are native optionals (`Int64?`, `Int32?`, `Double?`, `Bool?`) and lists of them are Swift arrays, which took the
  boxing (`KotlinLong(value:)` and the like) out of about 200 lines of `SwiftFitDecoder`. A `ByteArray` is a Kotlin class read one element at a time, as before.
- A module exported through another's signatures gets no short names: Swift writes
  `ExportedKotlinPackages.io.github.solcott.activitytracker.activityfile.DecodedActivity`, so `SwiftFitDecoder` declares type aliases.
  `:iosShell`, named by the umbrella with its package as the root package, gets `IosApp` and `FitSupport` as top-level names.
- Calf needed nothing: the framework's `export(libs.calf.ui)` has no counterpart, the app links without one and its native pieces
  work (the table above).

Size and time, debug build for the simulator on this machine:

| | Objective-C framework | Swift export | Swift export with three contracts | Swift export with every contract (phase 1) | With the presenter host and the SwiftUI shell (phase 2) | With generated resources, shared text and the samples (phase 3) |
|---|---|---|---|---|---|---|
| Generated Swift | none (a header) | 5,352 lines | 42,361 lines | 91,668 lines | 94,008 lines | 99,432 lines |
| App's debug dylib | 213.9 MB | 198.3 MB | 218.6 MB | 220.2 MB | 222.1 MB | 234.5 MB |
| Build with nothing changed | 6 s | 5 s | 5 s | 5 s | 5 s | 5 s |
| Build after a one-line Kotlin change in `:iosShell` | not measured | 50 s | 57 s | 54 s | 57 s | 61 s |
| First build of the binary, other modules already compiled | 187 s | 396 s | not measured | not measured | not measured | not measured |

The phase 3 column is a Debug build, which also exports the 2,529 lines of `:app-fixtures`; its dylib holds the 14,782 lines of string symbols Xcode generates from the catalog as well, which the Swift count leaves out. The phase 1 column has 21 contract modules exported whole and only kotlinx-serialization-core hidden. Twice the Swift of the probe costs
nothing measurable in a rebuild, so exporting the contracts whole stands and the facade alternative isn't needed.

### Contract probe (2026-11-09)

`:feature-login-api`, `:feature-segment-api` and `:feature-detail-api` were added to the umbrella on uncommitted changes, since removed.
A throwaway Swift file read `LoginState`, `StarredSegmentsState` and `ActivityDetailState`, called each `eventSink` and switched over
their sealed types. It compiled without an error or a warning. Nothing ran it, so every "works" below means "compiles".

What it took to get there:

1. As added, the export generated 71,192 lines of Swift and didn't build. The contracts name `TextFieldState`, which pulled in Compose
   foundation, ui-text and ui-graphics. Their generated Kotlin bridge doesn't compile (an `AnnotatedString` constructor taking a
   `List<Range<Any?>>`) and neither does their Swift (a nested type named `Type`).
2. `PlaceNameFields` (`:app-common-api`) has a property named `self`. The bridge of its `copy` then has two parameters named `self` and
   doesn't compile. Renaming the property fixed it.
3. With Compose (foundation, ui, ui-text, ui-graphics, ui-geometry, ui-unit, runtime, runtime-saveable), Ktor, Room, SQLite, Kermit,
   kotlinx-io and kotlinx-serialization-json hidden, and that property renamed, everything built.
4. Exposing Compose foundation alone, to see whether `TextFieldState` could stay, doesn't build either: its Swift names
   `AnnotatedString.Range` from the hidden ui-text.

| Construct | Result | What it means for the contracts |
|---|---|---|
| `sealed interface` events and sub-states | A Swift protocol with `sealedType()`, an enum Swift switches over exhaustively with no `default`. Works for the contracts' own, for `AuthCoordinator.Status` in `:core-data` and for `LoadStatus` in the uistate library | No change. They stay `sealed interface` |
| `data object` and `data class` cases | `LoginEvent.Submit.shared`; `StarredSegmentsEvent.Open(uri:)`. A case's payload is `case let .open(open)` then `open.value.uri` | No change |
| `eventSink: (Event) -> Unit` | A Swift closure, `state.eventSink(event)` | No change |
| `ImmutableList`, `ImmutableSet` fields | Exported, but as the Kotlin collection protocols: `size`, `_get(index:)`, `iterator()`, with untyped elements to cast. A plain `List<T>` field is a typed Swift array | Change to `List` and `Set` where Swift reads them, or wrap them once in Swift. `ImmutableMap` wasn't in the three contracts |
| `ContentState<T>`, `PagedState<T, M>` | Exported classes. `data` is `(any _KotlinBridgeable)?`, `items` is `[(any _KotlinBridgeable)?]`, cast with `as? T`. Status, `canLoadMore` and the rest are typed | No change. The Swift design system needs typed wrappers, as planned |
| `TextFieldState` field | With Compose exposed, a build failure. With Compose hidden, the field's getter and the state's constructor and `copy` are `fatalError()` stubs and the rest of the state works | The app-owned type goes ahead as designed |
| `Did`, `AtUri` value classes | Ordinary classes: `AtUri(value:)`, `.value`, and the companion's `of` and `parseOrNull` | No change |
| `Duration`, `Instant`, `LocalDate`, `ByteString` | The three contracts hold none of the first three. `LocalDateTime` (from `:core-activityfile`) is an exported class. `ByteString` (`PickedPhoto.bytes`) wasn't read | Still open for `Duration` and `Instant`; check them on the first contract that has one |
| `Screen`, `CircuitUiState`, `SubScreen` supertypes | Circuit's runtime modules export as far as named: `LoginState` is an `any CircuitUiState`, `LoginScreen.shared` an `any Screen`. `SubScreen` wasn't touched | No change |
| A property named `self` | Build failure in the generated bridge | Rename `PlaceNameFields.self`; the guard should refuse the name |
| `:core-data` and `:core-lexicon` types | 10,258 and 7,303 lines of Swift for what three contracts reach, a quarter and a sixth of everything generated | Moving model types out of `:core-data` is about size and about not needing Ktor and Room hidden, not about making the export work |
| Size and time | In the table above | Fine at three contracts; measure again at ten |

### Go or no-go

Go on Swift export. The app passes the simulator pass on it, the export carries every construct the contracts use except Compose types,
and the failures it has are build failures or, for a hidden type, a stub found by reading the generated Swift. The one runtime failure
the spike met came from the Kotlin beta, not from the export (the composable `fun interface` lambda above): it is in how the beta
compiles `:composeApp` against Circuit's klib, which the Objective-C framework linked too. That build wasn't run signed in to confirm.

What phase 1 has to do is smaller than planned:

- `TextFieldState` leaves the contracts (the app-owned type).
- `PlaceNameFields.self` is renamed.
- Collections the Swift UI reads become `List` and `Set`, or get one Swift wrapper. Decide per the first native list screen.
- The guard checks exported modules for Compose types, properties named `self`, and a hidden module's type in a signature Swift calls.
- Sealed interfaces, value classes and the Circuit supertypes need nothing.

### Phase 1 findings (`ios-swift-clean-contracts`)

The text field module, `:core-textfield`, exported whole as the Swift module `CoreTextfield`:

- `TextFieldState` is a Swift class. `value` is a `Swift.String` property with a getter and a setter, which is what a Swift field binds.
- `text`, a Kotlin `CharSequence`, is exported as `any ExportedKotlinPackages.kotlin.CharSequence`: usable (`state.text.length`), not a
  stub, and not a Swift `String`. Swift reads `value`.
- `rememberTextFieldState` is not in the generated Swift at all. A `@Composable` function is left out of the export, not stubbed, so it
  needs no exception in the stub check.
- `textFieldStateOf(initialText:)` is a function of the module's package (`ExportedKotlinPackages.….textfield.textFieldStateOf`; a
  top-level one when the module is given its package as root package, which the contract modules are not), and
  `setTextAndPlaceCursorAtEnd(text:)` and `clearText()` are methods on the class. A Kotlin default argument is not exported: Swift
  always passes `initialText`.
- `composeState`, typed `Any`, is `any _KotlinBridgeable`. Nothing in the module is a stub.
- An `expect class` whose actual is a class on iOS and a type alias to Compose's class in `nonAppleMain` compiles on every target and
  resolves from other modules' common code with no compiler warning.

Every contract module exported (`:app-common-api` and the 19 feature API modules, beside `:core-textfield` and `:iosShell`). A
throwaway Swift file, since removed, read a state and sent an event for each construct the probe had left open. It compiled without an
error or a warning; nothing ran it.

| Construct | Result | What it means |
|---|---|---|
| `Duration`, `Instant` (`UploadsState`) | Exported standard-library classes: `timeLeft?.inWholeSeconds`, `serverPausedUntil?.epochSeconds` | No change |
| `LocalDate` (`EditGearState`, `EditGearEvent.AcquiredChanged`) | An exported class Swift reads (`year`, `day`) and builds: `LocalDate(year:month:day:)` | No change |
| `ByteString` (`ImportActivityEvent.FilePicked`) | Readable as far as named, but Swift can't make one: Kotlin's `ByteArray` initialiser is a stub. With kotlinx-io-bytestring marked `EXPOSED` (about 900 lines), `toByteString(data as NSData)` builds it | The module is exposed |
| `ImmutableMap` (`ImportActivityEvent.SaveBatch`) | The getter is exported as the Kotlin map protocol (`size`, `_get`). The class had no initialiser at all | See the next row |
| A constructor taking `ImmutableList`, `ImmutableSet` or `ImmutableMap` | The class is exported with its getters and **no initialiser or `copy`**, with no warning. 100 contract classes, nearly all states, which Swift never builds. Four were events Swift could not send | Those four have a second constructor taking `List` or `Map`, which exports as an initialiser taking a Swift array or dictionary. The stub check fails on an event with no initialiser |
| `SubScreen` (`ReportScreen`) | Built and read in Swift like any class. The class has no `SubScreen` conformance in Swift: the generic interface isn't exported | Phase 2's host matches sub-screens in Kotlin |
| `GeoPoint` in `RouteEnds` | `ends.start?.lat`, `RouteEnds(start:end:)`, `RouteEnds.Companion.shared.HIDDEN` | No change |
| A text field in a state (`LoginState.handle`) | `state.handle.value`, read and set | As designed |
| A `@Serializable` screen | Its `serializer()` is a stub, typed with the hidden serialization-core: 55 in the contracts | Allowed by name in the stub check; Swift has no use for it |
| A type from a module that is neither named nor hidden (`:composeApp`) | Exported as far as named, under a generated module name. Not a stub | Only a hidden module's type makes a stub |
| An object extending an exported class; a declaration deprecated as an error | `private init()` without `override`; `@available(*, unavailable)` over a stubbed body | The stub check knows both |

Hidden modules: only kotlinx-serialization-core. The probe hid Ktor, Room, SQLite, Kermit, kotlinx-io and kotlinx-serialization-json
too, but with no Compose type left in the contracts the export builds without hiding them, and each comes through only as far as
`:core-data`'s signatures name it.

### Phase 2 probes (`ios-native-shell`, 2026-11-09)

Four things phase 2's design rests on, each tried on throwaway code (a probe object in `:iosShell`, a Swift file and a Compose screen
with a list and a horizontal pager), since removed. Run on the iPhone 16 Pro simulator with iOS 18.4 and the iPhone 17 Pro one with
iOS 26.0.

**Flows and suspend functions reach Swift, typed.**

- A `StateFlow<Int>` property is `any KotlinCoroutineSupport.KotlinTypedStateFlow<Swift.Int32>`, and a
  `StateFlow<CircuitUiState?>` is `KotlinTypedStateFlow<Optional<any …CircuitUiState>>`: the element type survives, and a view casts
  an element to its screen's state with `as?`. A `Flow` is `KotlinTypedFlow<Element>`.
- A typed state flow has a typed `value`. `asAsyncSequence()` on any of them gives a `KotlinFlowSequence<Element>`, an `AsyncSequence`
  to iterate with `for try await`.
- Cancelling the Swift task ends the Kotlin collector: its `onCompletion` ran with a `CancellationException`, and the Swift loop threw
  `CancellationError`.
- A `suspend fun` is `async throws` in Swift, and returned its value.
- Elements arrive on whatever executor the iterating task runs on. In a nonisolated function that was a background thread; under
  main-actor default isolation (below) it was the main thread. The wrapper that feeds views iterates on the main actor.
- Nothing needed configuring: the export brought `KotlinCoroutineSupport` in by itself once a signature named a flow.

**Identity holds.** Reading the same Kotlin object twice through a getter gave the same Swift object (`===` true), so Swift can tell
a replaced text field state from the same one without an id.

**Compose view controllers stack in a `NavigationStack`.**

- A pushed `ComposeUIViewController` is drawn from its first visible frame on both versions (a recording of the push, frame by frame):
  no blank first frame.
- With the navigation bar hidden, the edge swipe does nothing (iOS 18.4, without the delegate). With a delegate on
  `interactivePopGestureRecognizer` that allows it when the stack has more than one controller, it goes back, follows the finger and
  can be cancelled, over a list and over a horizontal pager, and SwiftUI's path loses its last element. At the root the delegate
  refuses and the app keeps responding.
- **On iOS 18.4 Compose keeps following the touch while UIKit drives the pop.** A cancelled back swipe that began over the pager
  turned its page. Making other recognisers wait for the edge pan (`shouldBeRequiredToFailBy`) did not stop it. Switching the screen's
  own gesture recognisers off when the pop gesture begins and on again when it ends does: the pager stays where it was, the swipe
  still cancels or completes, and the screen scrolls again afterwards. iOS 26.0 does not have the problem, and the same code is
  harmless there.
- iOS 26 has a second recogniser, `interactiveContentPopGestureRecognizer`. On a screen with the bar hidden a swipe from the middle
  of the content does not go back, with or without the delegate, so it is left alone.
- A swipe sent while the push is still animating is ignored, as in any UIKit app.
- Memory (physical footprint of the simulator process, a simple screen):

  | Compose controllers stacked | iOS 18.4 | iOS 26.0 |
  |---|---|---|
  | 1 | 59 MB | 63 MB |
  | 2 | 66 MB | 66 MB |
  | 8 | 79 MB | 74 MB |

  About 2 to 3 MB a screen, far under the limits the design set (40 MB a screen, 400 MB at eight). Real screens add their own
  content (images, a map), and a covered fallback screen keeps it in memory where the single Compose stack let a covered screen's
  UI go. That is measured on real screens when the shell exists.

**Swift 6 language mode with main-actor default isolation works.** The app's Swift, the generated Swift it imports and the probe
(flows iterated in tasks, Kotlin objects captured in them) compile with no error or warning. One change was needed:
`SwiftFitDecoder` is `nonisolated`, because Kotlin calls it from its own threads and a class that extends `KotlinBase` can't change
the isolation of the initialisers it overrides. A FIT import in the simulator still shows the spike's figures (7.83 mi, 45:00, 886 ft,
193 W, 152/167 bpm). The project stays in this mode.

One thing met on the way: an app built for an iOS 26 simulator does not launch on the iOS 18.4 one
(`Library not loaded: @rpath/libswiftCompatibilitySpan.dylib`). Xcode embeds that library only when the build's destination needs it,
so a build to run on iOS 18 names an iOS 18 destination, or a generic one.

### Phase 3 probes (`ios-native-foundations`, 2026-11-10)

Six questions asked with throwaway code before the design was built on their answers. All six came out as the design hoped, so no
fallback applies.

- **Xcode generates a typed symbol for every catalog entry** with `STRING_CATALOG_GENERATE_SYMBOLS` on. A key in snake case becomes a
  camel-case member of `LocalizedStringResource`: `login_sign_in` is `.loginSignIn`, and an entry with placeholders is a function of
  them, typed by specifier (`.loginLeft(_ arg1: String)`, `.errorWithReason(_:_:)`, `%lld` an `Int`). `Text(.loginIntro)` and
  `String(localized: .loginSignIn)` both take them. No two of the 2,104 names collide in camel case.
- **A plural can take its quantity apart from its arguments.** An entry whose text is one substitution on argument 1
  (`%#@quantity@`, `argNum` 1, `lld`), with each form's own placeholders numbered from 2, reads "1 like" and "3 likes", and
  "with bob and 1 other", from the symbol (`.probeWithOthers(quantity:_:_:)`: Xcode names the first argument after the substitution)
  and by name through `String.localizedStringWithFormat`. That is Compose's `pluralStringResource(res, quantity, args…)` exactly.
- **A percent sign** is written once in an entry without placeholders and doubled in one with them: `% of max heart rate (5)` and
  `Battery %1$@%%` both read with one.
- **The whole catalog costs little.** 2,104 entries (2,026 strings, 68 plurals, the ten items of the one array) are a 585 kB file
  and 14,782 lines of generated symbols. The first build after adding them took 9 s against 4 s for no change; a build after a
  one-line Swift edit took 5.6 s with them and 7.0 s without, which is noise; the debug app grew by 2.6 MB.
- **A vendored drawable is a template image as an SVG.** `directions_run.xml`'s path data, unchanged, in an SVG with the drawable's
  960 viewport, in an image set marked template and vector-preserving, draws sharp at 16, 24 and 48 points and takes a tint.
- **The samples reach Swift, and a view snapshots under Swift Testing.** `:app-fixtures` exports as 2,529 lines of Swift with no
  stubs once `ProfileSamples.self` is renamed, and `LoginSamples.shared.all` is a Swift dictionary of the seven states.
  swift-snapshot-testing 1.19.7 recorded `LoginView` for the failed sample, light and dark, and a second run compared equal. Its
  default records a missing reference and fails that run, and never rewrites one that exists.

### Phase 4 probes (`ios-native-settings`, 2026-11-10)

Asked with throwaway code before Settings' design was built on the answers: a scratch root view in place of `RootView`, on the iPad
Pro 13-inch simulators with iOS 26.0 and 18.4 and no session, driven by launch arguments and read from screenshots (the simulator
tool had no access to an iPad, so nothing was tapped or swiped).

**A `NavigationStack` can't go inside another's destination.** A pushed destination that hid the outer bar and drew an `HStack` of two
`NavigationStack`s, one per column, never showed, on either version: the outer path held the element, the destination's toolbar
preferences reached the outer bar (its title went), and the root stayed on screen. One `NavigationStack` in a column beside a plain
list failed the same way.

**`NavigationSplitView` in a destination draws, and decides too much.** On iOS 26.0 its sidebar is a floating panel with a bar and a
sidebar button of its own under the outer bar's back button. On iOS 18.4, upright, it shows only the detail until that button is
tapped. It owns which column shows and when it collapses, which the Kotlin stack must own.

**Two plain columns under the outer bar work.** An `HStack` of a `List` (360 points), a divider and a UIKit view controller that fills
the rest pushes and draws on both versions. The back button is the outer stack's, over the list. The inline title sits centred under
the tabs, which share the bar on an iPad, above both columns (`.toolbarTitleDisplayMode(.inlineLarge)` doesn't move it). The view
controller's top safe area ends under that bar (140 points on iOS 26.0, 128 on 18.4), so a page that draws its own bar there clears it.
A path set as the stack's first value isn't pushed; one set after the view appears is, which is how the Kotlin stack drives it.

**Immutable collections from Swift**, read from the settings pages' samples through the exported Kotlin `Collection`'s iterator:

| Element | What Swift gets |
|---|---|
| `String` | A Swift `String` |
| `Int` | `Int32` (`as? Int` is nil) |
| A class of a contract module, which is exported whole | The class: `ThresholdHistorySection`, and its own lists read the same way |
| A class of `:core-lexicon` named only as the collection's type argument (`PrivacyZone`) | An opaque `_KotlinExistential`: the export erases the argument, so nothing names the class and Swift has no type for it. Once any exported signature named it, the same element cast to it |
| A Kotlin enum (`ZoneKind`), even with its Swift type exported | An opaque `_KotlinExistential`, never the enum |
| A null list | `nil` |
| An empty list | No elements |

Two more things met on the way, both about a plain `List` in a contract:

- A `List<T>` property whose getter returns the immutable list itself stops the app in the generated getter
  (`-[_KotlinExistential count]: unrecognized selector`): a persistent list reaches Swift as an object of its own exported class, not as
  an array. Returning a copy (`toList()`) gives a typed Swift array, which also names `T`.
- A `List` of a Kotlin enum stops the app in the generated getter (`Could not cast … _KotlinExistential … to …ZoneKind`). The flows
  finding holds for collection elements: an enum is converted only as a property's own value.

`ContentState<ImmutableList<T>>.data`, typed `Any?`, arrives as a Swift array and casts to `[T]`.

**Not probed:** whether an iPad turns the app to landscape (it declared portrait only then). The change went on to allow every
orientation, and the iPhone was turned in its last walk.

### Phase 4 probes (settings pages, `ios-native-settings-pages`, 2026-11-10)

How a native settings page gets a bar of its own in the detail column, asked with throwaway code before the pages were designed: a
scratch root view by launch argument, a tab whose stack pushes `SettingsColumns` (the shell's own two-column view) with a form page,
on iPad Pro 13-inch simulators with iOS 26.0 and 18.4 and no session, read from screenshots.

- **A page's `.navigationTitle` never shows in the column.** The page and the list are one destination of the tab's stack, and the
  bar over both columns keeps the list's "Settings". So a native page there needs a header of its own, as the fallback pages already
  draw (their Compose bar).
- **A header in the column's top safe-area inset sits right under the outer bar**, centred over the column and level with the list's
  first section, on both versions: the column's safe area ends under the bar. Before iOS 26 it takes the bar material and a hairline
  to read as a bar; on iOS 26 `.safeAreaBar(edge: .top)` places it with the system's scroll edge effect and no fill, as the system's
  own bars look at rest.
- **`NavigationSplitView` with its column visibility held at `.all` and the sidebar button removed now draws two columns with a bar
  each**, on both versions (the first probe let it choose, and on 18.4 it hid the sidebar). Not taken: the outer bar's title moves
  into the sidebar's bar (a large title on 18.4, an inline one over a floating panel on 26.0), the list would stop being the same view
  narrow and wide, so crossing the size would rebuild it, and every fallback page would draw its Compose bar under the split's bar.

The probe pages fit an iPad's height, so content scrolling under the header was first seen in the walk.

### Phase 4 probes (settings lists, `ios-native-settings-lists`, 2026-11-10)

The Gear list and the blocked and muted lists bring the first pictures from the network and the first list loaded page by page. Asked
with throwaway Swift tests on the iPhone 17 simulator (iOS 26.0) and by reading the generated Swift, before the pages were designed:

- **Nuke 13.2.3 by Swift Package Manager** (swift-tools 6.0, iOS 15 minimum, no dependencies of its own) resolves and builds in the
  app target under Swift 6 with main-actor default isolation, with no warning from it. `ImagePipeline.shared.image(for:
  ImageRequest(id:data:))` decoded an image from bytes a closure handed it, which is how a `heldphoto:` URL can be answered.
- **These pages do meet held photos.** A gear item that isn't Public shows its photo as `heldphoto://blob/<cid>` (`GearRepository`),
  which the shared UI's Coil loader answers from the session's `HeldPhotos`. A Kotlin entry returning the held bytes as a `ByteString?`
  exports with no stub, `toNSData` (kotlinx-io-bytestring, exported whole) gives Swift the bytes, which round-tripped equal, and signed
  out it returns nil.
- **A `PagedState` reads in Swift.** `items` is a Swift array whose elements cast to the item's contract class (`BlockListAccount`,
  two of two in every sample that has items; the library's list is a plain one, so the generated getter's array is safe); `refresh`
  and `append` are `LoadStatus` with `sealedType()`; `canLoadMore` and `endReached` are typed. `hasAnswer` is the library's extension
  and isn't exported, like `ContentState`'s: the rule of what a paged list shows has to live in an exported module.
- **`ProfileViewBasic`, `Did` and `Handle` are classes Swift reads** (`did.value`, `handle.value`, `displayName`, `avatar`), because a
  contract's signature names them. `shownName`, an extension property of `:app-common-api`, exports as a static function,
  `AppPackage.getShownName(_:)`. An `ImmutableSet<Did>` reads through `elements(as: Did.self)` as Swift `Did`s.

Neither page needs far more than its share (both use images; only the blocked list pages), so they went in one change.

### Phase 4 probes (account, `ios-native-account`, 2026-11-10)

The Account and Leave pages bring the first sealed types from `:core-data` drawn natively, the first file a native page hands to another
app, and the first time of day. Asked with throwaway Swift tests over every Account and Leave sample on the iPhone 17 simulator (iOS
26.0), a scratch `IosApp` entry, and by reading the generated Swift:

- **The sealed states cast in Swift.** `GrantStatus` (`Current`, `SpacesUnsupported`, `Outdated`), `DataExportStatus` (each case, with
  `Running.phase` as the Swift enum), `SharedPlacesSummary` and `LeaveRun` read with `as?`. `EndingSession` and `PendingWork` read as
  classes, `LeaveStep` and `LeaveExport` as Swift enums, and `canLeave` as the getter it is.
- **`Outdated.missing` stops the app.** Its getter is generated as `Swift.Set<GrantFeature>` with `as!`, and reading it fails with
  "Could not cast value of type 'Swift.AnyHashable' to 'GrantFeature'": a set of enums is the same failure as a list of them. What an
  outdated grant is missing has to reach Swift as text.
- **`ExportArchive` arrives as a class** with its `name`, because `DataExportStatus.Ready` names it; and its `writeTo` is generated as
  `async throws` taking a Swift closure.
- **`kotlin.time.Instant` arrives as a class**, with `epochSeconds`, since the standard library is exported as far as signatures name it.
- **A `suspend` entry is an `async` call.** A scratch `suspend fun` on `IosApp` returning a `Boolean` was awaited from a Swift test and
  returned. So the archive is written by a `suspend` entry, not the callback the design first planned.
- **A function's `List<AppText>`** reads as `[any AppText]`, and each element resolves.

### Phase 4 probes (gear pages, `ios-native-gear-pages`, 2026-11-10)

A gear collection and Ignored devices are drawn natively; the gear item page isn't. It lists its counted activities as the feed's
`ActivityCard` (author, moderation notice and blur, route map, media, likes), so it goes native with or right after the native card,
not as iOS-only rows the other platforms don't have. Asked with throwaway Swift tests over every collection and Ignored devices sample
on the iPhone 17 simulator (iOS 26.0), and by reading the generated Swift:

- **A list as a `ContentState`'s value doesn't reach Swift as a Kotlin collection.** Ignored devices' content is
  `ContentState<ImmutableList<LinkedSensor>>`, the first content that is itself a list. Read through the erased `data`, a list holding
  devices arrives as a Swift `Array<Any>` (its elements cast to `LinkedSensor`), not as `KotlinCollection`, so `elements(as:)` doesn't
  apply. And the empty `persistentListOf()` the loading sample holds can't be read at all: the bridge sends it `count` and throws
  `NSInvalidArgumentException` (unrecognized selector). A loaded page with nothing ignored holds the same empty list, so the native page
  must never read `data`. `IgnoredSensorsState.sensors`, a getter typed `ImmutableList<LinkedSensor>` returning the content's value,
  reads as a Kotlin collection on every sample (empty, loading and failed included), through `elements(as:)`.
- **`LinkedSensor.weak` reads from Swift.** `weak` is a contextual keyword, generated as `public var weak: Swift.Bool`, and
  `sensor.weak` reads it; the contract keeps its name. `serialTail`, `manufacturer` and `model` read as Swift optionals.
- **A collection's `notes` read as a Swift optional** ("Cold water setup", nil for the empty sample), and its `members` through
  `elements(as: GearRow.self)` in the athlete's order, the wetsuit's `retired` true.
- **`deviceName` is a static function on the package** once it moves to `:app-common-api` (`GearPackage.deviceName(manufacturer:model:)`).
- **`contentSamples` has no `empty` kind.** Ignored devices' "nothing ignored" is a variant of its own.

### Phase 4 probes (follow lists, `ios-native-follow-lists`, 2026-11-10)

Followers, Following and Follow requests are opened from the profile, which is still the shared UI's. Asked with throwaway Swift tests
over every `FollowListSamples` and `FollowRequestsSamples` state on the iPhone 17 simulator (iOS 26.0), and by reading the generated
Swift, before the views were written:

- **A paged list's meta reads through the state's typed getter.** `FollowListState.subject` (the `PagedState`'s `meta`, erased in the
  state itself) is generated as `ProfileViewBasic?`: alice once a page has answered (empty, loaded, refreshing, refresh failed,
  loading more, page failed, end), nil while loading and after a failed first page.
- **A content holding lists is read through its typed getters.** `FollowRequestsState.requests` and `declined` read through
  `elements(as: ProfileViewBasic.self)` on every sample, the loading and failed defaults included (empty), so the page never reads the
  content's erased value; a getter for the tab's own list (`accounts`) is the same kind of getter.
- **A contract enum is `CaseIterable` in Swift.** Swift export generates `FollowRequestsTab` (and `FollowListKind`) as a Swift enum
  conforming to `CaseIterable`, `RawRepresentable` and `LosslessStringConvertible`: `allCases` is `[Requests, Declined]` in Kotlin's
  order, so a segmented choice needs no order written in Swift. `FollowRequestsEvent.SelectTab(tab: .Declined)` builds and reads back.
- **`.navigationSubtitle` sits under an inline title on iOS 26.0**, centred, and shrinks the title: about the subheadline's size, semibold,
  over the subtitle at the caption's size in the secondary colour. It has no counterpart before iOS 26, so the earlier form and a column
  header draw two lines in those sizes.

### Phase 4 probes (zones contracts, `ios-zones-contracts`, 2026-11-10)

Training zones, threshold history and Privacy zones wait for native charts and a map, but their contracts held three things Swift can't
read. Asked with a throwaway Swift test over the zones, sport zones, privacy zones and threshold history samples on a fresh iPhone 17
simulator (iOS 26.0), before the fix, and by reading the generated Swift:

- **Nothing in the export named `ZoneKind`, `Zone` or `PrivacyZone`.** The generated `ActivityTrackerCoreData.swift` and
  `ActivityTrackerCoreLexicon.swift` had no type for any of them, and `ZoneSummary.kinds` and `SportZonesState.kinds` are generated as
  `any ImmutableSet`.
- **Every element read as an opaque `_KotlinExistential`**: both kinds sets (two each, heart rate and power, heart rate and speed), both
  heart rate zone lists (five each), and the one privacy zone. `elements(as:)` couldn't have cast any of them, since Swift had no type to
  cast to.
- **`shownRows` with every row shown read as a typed array** on a five-row section, without stopping the app, and so did the collapsed
  read (`take` already copies). Only an **empty** persistent list stopped it: a section with no rows and every row shown failed in the
  generated getter with `-[_KotlinExistential count]: unrecognized selector`, the failure the settings probe met and the gear probe found
  for an erased empty value. So the bridge carries a non-empty persistent list as an array and sends the empty one (the library's shared
  instance) `count`. A threshold's section always has a row, so this one could never have stopped the app, but a copy costs nothing and
  holds whatever the list is.
- **Swift can't build the state it needs to read.** A data class has no `copy` in Swift and `ThresholdHistorySection`'s constructor takes
  an immutable list, which has no Swift initialiser, so the all-rows case needed a sample: `ThresholdHistorySamples.variants["allRows"]`,
  a section of five rows with every one shown.

### Phase 4 probes (gear editors, `ios-native-gear-editors`, 2026-11-10)

The gear and collection forms bring the first native photo pick, date choice, sheets and reordering. Asked with throwaway Swift tests
on an iPhone 17 simulator (iOS 26.0), a window snapshot of two scratch views, and by reading the generated Swift:

- **A `LocalDate` reads and builds in Swift.** `year` and `day` are `Int32`; `month` is kotlinx-datetime's `Month` as a Swift enum
  (`.JULY`) whose `rawValue` is its ordinal, so the month's number is `rawValue + 1` (`monthNumber` is there too, deprecated in Kotlin and
  so in Swift). `LocalDate(year:month:day:)` with a month number builds one (2026-07-02), which `AcquiredChanged(date:)` took.
- **`GearPhoto` casts.** `GearPhoto.Saved(url:)` built in Swift reads back through `as? GearPhoto.Saved`.
- **Swift's `==` on two equal Kotlin data objects is Kotlin's `equals`** (two `ActivityType(sport: "ride")`: `==` and `isEqual` true,
  `===` false). The forms still ask the state for membership, which keeps the rule in Kotlin.
- **`IosApp.compressPhoto` works from Swift, but Skia can't read a HEIC.** A 4,032 × 3,024 JPEG that UIKit wrote came back as WebP at
  2,048 × 1,536; a HEIC of the same picture and four bytes of junk both came back nil. So a picked photo is asked for in a compatible
  encoding (JPEG for a HEIC), and Swift re-encodes it as JPEG with UIKit before a second try when Kotlin can't read it, keeping the
  scaling, the format and the metadata stripping Kotlin's.
- **A sheet with its own `NavigationStack`, presented from a pushed destination, shows its bar**: its inline title and a Done in the
  confirmation place, unlike a stack inside a destination.
- **Edit mode held on for a whole `Form` marks only the section that can move.** The section with `onMove` and `onDelete` shows its delete
  controls and drag handles; a text field row and a button row above it are drawn as before. Whether they still take typing and taps is
  for the walk.

### Phase 4 probes (invite lists, `ios-native-invite-lists`, 2026-11-10)

Activity invites is opened from the own profile and Add people from the own activity, both still the shared UI's. Asked with a
throwaway Swift test over every `ActivityInvitesSamples` and `AddPeopleSamples` state on a cloned iPhone 17 simulator (iOS 26.0), and by
reading the generated Swift, before the getters or views were written:

- **`InviteeView` has a Swift type, and `InviteView` only in Debug.** Both are generated in `ActivityTrackerCoreLexicon.swift`, but
  the one exported signature naming `InviteView` is `:app-fixtures`' `fakeInvite`, which Release builds don't export (the contract names
  it only as a collection's element or inside an erased `ContentState`); `InviteeView` is named by `AddPeopleEvent.Remove(invitee:)`.
  So `ActivityInvitesTexts`' functions taking an `InviteView` are what give it a type in every build, as `ZoneTexts` did for `Zone`.
- **A content that is a class reads through the erased value.** `ActivityInvitesState.content.data` casts to `ActivityInvitesContent`
  and `AddPeopleState.content.data` to `AddPeopleContent` on every sample, the loading and failed defaults included; their lists read
  through the iterator as `InviteView` and `InviteeView` (two invites and one invitee loaded, none while loading, failed or empty), and
  `AddPeopleContent.full` reads as `Bool`. Only a content that is itself a list can't cross the erased value (the gear probe); the views
  still read typed getters on the state, so neither UI asks what the content holds.
- **`ActivityInvitesTab` is `CaseIterable`**, `allCases` `[Pending, Declined]` in Kotlin's order, as `FollowRequestsTab` is.
- **The events take what Swift holds.** `ActivityInvitesEvent.Open(invite:)` and `Decline(invite:)` take the `AtUri` an `InviteView.uri`
  is, and `AddPeopleEvent.Remove(invitee:)` the Swift `InviteeView`, read from the generated initialisers.

### Phase 4 probes (uploads, `ios-native-uploads`, 2026-11-10)

The upload queue is the first native page whose loaded content is a `:core-data` model holding plain Kotlin lists, and the first with a
length of time. Asked with a throwaway Swift test over every `UploadsSamples` sample on a cloned iPhone 17 simulator (iOS 26.0), and by
reading the generated Swift:

- **`UploadQueueView`, `UploadImport`, `UploadItem` and `RejectedChange` already had Swift types**, because the samples name them, and
  their `List` fields are generated as typed arrays (`[UploadItem]`, `[UploadImport]`, `[RejectedChange]`; `UploadSource.Export.kinds`
  as `[String]`).
- **An empty plain list reads.** The empty queue's five fields are `emptyList()`, and each read as an empty Swift array without stopping
  the app. So the `List`-getter crash is the immutable library's shared empty instance alone; Kotlin's `EmptyList` and a `take`'s copy
  are safe, and the native page reads the model's fields directly.
- **`kotlin.time.Duration` arrives as a class**, boxed from the value class, with `inWholeSeconds` and the other whole-unit getters:
  the part-way sample's `timeLeft` read 93,600 seconds. An `#expect` comparing that `Int64?` with the literal `26 * 3600` failed while
  printing 93600 on both sides, so a test compares with a typed value.
- **The rest cast as expected**: `content.data as? UploadQueueView`, `UploadSource.Export` and `.Files`, `ImportSaveStatus.Saving`,
  `UploadState` and `RejectedChange.Kind` as Swift enums, `AtUri.rkey`, `savedAt` and `serverPausedUntil` as `Instant`s, and
  `expandedImports` through `elements(as: String.self)`, empty and not.
- **Swift can make a Kotlin `TimeZone` and `Instant`**: `TimeZone.Companion.shared.of(zoneId:)` and `currentSystemDefault()`, and
  `Instant.Companion.shared.fromEpochMilliseconds(epochMilliseconds:)`, exported because other signatures name them. `fromEpochSeconds`
  is ambiguous from Swift (an `Int64` and an `Int32` overload of its adjustment), so Swift goes through milliseconds.

### Phase 4 probes (segment lists, `ios-native-segment-lists`, 2026-11-10)

Starred segments, a segment's leaderboard and Mayors, and a dive site's Regulars are opened from the own profile, the segment page and
the site page, all still the shared UI's. Asked with a throwaway Swift test over every `StarredSegmentsSamples`,
`SegmentLeaderboardSamples`, `SegmentMayorsSamples` and `DiveSiteRegularsSamples` state on a cloned iPhone 17 simulator (iOS 26.0), and
by reading the generated Swift, before the texts or views were written:

- **Every entry type already has a Swift type.** `LeaderboardEntry`, `EffortView`, `MayorEntry`, `DiveSiteRegularEntry`, `SegmentView`
  and `SegmentRecord` are generated in `ActivityTrackerCoreLexicon.swift`, since each state's `viewerEntry` names its entry (and
  `StarredSegment`, a contract class, names `SegmentView`). The erased items cast to them on every sample (three leaderboard entries,
  two Mayors, two regulars, two stars once loaded; none while loading, failed or empty), and `rank`, `elapsed`, `startedOn`,
  `activity`, `efforts`, `visits`, `reachedOn`, `currentName`, `distanceMeters`, `record.sport` and `record.kind` (nil) read typed.
- **The boards' empty lists read.** `boards` (the default `persistentListOf()`) reads as `[]` through `elements(as: String.self)`, and
  Starred segments' `removing` (`persistentSetOf()`) through `elements(as: AtUri.self)`; no sample lists types or a viewer entry.
- **An event takes a nil choice.** `SegmentLeaderboardEvent.AgeGroupChanged(ageGroup: nil)` builds and reads back nil, and `"45-54"`
  as itself, so "All ages" needs nothing of its own.
- **A new choice is a first page.** The loading and failed samples are `PagedShown.firstPage` (no answer yet), and the presenters key
  their paging on the filters, so a choice shows a list that hasn't answered: the shared screen keeps its filters over that first
  page's spinner, and a native board must too, as must Starred segments over its sign-in row (its paging is disabled then).

### Phase 4 probes (edit profile, `ios-native-edit-profile`, 2026-11-10)

Edit profile is the largest native form yet, and the first whose contract holds an enum list, a date as text and a second photo
compression. Asked with throwaway Swift tests and scratch `IosApp` entries on a cloned iPhone 17 simulator (iOS 26.0), and by reading the
generated Swift, before the getters or the view were written:

- **`nowHidden` is an untyped `ImmutableList` of an enum.** Its generated getter names no element type, and its elements are
  `DetailGroup`s, which no collection can carry to Swift (`ios-native-settings-pages`), so it wasn't read; the view asks a getter per
  group (`isNowHidden(group)`) instead.
- **`DetailGroup` itself crosses fine.** It is a Swift enum (`.Location`, raw value its ordinal) with `visibility(visibility:)`, which
  read `DetailsVisibility`'s fields as `String?` (nil for a group not set); `VisibilityChanged(group: .Body, visibility:)` builds with a
  case. So a contract function taking a group is the way to ask about one.
- **Every `EditProfileSamples` state reads**, and their visibilities are all nil: the samples never fill them, though the presenter
  always does. The native picker needs a value, so the samples now carry one per group and `visibilityOf(group)` reads a missing one as
  Only me.
- **`AvatarImage` casts both ways**: `Saved(url:)` and `Picked(jpeg:)` built in Swift read back through `as?`, the bytes as a
  `ByteString`. The companion's limits read as `Int32`.
- **The avatar's compression reads a HEIC.** `compressAvatar` (FileKit's JPEG over UIKit) turned a 2,000 × 1,500 JPEG and a HEIC of it
  into 512 × 384 JPEGs of about 4 KB, and four bytes of junk into nil (a throw, caught). So the gear form's JPEG retry is never needed for
  it, and stays harmless.
- **`EditProfileState.description` never reaches Swift.** The generated Swift has every field but that one: Swift export leaves out a
  member named `description`, which every Swift object already has, with no warning. Found reading the generated Swift for the bio's
  field; the contract gains `bio`, a getter for the same `TextFieldState`.
- **A plain `List` of a class crosses both ways.** A Kotlin function taking and returning `List<PickedPhoto>` took a Swift array and
  returned a typed `Array<PickedPhoto>`, reversed; an empty one came back empty. So the country search takes and returns arrays.

### Phase 4 probes (zones pages, `ios-native-zones-pages`, 2026-11-10)

Training zones, the default and sport zones forms and Privacy zones need no chart or map: their pages are lists, menus and number
fields. Asked with a throwaway Swift test on a simulator made for the change (iPhone 17, iOS 26.0), over the shared samples:

- **The zone models read typed.** `ZonesState.heartRateModels.elements(as: ZoneModel.self)` gives the five heart rate models with their
  tokens, and `basis` reads as Swift's enum (`.ThresholdHeartRate`), since the states' own `heartRateModel` property names the class.
  `Zone.name` already read as its enum (`ios-zones-contracts`).
- **A `:core-data` enum with a property reads whole.** `SportZonesState.paceUnit` is `.PerKilometer` with `meters` 1000.0, and
  `defaults.maxHeartRate` an `Int64?` (190): a contract enum carrying a token and limits can name a field for Swift.
- **A digit typed in a burst after a refused letter is lost.** Driving `TextFieldModel` as the field does when keystrokes arrive before
  the refusal's redraw (`"9"`, `"9a"`, then `"9a5"`) under digits only leaves `"9"` with two refusals: the second edit holds the refused
  letter too, so the 5 goes with it. This is the gear weight's open item; this change makes digits only keep an edit's digits.

### Phase 4 probes (activity card, `ios-native-activity-card`, 2026-11-11)

The card is the first native piece every page listing activities shares, and the batch import's page the first drawn with it. Asked with
a throwaway Swift test over the shared samples on a simulator made for the change (iPhone 17, iOS 26.0), before the texts or views were
written:

- **`ActivityView` already has a Swift type**, because `ActivityListEvent`'s cases name it: `ImportBatchSamples`' erased page items (2 of
  2) cast to it, and `likeCount` and `commentCount` read as `Int64`, `viewer?.like`, `author.avatar`, `labels` and `media` typed (nil on
  these samples), `visibility` as `"public"` and `record.utcOffset` as `-360`. `FeedSamples.activities` read through
  `elements(as: ActivityView.self)`, and the first one's `group` as one partner, Carol.
- **The shared list events build from Swift**: `ImportBatchEvent.Activity(event: ActivityListEvent.ToggleLike(activity:))`, so a native
  card sends what the shared one does, wrapped by its host.
- **`Revealed` reads and reveals**: `contains(key:)` and `eventSink` are callable, so a page's revealed photos need nothing new.
- **Not probed with throwaway code**: a route's nested lists and a time of day in an offset, both this change's own Kotlin, checked by its
  tests instead.

### Phase 4 probes (host sub-screens, `ios-host-subcircuits`, 2026-11-11)

Asked with throwaway code on a simulator made for the change (iPhone 17, iOS 26.0), after the host's own Kotlin was written and its JVM
tests passed: a Kotlin helper built a `SessionHost` (`sessionHostOf`) over a stand-in feed presenter whose state held a real
`FeedFilterScreen`, and a stand-in filter presenter, ran it on the main thread with the display-link clock, and `IosApp` handed the
feed's record to a Swift test.

- **The lookup crosses as written.** `HostRecord.sub(screen: Any, outerEventSink: (SubCircuitOuterEvent) -> Unit)` is
  `sub(screen: any _KotlinBridgeable, outerEventSink: (any SubCircuitOuterEvent) -> Void)` in Swift, with no stub: the screen object the
  state holds (`FeedFilterScreen`, a plain class in Swift) passes as is. `HostSubRecord.state` is a typed flow of
  `Optional<any SubCircuitUiState>`, its `value` cast to `FeedFilterState` with `as?`, and `asAsyncSequence()` followed it (narrowed,
  then cleared).
- **Asking twice gives the same object** (`===`), sending outer events to the newest closure; after `close()` the same screen gives a new
  one.
- **Events and outer events.** `SetRange(range: DateRange.LastDays(days: 30))` and `Clear.shared` sent from Swift reached the sub-screen's
  presenter; its outer events reached the closure as `FeedFilterOuterEvent.Changed`, with the filter readable (`Changed(nil)` when first
  shown, then the last 30 days, then nil).
- **`SubScreenView` draws and redraws.** A second throwaway test put `SubScreenView { FeedFilterBar(state:) }` over the same record in a
  window: the bar drew "Any time", and after a `SetRange` sent through the drawn state's sink it redrew "Last 7 days" with Clear, the
  outer events following.

### Phase 4 probes (gear item, `ios-native-gear-item`, 2026-11-11)

The gear item page lists activities with the native card and shows lexicon views no native page had read. Asked through the text tests
over the shared samples on a simulator made for the change (iPhone 17, iOS 26.0), and by reading the generated Swift:

- **`GearPage` gives its lexicon views Swift types.** `GearView`, `GearComponent` and `GearMountView` read typed (aliased as
  `LexiconGearView`, since the page's view needed the name), `components`, `previous` and `mounts` through `elements(as:)`, a
  component's `from` and `to` as `Instant`s. `GearUsage.bySport` and `byYear` are plain lists of classes nothing exported names, so the
  page never reads them: `GearPageTexts.bySport` and `byYear` give `GearFigure` rows instead.
- **A content of a nullable value reads through its typed getter.** `ContentState<GearPage?>` is read from `state.page` inside
  `ContentStateView`'s form without a value, as Ignored devices reads its list.
- **`linkedProfiles`, an immutable list of `String`, reads as `[String]`** through `elements(as: String.self)`.
- **A `GearState` function taking a `TimeZone` and an `Instant` is callable from Swift** with `clock.kotlinZone` and `clock.now`, and
  its `LocalDate?` result turns into a `CalendarDay`.

### Phase 4 probes (profile, `ios-native-profile`, 2026-11-11)

The profile is the last shared page between the You tab and the native pages it opens. Asked by reading the generated Swift for
`:feature-profile-api` and `:core-lexicon` (the migration branch's Debug export), before the texts or the view were written:

- **`ProfileState.profile` is typed** (`ProfileView?`), as are `viewerDid`, `revealed`, `reporting`, `error` and the counts
  (`followRequestCount` an `Int32?`). `activities` and `posts` are the erased `ContentState` and `PagedState`, so the activities need a
  typed getter, as every list so far has.
- **`ProfileView.description` never reaches Swift.** The bio is dropped like `EditProfileState.description`; every other field is there
  (`details`, `viewer`, `followPolicy`, the counts as `Int64?`, `labels` as a typed array). The page reads the bio through a getter of
  another name.
- **Report and Posts can't be drawn natively.** Report's sheet is `SubCircuitContent(ReportScreen)` and a post is the shared `PostCard`
  (with the quote sheet and Report), so neither has a native form; a native page can't host part of the Compose page, since
  `IosApp.screenViewController(record:)` draws the whole screen.

### Phase 4 probes (posts, `ios-native-post-card`, 2026-11-11)

Asked by reading the generated Swift for `:feature-post-api`, `:app-common-api` and `:core-lexicon`, before the views were written:

- **A post's embed is a sealed protocol Swift can't read well.** `PostView.embed` is `any PostEmbedView`, whose `Unknown` member holds
  a `JsonObject`, and `QuotedPost` and `QuotedPostEmbed` are the same; so the card reads typed getters (`PostCardContent.proposal`,
  `quoted`, `quotedLink`, `quotedUnavailable`, `embedUnknown`) and never casts.
- **`PostPageState.content` and `PostQuotesState.quotes` are erased**, as every `ContentState` and `PagedState`: the page gets a typed
  `page` getter, and the quotes go through `PagedList` with the item named. `PostPage`'s own fields (`post`, `comments`, `likesList`,
  `repostsList`, `error`, `quote`) are typed.
- **`CommentsState` and `CommentThread` hold their comments in `ImmutableList`s**, read with `elements(as:)`; the thread structure, the
  indent and each comment's menu are flattened in Kotlin (`CommentsTexts.rows`) so Swift doesn't walk threads itself.
- **`QuoteFields` is a plain part of each host's state**, every field typed (`audiences` an `ImmutableList` of text), so the quote sheet
  is a SwiftUI sheet over it and needs no sub-screen.

### Phase 2 findings (`ios-native-shell`)

What building the host and the shell on Swift export showed, beyond the probes:

- **A flow's elements reach Swift only as objects.** The generated flow support turns each element into a Swift value with a cast
  (`__createBridgeable(...) as! Element`). That works for an object, a number and a string. A Kotlin enum is not converted into its
  Swift enum and the cast stops the app (`Could not cast value of type '_KotlinExistential<…>' to '…SessionMode'`); a list is not
  relied on either. A property of an object is converted properly, so each such flow in the host has a twin whose elements are small
  objects holding the value (`SwiftFlows.kt`: `shown`, `shownMode`, `shownTab`).
- **An interface-typed value needs its Kotlin class's generated Swift class in the binary, and Xcode links only the generated modules
  the app's Swift refers to.** The generated modules are objects in one static library, and an object nothing references is left out.
  Reading `HostRecord.screen` (an `any Screen`) for the feed's screen, whose module no Swift code named, stopped the app inside the
  runtime's fallback (`_kotlinGetExistentialType`). `-ObjC` does not pull the modules in, and `-force_load` of the whole library fails
  to link, because the library also holds stale objects from earlier exports. So Swift never reads a screen object: a record carries
  its screen's class name (`screenName`), the registry of native screens is keyed by it, and registering a screen names its Swift
  types, which is what links their modules. A state is read only for a native screen, by a view that names its type.
- **A public constructor is exported and followed.** The hosts took `Set<Presenter.Factory>` in public constructors, which led the
  export to Circuit's `CircuitContext`, whose generated Kotlin bridge doesn't compile (a `MutableMap<KClass<*>, Any>` read as
  `MutableMap<Any?, Any?>`, the erased-generic problem again). Their constructors are `internal`. A top-level function of a module
  reached only through signatures is not exported, so the factory other modules' tests use (`appHostOf`) costs nothing.
- **The host's types export as designed**: `RootState` with `sealedType()`, `KotlinTypedStateFlow<any RootState>`, a typed flow of
  each screen's `CircuitUiState?`, Kotlin enums as Swift enums when they are properties, `[TabStack]` from a `List` property.
- **Stale generated modules stay in the build products.** A `CoreLocation.swiftmodule` left by an earlier export, when `:core-location`
  was generated under that name, shadowed Apple's CoreLocation for the new test target (`compiling for iOS 17.0, but module
  'CoreLocation' has a minimum deployment target of iOS 18.0`). Deleting it from Xcode's build products and
  `iosExport/build/SPMBuild` fixed it; a clean checkout never has it.
- **Hiding the tab bar.** A screen that keeps the tabs says nothing about them. Asking for them to be visible on every other screen
  kept them over a whole-window flow pushed on top.
- Memory with the shell on the iOS 26 simulator, signed in, with the feed, an activity with its map, the segment browser's map and
  the profile open across the three tabs: 259 MB. There is no figure for the single Compose stack in the same state to compare with.

### Phase 3 findings (`ios-native-foundations`)

- **Every placeholder is a string on iOS.** A `%lld` filled by `String(localized:)` is grouped by Foundation ("2,026" for a year),
  where ui/number-format has the shared `formatWhole` decide what is a quantity. So the generator writes `%@` for `%d` too, Xcode's
  symbols take `String` arguments, and a number goes in as text Kotlin formatted. It also lets one lookup fill any string from a
  list of texts, which is what resolving a choice made in Kotlin needs.
- **Kotlin resolves, Swift only looks up.** `AppText.resolve(source)` walks arguments and fallbacks in Kotlin and asks a
  `TextSource` for each string; Swift implements those two methods over the catalog (`String(format:locale:arguments:)` with the
  quantity first selects a plural's form by name, as the symbol does). A Kotlin interface implemented by a Swift class worked as
  the FIT decoder's does.
- **A library's extension properties don't reach Swift.** `ContentState` exports its three fields and no `hasLoaded`, `errorOrNull`
  or `isLoading`, since the library comes through only as far as signatures name it. The rule those express moved into
  `ContentShown` in `:app-common-api`, which both UIs now ask.
- **An object's `Map` or `Regex` field fails the strict stability check** in an `-api` module, though nothing composes it. They are
  file-level private values instead, which the check doesn't read.
- **Names replaced typed resources in Kotlin** for what moved (112 error strings, the unit strings, the sport images). The error
  string's name is a rule (`error_` and the kind in snake case, one exception listed), and `SharedTextNamesTest` fails for any name
  with no resource.
- **`Tab` with a `LocalizedStringResource` title is iOS 26 only.** The tabs use `Tab(value:content:label:)` with a `Label`, which
  iOS 18 has.
- **A view snapshots faithfully only in the app's window, with time stopped.** Rendered off screen, a `List`'s rows drew as separate
  capsules; and a spinner was at a different turn on each run until the view's layer speed was zero.
- **The test target has main-actor default isolation too**, so what a test trait reads (`Snapshots.comparesHere`) is `nonisolated`.
- **`ProfileSamples.self` was the fixtures' one member Swift export can't carry**; it is `own`, under the same sample name.
- Xcode's symbol for a plural names the quantity (`.likeCount(quantity: 3, "3")`), and for an array item its index
  (`.segmentRankOrdinals2`).

### Phase 4 findings (settings, `ios-native-settings`)

- **A screen pushed natively stayed blank, because of how `Following` followed its flow.** It hung the task that follows the flow on
  its content, and content that draws nothing for the first value (a screen before its presenter's first state) is no view at all, so
  the task never started and the state never arrived. Sign-in never met it: its presenter has always published by the time its view
  is made. `Following` now follows from a `ZStack` around its content. Found by walking the app, not by a test: nothing in the test
  target can make a Kotlin flow that changes.
- **The layout that was proposed didn't survive its probe** (a `NavigationStack` per column; see the probes). What was built is
  simpler for it: no navigation container in Settings' destination, the stack's own bar and back, and the list as one view narrow and
  wide, which keeps its scroll position across the width without any code for it.
- **Kotlin decides, Swift draws, again.** Whether a stack ends in "Settings and only settings pages" depends on an interface of each
  screen, which Swift can't see without reading screen objects. So the host reports the layout as data (`shown.split`,
  `shown.pushed`), from the one rule the Compose decoration now asks too (`settingsPagesOnTop`), and the only thing Swift reports is
  its size. Which record gets the two-column destination is also Kotlin's fact (`paneDefault`), not a screen name in Swift.
- **Portrait only was the Xcode template's setting, not a decision,** and it had been read as a constraint. The app now turns, and
  iOS has its own rule for the split, a size in points (`settingsSplitFits`: 700 wide, 500 tall): 840, the other platforms' dp
  number, told iPads apart by model for six points, and width alone would split every iPhone on its side.
- **A record can live beside a stack.** Activity defaults beside the list isn't "a page opened", so it can't be on the stack. The
  host keeps its retained state with its Settings record and runs it only while the UI reports it visible, which `ScreenView`
  already did for every record it draws.
- **A fallback page works in a column unchanged**, with two small facts fed to it: it must not hide the navigation bar, which there
  is the stack's and not its own (`ScreenView(inPane:)`), and its Compose bar hides its back button through `LocalIsPaneRoot`, read
  from `HostRecord.paneRoot`, since one cached view controller draws the page both beside the list and alone.
- **Equality of screens stays in Kotlin.** The list marks a row by asking `SettingsPane.marks(page)`, so the blocked and muted lists,
  two values of one class, are told apart by Kotlin's `equals`. Swift builds the screens it sends in `SettingsEvent.Open`, which is
  also what links the gear and profile contract modules into the app.
- **A Kotlin object in SwiftUI's environment needs a Sendable wrapper** (`SettingsLayout` over `SettingsPane`), as `Stats` needed
  for the unit system.
- **Snapshot references are the same on any iPhone of the recorded OS**: recorded on an iPhone 17 simulator and compared with the
  ones recorded earlier on an iPhone 17 Pro. A view can be snapshotted at an iPad's size (1,032 points wide) on an iPhone simulator.
- **A row's summary wraps under its title's priority.** A long summary ("1 zone, start and end hidden") takes two lines beside a
  title that keeps its one. In a column much narrower than any real one the summary collapses to a letter a line: the rows haven't
  been through a Dynamic Type audit.
- **Taps sent to the simulator right after launch arrive late.** Two were delivered seconds later to a different screen and opened an
  activity's menu. Nothing was chosen from it; the walk went one action at a time after that.

### Phase 4 findings (settings pages, `ios-native-settings-pages`)

- **A page's navigation title is lost in the detail column**, so a native page beside the list titles itself in a header (`PageBar`
  through `pageTitle`), as the fallback pages' own Compose bars already do. Alone it uses the stack's bar. One modifier covers both,
  told which by `\.inPane`, which `ScreenView(inPane:)` now sets for a native screen too.
- **The form's own picker row moves its value under a long label.** A category with its help (Physiology, Dive data, Gear) got its
  choice on a line of its own, left-aligned under the help. Caught by the user on the first walk; the snapshots had shown it and it
  had been taken for the system's layout. A throwaway snapshot compared the ways out: `LabeledContent` wraps the same way; a menu
  picker beside the label stays right but brings a button's padding, making every row 64 points tall instead of the form's 52; a
  `Menu` holding an inline `Picker`, with the current choice and the system's up-down chevron as its label, keeps both the row
  height and the choice on the right, and opens the same checked menu. The rows are that (`ActivityDefaultsView.picker`), with the
  visible label hidden from VoiceOver and the menu given the row's name and value.
- **The simulator tool's tap doesn't flip an iOS 26 switch.** A quick tap on a `Toggle` did nothing, on both switches; a tap held
  for a quarter of a second flipped it, and the save went through. Menus and the segmented choice take the quick tap.
- **The native header sits about 8 points higher than a fallback page's own Compose title** in the same column (iPad Air 11-inch,
  iOS 26.0). Both are under the bar over both columns and centred over the column; the difference is the Compose bar's own top
  padding. Left as it is: it shows only when choosing between a native and a fallback page, and goes when the pages are native.
- **Conditions belong with the text.** The shared pages decided in composables when each switch is offered, when it can be changed
  and which grouping choice shows as chosen. Those moved to getters on the states in `-api`, beside the text, so the two UIs can't
  drift; getters export to Swift like fields and cost the strict stability check nothing.
- **A list of enum values reaches Swift as a list of contract objects.** `groupOptions` holds `GroupOption`s, each with its
  `GroupChoice` as a property, which Swift reads as its own enum and switches over, and with its label as an `AppText`. It also takes
  the order of the choices out of both UIs.
- **One note said once.** The shared page repeats the Followers note under each picker set to Followers; a grouped form says it once
  under the section that has such a choice. The words and the condition are Kotlin's; only where it sits differs.
- **A snapshot of a long form needs its whole height.** Activity defaults' failure text is at the end of the form, below a phone's
  height, so its samples are recorded 1,500 points tall.
- **Kotlin 2.5.0-Beta2's incremental compilation failed twice** on `:feature-settings-api` after `:app-common-api` changed
  ("The following FqNames can't be derived from DirtyData.dirtyLookupSymbols"), on the JVM and on the Android target. Rerunning the
  task (`--rerun`) compiles it; nothing in the code was wrong.

### Phase 4 findings (settings lists, `ios-native-settings-lists`)

- **A rule in a library extension moves into an exported module, again.** What a paged list shows around its items rested on
  `hasAnswer`, which isn't exported; `PagedShown` (`:app-common-api`) now decides it for both UIs, as `ContentShown` does for loaded
  content, and `pagedRefreshStatus` and `pagedFooter` ask it with their test tags unchanged (all 975 Android screenshots compared equal).
- **Load more needs the last item's own visibility, not "the end appeared".** `PagedList` keeps the ids of the rows on screen and asks
  for more while the last loaded item's id is among them and the state can load more. A flag set when the last row appeared would stay
  set after a page lands, with the old last row still on screen, and ask again at once. `PagedListTests` draw a list in a window: two
  items with more to come ask once, the last page asks nothing, a hundred items ask nothing until scrolled.
- **A set the view only asks about stays in Kotlin.** `BlockListState.isRemoving(account)` answers whether an account's change is in
  flight, so neither UI reads `removing`; Swift can read an `ImmutableSet<Did>` (the probe), but has no need to.
- **The glass behind a bar's buttons differs a little between runs on iOS 26.** The gear list alone, with Add and More in the stack's
  bar, failed a comparison once on 610 pixels off by at most 3 levels, all inside the buttons' glass capsule. Its snapshots allow one
  pixel in a thousand to differ (`Snapshots.barButtons`); every other snapshot still compares every pixel.
- **A list's totals keep their line.** The first recording wrapped "No activities yet" and "14 days · 41,000 m" into two lines beside
  the item's name; the shared row measures the total first. `AppItemRow`'s summary is fixed to its one line and the title column takes
  what is left.
- **Status rows outside a section sit at a section's spacing.** A refresh's failure over the gear list, and a next page's failure under
  the accounts, read as page-level notices rather than as part of the card; kept, since the shared UI also puts them outside its rows.
- **The profile menu's Mute reads Mute until the profile has learned it is muted.** Seen on the walk: after muting from a profile the
  menu kept offering Mute for a few seconds, while Muted accounts already listed the account. That is the shared profile page, not these
  lists.
- **`check`'s detekt counts an object's functions.** `LabelText` was at its limit, so the avatar's initial is `avatarInitial` beside
  `shownName` in `ShownName.kt`; Swift calls it as `AppPackage.avatarInitial(name:)`.

### Phase 4 findings (account, `ios-native-account`)

- **A deeper page in the column sends its own way back.** `pageTitle(_:back:)` takes the page's `Back` event, as the shared top bar's
  `onBack` does, and the header shows it only when `\.paneRoot` is false. A no-op default was rejected: it would draw a back button that
  does nothing on a deeper page that forgot one. `canGoBack: false` hides the header's button and, alone, the stack's
  (`navigationBarBackButtonHidden`), which takes the edge swipe with it.
- **Swift export's concurrency meets Swift 6's.** A Kotlin class passed from the main actor into a Kotlin `async` call is "sending
  'archive' risks causing data races": Kotlin's classes aren't `Sendable` to Swift. The archive is immutable and the write runs on
  Kotlin's IO dispatcher, so the one hand-off is `nonisolated(unsafe)`, and `ArchiveShare`'s injected write and sheet are `@MainActor`.
- **A `CharSequence` parameter doesn't take a Swift `String`.** `LeaveState.canLeave(typed:)` takes a `String` so the view can ask with
  the text it holds, which reaches the state's field a keystroke later.
- **Text the shared page put in a value's place reads badly there natively.** The Leave row's summary is a sentence; as an `AppListRow`
  value it wrapped into a narrow column of nine lines. It sits under the title instead, as a footnote.
- **The alerts are left to SwiftUI, and so are untested by snapshots.** `.alert` presents over the page, so the snapshots show the page
  under it; the alerts' words are held by `AccountTextTests` against the samples that ask them.
- **The time of day is the platform's.** Compose formats `AppText.Time` with `timeFormat()` in `LocalTimeZone` (`LocalHourClock` in
  previews), Swift with `Date.formatted(date: .omitted, time: .shortened)`, which follows the device's 12/24-hour setting as
  `uses24HourClock()` reads it on iOS.
- **Walked signed in on an iPhone 17 Pro and an iPad Air 11-inch (iOS 26.0), and launched to sign-in on iOS 18.4.** On iPad the share
  sheet is a popover centred over the window with no arrow, and the kept question follows it as on iPhone. Leave beside the list shows
  the header's back chevron upright and on its side, and the list keeps Account marked. Still unseen: a running leave, Sign out's
  question (only with work pending), cancelling a running export (a local export ends before a checked tap lands), and the pages on
  iOS 18 signed in.

### Phase 4 findings (gear pages, `ios-native-gear-pages`)

- **A `ContentState` of a list gets a typed getter, and the view never reads the value.** The probe above found the erased value
  unreadable when empty, which is every account with nothing ignored. `IgnoredSensorsState.sensors` returns the content's value typed,
  and `ContentStateView` gained a form without a value, which lays out progress and failure from `ContentShown` and leaves reading the
  content to the view. The walk opened the real, empty Ignored devices page natively on an iPhone and an iPad without a crash.
- **`AppItemRow` needs no change for a row without a picture.** `EmptyView()` as its leading view takes no space and no spacing in the
  row's `HStack`; the device rows line up with the section's inset.
- **A device's words now come from one place.** `LinkedSensorTexts` over `deviceName` (moved to `:app-common-api`, the same package, so
  the import's new devices compile unchanged) gives the device rows on Ignored devices, on the gear page's linked devices and, once the
  gear page is native, there too. The shared gear page's linked devices were seen on the iPhone reading "Garmin Edge 840 / Serial …2071".
- **The delete alert is the system's, bound to the state's question.** Dismissing sends `DismissDelete` and only Delete collection sends
  `ConfirmDelete`; the presenter pops the page once the delete lands, which the native stack follows like any pop.
- **Walked signed in on an iPhone 17 Pro and an iPad Air 11-inch (iOS 26.0), and launched to sign-in on iOS 18.4.** A collection made
  through the shared editor, opened natively, an item opened from it and back, Edit opened and left, Delete cancelled and then
  confirmed. On the iPad the collection and Ignored devices take the whole window from Gear beside the list, upright and on its side, and
  back returns to the list beside Gear. Still unseen: ignored devices with rows, the failure and deleting states on a device, and the
  pages on iOS 18 signed in.

### Phase 4 findings (follow lists, `ios-native-follow-lists`)

- **A native page pushed over a shared one needs nothing new.** Followers and Following push from the shared profile in the You
  tab's stack (and from another athlete's profile pushed from them), take the system's bar and back button, and a profile opened from
  them is the shared one again; the edge swipe and both back buttons go back through the mixed stack as it was left.
- **The system's navigation subtitle shrinks the title.** On iOS 26 `.navigationSubtitle` draws the title a size smaller (about the
  subheadline's, semibold) over the subtitle at the caption's size. `pageTitle(_:subtitle:)` uses it in the stack's bar, and draws the
  same two lines itself before iOS 26 (a principal toolbar item, the navigation title kept for the back button) and in a column header.
  A first recording in the header's usual 17-point headline read larger than the bar beside it; the gallery's `pageBarSubtitle` holds
  both together. The pre-26 form was drawn once on an iOS 18.4 simulator (a throwaway image, since references are 26.0's): two
  centred lines in the bar and in the header.
- **A contract enum's cases are Swift's `allCases`.** Swift export makes a Kotlin enum `CaseIterable`, so the segmented choice of
  Requests and Declined iterates it in Kotlin's order with no order written in Swift; the event takes the Swift case.
- **A notice that two features share is a rule in `:app-common-api`.** Whether a list shows its failed action or that the session must
  sign in again, and whether it offers Sign in again, was decided in `signInAgainItem`; `SignInAgainNotice` decides it for Follow
  requests, Activity invites and Add people in both UIs, with no visible change.
- **A list's explanation is its section's footer.** The Declined note sits above the rows in the shared page; natively it is the
  section's footer, where iOS explains a list, and the truncated note too (each is one tab's, so they never meet).
- **Walked signed in on an iPhone 17 Pro and an iPad Air 11-inch (iOS 26.0), and launched to sign-in on iOS 18.4.** Following,
  another athlete's Followers and an empty Followers list, opened from profiles, upright and on its side. Still unseen: Follow
  requests and Remove on a device (the test account doesn't approve followers, and the walk didn't turn that on), a second page, and
  the pages on iOS 18 signed in.

### Phase 4 findings (invite lists, `ios-native-invite-lists`)

- **Follow requests' shape carries over whole.** Activity invites is `FollowRequestsView` with other rows: the segmented choice over
  the enum's `allCases`, `RefreshStatusView`, the `SignInAgainNotice` row and the Declined note as the section's footer needed nothing
  new, and neither did Add people's two headed sections of `AppItemRow`s.
- **A row with two buttons has no room for a picture.** The first recording led each invite with its inviter's `Avatar`; beside Decline
  and View that left the text about 130 points on an iPhone 17, and "32.40 km · 1:05:00" broke after the dot. Without the picture, as
  the shared row is, it stays on one line. No string uses a no-break space, and one around the dot would only move the break into
  "32.40 km".
- **A joined line is a shared string, not Kotlin's `" · "`.** `InviteSummary` joined the distance and time with `joinToString(" · ")`;
  `ActivityInvitesTexts.summary` names `activity_invites_summary` instead, so the separator is the catalog's like every other word.
- **A feature's own text names need their own check.** `SharedTextNamesTest` lives in `:app-common-ui` and can't see a feature's
  `-api`, so `InvitesTextNamesTest` (`:feature-invites-ui`) checks every name `ActivityInvitesTexts` and `AddPeopleTexts` choose.
- **A long list's snapshot is drawn tall.** The full activity's 20 people push its note below an iPhone's height, so its snapshot is
  2,000 points tall (`assertAppSnapshot(size:)`); every other sample is the screen's size.
- **A worktree's build needs the checkout's `local.properties` to walk.** Without `activitytracker.deviceHost` the build isn't in device
  mode and calls the local hosts, while the signed-in simulators' session belongs to the public dev ones: every AppView call failed
  until the file was copied in and the app rebuilt. And a build for the iOS 26.0 destination doesn't run on 18.4
  (`libswiftCompatibilitySpan.dylib` isn't embedded), so the 18.4 launch takes a build made for that destination.
- **Walked** (see the change's tasks): Add people from an activity on the signed-in iPhone 17 Pro and iPad Air 11-inch (iOS 26.0),
  upright and on its side, back by the swipe and the button, and launched to sign-in on iOS 18.4. Still unseen: Activity invites on a device (the local network has no invites, and making one writes records),
  Add people with followers, and the pages on iOS 18 signed in.

### Phase 4 findings (zones contracts, `ios-zones-contracts`)

- **A kind list of objects reads in order, and a function taking the enum works.** `ZoneSummary.kinds` and `SportZonesState.kinds`
  read through `elements(as: ZoneKindOption.self).map(\.kind)` as `[.HeartRate, .Power]` and `[.HeartRate, .Speed]`, and
  `offers(kind: .Power)` is generated as a method taking the Swift enum and answers as the Kotlin one does. `ZoneKindOption.forSport`
  takes the sport rather than the set, so no exported signature holds a set of enums.
- **A text function is enough to give an element type a Swift type.** Once `ZoneTexts.label(zone:)` and `privacyZoneName(zone:index:)`
  named them, `Zone` (with `name` as the Swift `ZoneName`, `low` and `high` as `Int32`, the last `high` nil) and `PrivacyZone` (label,
  radius, centre) were generated, and the same lists' elements cast to them. The only stub it brought is `PrivacyZone.Companion.serializer`
  in `:core-lexicon`, the allowed serializer kind, in a module the check lists but doesn't fail on.
- **The `List` rule is about an empty persistent list.** Corrected from the settings probe: a `List` getter returning a non-empty
  persistent list reads as a typed array; the bridge stops the app only on the empty one. `shownRows` copies anyway, and the rules now say
  why.
- **Swift can't build a state to read, so the samples carry it.** Data classes have no `copy` in Swift and a constructor taking an
  immutable list has none at all, so a state a test needs is a fixture variant (`ThresholdHistorySamples.variants["allRows"]`).
- **No visible change.** The shared pages keep their words and layout; nothing was walked on a device, since nothing native reads these
  contracts yet.

### Phase 4 findings (gear editors, `ios-native-gear-editors`)

- **A form's Save in the bar needs its own condition.** The shared form's Save is the form's last row, so it isn't there before the item
  loads. Moved to the bar, it showed over the spinner and the load failure in the first snapshots. `showsSave` (the content has loaded)
  on both states decides it now, for both UIs' sake.
- **A rule in a Compose constant moved to the contract.** The weight's seven digits and the starting values' nine characters were private
  constants of `EditGearUi`; they are `EditGearLimits`, which the Compose fields and `TextFieldRules` both read, beside the records' own
  maximums. Swift counts what the athlete sees and Compose counts UTF-16 units, so text with emoji can stop at different lengths; every
  limit here is far above what a name or note reaches.
- **A joined list is text with a rule.** "Ride, Run" was `joinToString(", ")` over resolved labels in a composable; it is `AppText.joined`
  over a pair string (`text_list_pair`), which keeps the order and the separator in the catalog.
- **The type pickers' nesting was already a rule.** `sportTypeList` decided which sub-sport shows under its sport inline; `nestsIn` in
  `:app-common-api` is that rule, which it and `SportTypeGroup.rows` ask, so the native sheet nests exactly as the shared one (Gravel Ride
  under Ride; Indoor Row, filed under Gym, on a row of its own).
- **A sheet's Cancel that undoes nothing is left out.** The shared sports and default-for sheets send each tick at once and have a Cancel
  that does what Done does; the native sheets have Done only. Add items keeps Cancel, since its ticks are sent only by Add.
- **Edit mode for a whole form is the native reorder.** Held on for the collection form, it gives the items their handles and delete
  controls and leaves the name and notes alone (the probe). SwiftUI reports a move as the old index and the index to insert before;
  `MemberMove` turns that into Kotlin's final index (one less when moving down) and ignores a drop where the row was.
- **A view asks the state, not its lists.** Which sports and types are ticked are `isForSport(sport)` and `isDefaultFor(type)`, which
  the Compose sheets ask too, so neither UI reads `sports` or `defaultFor` to answer a membership question.
- **Walked signed in on an iPhone 17 Pro and an iPad Air 11-inch (iOS 26.0), and launched to sign-in on iOS 18.4.** An item and a
  collection were made, edited, retired and put back, reordered and cut, and deleted again, through the native forms; the photo picker
  needed no permission, and a picked photo shown on Gear afterwards came from the session's held bytes. A refused letter took the digit
  typed straight after it with it (open items). On iPad the calendar's popover points at the middle of its row rather than at the button;
  it is the row the popover is attached to. After Retire and Put back in use the shared gear page and the Gear list showed the change
  some seconds late, as the AppView caught up; the form itself had saved.

### Phase 4 findings (uploads, `ios-native-uploads`)

- **A length of time is a plural, not an `AppText` case.** `AppText.Time` exists because the 12- or 24-hour clock is a device setting;
  nothing about "About 3 h left" is, so its unit, rounding and words are `DurationText.approximate`'s, and neither resolver changed.
  The three `uploads_time_left_*` plurals became `duration_minutes`, `duration_hours`, `duration_days` inside `uploads_time_left`, with
  the same words. `StatText.duration` ("1:23:45") stays the clock form; another form joins `DurationText` when a screen needs it.
- **Day words need the UI's clock, so Kotlin takes it as arguments.** "Today", "Yesterday" and "tomorrow at" depend on the current
  date in a zone that previews and tests fix, so `DayText` and the queue's text functions take `now` and `zone`. Compose passes
  `LocalClock` and `LocalTimeZone`; Swift a `ShownClock` from the environment, whose text source also formats `AppText.Time` in its
  zone, so a day and a time of day are never read on two clocks. Without it the snapshots would read the machine's day and zone.
  `startTime` and `untilText` now resolve `DayText`, so the feed's day words are written once.
- **A list joined by a translated separator becomes a nested pair.** `uploads_list_separator` (", ") was joined in a composable; an
  `AppText` can't join, so it is `uploads_list_pair` ("%1$s, %2$s") folded over the names, and the refused and skipped counts a new
  `uploads_import_counts` ("%1$s · %2$s").
- **A row with its own colours stays the view's.** `AppItemRow` draws every line secondary, and a refused item's state is red, so the
  queue's rows are a small `UploadRow` beside the view, its actions outside the row's button as `AppItemRow` keeps them. No
  design-system piece changed.
- **Two alerts on one view, each bound to its own question.** `confirmingCancel` and `confirmingCancelRest` each drive an
  `.alert(_:isPresented:presenting:)`; closing either sends `Dismiss`. The presenter clears the question on confirming too, so the
  order SwiftUI calls the action and the binding's setter in doesn't matter.
- **Swift's `Duration` comparison trap.** `state.timeLeft?.inWholeSeconds == 26 * 3600` failed in a Swift test while printing 93600
  on both sides; `Int64(26 * 3600)` passes.
- **Reaching the queue on the local stack takes a batch import.** Uploads opens only from the feed's upload row (while something
  uploads or is refused) or an import's "View uploads", and local uploads finish in a second or two, so the row is gone before it can
  be tapped; a single-file import opens its activity instead. Two GPX files copied into the simulator's On My iPhone storage
  (`…/Containers/Shared/AppGroup/<id>/File Provider Storage`) and imported together end on "Import saved" with View uploads, which
  stays useful after they upload. Walked that way on an iPhone 17 Pro (iOS 26.0), signed in: the native page drew the import's group,
  its items and Finished, an item opened its activity, and back and the activity's delete returned to it. Every walk-test activity was
  Only me and deleted afterwards. A deleted activity's finished item stays in the upload log until the week's prune or Clear. In
  passing: after Save on the shared Add activity and single-file import forms, the fallback screen kept its last frame ("Saving…")
  under the returned tab bar until a tab was tapped.

### Phase 4 findings (segment lists, `ios-native-segment-lists`)

- **A board's filters are one shared shape.** Every choice on the three boards (period or window, scope, type, age, weight, gender) is
  a `BoardChoice` that both UIs draw: Compose as `BoardSegments` and `BoardChoiceChip` (replacing `ScopeControl` and `ChoiceChip`),
  Swift as `BoardSegments` and `BoardMenu`. The options reach Swift as an immutable list of `BoardOption` through `elements(as:)`, and
  `BoardChoice.isChosen(option)` is what gives `BoardOption` a Swift type. Swift binds a picker to an option's index, so no Kotlin class
  needs to be `Hashable`.
- **A list cell clips a row that scrolls sideways.** The first recording put the menus in a horizontal `ScrollView` from edge to edge;
  the inset grouped cell clipped it 16 points short of the screen's edge, and `scrollClipDisabled()` doesn't reach past the cell. The
  menus now wrap onto lines (`FlowRow`), as the shared filters' `FlowRow` does.
- **A new choice is a first page, so the choices must stay.** The board presenters key their paging on the filters, so choosing one
  shows a list that hasn't answered; `PagedList` drew a first page alone, centred, which would have hidden the choices just made.
  `keepsTop:` keeps the top rows and puts the first page's progress or failure under them. Starred segments doesn't need it: while it
  must sign in again its paging is disabled, and the page is the notice alone.
- **A day line beside a count is tight on an iPhone.** Inset margins, the disclosure mark and a 40-point avatar left "Reached May 20,
  2026" two lines beside "9 efforts". The row takes the shared row's 36-point avatar, a rank column that grows only for a long rank,
  and the footnote's size for the day, so it keeps to one line beside a usual count and wraps (rather than shrinking) beside a long one
  or at large text sizes.
- **Moving text to Kotlin grouped two counts.** The Mayors and Regulars counts were fed the raw `Int` in Compose; through `formatWhole`
  a thousand or more is now grouped on every platform ("1,204 efforts"), as ui/number-format says. Nothing else visible changed, and no
  Android reference moved.
- **A calendar date written out is shared now.** `DateText.date` ("Jan 5, 2026") is what the threshold history, effort lists and
  boards show; `DayText` keeps the day beside today. A date in the activity's own UTC offset (`startTime`) is still the shared UI's.
- **The Release build was already broken, before this change, and is fixed since.** `UploadQueueView` had a Swift type only through
  `:app-fixtures`, which a Release build leaves out, so `KotlinTypes.swift`'s upload queue alias didn't compile there
  (`ios-native-uploads` never built Release). This change's own Swift compiled there, and the stub report added only allowed
  serializers. The fix came after the merge: `UploadsState.queue`, a getter typed `UploadQueueView?`, names the class in an `-api`
  signature, as `IgnoredSensorsState.sensors` does for its list, and `UploadsView` reads it inside `ContentStateView`'s form without a
  value instead of casting the erased content. Release builds again with the segment lists, edit profile and zones pages merged.
- **Walked signed in on an iPhone 17 Pro and an iPad Air 11-inch (iOS 26.0), and launched to sign-in on iOS 18.4.** Starred segments
  with the account's one star in its units, its segment's leaderboard (a period restarting the board under the choices, the weight
  menu in pounds, a chosen class filling its chip) and Mayors (the eight windows, an entry opening the shared profile and back), by the
  swipe and the buttons, the iPad upright and on its side. Nothing was written. Still unseen: Regulars on a device (a dive site opens
  only from a dive activity, and the account has none), unstarring, a gone star, the viewer's own place apart, a type menu, and the
  pages on iOS 18 signed in.

### Phase 4 findings (edit profile, `ios-native-edit-profile`)

- **Swift export drops a member named `description` without a word.** `EditProfileState.description` had no Swift getter at all; the
  bio's field is `bio` in Swift, a getter for the same `TextFieldState`. Any contract field named like an `NSObject` member needs the
  same; only reading the generated Swift shows it.
- **A limit the shared form counts isn't a refusal natively.** The gear form refuses typing past a field's maximum; edit profile's
  shared form lets it through, turns the counter red and holds Save back. A refusing rule would also refuse every edit of a value
  already too long (saved by another app), even one that shortens it, so the native form counts as the shared one does.
- **Enum lists are asked per member.** `nowHidden` (`ImmutableList<DetailGroup>`) can't reach Swift, but `DetailGroup` itself can, so
  each section asks `isNowHidden(group)`, `visibilityOf(group)` and `followersNote(group)` of the state; the Compose form asks the same.
- **A date held as text crosses as a `LocalDate`.** The birthdate is `YYYY-MM-DD` text; a getter parses it (`birthDay`, malformed text
  no date) and the event gains a constructor taking a `LocalDate?`, so the gear form's `CalendarDay` and popover serve it unchanged.
- **A search rule can take what each platform makes.** The country order and match (a typed code first, then names containing the
  text) are Kotlin's (`CountrySearch`), while the names and the folding of case and accents are each platform's (`countryName` and
  `stripDiacritics` in Compose, `Locale` and `folding` in Swift); Swift passes and gets back plain arrays (the probe). The codes moved
  from `:app-common-ui` into `:feature-profile-api`, the one feature that offers them.
- **A UI-held choice stays the UI's.** Self-describe before any words are typed is a view state in both UIs (nothing is sent until then);
  what it means, what shows as chosen and what a choice sends are `GenderChoices`' functions, which take it as `chose`.
- **Samples with blank choices hide differences.** The samples left every group's visibility null, which the shared picker showed blank
  and a native menu can't; they now carry what the presenter always fills, and the Android Edit profile references show "Public" where
  they were blank.
- **The shell reaches a feature only through its `-api`.** `IosApp.compressAvatar` first pulled in `:feature-profile-ui`, which
  `checkModuleBoundaries` refuses; `compressAvatar` moved to `:app-common-ui` beside `compressPhoto`, and `:iosShell` takes only
  `:feature-profile-api` (for `EditProfileLimits`). A platform helper a native view needs from a feature's UI moves to a shared module.
- **A hidden field's model misses what the presenter seeds.** A `TextFieldModel` follows its `TextFieldState` only while its
  `AppTextField` is on screen (the field's task binds it). The gender words field shows only after Self-describe, so after the
  presenter filled the saved words its model was still empty, and a reopened form read "Not set" over a saved "Walk test". A
  condition that decides whether a field shows reads the Kotlin state (`state.gender.value`), never the model of a field not shown.
  The snapshots and previews didn't catch it because a sample's state is filled before the view is made; the walk did.
- **Walked on an iPhone 17 Pro (iOS 26.0), signed in as alice.test on the local stack.** Opened natively from the profile, filled
  from the saved profile; back kept nothing; the counter followed typing; the weight turned red and held Save back; the birthdate
  popover greyed out future days; the gender menu offered five choices and Self-describe its field; the photo picker opened with no
  permission prompt and showed the compressed photo; "us" listed United States first; a save updated the profile and a reopened form
  showed the saved photo from its URL; the public-until-now warning showed beside the Followers note and went once the country was
  unset. Every value was put back and saved, and the profile read as before.

### Phase 4 findings (zones pages, `ios-native-zones-pages`)

- **None of the four pages needed a chart or a map.** Training zones lists numbers and zones, the two forms are number rows and menus,
  and Privacy zones names each zone with a radius menu and never shows its centre (the shared page doesn't either). Only threshold
  history draws a chart; it stays the shared UI's, opened from the native Training zones.
- **A contract enum names a field for Swift.** `ZoneThreshold` (each zones field's `Threshold` token, digits rule and length, and
  `isValid(text, paceUnit)`) is a parameter and property Swift reads as its own enum, so no view needs `Threshold`'s strings or
  `ThresholdInput`, which Swift has no type for. The pace's length is the longest pace `PaceText` writes at the lexicon's slowest speed
  (6 characters), the heart rates' and power's the digits of their maxima.
- **A text function returning a plain `List` of a contract class reads as a typed array** (`TrainingZonesTexts.thresholds`, a list of
  `ZoneThresholdLine`): the rule that only an empty persistent list fails holds for function results too.
- **Save over Kotlin fields needs the view to read the models.** `canSave` reads the Kotlin text states, which SwiftUI doesn't observe;
  the forms read each `TextFieldModel`'s text in their body first, and validity is asked with the text the view holds
  (`isValid(threshold, text)`), as Leave asks `canLeave(typed)`.
- **A snapshot is at most 2,730 points tall.** At three pixels a point an image of 3,500 points (10,500 pixels) came back blank; the
  renderer's limit is 8,192 pixels. A sample whose whole page is longer is split (Training zones: one sport per variant).
- **A section header's text is bold on iOS 26.** A help paragraph placed as a header, as the zones forms and Privacy zones open with
  theirs, needs the subheadline in regular weight, or it reads as a heading.
- **A row of three texts wraps the wrong one.** With the range kept on its line, a long zone name broke mid-word ("Recov-ery") on a
  phone; `ZoneRows.ZoneRow` draws one line when everything fits (`ViewThatFits`) and puts the range under the name otherwise.
- **Walked signed in on an iPhone 17 Pro and an iPad Air 11-inch (iOS 26.0), and launched to sign-in on iOS 18.4.** A burst "17a0" read
  "170"; an out-of-range max turned red and greyed Save as typed; a Run override was saved, seen, given and taken back a visibility, and
  removed; beside Settings' list the default zones were the column's second page with a back button; Privacy zones changed its hidden
  distance and put it back. Removing a sport leaves its values in the threshold history (shared data, not this change), so Training
  zones keeps offering visibility and history until they are removed there. A privacy zone was added at the simulator's location
  (set with `simctl location`, since a simulator has none by default), its radius changed and put back, and removed by its swipe.

### Phase 4 findings (activity card, `ios-native-activity-card`)

- **The feed can't be first.** Its filter bar is `SubCircuitContent(state.filterScreen)`, and a SubCircuit's presenter runs only inside
  Compose's `SubCircuitContent`: the presenter host runs screens, not sub-screens. With posts, import cards, upload rows, two prompts and
  the report and quote sheets also shared, the feed is its own change, which starts with SubCircuits in the host. A batch import's page
  lists nothing but cards, so it is the card's first page, reached from the feed's import card.
- **A start time needs the activity's clock in both UIs.** `AppText.Time` formatted only in the device's zone; the card's start is on
  the activity's own offset. `Time` gained `utcOffsetMinutes` and `TextSource.time` takes it, so one `AppText` case still serves every
  time of day, and `StartText` holds the rule `startTime` had (the detail page's start line resolves it too). This closes the open item
  about the feed's start times.
- **A rule that needs geometry moves as placed points.** The line preview's decoding, smoothing and fit stay Kotlin's: `RouteShape`
  returns each piece's points on the tile, the ends and the breaks, and Swift only strokes them. The fit itself moved to
  `:app-common-api` as `TileFit`, which the shared `canvasFit` now asks too, once the Pair branch (merged in before archiving) had made
  `canvasFit`, `RouteCurve` and `TrackMath` take `GeoPoint`s.
- **A contract's list fields are `ImmutableList`s, a function's results plain `List`s.** `RouteShape`'s and `ImportBatchMonth`'s list
  fields first failed the strict stability check as `List`; as `ImmutableList`s Swift reads them with `elements(as:)`. Text functions
  (`stats(units)`, `notices()`, `locks()`) return plain lists, which arrive as typed arrays.
- **`description` again.** `ActivityCardContent.description()` and `LockLine.description` would have been dropped by Swift export like
  `EditProfileState.description`; they are `descriptionText()` and `spoken`.
- **A list row's buttons need styles.** A `.borderless` author button tinted the whole line in the accent colour; `.plain` keeps the
  line's colours and still takes only its own tap, while the row's tap gesture opens the activity. A long notice in a `FlowRow` ran off
  the card (the layout proposes no width to a chip), so the notices stack and wrap.
- **No gear line.** The task named the card's gear line, but the shared card has none: gear shows on the activity page only.
- **Walked on an iPhone 17 Pro (iOS 26.0), signed in as alice on the local stack, and launched to sign-in on iOS 18.4.** A batch of five
  Only me GPX runs (one zip, since the document picker offers no multi-select of loose files in On My iPhone) made an import card on the
  shared feed, which pushed the native page: month headings, and cards whose start, figures and route read as the shared activity page
  did; a card opened its activity and back returned to the page. The five were deleted afterwards. A deleted activity stays on the batch
  page until it is reopened, as on the shared page. Still unseen on a device: Like (the batch was her own), photos and their labels, a
  group, a dive, locks, a second page, VoiceOver, and the page on iOS 18 signed in or on an iPad.

### Phase 4 findings (host sub-screens, `ios-host-subcircuits`)

- **The UI asks; the host doesn't discover.** A contract listing its sub-screens would let the host run them alone, but every contract
  with one would change, the outer-event mapping would leave the Compose UIs or be written twice, and a generic holder in a contract
  meets Swift export's erased generics. Asking mirrors `SubCircuitContent`: the UI decides what shows (a sheet only while open) and maps
  outer events itself.
- **A sub-screen runs inside its record, not by its view's visibility.** SwiftUI's `onDisappear` also fires for a view a push covers,
  after the animation, in no fixed order against the host's recomposition, so a sub-screen stopped by it could be dropped instead of
  saved with its record. Inside the record's composition (`RunRecord`, keyed by the sub-record) it is saved, restored and forgotten
  exactly as under `SubCircuitContent`. A view's `@State` can't own a sub-record either: its initial value is rebuilt on every body of
  the view's parent, so a sheet closes its own (`HostSubRecord.close()`).
- **An enum's cases keep their Kotlin names.** `Selection.All` and `SportCategory.Running` are `.All` and `.Running` in Swift, not
  lower-cased as Swift's own enums are.
- **The feed's outside Clear didn't reset the bar (fixed).** `FeedPresenter` cleared by giving the bar a new `FeedFilterScreen(Default)`,
  which was equal to the first one when the athlete began from the default, so neither `SubCircuitContent` (keyed by the screen) nor the
  host remounted it: the feed showed everything while the bar kept its chips. The screen now carries a `generation`, which Clear bumps, so
  the cleared screen is a new one in both UIs; `FeedFilterThroughHostTest` clears through the host and gets a new bar at the default.
  Found by reading the code, not seen on a device.
- **A test entry beside `appHostOf`.** `sessionHostOf` builds a `SessionHost` over given factories for another module's test, which
  `FeedFilterThroughHostTest` (`composeApp`) uses to run the real filter presenter over the shared session fixture. Like `appHostOf`, a
  top-level function of a module reached only through signatures, so Swift export leaves it out.

### Phase 4 findings (gear item, `ios-native-gear-item`)

- **A rule over a list of totals is one function returning rows.** Which totals apply to a type (distance where it counts or matters,
  vertical for skis), the dives and bottom time, and when a breakdown shows at all were conditions inside composables; they are
  `GearPageTexts.figures`, `bySport` and `byYear`, lists of `GearFigure(label, value)` both UIs lay out in order, rather than a getter
  per total each UI would have to order alike.
- **Which error shows where is a getter.** The shared page split `error` by a private set of mount kinds; `componentsError` and
  `activitiesError` on the state say it once.
- **The unmount date's bounds are days, not picker millis.** `unmountFirstDay(zone)` and `unmountInitialDay(now, zone)` give
  `LocalDate`s, which Compose turns into its picker's millis and Swift into `CalendarDay`s. A `DatePicker` shows days in the
  environment's time zone, so a snapshot that sets only a UTC calendar showed the day before; the sheet reads the device's calendar,
  as the gear form's date does.
- **Mount dates stay ISO** ("Since 2026-07-01"), as the shared page writes them; `DateText` would be a visible change on every platform.
- **The collections are ticked rows natively**, as on the native gear form, not the shared page's chips and Add menu.
- **Walked signed in on an iPhone 17 Pro (iOS 26.0), as alice on the local stack, and tested on iOS 18.4.** Gear opened the native page for
  her Garmin Edge 840 and SRAM X0 derailleur (Only me, imported gear): the header with its brand and model, Mount a component, a
  linked device with its serial, the totals in her imperial units, and the activities counted as native cards with their routes and the
  owner's Gear lock; a card opened its activity and back returned to the page where it was scrolled. Mount a component opened the sheet
  over her four mountable items, and Cancel closed it. Nothing was saved: mounting would have changed her existing items. Still unseen
  on a device: a component's menu, Replace and the unmount calendar (her gear has no components), the collections (she has none),
  Previous and Mounted on, the breakdowns (one sport and one year each), linked profiles, a follower's view, a photo, and the page on
  iOS 18 signed in or on an iPad. The snapshots cover each of them.

### Phase 4 findings (posts, `ios-native-post-card`)

- **The first native comment list.** The post page needed comments, which the shared activity page shares; `CommentsSection` draws
  them from `CommentsTexts.rows` (one typed list in the shared order, each row with its key, kind, indent, words and menu) and is ready
  for the activity page. `CommentAction` moved to `:app-common-api` beside `CommentsTexts.actions`, which the shared list asks now.
- **Report waits on posts and comments too**, as on the profile: the native card's menu shows only Delete on the viewer's own quote,
  and the comment menus leave Report out. A spec delta (`moderation/reports`) records it as its own requirement, for the report branch
  to drop.
- **A view named for a Kotlin class.** The native page is `PostPage`, so Kotlin's loaded `PostPage` is `LoadedPostPage` in Swift.
- **`AppIconButton` takes Kotlin's text** now (`init(_:label: any AppText)`), for Undo repost and a vote naming its proposal.
- **`AppTextField` has a `.bare` layout**: the filled box with no caption over it, its label read by VoiceOver only, for a field whose
  hint already names it beside its own button (the comment field and Send). The stacked layout showed "Add a comment" twice.
- **Snapshot images are copied flat into the test bundle**, so two suites' tests of the same name (`card(sample:)`,
  `page(sample:)`) clash at build time ("Multiple commands produce"). Each suite's test names are its own (`postCard`, `postPage`).
- **A sheet with bar buttons needs `Snapshots.barButtons`** too: the quote sheet's glass Cancel and Post differ by a few pixels between
  runs, as the gear and profile forms' do. `FeedFilterSnapshotTests.typePicker` (the type sheet, with Cancel and Apply) doesn't use it
  and failed on every iOS 26.0 run here, with no visible difference: a follow-up for its owner.
- **The local stack had no posts.** A post shares its author's own proposal or quotes another post, so the walk made one by hand as
  rider1.test (sharing an adopted proposal), walked it, and deleted it. Walked on the signed-in iPhone 17 Pro (iOS 26.0): the profile's
  Posts tab, the card, the post page, Like and the likers, the quote sheet, the quotes list, Delete quote and a comment sent and deleted,
  every write put back.

### Phase 4 findings (profile, `ios-native-profile`)

- **Report and Posts can't ride along as fallbacks.** A native page can't host part of the Compose page, and swapping the whole page to
  the fallback while Posts is chosen or the report sheet opens would change it under the athlete's finger. So the native menu has Mute
  and Block only and the page lists activities with no Activities and Posts choice; both wait for SubCircuits in the host and a native
  post card, and the spec deltas (`moderation/reports`, `feed/posts`) say so. The Compose page keeps both.
- **`description` again, on a lexicon view.** `ProfileView.description` (the bio) has no Swift getter, like
  `EditProfileState.description`; `ProfileState.bio` reads it. A lexicon view's field named `description` needs a contract getter.
- **A count's plural takes formatted text.** The plurals' `%1$d` become `%2$@` in the catalog, so `ProfileTexts` passes
  `formatWhole` text for the counts, and `"N+"` for the requests, as every Kotlin count does.
- **A media label says nothing more to others.** On someone else's profile a media label only blurs the avatar; the notices are for a
  warning, the athlete's own note or a takedown. The first `labeled` sample expected a notice from a spam label beside it, which the
  shared profile never showed either (it passes no `warn`); a `warned` sample covers the warning.
- **The own entries are navigation rows.** The shared page lays Edit profile, Starred segments, invites and requests out as a row of
  buttons; natively they are a section of rows with chevrons, which is how iOS lists pages to open. Follow stays a button.
- **The page's name can't be `ProfileView`**: that is the lexicon's alias, so the view is `ProfilePage`.
- **`AppButton` takes Kotlin's text** now (`init(_: any AppText)`), as `AppSecondaryButton` already did.
- **The first native tab root has no title on an iPad.** The profile is the You tab's root, the first native page to be one. With the
  tabs at the top on an iPad (iOS 26.0) its inline title doesn't show, where the shared page drew the athlete's name as a large title;
  pushed (bob's profile) the title shows. The shell has no notion of a native tab root yet (`LocalIsNavRoot` is Compose's).
- **Walked signed in on an iPad Air 11-inch (iOS 26.0)**, since the signed-in iPhone was another session's: alice's own profile with its
  entries and cards, Edit profile and Following pushed natively from it, bob's profile from Following, Mute and Unmute, Block after the
  confirmation and Unblock, unfollow and follow, and a card opening the shared activity page; every write put back. Still unseen on a
  device: Requested, the own requests and invites entries, labels, the Bluesky note, an iPhone, and iOS 18 signed in.

### Phase 4 findings (report sheet, `ios-native-report-sheet`)

- **A page's record reaches a sheet through the environment.** The registry hands a native page only its state, so a sheet modifier
  deep in the page couldn't ask the host for its sub-screen. `NativeScreen` sets `\.hostRecord`; outside the host (previews,
  snapshots) it is nil and the modifier shows nothing, so a snapshot draws the sheet's view from a sample instead.
- **The modifier keeps the screen it opened with.** The carrier clears `sheet` on the same state that closes it, but SwiftUI is still
  sliding the sheet away, so the content draws the kept screen until `onDismiss`, which closes that screen's sub-record (asking the
  record for it again returns the held one). The Block confirmation, set in the same state, shows once the sheet has gone.
- **Swift builds no moderation target.** `ReportingEvent.Open` takes a `:core-data` `ReportTarget`; the profile's is built in Kotlin
  (`ProfileState.reportEvent(authorName)`), which the Compose menu asks too. A post's menu will want the same kind of getter.
- **The reasons are objects.** `ReportTexts.reasons` is a list of `ReportReasonChoice`s, each holding one `ReportReason`, since a
  collection of enums can't reach Swift; `ReportState.chose(choice)` and `noteRequired` decide the marks and the note's label.
- **The shared card offers no Report.** The task asked for Report in the native card's menu wherever the shared card offers it; the
  shared `ActivityCard` has no menu at all (an activity is reported from its page, still the shared UI's), so neither card has one.
- **Native layout differs where iOS has its own idiom.** The reasons are checked rows rather than chips, and the name sheet's name is
  a form row with what proposing shows in its footer, the shared sheet's line above the field.
- **A field a getter reads live must be read in the presenter's composition too.** `ReportState.canSend` read the note live, which
  Compose follows, but SwiftUI redraws only on a new state, and the presenter never read the note, so typing a required note left the
  native Send off (found on the iPad walk). `ReportState.noteWritten` is read there now, as `EditGearPresenter` reads its name. Any
  getter over a `TextFieldState` that a native control's enabled state follows needs the same.
- **The note limit is 1900**, not the spec's 2000 (`ReportRepository.MAX_NOTE_LENGTH`, which leaves room for a photo's note); both
  sheets refuse an edit past it.

### Phase 4 findings (post report, `ios-native-post-report`)

- **A post's Report needed no new getter.** `PostCardContent.menuItem()` and `menuEvent()` already chose Delete quote or Report, and
  the shared post sink turns `PostEvent.Report` into the sheet for the post, so the native card only learned whether Report is offered
  (`canReport:`, defaulting to off so the card's existing callers are unchanged).
- **A comment's Report was built in Compose code.** `CommentsUi` built `ReportingEvent.Open(ReportTarget.Comment(…))` itself;
  `Reporting.reportComment(comment, authorName)` (`:feature-report-api`, since `CommentsTexts` in `:app-common-api` can't see
  `Reporting`) builds it now for both lists. The author's name is the Compose list's (display name, else `@handle`).
- **The profile's Posts tab offers Report too**, as the shared tab does; the native profile already carried `reporting(_:)`.
- **The quotes list's references changed**: its samples have a labeler, so others' quotes now show their menu.
- **The local stack had no posts again.** A post sharing a proposal must use the proposal's own rkey (the indexer refuses another);
  the walk wrote rider1's post as `3mzq1` and a comment on it through the PDS, reported both from the iPhone 17 Pro to the local Ozone
  (`moderation_event` shows each report with its own subject), and deleted both.

### Phase 4 findings (feed, `ios-native-feed`)

- **A large title has no title menu.** `toolbarTitleMenu` drew its chevron and opened only once the list had scrolled the title inline;
  on the large title of a tab root a tap did nothing (iPad, iOS 26.0). The scopes are a chip leading the filter bar's line
  (`FeedFilterBar(leading:)`, an `AppMenuChip`), and the title's menu stays for the inline title. A tab root that picks what it shows
  from its title wants the same.
- **The bar can be a list row.** The shared feed pins its bar above the list because a lazy item scrolled off loses its state; the
  host keeps a sub-screen for its record's life, so natively the bar scrolls with the list and under the large title, and the first
  load's progress or failure is a row under it. Its type sheet presented from the row as before.
- **Pull to refresh waits on the state.** `refreshable` ends its spinner when its action returns, and the action only sends an event;
  `pullToRefresh` polls the page's refreshing flag (kept in a reference the task reads, since a view's properties are the values it was
  drawn with) until the refresh has shown running and ended, or briefly if it never shows running.
- **The outside Clear resets the bar** (seen on the iPad): Clear filters on the empty feed cleared the chips with the list, through
  the screen's `generation` (ios-host-subcircuits), so the open item about it is closed.
- **Counts grouped.** The upload rows and the import card passed raw ints to plurals in the Compose feed ("3000 imported"); as
  `FeedUploadTexts` and `ImportCardTexts` they pass `formatWhole` text, as `ui/number-format` asks, on every platform.
- **There is no next page.** The feed reads its cache and then the AppView's first page; the walk reached the list's end with nothing
  more to load, on the native page as on the shared one.
- **A snapshot draws a large title against the window's edge**, with none of the margin it has on a device, so the feed's snapshots
  use the inline title; the page is a tab root in the app.
- **Walked on the signed-in iPad Air 11-inch (iOS 26.0) as alice:** the native feed with her and bob's activities as native cards
  (badges, locks, a route, blurred photos), the scope chip to Mine and back (title and list followed), Last 7 days (chip filled, Clear
  shown, list narrowed), Swim from the type sheet (the empty message with Clear filters, which cleared the chips too), pull to refresh
  (the system's spinner, then settled), Add's three entries, a card opening its activity and back, the inline title's scope menu, and
  the end of the list. Nothing was written; the scope and filters were put back to Following with none.

### Phase 4 findings (search, `ios-native-search`)

- **A `safeAreaBar` under a large title blurs the title.** The choice placed in a bar under the navigation bar (`pageBar`'s iOS 26
  form) put the scroll edge effect over the large title, which drew faded and blurred on the iPhone and in the snapshots. The choice
  is the list's first row instead, as the feed's filters are, and sits over the embed in Segments mode, where nothing scrolls.
- **A shared screen can be embedded in a native page.** `SegmentBrowseViewController(record)` composes `CircuitContent` of a screen
  the host doesn't run, inside the session's Circuit locals, with a navigator that sends the record's latest state's events; Swift
  places it with `FallbackContainerController` and holds it in the page's `@State`, so switching modes keeps its composition (the
  map's camera, its query). Compose pads for the keyboard itself, so the embed ignores SwiftUI's keyboard safe area. Its edit menu
  ("Select All") can stay on screen after the keyboard goes, as in any fallback screen.
- **`searchable` binds to a `TextFieldModel`** as `AppTextField` does: the binding edits the model and the page follows the state's
  `values`, so a query the presenter keeps comes back after a profile is popped; Cancel clears it through the same binding.
- **Counts grouped.** The left-elsewhere changes count was a raw int to a plural in the Compose screen ("1200 changes"); as
  `LeftElsewhereTexts` it passes `formatWhole` text, on every platform.
- **Walked on the signed-in iPhone 17 Pro (iOS 26.0) as alice:** the prompt, "bo" finding bob, his profile and back with the query
  kept, "zzz" with no match, Cancel, Segments with the shared browse (located, segments listed, a segment's page opened and back),
  and the mode and map kept across Feed and across Athletes. Nothing was written.

### Phase 4 findings (map, `ios-native-map`)

- **MapKit, not MapLibre.** The stack table chose MapLibre Native for its styles; MapKit's SwiftUI `Map` needs no package and draws
  every mark (`MapPolyline` with a dash `StrokeStyle`, `Annotation` views for the markers), so the marks match every platform while the
  tiles are Apple's. The stack table says so.
- **Kotlin frames, MapKit follows.** `MapCamera` turns `RouteMap.frame` (MapLibre's zoom: a 512-point world at zoom 0) into a
  `MapCameraPosition.rect` of the view's own size: MapKit's world is 2^28 map points wide, so a point spans `2^28 / (512 · 2^zoom)` of
  them. The closest-zoom rule (16) comes with it; MapKit's own region fit has none. A Swift test holds the conversion to Kotlin's frame.
- **A Kotlin object isn't `Equatable` to SwiftUI's `onChange`**, so the map frames again on `RouteMap.key` (the route and its marks as
  text), not on every new state, which would undo a viewer's pan on an interactive map.
- **`annotationTitles(.hidden)` is a `MapContent` modifier**, per annotation; on the `Map` it doesn't compile.
- **A static map in a `List` row scrolls with the page**: `interactionModes: []` lets a drag that starts on it scroll the list (seen
  on the iPad), so no gesture shield is needed.
- **Previews and snapshots read `\.liveMaps`**, false unless `SessionView` sets it, so they draw `RouteSketch` and never fetch tiles;
  sniffing the preview or test host would miss one or the other. `RouteShape`'s ends became optional so the stand-in honours hidden
  ends (a dive site's outline).
- **The live Compose map keeps `cameraForBounds`**; only the feed's map images and the native map share `MapFrame`, so Android and
  desktop are unchanged (open item).
- **Text the page shared with the segment page moved once**: `PlaceTexts` (creator, credit, licence, status and its help, whether Open
  the original shows) and `PlaceNameTexts` (the names section, including the vote a tap sends) in `:app-common-api`; `PlaceStatus` now
  takes the texts rather than string resources. One new string, `place_name_proposal_line`, joins a proposal's author and totals.
- **Walked on the signed-in iPad Air 11-inch (iOS 26.0) as alice:** Blue Hole from a dive's page, with the live map (tiles, outline,
  Apple's credit), the about lines in imperial units, the Regulars card opening the native board, the conditions, the names and the
  dives as cards; a drag over the map scrolled the page; dark mode; a card opened its dive. Nothing was written.

### Phase 4 findings (interactive maps, `ios-native-interactive-maps`)

- **A full screen inside a native page runs through the host, not a Compose view.** Segment browse is a Circuit screen with a
  navigator, so `HostRecord.sub` (SubCircuits only) can't run it. `HostRecord.embed(screen)` gives a child record whose presenter gets
  the parent's navigator and runs inside the parent's `RunRecord` under its own key, as `CircuitContent` runs inside `AthleteSearchUi`;
  an equal screen returns the same child. Compose's round trip through `AthleteSearchEvent.GoTo` ends in the same `goTo`, so the
  native page skips it. `SegmentBrowseThroughHostTest` runs the real browse presenter this way.
- **SwiftUI's `Map` has no line hit test.** A tap is turned into a coordinate by `MapReader`'s proxy, a point 12 points away gives the
  reach in metres, and Kotlin picks the run (`SegmentsMap.nearest`, distance to each stretch, lifts excluded). MapLibre keeps its own
  rendered-feature query on the other platforms.
- **Where the map was left is the page's, not the presenter's.** The shared map saves its last camera with `rememberSaveable`; the
  native map keeps it in `SegmentsMapMemory`, held by the Search page, with the last camera request it took, so switching modes reopens
  it where it was (seen on the iPad). A request with a new id still moves it once.
- **`onMapCameraChange(frequency: .onEnd)`** reports the region on first layout and after each pan, which `MapViewport.ofDegrees` turns
  into the presenter's 1e-7 box, as MapLibre's `awaitViewport` and camera-stop do.
- **A browse snapshot at an iPad's landscape size renders offset by the test host's safe area** (the name field's label at the top
  edge), in or out of a `NavigationStack`; on the iPad itself the layout is right. The phone snapshots aren't affected.
- **The shared iOS location request answers granted at once** (the tracker asks the system), so the native view does the same when
  `askLocation` is set; the system's own prompt then shows once.
- **On a `Map`, a plain or simultaneous tap gesture never fires** (iOS 26; a log showed no call), so selecting a run needs a
  `highPriorityGesture(SpatialTapGesture())`, which leaves the map's pan and pinch working. **Inside a map's content `Color.accentColor`
  is the system's blue**, not the app's tint, so map lines take `Color("AccentColor")` by name (`RouteMapView` too). Both were found on
  the iPad, not in snapshots, which draw stand-ins.
- **A worktree with no `local.properties` builds an app that refreshes the session with the local client ID**, which the device-mode
  PDS refuses (`InvalidClientMetadataError`) — repeatedly, so the session could end. Copy the device-mode `local.properties` into a new
  worktree before installing its build on a signed-in simulator.
- **Walked on the signed-in iPad Air 11-inch (iOS 26.0) as alice**: browse beside the live map with the area's runs, nearest first, the
  capped note, a tap selecting a run and the list scrolling to it, a pan, the mode switch keeping the map, a name search with This area,
  Everywhere and Search everywhere, a segment opened and back, and from its page the native enlarged map with its credit, a pan and
  Recenter. Not seen: the sport filter's effect (the simulator's input never landed a menu choice), lifts (none there), and Accept invite
  (the stack has no invites).

### Phase 4 findings (charts, `ios-native-charts`)

- **Swift Charts draws the shared value, Kotlin chooses it.** `LineChart` (`:app-common-api`) holds the points, the y axis and its
  labelled ticks, the x ticks, a readout per point, the description and the band stops; `LineChartView` lays them out with
  `AreaMark`/`LineMark` per gap-free piece, `AxisMarks(values:)` at Kotlin's ticks and a `RuleMark` baseline. The generic rules moved to
  `:core-data` (`data.chart`) rather than `:app-common-api`, since `:ui-designsystem` holds no app types but already reaches
  `:core-data`; the Vico chart asks the same `ChartAxis`, and `GradeBands.brush` paints the same `BandStop.runs`.
- **A label at a chart's edge is dropped** by Swift Charts when it would overflow the plot; `chartXScale(range: .plotDimension(startPadding:
  18, endPadding: 18))` keeps half a label of room, as Vico's `FixedXLabels` does.
- **A SwiftUI drag over a list row takes the list's scroll**, even as a `simultaneousGesture` with `minimumDistance: 0`: on the simulator a
  vertical drag that started on the chart didn't scroll the page. A UIKit pan through `UIGestureRecognizerRepresentable` (iOS 18) that
  begins only when its velocity is mostly sideways (`ChartScrub`) scrubs, and every other drag is the list's.
- **A material background inside a chart annotation renders garbled** in snapshots (the marks' gradient shows through); the readout
  uses the page's background with a hairline.
- **A gradient tints a series across the marks' bounds**, which is the whole plot for a profile; band colours are catalog colours
  (`GradeBand0Color`…`4`) with the shared light and dark values.
- **An item of a `<string-array>` is a name too** (`AppText.named("segment_rank_ordinals_2")`): the iOS catalog already named items so,
  and the Compose resolver now reads `Res.allStringArrayResources` the same way.
- **Walked on the signed-in iPhone 17 Pro (iOS 26.0) as alice:** "Rush - 2112" from Search's segment browse, with the live map, the
  imperial lines, the tinted profile and its readout (a sideways drag, kept after release; a vertical drag from the chart scrolled the
  page), the OSM credit, Leaderboard, the Mayor card opening the native board, the efforts with PR opening their activity, dark mode,
  Star and unstar, and the expand button opening the shared enlarged map; "Easy Street" showed the ski run icon and no chart.

### Phase 4 findings (activity page, `ios-native-activity-page`)

- **The figures' words were the page's biggest Compose-only rule set.** `PageFigures.kt` (460 lines) chose every label and value with
  `stringResource`; it became `FigureTexts` over `StreamUnits` and `DiveText`, and the Compose helpers one line each. `text_dot_pair` and
  the new `text_arrow_pair` join a value's parts ("Air → EAN50", "100% → 70% · min 48%") as Compose's `joinToString` did, and
  `table_cell_description` joins a lap's header and value for its spoken row.
- **An inverted chart needs nothing in Swift.** `ChartAxis.of(inverted = true)` already gave Vico's negated axis; `CardLines` plots a
  pace negated and labels each tick with the unnegated value, so `LineChartView` draws the faster pace at the top unchanged. Only the
  average needed a field (`LineChart.average`, a dashed `RuleMark`).
- **A menu whose items need confirmation is a list of items with an optional event.** `ActivityMenuItem.event` is the event to send, or
  null for Split, Export and Delete, which each UI asks about first; the Compose `ManageMenu` and the native `ActivityPageMenu` both build
  from `ownerMenu`, so the rule of which items show is written once.
- **The recordings, members and combine cue are shared with import**, so their words went to `:app-common-api` (`RecordingTexts`)
  rather than the detail `-api`, which the import screen can't see.
- **A Kotlin extension property reaches Swift as a package function** (`DetailPackage.getCommentsState(page)`), as `getShownName` did.
- **iOS 26 goes back on a rightward drag anywhere over a page,** which took a rightward scrub on a chart (`ChartScrub` only began on
  sideways drags, but the stack's pan won). The chart's pan delegate now makes every other non-scroll pan wait for it to fail
  (`shouldBeRequiredToFailBy`), the stack's back included, whose view can be the pan's own host view.
- **A Release build sees a feature's `-api` only if Swift imports it:** Debug reached `FeatureDetailApi` through the samples, which
  Release doesn't export, so `KotlinTypes.swift` imports it like the others.
- **A snapshot taller than about 4,000 points renders blank;** the page's tall images are 2,600 points, and a card cut off below them
  gets a suite of its own (`lapsAndElevationCards`).
- **Walked on the signed-in iPad Air (iOS 26.0) as alice** with the tunnel up: the owner's and someone else's pages, the live map,
  landscape's two panes, the combine banner, native charts with readouts, laps, nested efforts, stats, gear, comments, the menus and
  the report sheet, dark mode.

### Phase 4 findings (leftovers, `ios-native-leftovers`)

- **A threshold's step chart is a `LineChart` too.** Kotlin keeps `ThresholdChart`'s 0–1 placements as the chart's units, adds the
  run-on point at today (no dot), and sets the axis with the Vico chart's insets so the labels have room; the y ticks are the lowest and
  highest values, which the Compose page shows beside its chart. Swift Charts' `.stepEnd` interpolation draws a value held until the next
  point, for both the line and the area. The x labels (the first date and Today) are text under the chart, not ticks: Swift Charts drops
  a label at the plot's edge that would overflow (`ios-native-charts`), and a long date does.
- **A menu's label takes the accent.** `Image.foregroundStyle(.secondary)` inside a `Menu`'s label is overridden by the tint; the muted
  remove button sets `.tint(Color(.secondaryLabel))` on the menu.
- **A Kotlin `TextSource` can come from Swift.** `CatalogText` already implements it, so a rule that needs labels resolved (the type
  search) takes the source and Swift passes `CatalogText.shared`; Compose resolves its labels in composition and passes a matcher.
- **Why snapshots failed only in a full run.** The failure seen here (`GearEditorsSnapshotTests` `nothingToAdd.dark`, 44,085 pixels of
  1,485,540 off by at most 9 levels, all under the sheet's bar) is the soft scroll edge's blur, which the render server draws from a
  backdrop it captures on its own time. A single draw straight after layout could catch it behind, and it did only while the full run
  kept the process busy: twelve busy CPU cores didn't reproduce it, and the suites passed alone. `assertAppSnapshot` now draws through the
  library's own key-window path until two draws in a row are the same (at most six, 50 ms apart, synchronously so no other test runs and
  changes the key window), so the references stay true. Also seen in a full run: a cast of an erased `content.data` to a Swift array
  threw `count` unrecognized (`NSArray` bridging) while passing alone; the two tests doing it read the typed getter
  (`ThresholdHistoryState.groups`) through `elements(as:)` now, as the rules say.

### Phase 4 findings (the rest of the activity page, `ios-native-activity-rest`)

- **Every card chart is one `LineChart`, with defaulted layers.** `backdrop`, `others` (`ChartLine` with a tone), `marks` (`ChartMark`,
  a warning in red) and `fillAbove` cover what Vico's `TimeSeriesChart` draws around a line (the elevation behind effort lines, a pair's
  right leg, more tanks, a dive's ceiling, switches and warnings, its depth filled to the surface); the power curve is a line over its
  durations' indices with ticks at 5 s, 1, 5 and 20 min and 1 h, and the gears a step line held to the end. `CardLines.of` returns all of
  a card's charts in drawing order, so the native card just lists them; the Compose card keeps Vico and asks the same descriptions,
  labels and details.
- **Swift Charts has no second y scale,** so Kotlin puts the backdrop on the chart's own axis (`LineChart.backdropOf`: its low at the
  axis's bottom, its span, at least 20 in its unit, filling three fifths), which the JVM tests check. A backdrop must not follow the line's
  step interpolation (the gears' elevation turned into stairs).
- **A mark's label goes beside its rule, inside the plot.** At `.bottom` a warning's label sat on the time axis' labels; `.trailing`
  aligned top (a switch) or bottom (a warning), fitted to the chart, keeps both readable.
- **Swift export erases an `ImmutableList` parameter too,** so the native page asks for whole values: `StructureTableTexts.of(section,
  charts, units)` rather than `legs(charts.legs)`, `ActivityPageTexts.sourceCaption(page, section)`, and `SegmentEffortRow.guideAt(index)`
  for a list of `Boolean`s.
- **The gallery zooms in UIKit.** SwiftUI has no pinch-and-pan image, so a zoomed photo is a `UIScrollView` with a double tap and a
  single tap that waits for it; while zoomed the paged `TabView` is replaced by that one photo, so a pan moves the photo, as the shared
  pager stops swiping. `RemoteImage(fits:)` shows a whole photo.
- **The combination sheet's open state is the view's,** as `rememberSaveable` is in Compose; it closes when a split leaves nothing
  combined (`CombinationTexts.offersEdit`).
- **A `UIViewRepresentable` has no size in `updateUIView` on its first pass,** so a scroll view fitting its content sizes it in its own
  `layoutSubviews` (the gallery's zoomed photo was black until it did).
- **An iOS 18.4 run needs its own build:** a build for the 26.0 destination lacks `libswiftCompatibilitySpan` and stops before the first
  test.
- **Walked on the signed-in iPhone 17 Pro (iOS 26) as alice:** the gallery with zoom and the native report sheet, the enlarged route map,
  nested efforts with guides and span bars on real Corner Canyon efforts, and the combine picker's empty state. The layered charts,
  structure tables, captions and the combination sheet are seen only in snapshots (see the change's Not verified).
### Phase 4 findings (forms, `ios-native-forms`)

- **A getter a native Save follows has to be a field.** `ManualActivityState.valid` read the duration's text in a getter, which kept the
  Compose form live but published nothing when only the text changed: two states with the same fields are equal, so the host sends
  none. The presenter now reads the text in its composition into `durationEntered`, as `ReportState.noteWritten` does, and a test that
  asserted `valid` on the first state waits for the published one instead.
- **`description` is dropped again.** `ActivityDetailsFields.description` reached Swift as the object's own `description` (a `String`),
  so the form got `about`, a getter under another name.
- **Value classes in a list stay in Kotlin.** `GearField.selected` holds `AtUri`s; rather than reading them in Swift, the field asks
  `isSelected(option)`, which the Compose sheet asks too. The picker's `GearEvent` clashes with the gear page's alias, so Swift names it
  `GearFieldEvent`.
- **Leaving a form that asks first.** The stack's back pops without asking the presenter, so a form whose Back can ask (Edit's unsaved
  changes) hides it with `pageTitle(canGoBack: false)` and leads its bar with Cancel, sending `Back`: the system's edit-form shape.
- **Two SwiftUI form details.** A segmented `Picker` as a row with a clear background squares off the rows after it in the same
  section, so it gets a section of its own. A stepped `Slider` draws a tick per step on iOS 26 (95 ticks for a 25–500 m radius in 5 m
  steps, a dotted line), so the radius slider is continuous and the presenter rounds and clamps it.
- **Swift Testing's macros don't nest.** A `#require` inside another `#require`'s argument fails to compile ("recursive expansion");
  each value is required on its own line.

### Phase 4 findings (create segment, `ios-native-create-segment`)

- **SwiftUI has no range slider.** `RangeSlider` draws the system slider's look with two thumbs; only the thumbs take a drag (from
  their own `DragGesture` in the track's coordinate space), so a vertical drag elsewhere on the form's row still scrolls the form.
  Each thumb is its own adjustable element stepping by a hundredth of the range, which VoiceOver names Start or End of the stretch.
- **A highlight belongs to the map, not the frame.** Putting the stretch on `RouteMap` made it a new state each move (it's in
  `equals`) while `key`, which the native map reframes on, leaves it out: dragging moves the line and not the camera.
- **A sample's numbers can be wrong without anyone seeing.** The loaded sample's start and end were 0.2 and 0.6 *metres* of a 2.4 km
  route (its comment meant fractions), so the shared slider's thumbs sat together at the left in every Android screenshot; the native
  snapshots showed it at once. They now choose 480 m to 1,440 m.

### Phase 4 findings (import, `ios-native-import`)

- **One file goes as a source too.** The contract offers `FilePicked` (bytes) beside `FilesPicked`, but the shared picker sends
  `FilesPicked` for one file as well: an archive can be gigabytes, and a batch save reopens its files from bookmarks, which only a
  `PlatformBacked` source gives (`ByteArrayImportSource` isn't one). So `fileImporter`'s URLs go through `IosApp.importSources`, which
  wraps each in the `PickedFileSource` FileKit's iOS picker makes; FileKit holds each URL's security scope while its stream is open.
  `FilePicked` stays for tests.
- **Swift builds the events that word text through a `TextSource`.** Save's and Save batch's default titles are the rows' default
  titles in words; rather than Swift picking the untitled rows and building a dictionary, the state builds the event
  (`saveEvent(source)`, `saveBatchEvent(source)`) from `CatalogText`, and Compose resolves the same `defaultTitles` rule in composition.
- **Nested sealed events read as nested types**: `ImportActivityEvent.Saved.Done.shared`, `ImportActivityEvent.After.ApplyToPast.shared`.
- **A snapshot taller than 8,192 pixels draws blank, with no error.** Every 393 × 3,200 point reference (9,600 pixels at 3x) recorded
  as an empty page while the 1,180 × 2,400 iPad one drew; 2,700 points (8,100 pixels) draws. Look at a new suite's images before
  trusting its comparisons.
- **A list row's second button keeps its text's colour and alignment only as `.plain`.** `.borderless` tinted the row's title and
  lines and centred the wrapped ones; the tick beside it stays `.borderless`, so each takes its own taps.
- **The shared iOS layout never showed a failed single-file save**: Save sat in the bar and the save panel, where the error is, shows
  only from 840dp. The native form shows it at the top, as the other forms do.

### Phase 4 findings (record, `ios-native-record`)

- **The shared iOS UI never asked for location.** Its `rememberLocationPermissionRequest` answered "granted" at once and left the
  asking to `IosLocationTracker.start`, so a refusal was never told to the screen, which then showed a recording that didn't record.
  The native screen asks Core Location before Start, sends the real answer, and says when access is off. The `*.ios.kt` actual goes
  with phase 5.
- **A list of enums can't reach Swift, again.** `RecordTexts.actions` as a `List<RecordAction>` crashed the test host on the first
  read ("Could not cast `_KotlinExistential` to `RecordAction`"), as the open item below says; `controls` gives `RecordControl`s, each
  holding its action. A single enum argument or property crosses fine (`label(action: .Discard)`).
- **`SportCatalog` has no Swift type**: nothing exported named it, so the native sheet's indoor filter is a Kotlin function on the
  field (`offeredQuickTypes`) rather than an alias.
- **Three wide buttons don't fit a phone's bar with "Finish & save".** It wrapped to two lines, and shrunk to fit it was cut off; it
  takes a row of its own instead. A label is the widest it gets in English; another language will want the same room.
- **The form's top gap is the snapshot harness's**, the same on every native form's references, not Record's.

### Phase 4 findings (moderation, `ios-native-moderation`)

- **Nothing on iOS opens it.** Settings offers Moderation only off mobile (`ui/settings-layout`), and the native list never did, so
  the page was only a `FallbackScreen` waiting for a path to it (a restored stack, a future deep link). It is native anyway, so phase 5
  can delete the fallback with no screen left behind; offering it on iPhone or iPad stays a product choice of its own.
- **Which screens the host can show is a source fact, not a graph one.** Every screen a navigator can push is `@CircuitSerializable`
  in a contract module, and sub-screens never are, so `checkIosScreenRegistry` reads the contracts' sources and `ScreenRegistry.swift`
  as text and runs in `check` on any machine. Reading them from the Kotlin graph instead would need a signed-in session, since the
  session graph holds the session screens' serializer registrations.
- **The package is shared.** `ModerationScreen` is in `app.moderation`, beside `:app-common-api`'s `ModerationTexts` (labels' notices),
  so the page's own texts are `ModerationSubjectTexts`, and the aliases reach both through `AppPackage.moderation`.
- **A new contract module needs its `import` in `KotlinTypes.swift`.** Debug found `FeatureModerationApi`'s types through the samples'
  module, so the aliases compiled; Release, which leaves the samples out, didn't until the import was added. A new feature's first
  native screen should check a Release build.
- **The activity is drawn inert, as the shared page draws it:** no author link, Like, tap or photo opening, and its blurred photos
  stay blurred. Revealing them for a moderator would be a change on both UIs.

### Phase 5 prep findings (photo compression, `ios-libwebp-photos`)

- **libwebp comes as a Swift package, not a cinterop.** SDWebImage's `libwebp-Xcode` (BSD-3-Clause, 1.6.0 = libwebp 1.6.0, released
  2026-07-31) is a C target over Google's sources that SPM builds per architecture, so nothing prebuilt is checked in and the exact
  pin plus `Package.resolved` keep the build reproducible. `import libwebp` exposes `encode.h` through SPM's umbrella directory, and
  Swift calls `WebPEncodeRGBA` and `WebPFree` directly. A cinterop would have needed libwebp built per Kotlin target by CMake.
- **One ImageIO call replaces Skia's decode, orientation matrix and scaling.** `CGImageSourceCreateThumbnailAtIndex` with
  `FromImageAlways`, `WithTransform` and `ThumbnailMaxPixelSize` reads JPEG and HEIC alike, turns an orientation-6 photo upright and
  never scales up (a 300 × 400 photo stays that size). A 4,032 × 3,024 camera-like photo came out 2,048 × 1,536 under 1.5 MB at the
  first step.
- **The pixels are drawn into sRGB and unpremultiplied** (`vImageUnpremultiplyData_RGBA8888`) before libwebp, which takes straight
  RGBA. An opaque photo's WebP is a bare `VP8 ` chunk: no `VP8X`, `EXIF`, `XMP ` or `ICCP`, so GPS data written into the source is
  gone, as with Skia.
- **Swift runs the steps, Kotlin owns them.** `PhotoCompression.steps` (a plain `List` of a two-`Int` class) and `targetBytes` (a
  `const val` in an object) read fine from Swift; the shared `compressPhoto` reads the same list.
- **Debug encoding is quick enough.** In the Debug test build (libwebp unoptimised) the 4,032 × 3,024 photo took about 5 s including
  drawing the source; small photos take milliseconds.
- **The simulators write HEIC** (`CGImageDestination` with `UTType.heic`, on iOS 26.0 and 18.4), so the HEIC test makes its own.
- **`ImportSnapshotTests` flaked under load** in this change's runs: a different snapshot each time (the zone sheet's search field
  before its clear button drew, the iPad page drawn about 20 points high). Not this change's screens; not rerun on the base commit.

### Phase 5 findings (`ios-drop-compose`)

- **The Release app is 41% smaller.** The ad-hoc-signed Release simulator `.app` went from 180.1 MB to 106.2 MB, its binary from
  182.5 MB to 108.1 MB, and `compose-resources/` left the bundle. The binary has no symbol of Skia, Skiko, Compose UI, Foundation or
  Material, Coil, MapLibre, Vico or Calf (`nm -U`: 17,544 Skiko and 17,052 Compose UI symbols before, none after); the Compose
  runtime's stay (4,569, from 7,538), since the presenters run on it. The iOS export's compile classpath went from 163 modules to 95.
- **SubCircuit was the last way in.** Its published `api` dependencies are Compose UI and Foundation on every target, though only
  `SubCircuitContent` and `SubUi` use them, so with every UI module gone Compose UI still resolved on iOS through the contracts. Excluding
  the two groups from the iOS configurations works: the Kotlin/Native link reports no partial-linkage warning, since nothing iOS reaches
  names them. `checkIosDependencies` holds it, so a Circuit upgrade that starts needing them fails `check` rather than the app.
- **Contributed accessors keep the Circuits off the iOS graph.** `AppGraph` and `SessionGraph` name no UI; `:composeApp` contributes
  `AppCircuits` and `SessionCircuits` to `AppScope` and `SessionScope`, which Metro merges into every graph generated where that module is
  on the classpath (Android, desktop, web), and `App` reads them through a cast (`graph.circuits`). The iOS graph is generated in
  `:app-graph`, where they are not. Metro warns there that the 51 contributed `CircuitSerializerRegistration`s are unused: the host saves
  state with `rememberSaveable`, not Circuit's saver, so it is expected.
- **The iOS text field state needs no Compose Foundation.** Snapshot state holding the text, saved by `rememberSaveable`; SwiftUI's field
  owns the cursor and selection, so nothing else is kept.
- **The data export's notification read Compose resources on iOS** (`getString(Res.string…)`), the one place iOS Kotlin did. It reads
  the generated String Catalog through `NSBundle` now.
- **`checkModuleBoundaries` counts `:app-graph` as an app shell**, since the iOS graph aggregates every presenter's factories.
- **Left for a cleanup**: `UiPlatform.Ios` and the shared theme's iOS look (`IosColors`, its shapes and type), `LocalPinnedOverContent`,
  `LocalFloatingBottomBarHeight`, `AppIcon`'s Cupertino glyphs (and `calf-cupertino-icons`) and `AppShell`'s iOS branch compile into no
  build now but are still selectable by a preview; and `:ui-designsystem`'s `expect`s keep their Material `actual`s in `nonAppleMain`,
  which now covers every target.
- `IosResourcesTest`'s `check` run had failed since `checkIosScreenRegistry` joined `check` (its project had no registry); fixed here.

## Open items

- A background task running on Swift export, which needs a device, and the signed-in app on iOS 18, which needs a simulator runtime
  whose HTTP/3 works or a tunnel without it.
- The macOS CI job has not run: it needs the branch pushed. Its runner label and the memory it gives Gradle are untested.
- A class whose constructor takes an immutable collection has no Swift initialiser. The stub check catches that on events; a payload
  class an event takes (`FeedFilter`, `CategorySelection`) shows up only when Swift tries to build one.
- Whether the `self` clash is reported upstream, and fixed before Kotlin 2.5.0. The erased `List<Pair<…>>` bridge needs no upstream
  fix: `Pair` and `Triple` are kept out of every non-private signature by detekt's `NoPairInApi`, so Swift export never meets one.
- Whether the composable `fun interface` lambda linkage error is reported upstream, and a check for such lambdas until it is fixed: a
  missed one crashes only on iOS and web, and only when its screen draws.
- ~~Photo compression is WebP through Skia on iOS~~: done (`ios-libwebp-photos`), libwebp from Swift.
- Settings' list beside its page was walked on an iPad Air 11-inch (iOS 26.0) with an account, upright and on its side, and a
  profile opened from the blocked list beside it takes the whole window and comes back to the list beside Blocked accounts
  (`ios-native-settings-lists`). Still to see: a window resized across 700 points with the list scrolled, and all of it on iOS 18.
- The app turns now (portrait and both landscapes on iPhone, all four on iPad), and no screen was laid out for a phone on its
  side: each lays out by the width it gets. Only the feed, the profile, Settings, the Account page and an activity were looked at turned.
  An activity on an iPhone on its side took the shared UI's side-pane layout in 402 points of height; a window under 500dp tall
  is now never `Expanded` (`short-window-layout`, ui/window-layout), on every platform. Record, the maps and the charts want the same look.
- Decided (`ios-native-settings-pages`): a native page in the detail column draws a column header from the design system
  (`pageTitle`, `PageBar`), not a `NavigationSplitView`. Done (`ios-native-account`): its back button for a deeper native page, from
  `HostRecord.paneRoot`, first on Leave opened from Account.
- ~~The gear item page (`GearScreen`) is still the shared UI's (`ios-native-gear-pages`)~~: done (`ios-native-gear-item`), with its
  activities as native cards, its components, mount sheet and a SwiftUI unmount calendar, and its linked devices asking
  `LinkedSensorTexts`. The gear editors are native (`ios-native-gear-editors`). Its mount dates are still ISO dates on every platform.
- ~~The profile (`ProfileScreen`) is still the shared UI's (`ios-native-follow-lists`)~~: native (`ios-native-profile`), with the
  native activity card. ~~Report on someone else's profile~~: done (`ios-native-report-sheet`), the native report sheet. ~~The
  Posts tab~~: done (`ios-native-post-card`), native post cards with the quote sheet, with Report (`ios-native-post-report`). ~~A native tab root (the profile in the You tab) shows no title on an iPad with its
  tabs at the top~~: a tab's first page now titles itself large (`\.tabRoot`, set by `StackView` on the stack's root), as the shared
  root did; the pages pushed over it keep inline titles. Follow requests and Remove were drawn natively but not seen on a device, since the test account doesn't approve
  followers. ~~Activity invites and Add people can build on `SignInAgainNotice` when they go native~~: done
  (`ios-native-invite-lists`). ~~Accepting an invite (`AcceptInviteScreen`) is still the shared UI's~~: native
  (`ios-native-interactive-maps`), not yet seen on a device; Activity invites itself wasn't seen on a device, since the local network has
  no invites.
- ~~`LeftElsewhereScreen` (left on another device) is still the shared UI's~~: done (`ios-native-search`), `LeftElsewhereView` over
  `LeftElsewhereTexts`, in a stack of its own. A leave resuming (`LeaveView` shown alone) still has no stack, so its title doesn't show.
- ~~Segment browse is still the shared UI's, embedded in the native Search tab's Segments mode (`ios-native-search`)~~: native
  (`ios-native-interactive-maps`), embedded through the host (`HostRecord.embed`); the Compose embed is gone. Its runs, a tap selecting
  one and opening a segment were not seen on a device: the walk had no tunnel to the AppView.
- ~~**What still goes through the shared Compose UI on iOS**~~: nothing, and the iOS app no longer contains it (`ios-drop-compose`). (Checked against `ScreenRegistry.app` after `ios-native-moderation`, and
  held by `checkIosScreenRegistry` in `check`): nothing. ~~`ModerationScreen`~~ is native (`ios-native-moderation`), and every
  `@CircuitSerializable` screen of a contract module has an entry. `ReportScreen`, `ProposePlaceNameScreen` and `FeedFilterScreen` are
  SubCircuits drawn natively through the host (`ReportSheet`, `ProposePlaceNameSheet`, `FeedFilterBar`). So phase 5 needs, in order:
  ~~Moderation native so `FallbackScreen` has nothing left to show~~ (done); delete `FallbackScreen` and the host's Compose bridge (`composeState` on `TextFieldState`, the fallback
  controllers), the `*.ios.kt` actuals (`LocationPermission.ios.kt` among them) and the UI modules' iOS targets; split the Metro graph
  so the iOS one binds no `Ui.Factory`; drop Calf, Haze, Vico, Coil (Nuke already loads every native picture) and MapLibre Compose
  from iOS; retire `ui/ios-navigation-bar`. ~~**Photo compression needs a replacement first**~~: done (`ios-libwebp-photos`). A picked activity or gear photo is
  compressed in Swift (`PhotoCompressor`): ImageIO decodes, turns and scales it (a HEIC included, so `PhotoPick`'s retry is gone),
  libwebp (SDWebImage's `libwebp-Xcode`, a Swift package) encodes it as WebP through Kotlin's `PhotoCompression` steps, and
  `IosApp.compressPhoto` is gone. The Compose iOS `encodePhoto` actual is unreachable and goes with the UI modules' iOS targets.
  `compressAvatar` is FileKit's JPEG over UIKit, with no Skia, but lives in `:app-common-ui`, so phase 5 moves it (or its call) out
  before dropping that module's iOS target.
- A collection of Kotlin enums can't reach Swift, as an immutable collection or a plain `List`. Decided
  (`ios-native-settings-pages`): the contract offers an immutable list of its own objects, each holding one value as a property, which
  Swift reads as the enum (`SegmentsAndGroupsState.groupOptions`, `GroupOption`). ~~`ZoneSummary.kinds` and `SportZonesState.kinds`
  (sets of `ZoneKind`) still need theirs~~: done (`ios-zones-contracts`), a list of `ZoneKindOption` with `offers(kind)`.
- A class of `:core-data` or `:core-lexicon` that appears only as an immutable collection's element has no Swift type until something
  exported names it. ~~`PrivacyZone` and `Zone`~~: done (`ios-zones-contracts`), named by `ZoneTexts`' functions, which a native row
  needs anyway. A new contract with such a list needs the same.
- ~~`ThresholdHistorySection.shownRows` returns its immutable `rows` as a `List` when all are shown~~: done (`ios-zones-contracts`), a
  copy. Only an empty persistent list stops the app through a `List` getter, and a threshold always has a row, so it never could have.
- ~~Training zones, threshold history and Privacy zones are still the shared UI's, waiting for native charts and a map~~: neither
  needed (`ios-native-zones-pages`). Training zones, the default and sport zones and Privacy zones are native, and a zone row's name and
  range are `AppText` (`ZoneNameText`, `ZoneTexts.range`). ~~Threshold history is still the shared UI's~~: native
  (`ios-native-leftovers`), with step charts, dots and labels on `LineChart` and the sparklines. Its Compose page keeps its Vico chart
  over `ThresholdChart`; only its text moved to `ThresholdHistoryTexts`.
- `Following`'s fix has no regression test: it would need a Kotlin flow the test target can change.
- Phase 2 was walked signed in only on the iOS 26 iPhone simulator. Still to see with an account: the shell on an iPad and on iOS 18,
  sign out and in, a leave started from Settings, and sign-in's expired, sign-in-again and left messages. The app's own Cancel while
  the browser is open was seen only behind the system's prompt.
- The `values` flow of a text field state reaches Swift a keystroke at a time, late. `TextFieldModel` reads the state on each report
  rather than taking the reported text; anything else that follows a field from Swift must do the same. The gear editor's fields, seeded by the presenter after the item
  loads, showed every saved value on a device (`ios-native-gear-editors`).
- A SwiftUI `TextField` keeps showing a refused edit until its view is redrawn. `AppTextField` redraws on a refusal; on the gear form's
  weight (digits only, `ios-native-gear-editors`) a refused letter is put back, but a digit typed in the same burst right after it was lost
  too, since it arrived as part of the refused text. Decided (`ios-native-zones-pages`): a digits-only field keeps an edit's digits and
  drops the rest, so that digit is kept; an edit over the maximum length is still refused whole, since cutting it would make another
  number.
- The iOS `Release` configuration exports different Kotlin from `Debug` (no `:app-fixtures`). CI builds Debug only, so a break
  that only a release build has would first show in an archive build. It did once: from `ios-native-uploads` until its fix,
  `KotlinTypes.swift`'s `UploadQueueView` alias didn't compile in Release, since only the samples named that `:core-data` class
  (found by `ios-native-zones-pages`). `UploadsState.queue` names it now. A `:core-data` or `:core-lexicon` class a native view
  aliases needs an `-api` signature naming it, not only a sample: build Release before archiving a change that aliases a new one.
- Snapshot references are true for iOS 26.0 simulators. CI's simulator is the newest the runner has, so CI compares them only when
  that is 26.0, and otherwise reports them skipped; they are a local check, as the Android references are on Linux.
- The generated images ignore `autoMirrored`, and the catalog is English only, as `strings.xml` is.
- About 120 composables still choose text for themselves (dates and times, the dive and stat tables, feature labels). Each moves to
  an `AppText` function when a native view first needs it. A time of day is `AppText.Time` (`ios-native-account`); a rough length of
  time is `DurationText.approximate`, and a day beside today `DayText` (`ios-native-uploads`). A calendar date written out is
  `DateText`, and a segment's type `SegmentTexts` (`ios-native-segment-lists`). ~~The feed's start times, which read on the activity's own UTC offset~~: done (`StartText`, `ios-native-activity-card`). The
  short date (`shortDate`) is still the shared UI's.
- ~~Pictures from the network load through Nuke on native pages and through Coil on fallback pages~~: Coil left iOS with the fallback
  (`ios-drop-compose`); Nuke is the one cache. A held photo (`heldphoto:`) on a native page was seen only in the probe, from bytes Swift made:
  the test account's gear has no photos. Avatars were seen only as initials, for the same reason.
- `PagedList` asking for a second page was seen in its tests, not on a device: the test account's lists hold one account at most.
- ~~The segment page (`SegmentScreen`) is still the shared UI's~~: native (`ios-native-charts`), with the native chart and map. ~~Its
  enlarged map and segment browse~~: native (`ios-native-interactive-maps`). The create-segment page and the highlighted stretch a new
  segment shows are still the shared UI's. ~~The dive site page~~: native (`ios-native-map`). The lists they open are native
  (`ios-native-segment-lists`). The live Compose map still frames with MapLibre's
  `cameraForBounds` (no closest zoom); moving it onto `MapFrame` wants a look on Android and desktop.
- ~~The activity page (`ActivityDetailScreen`) is still the shared UI's~~: native (`ios-native-activity-page`). ~~Still left on it:
  charts `LineChart` can't draw yet, the legs, lengths and sets tables, the edit-combination sheet, the chart source captions and the
  nested efforts' guide lines and span bars~~: done (`ios-native-activity-rest`), and ~~the media gallery, the enlarged route map and the
  combine picker~~ are native too. Two cards to a row on a medium width is the shared page's; the native one stacks them. The screens it
  opens that are still shared: none; ~~Create segment~~ (`ios-native-create-segment`) and ~~Add a recording (import)~~
  (`ios-native-import`) are native, and so are ~~Edit~~ and ~~Create dive site~~ (`ios-native-forms`), and manual entry, from the feed. The Compose charts keep Vico and its own drawing of the backdrop, other
  lines and marks; only their words are shared.
- ~~The feed (`FeedScreen`) is still the shared UI's~~: native (`ios-native-feed`), with its posts, import cards, upload rows, prompts,
  sheets, pull to refresh and empty and failure states; ~~the outside Clear may leave the bar's chips~~: it resets them (seen on the
  iPad). ~~Still left: the bar's type sheet has no search and no collapsed sports~~: done (`ios-native-leftovers`); ~~Report on a feed post waits for the post card's menu~~: done, the feed passes `canReport: state.reporting.available`; the feed loads one page on every platform, and paging it is a shared change; the shared feed's
  Refresh button for a pointer has no native twin (an iPad with a pointer pulls to refresh). Seen only in snapshots: posts, reposts and
  quotes in the feed, import cards, the upload rows, the prompts and a failed first load, which the local stack's alice didn't have.
- Posts are native (`ios-native-post-card`): the card, the post page with its comments, the quotes list, the quote sheet, and the
  profile's Posts tab, which closes the profile's Posts item above. ~~Still left out: Report on a post, a comment and a quote~~: done
  (`ios-native-post-report`). ~~The feed's posts and the activity page's comments are still the shared UI's~~: both native (`ios-native-feed`,
  `ios-native-activity-page`, which passes its `Reporting`). The native feed (`ios-native-feed`) offers Report on its cards too.
- ~~The report and place-name sheets (`ReportScreen`, `ProposePlaceNameScreen`) are SubCircuits the host can run now~~: both native
  (`ios-native-report-sheet`), shown by `reporting(_:)` and `placeNames(_:)`. ~~The native post card (`ios-native-post-card`) wires
  Report in its menu with `reporting(_:)` once the two merge~~: done (`ios-native-post-report`). The name sheet is placed on the native dive site page (`ios-native-map`).
- After the migration every UI change is made twice. Shared fixtures and snapshot coverage on both sides are the parity check.
