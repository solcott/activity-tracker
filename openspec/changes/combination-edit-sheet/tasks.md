# Tasks

## 1. Compact data source rows

- [x] 1.1 In `detail/DataSourcesUi.kt`, rework `SourceRow` into one line: the kind label, then a menu trigger (the chosen member's name in primary color with `AppIcon.DropDown`, opening an `AppMenu` of `offered` members with the chosen one checked) when `offered` is non-empty, otherwise the "From X" / "Left out" text, then the saving spinner and `AppSwitch`. Drop the `MenuField`. Verify that `./gradlew :composeApp:compileKotlinJvm` passes and that `DataSourcesSection` keeps its loading/error/retry block.

## 2. Edit sheet

- [x] 2.1 Add `edit_combination` (the Edit button's description), `edit_combination_title` ("Edit combination") and `action_done` and `action_edit` (both already exist and are reused), and reword `data_source_comes_from` as the menu's "%1$s comes from %2$s" description to `composeResources/values/strings.xml`. Verify that the generated `Res.string` accessors compile.
- [x] 2.2 Create `detail/EditCombinationSheet.kt`: an `AppBottomSheet(rememberAppSheetState())` with a title row (title `Text` and a "Done" `AppTextButton` that dismisses), a "Combined from" members list with remove `AppIconButton`s and the confirm `AppAlertDialog` moved from `Members` (sending `RemoveMember`), then `DataSourcesSection`. Verify that it compiles and that detekt `UseAdaptiveComponents` reports nothing for the file.
- [x] 2.3 In `detail/RecordingsUi.kt`, replace the `onRemove` parameter of `Members` with `onEdit: (() -> Unit)?`, which shows an `AppTextButton` beside the "Combined from" label. Drop the remove buttons and dialog there. Verify that the member links still open their pages.
- [x] 2.4 In `detail/ActivityDetailUi.kt` `Header`, remove the inline `DataSourcesSection`. Hold `editing` in `rememberSaveable`, pass `onEdit` only when `state.manage != null` and the activity has members, and render `EditCombinationSheet` only while `editing` and the activity still has members, so a split closes it. Verify that `./gradlew :composeApp:compileKotlinJvm` and `./gradlew :app-presenters:jvmTest --tests "*.CombinePresentersTest"` pass.

## 3. Verification

- [x] 3.1 Run `./gradlew composeStabilityCheck` and `./gradlew check`, and verify that both pass (ktfmt, detekt, lint, all tests).
- [x] 3.2 On desktop with hot reload, open alice's combined ride. Verify that the header shows "Combined from … Edit" with no switches or dropdowns. In the sheet, switch a kind's source, toggle a kind off and on, and remove a member through the confirm dialog, and verify that the charts follow each change. Take before and after screenshots.
- [x] 3.3 Open the same combined ride as a follower (another seeded account) and verify that the members show but there is no Edit button.
