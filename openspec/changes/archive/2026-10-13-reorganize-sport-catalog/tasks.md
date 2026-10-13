# Tasks

## 1. Catalog placement

- [x] 1.1 Add an optional `category: SportCategory? = null` to `SubSportInfo` and let `sub(...)` take it. Rebuild `SportCatalog.categories` per type, as in design.md: the generic type goes in the sport's category; each sub-sport goes in its own category, or its sport's when it has none; catalog order is kept. Delete the `shortcuts` map and its KDoc. Verify with `./gradlew :core-data:jvmTest --tests "*.SportCatalogTest"`, still passing with the old categories.
- [x] 1.2 Replace the `SportCategory` entries with the 20 in design.md's order (`Running` … `Other`), removing `Outdoor`. Re-file every sport per the move table. File `row:indoor`, `row:virtual` and `xcSki:indoor` under `Gym`. Update the enum KDoc, which says the groups follow Garmin's activity menus, to say Garmin Connect's list. Verify that `core-data` compiles and the provider-coverage tests in `SportCatalogTest` pass unchanged.
- [x] 1.3 Tighten `everyCategoryTypeIsKnownAndListedOnce` so every known type appears in exactly one category across all categories, not just once within each. Add tests:
  - Indoor Row is under `Gym` and not `WaterSports`.
  - Treadmill Run and Indoor Ride stay under `Running` and `Cycling`.
  - Walk is under `Walking`.

  Verify with `./gradlew :core-data:jvmTest --tests "*.SportCatalogTest"`.

## 2. Labels

- [x] 2.1 In `composeApp/src/commonMain/composeResources/values/strings.xml`:
  - Add `sport_category_*` strings for the new entries ("Gym & Fitness", "Mind & Body", "Combat", "Walking", "Hiking & Climbing", "Parasports", "Target", "Skating", "Adventure").
  - Remove `sport_category_Outdoor`.
  - Relabel `sub_sport_wheelchair_walkPace` to "Push - Walk Speed", `sub_sport_wheelchair_runPace` to "Push - Run Speed" and `sub_sport_run_indoorTrack` to "Indoor Track Run".

  Verify with a `SportLabelsTest` case asserting that every `SportCategory` entry has a `sport_category_<name>` string (add it if none exists), via `./gradlew :composeApp:jvmTest --tests "*.SportLabelsTest"`.

## 3. Type list

- [x] 3.1 Update `sportTypeList`'s KDoc (`composeApp/.../ui/SportTypeList.kt`), which still mentions the Gym shortcut, to describe sub-sports filed away from their sport. Every sport with sub-sports stays collapsible in every category (no flattening; see design.md). Verify that `:composeApp` compiles and that `./gradlew composeStabilityCheck` passes.
- [x] 3.2 Check the record picker on the desktop app (`./gradlew :desktopApp:hotRun --auto`, using the hot-reload MCP for a screenshot and the semantic tree):
  - Run is collapsible like Ride, and Swimming shows Swim collapsible plus Water Fitness.
  - Indoor Row is a top-level row under Gym & Fitness.
  - Choosing Gravel Ride and reopening shows "Gravel Ride" under the collapsed Ride row.
  - Searching "gravel" shows Gravel Ride.

## 4. Feed filter

- [x] 4.1 Update `FeedFilterPresenterTest`:
  - The Gym category test now uses a filed-elsewhere sub-sport (Indoor Row) in place of the removed shortcut; it stays mixed when only Indoor Row is selected.
  - Add a test that toggling `WaterSports` selects `row` whole, so Indoor Row reads as covered and `Gym` turns `Partial`.
  - Add a test that selecting every Row sub-sport, from Gym and Water Sports, normalizes to bare `row`.

  Fix any other fixtures naming `Outdoor` or moved types. Verify with `./gradlew :app-presenters:jvmTest --tests "*.FeedFilterPresenterTest" --tests "*.TypeSelectionTest"`.
- [x] 4.2 Check the feed filter's type picker on the desktop app:
  - Run and Walk are collapsible, and selecting Walk checks the Walk row and the Walking category.
  - Selecting only Trail Run shows Run and Running mixed.
  - Toggling Water Sports shows Indoor Row checked under Gym & Fitness, and Gym mixed.

## 5. Integration

- [x] 5.1 Run `./gradlew format` then `./gradlew check`, and verify both pass. That run covers the tests on every target, detekt, ktfmt and the Compose stability check.
- [x] 5.2 Run `openspec validate reorganize-sport-catalog --strict` and verify that the delta specs still match what was built. In particular, check every example in their scenarios (Gravel Ride, Indoor Row, Walking) against the final catalog.

## Workflow follow-up

- Archive the change with `/opsx:archive` once it's reviewed. That syncs the `record/activity-type-picker` and `feed/feed-filter` deltas into the main specs.
