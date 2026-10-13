# Design

## Context

The record picker's `ActivityTypeSheet` (`composeApp/.../record/ActivityTypePicker.kt`) already searches. It keeps a `rememberTextFieldState()` in the UI, builds a label map for every catalog type, matches a type on its own label, its sport's label or its tokens, filters the groups, and passes `expandAll = true` to `sportTypeList` while a query is entered.

The feed filter's `TypePickerSheet` (`composeApp/.../feedfilter/FeedFilterUi.kt`) renders `picker.categories` from the presenter through the same `sportTypeList`. It uses `onCategoryClick` and `categoryTrailing` for the category checkboxes.

`sportTypeList` already shows a sub-sport whose sport isn't listed in the same group as a top-level row, so a search that matches Trail Run but not Run needs no list changes.

## Goals / Non-Goals

**Goals:**
- One matching rule for both type sheets.
- No changes to the feed filter's presenter, state or events.

**Non-Goals:**
- Fuzzy matching, synonyms ("mtb") or ranking. The rule stays a case-insensitive substring match, as today.
- Selecting only the visible matches of a category (rejected during exploration; see the spec's "Category selection is unavailable during search").

## Decisions

**Search stays in the UI.** Labels come from `strings.xml` through `stringResource`, which presenters can't call. The query is UI-only text, so per compose-screens.md it is a `rememberTextFieldState()` in `TypePickerSheet`, not presenter state. The sheet leaves the composition when it closes, so the query resets each time it opens, as the spec requires.
- *Alternative: send the query to the presenter and match on tokens only.* That would miss labels such as "Stand Up Paddle" for `sup`, and it would split the matching rule between the two sheets.

**A shared matcher with a pure core.** The matching moves to `ui/` beside `sportTypeList`, in two layers:
- a plain function, `matchesTypeQuery(type, query, labels)`, that holds the rule;
- a composable, `activityTypeMatcher(query)`, that builds the label map from `activityTypeLabel` over `SportCatalog.categories` and returns a `(ActivityType) -> Boolean`.

Both sheets use the composable. The plain function can be unit-tested in `composeApp`'s `commonTest`, beside `SportLabelsTest`, without resources.
- *Alternative: copy the record picker's lambda into `FeedFilterUi`.* That's quicker, but the two searches would drift.

**Category headers during search.** While the query isn't blank, `TypePickerSheet` passes `onCategoryClick = null` and an empty `categoryTrailing` to `sportTypeList`. The header then has no checkbox and no click target. That needs no new parameter: `sportTypeList` already treats a null `onCategoryClick` as a plain header, as the record picker uses it. Type rows keep their tri-state checkboxes, and `ToggleType` still sends the row's type, so a sport row selects the whole sport.

**Layout follows the record picker.** The search field goes in the sheet's padded column under `AppSheetHeader`, with the "recent and common" chips below it only when the query is blank, and the empty-results text in the same column. The `LazyColumn` keeps `fillMaxSize()`, so the sheet doesn't jump as results narrow. The field reuses `sport_picker_search` and `sport_picker_no_matches`.

## Risks / Trade-offs

- [A sport row selects sub-sports a search might hide] → It can't happen: a sub-sport also matches on its sport's label and token, so whenever a sport row is shown, all its sub-sports are shown with it.
- [Building labels for about 180 types on each recomposition] → It's the record picker's existing cost. `stringResource` can't be called inside `remember`, so the map is built during composition; the sheet recomposes only on typing and selection.
- [The header's tri-state disappears mid-search, so the athlete loses the category summary] → It returns as soon as the query is cleared, and type rows still show their own state.
