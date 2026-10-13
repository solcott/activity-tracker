# Proposal

## Why

The sport catalog's picker groups have drifted. Cross-category shortcuts list some types twice (Walk under Running and Outdoor, ten indoor types under Gym as well as their own sport), Outdoor, Gym and Other have become grab-bags, and some labels read badly ("Wheelchair Run Pace", "Indoor Run" next to "Treadmill Run"). Garmin Connect's activity list shows a cleaner model: each type is listed once, indoor variants live in Gym only when their sport sits in an outdoor group.

## What Changes

- A sub-sport MAY be filed in a different category from its sport (Indoor Row under Gym & Fitness while Row stays in Water Sports). Every activity type is listed in exactly one category.
- Cross-category shortcuts are removed. Filing a sub-sport elsewhere replaces them.
- The categories become: Running, Cycling, Gym & Fitness, Mind & Body, Combat, Swimming, Multisport, Walking, Hiking & Climbing, Winter Sports, Parasports, Water Sports, Diving, Team Sports, Racket Sports, Target, Skating, Adventure, Motorsports, Other. Outdoor is removed. Its sports, and several from Gym and Other, are regrouped.
- In the feed filter, selecting a category selects each type listed in it. A sport listed there is selected whole, as before, so its sub-sports filed in another category are covered too. For example, Water Sports covers Indoor Row, which is listed under Gym & Fitness.
- Labels: the wheelchair sub-sports read "Push - Walk Speed" and "Push - Run Speed", and `run:indoorTrack` reads "Indoor Track Run".
- No lexicon tokens change. Sport and sub-sport values in records, the importer mappings and the AppView are untouched.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `record/activity-type-picker`:
  - Adds how types are grouped: each type is listed once.
  - Shortcuts become sub-sports filed in another category.
- `feed/feed-filter`:
  - What selecting a category covers is restated without shortcuts.
  - Collapsing and filed-elsewhere rows follow the picker's new rules.

## Impact

- `core/data`: `SportCatalog` (`SportCategory` entries, `SubSportInfo` gains an optional category, `categories` is built per type, shortcuts are removed) and `SportCatalogTest`.
- `composeApp`:
  - `strings.xml`: category names and the relabels.
  - `SportTypeList`: KDoc only; it already shows a sub-sport filed away from its sport as a top-level row.
  - Category icons, if any are keyed by `SportCategory`.
- `app/presenters`:
  - `FeedFilterPresenter` and its tests.
  - The selection logic already treats a listed sport as whole, so the expected changes are only fixtures and examples that name moved types or shortcuts.
- Unaffected: lexicons, `core/lexicon`, `DecodedActivityMapper`, the AppView, and stored records.
