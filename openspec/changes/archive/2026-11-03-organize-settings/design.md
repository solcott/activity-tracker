# Design

## Context

- **The screen today:** `SettingsScreen` is reached from Profile (`ProfilePresenter` → `navigator.goTo(SettingsScreen)`), so on the signed-in back stack it sits at `[…, Profile, Settings]`.
- **The presenter:** it is already built from holders: `rememberDefaultVisibilitySettings`, `rememberSegmentMatchingSetting` and `rememberGroupActivitiesSetting`, each taking `flights` and an `onError`. `SettingsState` flattens them, and one `error` serves them all.
- **Navigation:** `App.kt` renders that stack with a single `NavigableCircuitContent(decoratorFactory = GestureNavigationDecorationFactory())`, which gives iOS edge-swipe and Android predictive back. `LocalIsNavRoot` comes from `rootFlags(backStack)`. Each top-level tab keeps its stack by `resetRoot` with save/restore.
- **Layout and components:** screens size themselves with `WithWindowWidth`: `Compact` under 600dp, `Medium` to 840dp, `Expanded` beyond. `ui/adaptive` has no navigation-row component.
- **Approach:** a `NavDecoration` around `ListDetailPaneScaffold` can lay a Circuit back stack out as a list and a detail while the back stack stays the only selection state. Here the list isn't the stack's root, since Settings sits deep in the signed-in stack.

## Goals / Non-Goals

**Goals:**
- Settings' list and its pages are ordinary Circuit screens on the one back stack, so web, desktop's back shortcut, Android back and iOS swipe all keep working unchanged.
- The page the stack shows never depends on window width. Width changes only how records are laid out.
- Each page owns its state, events and error, and can be tested apart from the others.

**Non-Goals:**
- List-detail anywhere besides Settings. The decoration is written so a second list screen could adopt it, but nothing else is converted here.
- Changing what any setting does, stores or says (see proposal: nothing a setting does changes).
- Material's `ThreePaneScaffoldNavigator` or `adaptive-navigation`.

## Decisions

### Split along the existing holders
- **New pages:**
  - `ActivityDefaultsScreen` wraps `rememberDefaultVisibilitySettings`.
  - `SegmentsAndGroupsScreen` wraps `rememberSegmentMatchingSetting` and `rememberGroupActivitiesSetting`.

  Both are `data object`s in `feature/settings/api`, registered `@CircuitSerializable(SessionScope::class)`. `SettingsEvent.SegmentSettingEvent` and `GroupActivitiesChanged` move to `SegmentsAndGroupsEvent`.
- **Errors:** each page keeps its own `UiError`, so a failed save shows where it happened.
- **What the root reads:** only what it summarizes:
  - `ActivityDefaults` for the activity visibility
  - `PrivacyZones.current` for the count and route ends
  - `SegmentPrefsRepository` for matching on/off
  - units, `canModerate` and sign-out

  It reads those repositories directly instead of building the whole holders, so it never starts writes.
- **Alternatives:**
  - Keeping one presenter with SubCircuits: rejected, because the pages must be back-stack records for the detail pane and for back to work.
  - A holder per page shared with the root: unnecessary, since the root needs only read-only summaries.

### Row navigation: pop to Settings, then push
Every row event runs `navigator.popUntil { it is SettingsScreen }` and then `navigator.goTo(page)`. On a phone Settings is already on top, so the pop does nothing. On a wide window the pop clears whatever page the detail pane held, so choosing rows in turn replaces the page rather than stacking up. That gives the spec's "back leaves Settings" with no width check in the presenter. Moderation does the same pop before its push, so it never stacks on a settings page.

### `SettingsPage` marker in `:app-common-api`
`interface SettingsPage : Screen` goes on these screens:
- the settings feature's pages: `ActivityDefaultsScreen`, `SegmentsAndGroupsScreen`, `PrivacyZonesScreen`, `TrainingZonesScreen`, `SportZonesScreen`, `ZonesScreen`, `ThresholdHistoryScreen`
- other features' screens: `GearListScreen` (`feature/gear/api`) and `BlockListScreen` (`feature/profile/api`)

Those features already depend on `:app-common-api`, so this adds no new module edges and `checkModuleBoundaries` is unaffected. A marker keeps the decoration free of feature imports. The rejected alternative is an allow-list of screen classes in `composeApp`, which would have to change with each new page.

`ProfileScreen`, gear item screens and `ModerationScreen` stay unmarked, so they push full width.

### List-detail as a delegating `NavDecoration` in `composeApp/nav`
`SettingsListDetailDecoration(delegate)` wraps the decoration that `GestureNavigationDecorationFactory` creates, and its factory replaces that factory in `App.kt`. On each `DecoratedContent(args, navigator, modifier, content)` call it works as follows.

1. **Find the settings run.** Scan down from the top while records are `SettingsPage`. If the record below that run is `SettingsScreen`, the run belongs to Settings and Settings is the list. If the stack has no such run, nothing above Settings, or a non-page on top, take the pass-through path: `delegate.DecoratedContent(args, …)` exactly as today.
2. **Measure, then pass a trimmed stack to the delegate.** A `BoxWithConstraints` measures the space the shell gives the content. That is the available width, not the window, so the sidebar is accounted for, and it matches `WithWindowWidth`'s `Expanded`.
   - Under 840dp: pass through.
   - From 840dp: call the delegate with the args trimmed to end at the `SettingsScreen` record, and give it a `content` that, for that one arg, renders a `ListDetailPaneScaffold`:
     - **List pane:** `content(settingsArg)`.
     - **Detail pane:** `content(topArg)` when the run is non-empty. When it is empty, `CircuitContent(ActivityDefaultsScreen, navigator = navigator)`, which is not a back-stack record.
3. **Keep both panes live.** Wrap both panes in `ProvideRecordLifecycle(isActive = true)`. Circuit pauses the presenter of any record that isn't on top, which would freeze the list's summaries.

Because the delegate sees `Settings` as its top in every wide state, moving between pages animates as a pane swap, not a push. The gesture decoration still handles back for the whole trimmed stack, and a back gesture pops the real top record through `navigator`, so the stack and what is on screen stay in step.

Also:
- One `ListDetailPaneScaffold` for both pane counts once a settings run exists. Crossing 840dp then changes only the `ThreePaneScaffoldValue`, the list's composition survives, and it doesn't reload.
- The directive is built through the constructor with `maxVerticalPartitions = 1` and `shouldAutoFocusCurrentDestination = false`. The two-pane threshold comes from the measured width (`maxHorizontalPartitions = if (width >= 840.dp) 2 else 1`), not from `currentWindowAdaptiveInfo`.

The `movableContentOf(content)` trick keeps each record's composition when it moves between the delegate's slot and a pane.

**Alternatives:**
- A nested `NavigableCircuitContent` inside `SettingsUi`: rejected. It would be a second back stack that web, desktop back and the tab save/restore can't see.
- Pushing Activity defaults on wide windows: rejected. The stack would depend on window size, and back would have to skip an auto-pushed record.

### Pane locals
The decoration provides two locals, each in the lowest module whose readers need it:
- `LocalIsPaneRoot: Boolean`, in `:ui-designsystem` beside `LocalIsNavRoot` (`AppTopBar.kt`). It is true for the detail pane's first-level record. `AppTopBar` (common and iOS actual) hides its back button when either flag is set. Only `LocalIsNavRoot` gives the large iOS title.
- `LocalSettingsPane`, a small `@Immutable` value in `:app-common-api`, next to the marker, because it holds a `Screen`. It has `twoPane: Boolean` and `shownPage: SettingsPage?`: the run's first record, or `ActivityDefaultsScreen` when auto-shown. `SettingsUi` reads it to mark the selected row.

Rejected alternative: setting `LocalIsNavRoot` for the pane root, which would also give the detail page a tab root's large title.

### `AppListSection` / `AppListRow` in `ui/adaptive`
Settings' rows need a navigation row with a title, an optional summary, a trailing chevron or control, and a selected state. Sections group them under a heading.

| Platform | Section | Row |
|---|---|---|
| `nonAppleMain` (Android, desktop, web) | A plain column with a heading | Material `ListItem`, as `SportTypeList` already uses it |
| `iosMain` | An inset grouped section, like `AppCard` on iOS | iOS look: chevron `PlatformIcon`, separators inset to the text, dim-on-press |

`UseAdaptiveComponents` already flags `ListItem` outside the wrappers. Check that the rule accepts the new wrapper files.

### Dependency
Add `org.jetbrains.compose.material3.adaptive:adaptive` and `:adaptive-layout` at 1.3.0 to `gradle/libs.versions.toml`, with catalog notes saying why:
- The `androidx` coordinates are Android-only.
- JetBrains' repackage covers android, jvm, iOS and wasmJs.
- It needs Compose ≥ 1.10 and pulls in no material3.

Add only to `composeApp`'s commonMain.

## Risks / Trade-offs

- **[The decoration interacts with circuitx gesture navigation (predictive back, iOS swipe)]** → On a wide window the delegate's top is Settings, so a swipe animates Settings leaving rather than the detail page. Acceptable on wide windows, where iOS means iPad and edge swipe is rare. Cover the stack shapes with JVM tests and check on an iPad simulator.
- **[The auto-shown Activity defaults is not a record, so its retained state is lost when the athlete picks its row]** → Picking the row replaces the auto-shown page with a record of the same screen. The page reloads its defaults once, which are local reads from the tier space. Acceptable.
- **[`popUntil` on rows could drop a non-settings record]** → It pops only records above `SettingsScreen`, and rows are tappable only while Settings is visible, which means only settings pages are above it.
- **[Content width vs window width]** → The 840dp threshold uses the measured width, consistent with `WithWindowWidth`. It differs from Material's window-class default on purpose.
- **[Screenshot reference churn]** → Every settings reference changes. Look at the new references before committing them, and add a two-pane reference.

## Migration Plan

Client-only; no data, lexicon or AppView changes.
- **Saved stacks:** a saved back stack holding `SettingsScreen` restores as before, and the new screens register in `SessionScope`.
- **Rollback:** revert the change.
