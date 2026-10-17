# Proposal

## Why

A strength workout's feed card and activity page show "Distance 0.00 mi" (and the page "Elevation –"), because every non-dive activity gets distance and elevation stats whether or not its sport covers ground. The importer stores the 0 m the watch writes for a gym session, and the stat prints it.

## What Changes

- For a sport the app gives no pace or speed (strength, yoga, HIIT, climbing and the others the sport catalog marks as having no rate), the feed card and the activity page leave out distance when it's absent or zero, and the page leaves out elevation gain when it's absent or zero.
- Such a sport's distance and elevation still show when they're above zero (an indoor climb with a recorded ascent, water fitness with a distance).
- Sports with a pace or speed keep today's stats, a dash included, so a run with no distance still says so.
- The import preview follows the same rule, so it matches what the published activity will show.
- Display only: stored records are unchanged and nothing is rewritten, so older strength workouts show correctly at once.

## Capabilities

### New Capabilities
- `feed/activity-stats`: which summary stats an activity's feed card, page and import preview show for its sport.

### Modified Capabilities
None.

## Impact

- `composeApp`: `ui/ActivityCard.kt`, `detail/ActivityDetailUi.kt` (`overviewStats`), `record/ImportActivityUi.kt`; the shared decision goes next to `Format.rateStat`.
- `core/data` `SportCatalog`: read only (`RateKind.None`).
- No lexicon, AppView or data-layer change.
