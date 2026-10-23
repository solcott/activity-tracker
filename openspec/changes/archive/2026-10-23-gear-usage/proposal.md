# Proposal

## Why

The point of tracking gear is knowing how much it has been used: when to replace running shoes, how many days the skis have had, how many hours are on a fork. With activities listing their gear (`activity-gear`), the AppView already holds everything needed to add this up, at every tier. It's also the only place that can respect each viewer's access when someone else looks at a bike.

## What Changes

- **Usage totals per item**: activities, moving time, distance, elevation gain, elevation loss (vertical for skis and boards), days used, and for dive gear, dives and bottom time. Each total also breaks down by activity type and by year.
- **Starting usage.** An item can record the distance and time it had before the athlete started tracking it (gear bought used, or shoes already broken in). Starting usage is added to the totals and not to the breakdowns.
- **Totals for each viewer.** The athlete's own totals count all of their activities that list the item. Anyone else's totals count only the activities whose gear they may see, of an item they may see.
- **Each activity counts once.** A recording grouped under a combined activity counts only through that combined activity.
- **New queries**: `gear.getGear` (one item, its totals, breakdowns and starting usage) and `gear.getActorGear` (an athlete's items the viewer may see, each with its totals), plus `gear.getGearActivities` (the activities counted, newest first, paged).
- **A gear page.** Tapping an item in the gear list or on an activity page opens it: photo, details, totals, breakdowns and its activities. The athlete's own page has Edit.
- **The gear list shows each item's main total**: distance for bikes, shoes and wheels, days and vertical for skis and boards, dives for dive gear, and hours for everything else.

Out of scope: totals for components through their bike (`gear-components`), service and retirement reminders, and gear on profiles.

## Capabilities

### New Capabilities
- `gear/gear-usage`: how an item's usage is totalled, what each viewer's totals count, and where totals are shown.

### Modified Capabilities
None.

## Impact

- **Lexicons:** `gear/item.json` gains `startingDistance` (meters) and `startingTime` (seconds). New `gear/getGear.json`, `gear/getActorGear.json` and `gear/getGearActivities.json`, and `gear/defs.json#gearUsage` (totals, `bySport`, `byYear`).
- **core/lexicon:** the record fields, query descriptors and output models.
- **server/appview:** a `GearViews` aggregation over `activity_gear` joined to `activities`, with `visibleTo` on the gear row, the activity and the item, and routes for the three queries. `gear_items` gains the starting usage columns in a migration.
- **core/data:** `GearRepository` gains `usage`, `actorGear` and `activitiesPage` (an `Outcome<Page<…>>`).
- **Screens:** a new `GearScreen` (gear page). `GearListScreen` shows the main total per item. The activity page's gear rows open `GearScreen`. `EditGearScreen` gains the starting usage fields.
- **Dependencies:** `gear-items`, `activity-gear` (both in flight).
