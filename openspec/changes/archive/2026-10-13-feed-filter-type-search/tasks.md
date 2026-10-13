# Tasks

## 1. Shared type matcher

- [x] 1.1 Add `matchesTypeQuery(type, query, labels)` and the composable `activityTypeMatcher(query)` to `composeApp/.../ui/` beside `sportTypeList`, per design.md. The rule is a trimmed, case-insensitive substring of the type's label, its sport's label, or its sport or sub-sport token; a blank query matches everything. Add `ActivityTypeSearchTest` in `composeApp/src/commonTest/.../app/ui/` covering:
  - a sub-sport label ("gravel" matches Gravel Ride, not Road Ride);
  - a sport label matching its sub-sports;
  - a token match (`sup`);
  - case and surrounding whitespace;
  - a blank query.

  Verify with `./gradlew :composeApp:jvmTest --tests "*.ActivityTypeSearchTest"`.
- [x] 1.2 Switch `ActivityTypeSheet` (`record/ActivityTypePicker.kt`) to `activityTypeMatcher`, deleting its inline label map and lambda. Verify that `:composeApp` compiles and that searching "gravel" in the record picker on the desktop app still shows Gravel Ride.

## 2. Feed filter search

- [x] 2.1 In `TypePickerSheet` (`feedfilter/FeedFilterUi.kt`), add the search field per design.md:
  - a `rememberTextFieldState()` query and an `AppTextField` using `sport_picker_search` and the search icon;
  - the quick chips only while the query is blank;
  - `picker.categories` filtered by the matcher, dropping empty groups;
  - `sport_picker_no_matches` when no group is left;
  - `expandAll`, a null `onCategoryClick` and an empty `categoryTrailing` while the query isn't blank.

  Verify that `:composeApp` compiles and that `./gradlew composeStabilityCheck` passes.
- [x] 2.2 Check the feed filter's type picker on the desktop app (`./gradlew :desktopApp:hotRun --auto` with the hot-reload MCP) against the delta spec's scenarios:
  - "gravel" shows Gravel Ride only.
  - "row" shows Row under Water Sports, and Indoor Row and Virtual Row under Gym & Fitness, with no category checkboxes.
  - Tapping the Water Sports header while searching leaves the draft unchanged.
  - "trail" shows Trail Run without expanding Run.
  - A query with no matches shows "No matching sports".
  - Selecting Gravel Ride, then clearing the query, keeps it selected and brings back the category checkboxes.
  - Reopening the sheet starts with an empty query.

## 3. Integration

- [x] 3.1 Stop the desktop app, then run `./gradlew format` and `./gradlew check`, and verify both pass.
- [x] 3.2 Run `openspec validate feed-filter-type-search --strict` and verify it passes.

## Workflow follow-up

- Archive the change with `/opsx:archive` once it's reviewed. That syncs the `feed/feed-filter` delta into the main spec.
