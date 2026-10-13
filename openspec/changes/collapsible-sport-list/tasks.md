# Tasks

## 1. Extract the shared list (no behaviour change)

- [x] 1.1 Add `composeApp/.../ui/SportTypeList.kt` with `LazyListScope.sportTypeList(...)` (sticky category headers, rows, 24dp nesting) and `QuickTypeChips(...)`, taking `ImmutableList`s and slot lambdas per design.md; verify `./gradlew :composeApp:compileKotlinJvm` passes
- [x] 1.2 Switch `FeedFilterUi.TypePickerSheet` to `QuickTypeChips` and `sportTypeList` (category header slot keeps the select-all checkbox); verify on desktop hot reload that the sheet looks and behaves as before
- [x] 1.3 Switch `ActivityTypePicker.ActivityTypeSheet` to the shared pieces (search, `outdoorOnly` and close-on-choose stay in the sheet); verify on desktop hot reload that the manual-entry picker looks and behaves as before

## 2. Collapsing

- [x] 2.1 Add the visibility rule and chevron `AppIconButton` (rotation, `stateDescription`, content description naming the sport) to `sportTypeList`, with `expanded`, `onToggleExpanded` and `expandAll` params; add the content-description string to `strings.xml`; verify `./gradlew :composeApp:compileKotlinJvm` passes
- [x] 2.2 Hold a `rememberSaveable` expanded-sport set in each sheet, starting empty; record picker passes `expandAll = query.isNotBlank()`; verify on hot reload that both sheets open collapsed, a chevron expands one sport without selecting it, Indoor Ride shows under Gym collapsed, typing "trail" shows Trail Run, and reopening a sheet collapses everything again

## 3. Partial selection

- [x] 3.1 Add `AppTriStateCheckbox` to `ui/adaptive` (Material `TriStateCheckbox`; iOS `minusCircleFill`), map `TriStateCheckbox` to it in `UseAdaptiveComponents`, and drop `feed_filter_category_some`; verify `./gradlew :composeApp:compileKotlinJvm` passes
- [x] 3.2 Feed filter: category headers and sport rows use the mixed state when partly selected; record picker: a collapsed sport names its chosen sub-sport as supporting text (`hiddenChoice`); verify on hot reload that selecting Trail Run shows Run and Running mixed, selecting Run ticks it and all its sub-sports, and the record picker shows "Trail Run" under a collapsed Run

## 4. Integration

- [x] 4.1 Run `./gradlew check` (detekt `UseAdaptiveComponents`, `composeStabilityCheck`, ktfmt) and verify it passes
- [x] 4.2 Run `openspec validate collapsible-sport-list --strict` and verify it passes

## Workflow follow-up

- Archive the change once implemented and reviewed (`/opsx:archive`).
- Then consider `activity-type-picker-subcircuit`.
