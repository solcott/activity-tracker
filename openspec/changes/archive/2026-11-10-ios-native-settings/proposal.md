# Proposal

## Why

Since the iOS app got its SwiftUI shell (`ios-native-shell`, archived), Settings shows one page at a time on iPad: the Compose decoration that laid its list beside the open page doesn't run under a native shell, and `ui/settings-layout` records that as an iOS exception "until it is drawn natively". Phase 4 of the native iOS UI (`openspec/explorations/ios-native-ui.md`) moves the features one change each and names Settings first for that reason. It is also the cheapest first screen: a list of rows over a state with no loaded content, which still forces the three things every later screen needs settled: a native screen inside a tab's stack, a layout that draws more than one of Kotlin's records at once, and how Swift reads a contract's immutable collections.

## What Changes

- **Settings' list is native on iOS.** A SwiftUI view draws the states `SettingsPresenter` publishes: the Account row, the units choice saved in place, and a row per page with its summary, in the system's grouped list with the system's navigation bar. It says what the shared list says, from the same strings. Moderation stays off it, as on every phone and tablet.
- **The list shows beside its page on a wide iOS window again.** In a window at least 700 points wide and 500 tall, which is every iPad at full screen either way up, Settings is its list beside the page it opened, Activity defaults until a row is chosen, with the open page's row marked, as Android, desktop and web lay it out from 840dp. The iOS exception in `ui/settings-layout` goes. Narrower or shorter (an iPhone either way up, an iPad window sharing the screen), it is one page at a time, as now.
- **The iOS app turns with the device.** It declared portrait only, the Xcode template's setting. An iPhone now shows upright and on either side, an iPad any way up, on every screen.
- **The pages themselves are not migrated.** Account, Activity defaults, Privacy zones, Segments & groups, Training zones, Gear and the blocked and muted lists, and everything they open, are still drawn by the shared UI, now in the detail column when the window is wide. Each moves in its own change.
- **The presenter host says where Settings can split.** The back stack stays the only selection state. The host tells a UI when a stack ends in Settings and its pages, and holds the one screen that is shown without being on the stack (Activity defaults, until a row is chosen), running its presenter while it is on screen. The UI tells the host how wide it is.
- **Rows' summaries are chosen in Kotlin.** What the Privacy zones, Segments & groups and Activity defaults rows and the units note say moves from the Compose screen to `AppText` functions both UIs resolve, as sign-in's did.
- **Immutable collections reach Swift through one wrapper.** The decision phase 1 left for the first native list screen: contracts keep `ImmutableList` and `ImmutableSet`, and Swift reads either as a typed array through one helper, for text, whole numbers and classes Swift has a type for. Settings' own state has no collection, so the helper is proven on the settings pages' sample states, and what it can't carry (a Kotlin enum, a class no exported signature names) is recorded.
- **Previews and snapshots** of the list in each sample state, and of the list beside a page at an iPad's size.

Out of scope: any settings page's native view; the Compose decoration on Android, desktop and web, which is unchanged; laying any other screen out differently for a phone on its side; SwiftLint and swift-format; removing the shared `SettingsUi` from iOS builds (phase 5).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui/ios-native-shell`: the app turns with the device.
- `ui/settings-layout`: the iOS exception is removed; an iOS window with room (its own rule, by size in points) shows the list beside its page; the list is drawn natively on iOS and says what the shared one says; how back works beside a page on iOS.
- `dev/presenter-host`: the host reports when a stack's top is Settings and its pages, holds the page shown beside Settings until a row is chosen, and runs it while it shows.
- `dev/ios-swift-interop`: Swift reads a contract's immutable list or set as a typed array.
- `ui/ios-design-system`: a native list row can be marked as the one whose page is shown.

## Impact

- **`:app-host`:** the settings split of a stack (which record is Settings, its pages, the page shown first), a record that lives beside a stack's record, the size a UI reports and the rule for whether it has room, and what a record's shared UI needs to hide its back button in a pane. JVM tests.
- **`:feature-settings-api`, `:app-common-api`:** `SettingsTexts` (row summaries, the units note), `LabelText.visibility`, and the rule for which row a layout marks, each with tests. **`:feature-settings-ui`, `:app-common-ui`:** resolve them; no screenshot reference changes.
- **`:composeApp`:** the iOS fallback host gives a pane's first page no back button; the Compose decoration reads the split rule from `:app-host` instead of holding its own copy.
- **`iosApp`:** `SettingsView`, the split layout in the shell, the supported orientations, a selected state on `AppListRow`, the collection helper, previews, snapshot tests and their reference images; `ScreenRegistry.app` gains Settings.
- **Docs:** the roadmap's phase 4 section, findings and open items; `.agents/rules/compose-screens.md` and `adaptive-ui.md`; `AGENTS.md` where it describes the host.
- **Risk carried:** the layout is probed first, on iPad simulators with no session (the first one proposed, a navigation stack per column, turned out not to display; see `design.md`). The split itself can't be walked on an iPad signed in (no signed-in iPad simulator), so it is exercised on the signed-in iPhone with the width rule lowered in a scratch build and recorded as not verified on iPad.
