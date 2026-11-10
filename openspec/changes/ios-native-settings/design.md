# Design

## Context

What exists, from the code:

- **The contract.** `SettingsScreen` and `SettingsState` (`feature/settings/api`): the handle, `needsSignIn`, `units`, `saving`, `error`, and four summary fields (`defaultActivityVisibility`, `privacyZoneCount`, `hidesRouteEnds`, `segmentMatching`), each null until read, plus `canModerate`. `SettingsEvent` is `Back`, `UnitsChanged`, `Open(page: SettingsPage)` and `OpenModeration`. The state has **no collection field**. `SettingsPage` (`:app-common-api`) marks the screens that open in the detail pane: Account, Activity defaults, Privacy zones, Segments & groups, Training zones and the pages under them (Zones, a sport's zones, threshold history, Leave), the gear list (`:feature-gear-api`) and the blocked and muted lists (`:feature-profile-api`). `SettingsPane(twoPane, shownPage)` sits beside it.
- **The presenter** opens a row with `popUntil { it is SettingsScreen }` then `goTo(page)`, so a row always replaces the open page, and `Back` pops Settings and everything above it. Neither changes.
- **The Compose UI** (`SettingsUi`) builds the Privacy zones summary (none, a plural, wrapped when ends are hidden), the visibility label, the matching on/off summary and the units note in composables, marks a row from `LocalSettingsPane`, and offers Moderation only when the platform isn't mobile, which iOS is.
- **The split on the other platforms** is `SettingsListDetailDecoration` (`:composeApp`): when the stack ends in Settings with only `SettingsPage`s above it and 840dp is available, it draws Settings as its list beside the first of those pages, and Activity defaults as plain `CircuitContent`, not a record, when there is none. The back stack stays the only selection state.
- **The iOS shell** (`iosApp/iosApp/Shell`): a `NavigationStack(path:)` per tab whose path is the Kotlin stack's records above the first, written back only as a shorter path (`StackPath`). A record draws through `ScreenRegistry.app` or, with no entry, as a Compose view controller cached per record key (`FallbackControllers`), with the native bar hidden and `BackSwipe` restoring the edge swipe. Settings is not a tab: it is pushed on a tab's stack, usually `[profile, Settings, …]`.
- **The host** (`:app-host`) runs the top record of the chosen tab and any record the UI reports visible, and keeps each record's retained state while it is on a stack. It has no notion of a screen that is shown but not on a stack, other than the two whole-session screens.
- The app declared portrait only (`INFOPLIST_KEY_UISupportedInterfaceOrientations`), for iPhone and iPad alike: the Xcode template's setting from the first commit, never a decision. This change lifts it (decision 13).

## Goals / Non-Goals

**Goals:**

- Settings' list is a SwiftUI view any later list screen can copy: the system's bar and back, `List` and `Section`, `AppListRow`, strings by symbol, text with a rule from Kotlin.
- A wide iOS window shows the list beside its page by the rule the other platforms follow, with Kotlin's stack still the only selection state.
- The rule for the split is written once, in Kotlin, and tested on the JVM; Swift only draws what it is told.
- Fallback pages work in the detail column unchanged, so no page has to be migrated with the list.
- The immutable-collection decision is made and proven, though this screen has none.

**Non-Goals:**

- A native view for any settings page.
- Changing the Compose layout on Android, desktop or web beyond reading the shared rule.
- A layout of any other screen made for a phone on its side: every screen turns, laid out by the width it gets as it already is.

## Decisions

### 1. Probe first

Four questions were asked with throwaway code before the work that depends on them, on iPad simulators with no session (a scratch root view in place of `RootView`). The answers are in the roadmap under "Phase 4 probes"; two of them changed this design.

1. **A stack per column inside a pushed destination** was the layout first proposed: Settings' destination hiding the outer bar and drawing an `HStack` of two `NavigationStack`s. **It doesn't work.** On iOS 26.0 and 18.4 a destination that holds a `NavigationStack` is never shown: the path changes, the destination's toolbar preferences apply to the outer bar, and the root stays on screen. Decision 2 is the fallback this question named, which turned out simpler.
2. **`NavigationSplitView` as a pushed destination** does draw, with a bar of its own under the outer one and a sidebar button. On iOS 18.4 upright it hides its sidebar until that button is tapped; on iOS 26.0 it shows it as a floating panel. It decides for itself what Kotlin must decide.
3. **Immutable collections from Swift:** decision 9.
4. **Rotation** was not probed before the build: nothing then turned a simulator. With landscape allowed (decision 13) the signed-in iPhone was turned in the final walk.

A third layout was probed after the first failed: the two columns as plain views under the outer bar (decision 2). It pushes and draws on both versions, and a UIKit view controller filling the detail column, as a fallback page does, reads a top safe area that ends under the outer bar (140 points on iOS 26.0, 128 on 18.4, on a 13-inch iPad with the tabs in the bar), so a page's own bar sits clear of it.

### 2. Two plain columns in Settings' destination, under the outer stack's bar

Settings' destination in the tab's `NavigationStack` is always the same view:

```
HStack(spacing: 0) {
    ScreenView(Settings)                     // the native list; 360 points when a page is beside it
    if a split is reported {
        Divider()
        ScreenView(the pane's top page)      // .id(its record's key)
    }
}
```

- **No navigation container inside the destination.** Probe 1 rules a stack out, and nothing needs one: every settings page is a fallback that draws its own bar, and a deeper page's back button already sends its presenter's `Back`, which pops Kotlin's stack. The detail column draws the top settings page, as the Compose decoration's detail pane does.
- **The outer path is trimmed, the stack isn't.** Kotlin holds `[profile, Settings, Training zones, Run zones]`; the outer `NavigationStack` is given `[Settings]` (`ShownRecords.pushed`), and the detail column draws Run zones. Choosing another row or opening a deeper page changes which record the column draws; its `id` is the record's key, so the page is replaced.
- **Back** is the outer stack's own: the system's back button in the bar over the list, and the system's edge swipe. Both write a shorter path, which `StackPath` already maps to `popTo`, and popping Settings drops its pages with it. `SettingsEvent.Back` is never sent on iOS.
- **Another screen on top** (a profile from the blocked list): the host reports no split, the outer path is the whole stack again, and the profile pushes full width over the pages. Going back restores the split.
- **The same view narrow and wide.** The list is in the same place in the view tree whether or not a page is beside it, so a window that gains or loses room for the page keeps the SwiftUI list as it is, scroll position included, and `ui/settings-layout`'s resizing requirement holds on iOS with no exception. (The two-stack layout would have rebuilt it.)
- **A fallback page in the column** must not hide the navigation bar, which is now the outer stack's: `ScreenView` hides it only for a fallback that has the destination to itself. The page's Compose view controller is the one `FallbackControllers` caches per record key, wherever it is drawn; `FallbackContainerController` already re-parents a controller an earlier container held.
- **The title** is the outer bar's, "Settings", placed by the system. On an iPad, where the tabs share the bar, that is a centred line under them, above both columns.

Which record gets this destination is Kotlin's fact, not a name test in Swift: the one with a `paneDefault` (decision 4).

Why not `NavigationSplitView`: probe 2. It is meant for the root of a scene or a tab, and Settings is pushed on a tab's stack beneath a profile; and it owns state Kotlin must own (which column shows, when it collapses to a stack, its own bar and back). When the first settings page is drawn natively and wants a bar of its own in the detail column, this is the question to reopen.

**List width** is 360 points, the list pane's width on the other platforms.

### 3. Kotlin decides the split; Swift reports its size

`:app-host` gains:

- `TabStack.setSize(width, height)`: the UI reports the size its stack has, in points. The stack keeps only whether that has room for two panes (`settingsSplitFits`, decision 13), so a resize's intermediate sizes change nothing.
- `SettingsSplit`: for a stack that ends in Settings with only `SettingsPage`s above it, the Settings record, `page` (the top settings page, or the record of decision 4 while there is none), whether that page is the pane's first (decision 5), and `pane` (a `SettingsPane` with `twoPane` true and the marked page, which is the first page's screen).
- `ShownRecords`, which Swift already follows, gains `split` (non-null only while the UI is wide and the stack has that shape) and `pushed` (the records the outer stack shows: all of them, or those up to Settings). `TabStack.shown` becomes a flow of both inputs.
- The shape rule itself, `settingsPagesOnTop(stack, screenOf)`, is one function that `SettingsListDetailDecoration` also calls in place of its private copy, so the two shells can't disagree about what counts as "only settings pages above Settings".

Swift measures the stack's size (`onGeometryChange`), calls `setSize`, and draws `shown.pushed` and `shown.split`. It holds no rule. The alternative, Swift deciding from `screenName`s, would repeat the `SettingsPage` rule in Swift, where it can't see a screen's interfaces.

Settings' destination follows `stack.shown` itself (a `Following` inside the destination view) rather than reading what the `navigationDestination` closure captured, so an already-pushed Settings redraws when the split changes.

### 4. The page shown until a row is chosen is a record beside the stack

The Compose decoration draws Activity defaults with `CircuitContent` when nothing is above Settings. The host has no such thing, so a `HostRecord` gains `paneDefault: HostRecord?`: set, for a Settings record, to an Activity defaults record made with it, with the tab's navigator and a key of its own. It is never in `records`. The host adds it to the records whose retained state is kept for as long as its Settings record is on the stack, and runs its presenter while the UI reports it visible (`setVisible`, which `ScreenView` already calls). `SettingsSplit.page` is this record while no page is open. A record with a `paneDefault` is also how the shell knows which destination lays out two columns.

The alternative, pushing Activity defaults onto the stack when the UI is wide, would make what the stack holds depend on the window's width, which `ui/settings-layout` forbids ("that SHALL NOT count as a page opened") and the Compose decoration's doc comment explains.

### 5. A pane's first page hides its back button through the record

A fallback page draws its own Compose bar, whose back button hides under `LocalIsPaneRoot`. The same cached view controller is a pane's first page while wide and an ordinary pushed screen while narrow, so the flag can't be fixed when the controller is made. `HostRecord` gains `paneRoot: StateFlow<Boolean>`, derived by its stack from `shown` (true for the pane's first page: the one page above Settings, or the `paneDefault`), and the iOS fallback host (`IosHost.Screen`) provides it as `LocalIsPaneRoot`. Nothing in Swift sets it. A native page will read the same fact from the SwiftUI environment when the first one is written.

### 6. Which row is marked is one Kotlin function

`SettingsPane.marks(page)` in `:app-common-api`: true when the pane has two panes and shows `page`. `SettingsUi` calls it in place of its local comparison, and `SettingsView` calls it with the screen each row opens, reading the pane from the environment (`\.settingsLayout`, a Sendable Swift wrapper over the Kotlin `SettingsPane`: `SettingsPane.Single` unless the split sets `split.pane`). Kotlin's `equals` tells the blocked list from the muted one, which share a class. Swift names the three contract modules' screens to build `SettingsEvent.Open`, which also links their modules.

`AppListRow` gains `selected`: the row's background is the system's selected-cell grey and it carries the selected accessibility trait.

### 7. `SettingsView`

A `List` (inset grouped) of `Section`s with the shared headers, in the shared order:

- **Account:** one `AppListRow`, the handle, with `settings_account_needs_sign_in` as its summary when `needsSignIn`.
- **General:** the units heading, a segmented `Picker` over `UnitSystem` that sends `UnitsChanged` and is disabled while `saving`, the note under it (decision 8) and `ErrorMessage` for `state.error`.
- **Privacy & sharing, Training, Safety:** rows that send `Open` with their screen, with the summaries of decision 8.
- No Moderation row: the shared UI offers it only off mobile.

Title `settings_title`, inline, as the shared bar is for a screen that isn't a tab's root. Its back button is the outer stack's, the system's own, narrow and wide.

Registered in `ScreenRegistry.app` as `SettingsScreen` with `SettingsState`.

### 8. Text chosen in Kotlin

`SettingsTexts` in `:feature-settings-api`, returning `AppText` (or null for a summary left out), with `commonTest`s:

- `privacyZones(count, hidesRouteEnds)`: null until read; `settings_privacy_zones_none` or the `settings_privacy_zones_count` plural; wrapped in `settings_privacy_zones_ends` when ends are hidden.
- `segmentMatching(on)`: null while unknown, else `settings_segments_on` or `settings_segments_off`.
- `defaultVisibility(visibility)`: null until read, else `LabelText.visibility`.
- `unitsNote(units, saving)`: `settings_saving`, or the unit system's example line.

`LabelText.visibility(value)` joins the labels in `:app-common-api` (an unknown value reads as Only me, as `visibilityLabel` does today), and `visibilityLabel` in `:app-common-ui` resolves it. `SettingsUi` resolves all of them with `string()`. `SharedTextNamesTest` gains these names. No Android screenshot reference may change.

### 9. Immutable collections: one Swift wrapper, with two limits

Contracts keep `ImmutableList`, `ImmutableSet` and `ImmutableMap`. Swift reads a list or set through an extension on the exported Kotlin `Collection` protocol, `elements(as:)`, which walks the collection's iterator once and returns `[Element]`; an element of another type is an `assertionFailure` in debug and skipped in release.

What probe 3 found, which the helper's doc comment and the rules record:

- **Text and whole numbers arrive typed:** a `String`, and an `Int` as `Int32` (never Swift's `Int`).
- **A class arrives typed only if Swift has a type for it.** A contract module is exported whole, so its own classes always do. A class from a module exported only as far as signatures name it (`:core-data`, `:core-lexicon`) has no Swift type at all when its only mention is as an immutable collection's type argument, which the export erases: `PrivacyZone` in `PrivacyZonesState.zones` arrived as an opaque object. Naming the class in any exported signature makes it appear, and the same elements then cast.
- **A Kotlin enum element never converts,** in an immutable collection or in a plain `List`: a `List<ZoneKind>` property stopped the app in the generated getter (`Could not cast … _KotlinExistential … to ZoneKind`), the flows finding again. A native view that needs a collection of enums needs the contract to offer it another way (names, or one flag per case), decided when the first one does.
- **A `List`-typed property must not return the immutable list itself.** `val zonesList: List<PrivacyZone>? get() = zones` stopped the app in the generated getter (`-[_KotlinExistential count]: unrecognized selector`): the export hands a persistent list to Swift as an object of its own exported class, not as an array. With `zones?.toList()` it is a typed Swift array. So a typed twin getter is not a safer alternative to the helper, and `ThresholdHistorySection.shownRows`, which returns its immutable `rows` when all are shown, will fail the same way when a native view reads it (an open item).
- `ContentState<ImmutableList<T>>.data`, typed `Any?`, arrives as a Swift array that casts to `[T]`.

Why not change the fields to `List` and `Set`: 42 contract files and their presenters, fixtures and tests would change on every platform; the stability check fails a `List` field in an `-api` module; and it would fix neither enums nor, by itself, the classes Swift has no type for (a typed array would name them, which is the one thing it does better).

It is tested on the settings pages' samples: text, numbers, a contract's own classes, an empty list and a nullable one. A map is left until a native view reads one: the exported `Map` protocol already gives its keys as a Swift set.

### 10. Previews and snapshots

- `Samples.settings` reads `SettingsSamples.all`. `SettingsView`'s preview pages through them.
- `SettingsSnapshotTests`: each sample at the phone size, light and dark, with an `everySampleHasASnapshot` test like sign-in's; and the list beside a page at 1,032 by 800 points with a row marked, drawn through the same column layout the shell uses (`SettingsColumns`) with a stand-in for the page (a snapshot can't hold a host's records).
- `DesignGallery.rows` gains a selected row, which rerecords the rows reference.

### 11. Seeing the split with an account

No iPad simulator is signed in, and signing one in is not something the agent does. The split's mechanics (the trimmed path, fallback pages in the detail column, the pane's first page losing its back button, a deeper page's back, back from Settings) are walked on the signed-in iPhone 17 Pro simulator with `SETTINGS_SPLIT_MIN_WIDTH` and the list's width lowered in a scratch build, reverted before the final checks. Its look at an iPad's size is the snapshot of decision 10 and the probe's screenshots. An iPhone never has room for the split at the real rule (decision 13), so it can't cross it while Settings is open either: keeping the list's scroll position across it rests on the view's structure and is listed as not verified. What the iPhone does show is Settings and its pages turned onto their side and back. A real iPad window, signed in, is listed as not verified.

### 12. `Following` follows from a view that always exists (found while applying)

Walking the first build, Settings pushed and stayed blank. `Following` attached the task that follows a Kotlin flow to its content, and a screen's content is empty until its presenter's first state, which for a pushed screen comes after its view is made; a task on no view never starts. Sign-in never met it. `Following` now wraps its content in a `ZStack` and hangs the task on that. It isn't a decision of this change so much as a fault in the shell this change was the first to reach; it is fixed here because Settings can't show without it.

### 13. The app turns, and iOS has its own rule for the split (added after the first apply)

Asked for after the first apply, when the walk showed that portrait only left every iPad but the 13-inch ones a page at a time.

- **Orientations.** `INFOPLIST_KEY_UISupportedInterfaceOrientations_iPhone` is portrait and both landscapes, the system's convention for a phone (not upside down); `_iPad` is all four, which is also what iPad multitasking and resizable windows need. Nothing in the app locks a screen's orientation.
- **The rule is a size, not 840 points of width.** `settingsSplitFits(width, height)` in `:app-host`: at least 700 points wide and 500 tall. 700 is under every iPad held upright at full screen (iPad mini 744, iPad 820, 11-inch 834, 13-inch 1,024 or 1,032) and over half of the widest iPad's screen (683), so an iPad at full screen shows the list beside its page either way up and a window sharing the screen shows one page at a time. With the list 360 points wide the page gets 384 on an iPad mini, a phone's width, which every page already lays out for.
- **Why a height too.** On its side every iPhone is wider than 700 points (852 to 956) and at most 440 tall. Two columns under a bar and over the tabs would be three hundred points high, so a phone stays one page at a time, as the system's own Settings does. The shortest iPad at full screen is 744 tall.
- **Why not 840, the other platforms' number.** It was chosen for dp on Android and desktop windows, and on iOS it splits iPads by model for six points (834). **Why not the horizontal size class:** it is regular on the large iPhones on their side and its threshold in a resizable iPad window isn't a number the JVM tests can state; a size in points is decided in Kotlin with the rest of the rule.
- **Alternative considered:** a narrower list under 840 points. Not needed: 384 points is enough for a page.

## Risks / Trade-offs

- [A stack inside a stack's destination doesn't display] → Found by the probe before anything was built on it; the layout has no navigation container inside the destination.
- [The outer bar's title sits centred over both columns on iPad] → Seen on an iPad: centred in the window it lands over the page column, above the page's own title. Beside a page `SettingsView` now leaves the bar's centre empty and puts its title next to the back button, over the list (task 9.1).
- [A deeper page opens in the column without a push transition] → As on the other platforms' detail pane; its back button is its own bar's.
- [The split can't be seen signed in on an iPad] → Kotlin's rule is JVM-tested, the mechanics are walked on the iPhone with the width lowered, the look is snapshotted; the rest is in "Not verified".
- [Every screen now turns, and none was laid out for a phone on its side] → Each already lays out by the width it gets (`WindowWidth` in the shared UI, the system's list and bars natively). The feed, the profile, Settings and the Account page were looked at on their side; the rest are in "Not verified".
- [The iOS rule is no longer the other platforms' 840] → Deliberate (decision 13), and written in ui/settings-layout as iOS's own rule. The shared UI inside a pane still lays itself out by the width the pane gives it.
- [`paneDefault` runs a second presenter beside Settings] → Only while the UI reports it visible, which only the split does.
- [The shared rule function changes `SettingsListDetailDecoration`] → Its existing test and the Android list-detail screenshots hold it.

## Open Questions

- How a native settings page gets a bar of its own in the detail column (a column header from the design system, or `NavigationSplitView` after all): answered by the first native page.
