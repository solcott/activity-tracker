# Design

## Context

`Header` in `detail/ActivityDetailUi.kt` currently renders `Members` (`detail/RecordingsUi.kt`). For the owner, `Members` adds a remove `AppIconButton` per member plus a confirm `AppAlertDialog`, and `Header` follows it with `DataSourcesSection` (`detail/DataSourcesUi.kt`). The presenter already provides everything the editor needs: `ActivityDetailState.Loaded.dataSources` (owner-only, loaded eagerly), `manage` (non-null for the owner), and the events `ChooseSource`, `SetIncluded`, `RetrySources` and `RemoveMember`. The Export sheet (`ExportActivityUi.kt`, shown from `ManageActivityUi.kt` with a UI-local `exporting` flag) is the model for an owner-only sheet on this screen.

## Goals / Non-Goals

**Goals:**
- Show one line on the page and put every combination edit in one sheet, as described in the specs.
- Leave presenter behavior and its tests unchanged.

**Non-Goals:**
- Adding members (combine) or splitting/deleting, which stay where they are (combine prompt and banner, overflow menu).
- Lazy-loading `dataSources` when the sheet opens.
- Changing how a member is named, or the chart captions.

## Decisions

- **Sheet visibility is UI state.** A `rememberSaveable { mutableStateOf(false) }` in `Header` (or `Members`), the same way `exporting` works for Export. A presenter event/state field would only add test churn for something no other layer observes. The alternative was an `ActivityDetailEvent.EditCombination` + `State.editing`. We rejected it: nothing needs to survive outside the composition, and saveable state covers configuration changes.
- **New `EditCombinationSheet` composable** in `detail/EditCombinationSheet.kt`, using `AppBottomSheet(rememberAppSheetState())` (full height, since the list can be long). It has two sections:
  1. Members, with remove buttons and the confirm dialog, both moved out of `Members`.
  2. The data sources rows, reused from `DataSourcesUi.kt`.
  The `onRemove` parameter of `Members` goes away. `Members` gains an optional `onEdit: (() -> Unit)?` that shows an `AppTextButton` after the "Combined from" label.
- **Header row without Cancel/Confirm.** `AppSheetHeader` requires a confirm action, and Cancel would wrongly suggest that edits are reverted. Every edit here takes effect immediately, as it does today. So the sheet gets a title `Text` and a trailing "Done" `AppTextButton` that dismisses it. Swiping and scrim taps dismiss it too.
- **Compact source row.** One `Row`: the kind label (weight 1f). When `offered` is non-empty, next to it goes a text-button-style trigger showing the chosen member's name and an `AppMenu` (anchored in a `Box`) listing the offered members. Otherwise the "From X" or "Left out" text appears, as now. Then the saving spinner and the `AppSwitch`. This replaces the outlined `MenuField` under each row. `MenuField` stays in use elsewhere.
- **Close on split.** The sheet closes itself when the activity is no longer combined (`activity.members` becomes empty / `state.dataSources == null`). It does this through a `LaunchedEffect` on that condition, or simply by rendering the sheet only while that condition holds.

## Risks / Trade-offs

- [Discoverability of source choices drops] → The chart captions already name devices, and the Edit button sits right next to "Combined from".
- [The menu trigger in a row might be read as plain text] → Style it with the primary color and the `AppIcon.DropDown` icon. Check it on iOS and Material during verification.
- [Removing the `onRemove` parameter of `Members` touches every caller] → It has only the one caller, in `Header`.
