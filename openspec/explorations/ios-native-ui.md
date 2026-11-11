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
   - **The zones contracts** (`ios-zones-contracts`). No page goes native: Training zones, threshold history and Privacy zones wait for
     native charts and a map. Their contracts now read from Swift without a crash or an opaque element: the zone kinds are a list of
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
5. **Remove Compose rendering from iOS.** Delete the fallback host, the `*.ios.kt` actuals and the iOS targets of the UI modules; drop
   Calf, Haze, Vico, Coil and MapLibre Compose from iOS; split the Metro graph so the iOS one needs no `Ui.Factory`; retire
   `ui/ios-navigation-bar`.

## Recommended iOS stack

| Need | Use | Replaces |
|---|---|---|
| State observation | Observation (`@Observable`), `AsyncSequence` from exported flows | |
| Navigation | `TabView`, `NavigationStack(path:)`, `NavigationSplitView` for Settings on iPad | Calf's tab bar, the nav decoration |
| Sheets, alerts, menus, dates | `.sheet`, `.alert`, `.confirmationDialog`, `Menu`, `DatePicker` | Calf's adaptives |
| Maps | MapLibre Native for iOS with its SwiftUI DSL; `MLNMapSnapshotter` for thumbnails | MapLibre Compose |
| Charts | Swift Charts | Vico |
| Images | Nuke with NukeUI, and a custom loader for `heldphoto:` URLs | Coil |
| Pickers and sharing | `PhotosPicker`, `.fileImporter`, `ShareLink` | FileKit's Compose launchers |
| Icons and text | SF Symbols, a String Catalog, Dynamic Type | Compose resources |
| Tests | Swift Testing, swift-snapshot-testing, Xcode Previews, a small XCUITest smoke run | |
| Lint and format | swift-format and SwiftLint | |
| Concurrency | Swift 6 mode with default main-actor isolation | |
| Project layout | One app target with a synchronized folder per feature; Swift Package Manager for dependencies only | |

MapKit was set aside for maps because it can't render the OpenFreeMap styles the other platforms use.

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
- Exporting `:core-activityfile` whole doesn't build. `PerSecond.hold(samples: List<Pair<Double, Int>>)` becomes a Kotlin bridge that
  reads the argument as `List<Pair<Any?, Any?>>`, which the compiler rejects. So `:iosShell` has a `FitSupport` object that repeats the
  seven helper calls the Swift decoder makes on `FitDives`, `FitHeart` and `FitUnits`, and makes the exception it throws.
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

## Open items

- A background task running on Swift export, which needs a device, and the signed-in app on iOS 18, which needs a simulator runtime
  whose HTTP/3 works or a tunnel without it.
- The macOS CI job has not run: it needs the branch pushed. Its runner label and the memory it gives Gradle are untested.
- A class whose constructor takes an immutable collection has no Swift initialiser. The stub check catches that on events; a payload
  class an event takes (`FeedFilter`, `CategorySelection`) shows up only when Swift tries to build one.
- Whether the erased `List<Pair<…>>` bridge and the `self` clash are reported upstream, and fixed before Kotlin 2.5.0.
- Whether the composable `fun interface` lambda linkage error is reported upstream, and a check for such lambdas until it is fixed: a
  missed one crashes only on iOS and web, and only when its screen draws.
- Photo compression is WebP through Skia on iOS, and iOS has no system WebP encoder. A native pick goes through the same compression
  (`IosApp.compressPhoto`); Skia can't read a HEIC, so the picker asks for a compatible encoding and `PhotoPick` retries what Kotlin
  can't read as UIKit's JPEG. Phase 5, which drops Skia from iOS, needs another WebP encoder or a JPEG fallback for every photo.
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
- The gear item page (`GearScreen`) is still the shared UI's (`ios-native-gear-pages`): it lists its activities as the feed's
  `ActivityCard`, so it goes native with or right after the native activity card, with its components, mount sheet and a SwiftUI
  unmount date picker; its linked devices already ask `LinkedSensorTexts`. The gear editors are native (`ios-native-gear-editors`).
- The profile (`ProfileScreen`) is still the shared UI's (`ios-native-follow-lists`): its header, tabs and activity cards are the
  feed's work. Follow requests and Remove were drawn natively but not seen on a device, since the test account doesn't approve
  followers. ~~Activity invites and Add people can build on `SignInAgainNotice` when they go native~~: done
  (`ios-native-invite-lists`). Accepting an invite (`AcceptInviteScreen`) is still the shared UI's, waiting for the native route preview
  and visibility form; Activity invites itself wasn't seen on a device, since the local network has no invites.
- `LeftElsewhereScreen` (left on another device) is still the shared UI's, shown alone in place of the app; it shares Leave's words
  and could build on `LeaveTexts`.
- A collection of Kotlin enums can't reach Swift, as an immutable collection or a plain `List`. Decided
  (`ios-native-settings-pages`): the contract offers an immutable list of its own objects, each holding one value as a property, which
  Swift reads as the enum (`SegmentsAndGroupsState.groupOptions`, `GroupOption`). ~~`ZoneSummary.kinds` and `SportZonesState.kinds`
  (sets of `ZoneKind`) still need theirs~~: done (`ios-zones-contracts`), a list of `ZoneKindOption` with `offers(kind)`.
- A class of `:core-data` or `:core-lexicon` that appears only as an immutable collection's element has no Swift type until something
  exported names it. ~~`PrivacyZone` and `Zone`~~: done (`ios-zones-contracts`), named by `ZoneTexts`' functions, which a native row
  needs anyway. A new contract with such a list needs the same.
- ~~`ThresholdHistorySection.shownRows` returns its immutable `rows` as a `List` when all are shown~~: done (`ios-zones-contracts`), a
  copy. Only an empty persistent list stops the app through a `List` getter, and a threshold always has a row, so it never could have.
- Training zones, threshold history and Privacy zones are still the shared UI's, waiting for native charts and a map. Their contracts
  are ready (`ZonesContractsTests`); a zone row's name (`zoneName`, shared with the activity charts' time in zone) and its range (units
  and paces) are still chosen in composables and move to `AppText` with the native page.
- `Following`'s fix has no regression test: it would need a Kotlin flow the test target can change.
- Phase 2 was walked signed in only on the iOS 26 iPhone simulator. Still to see with an account: the shell on an iPad and on iOS 18,
  sign out and in, a leave started from Settings, and sign-in's expired, sign-in-again and left messages. The app's own Cancel while
  the browser is open was seen only behind the system's prompt.
- The `values` flow of a text field state reaches Swift a keystroke at a time, late. `TextFieldModel` reads the state on each report
  rather than taking the reported text; anything else that follows a field from Swift must do the same. The gear editor's fields, seeded by the presenter after the item
  loads, showed every saved value on a device (`ios-native-gear-editors`).
- A SwiftUI `TextField` keeps showing a refused edit until its view is redrawn. `AppTextField` redraws on a refusal; on the gear form's
  weight (digits only, `ios-native-gear-editors`) a refused letter is put back, but a digit typed in the same burst right after it was lost
  too, since it arrived as part of the refused text. Typed alone it is taken. Accepting the edit with the refused characters taken out
  would keep it, at the cost of the rule that a refusal never rewrites what was typed; decide with the next digits-only field.
- The iOS `Release` configuration exports different Kotlin from `Debug` (no `:app-fixtures`). CI builds Debug only, so a break
  that only a release build has would first show in an archive build.
- Snapshot references are true for iOS 26.0 simulators. CI's simulator is the newest the runner has, so CI compares them only when
  that is 26.0, and otherwise reports them skipped; they are a local check, as the Android references are on Linux.
- The generated images ignore `autoMirrored`, and the catalog is English only, as `strings.xml` is.
- About 120 composables still choose text for themselves (dates and times, the dive and stat tables, feature labels). Each moves to
  an `AppText` function when a native view first needs it. A time of day is `AppText.Time` (`ios-native-account`); a rough length of
  time is `DurationText.approximate`, and a day beside today `DayText` (`ios-native-uploads`). A date in general, and the feed's start
  times, which read on the activity's own UTC offset (`startTime`), are still the shared UI's.
- Pictures from the network load through Nuke on native pages and through Coil on fallback pages, two caches of the same images,
  until phase 5 drops Coil from iOS. A held photo (`heldphoto:`) on a native page was seen only in the probe, from bytes Swift made:
  the test account's gear has no photos. Avatars were seen only as initials, for the same reason.
- `PagedList` asking for a second page was seen in its tests, not on a device: the test account's lists hold one account at most.
- After the migration every UI change is made twice. Shared fixtures and snapshot coverage on both sides are the parity check.
