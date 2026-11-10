# Proposal

## Why

`ios-native-settings` (archived) drew Settings' list natively on iOS and laid it beside its page on an iPad, but every page is still the shared Compose UI shown through the fallback, including Activity defaults, which an iPad shows beside the list the moment Settings opens. Phase 4 of the native iOS UI (`openspec/explorations/ios-native-ui.md`) moves the features one change each, lists and forms first. Activity defaults and Segments & groups are the two simplest pages: forms of pickers and switches over a state with no loaded content. Drawing them natively settles the question the roadmap left for the first native page, how such a page gets a bar of its own in the detail column, and the two open items on what Swift can read, as far as these pages need them.

## What Changes

- **Activity defaults is native on iOS.** A SwiftUI form draws who new activities are shown to and each detail category's default, offering only the activity's visibility or narrower for a category, the Followers note, the locked note, "Saving…" and a failed save, in the same words as the shared page. Alone it uses the stack's own bar and back; beside the list it is the page Settings shows until a row is chosen.
- **Segments & groups is native on iOS.** Segment matching, the leaderboard and bracket-board switches, offered under the same conditions as the shared page, and the grouping choice (Off, Connected, Anyone), each with its note.
- **A native page beside the list has a bar of its own.** The probe (in `design.md`) found that a page's navigation title never shows in the detail column: the bar over both columns is the stack's, and it keeps Settings' title. So a native page in the column draws a column header from the design system with its title, as the shared pages draw their own bar there, and no back button, since the list beside it stays. `NavigationSplitView` was probed again and set aside.
- **Kotlin chooses the text and the conditions, both UIs ask.** What these pages build with a rule moves to `AppText` functions and plain functions in the `-api` modules: a detail category's name and help, the Followers note, the locked note, each page's note under its choices, the grouping choices with their labels, which grouping choice shows as chosen, and when each switch is offered and can be changed. The Compose pages resolve and ask the same functions, with no visible change on Android, desktop or web.
- **A choice among a Kotlin enum's values reaches Swift as a list of objects.** A list of enum values can't reach Swift; the grouping choices are offered as a list of small objects, each holding its enum value as a property (which Swift reads as its own enum) and its label. That is the rule the roadmap's open item asked for. These pages hold no `:core-data` or `:core-lexicon` class as a collection's element, so that open item stays open, recorded as not needed here.
- **Previews and snapshot tests** of both pages in each shared sample, alone and in the detail column beside the list at an iPad's size; the registry gains both screens. Every other settings page keeps the fallback.

Out of scope: zones, training zones, sport zones and threshold history (charts), privacy zones (map), Account and Leave; a back button in the column header for a deeper native page (no native page is deeper yet); the visibility pickers of other screens (an activity's, record's and import's), which keep the shared UI.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui/settings-layout`: Activity defaults and Segments & groups are drawn natively on iOS and say what the shared pages say; a native page beside the list shows its own title and no back button.
- `ui/ios-design-system`: a page drawn beside another screen has a column header in place of the stack's bar.
- `dev/ios-swift-interop`: a choice among an enum's values is offered to Swift as a list of objects holding the value.
- `dev/shared-display-text`: the visibility pickers' labels and notes and the two pages' notes are chosen in shared code.

## Impact

- **`:app-common-api`:** `VisibilityTexts` (category names and help, the Followers and locked notes) and the options a visibility picker offers; tests. **`:app-common-ui`:** `VisibilityPicker`, `DetailVisibilityPicker` and `ActivityVisibilityForm` resolve them; no screenshot reference changes.
- **`:feature-settings-api`:** `SettingsTexts` gains the two pages' notes; `SegmentsAndGroupsState` gains the offered and enabled conditions and the grouping options; `ActivityDefaultsState` whether it can be changed; tests. **`:feature-settings-ui`:** both pages ask them.
- **`iosApp`:** `ActivityDefaultsView`, `SegmentsAndGroupsView`, a column header (`PageBar`) in `Design`, `ScreenView` telling a native page it is in a column, type aliases, samples, previews, snapshot tests and their references; `ScreenRegistry.app` gains both.
- **Docs:** the roadmap's phase 4 entry, a "Phase 4 probes (settings pages)" and findings section, its open items; `.agents/rules/compose-screens.md`.
- **Risk carried:** iOS 18 can't be walked signed in (its simulators can't reach the local stack), so there the pages rest on the probe, the Swift tests and a launch of the build; the snapshots compare only on iOS 26.0.
