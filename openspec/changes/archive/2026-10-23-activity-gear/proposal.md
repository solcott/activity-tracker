# Proposal

## Why

A gear list (`gear-items`) is only useful once activities say which gear they used. Athletes need to pick the bike, shoes or skis for an activity when they record, enter, import or edit it, and their usual gear should be filled in for them per activity type, the way Strava and Garmin default a bike or shoes. Which gear an athlete uses can be personal (an expensive bike, a commute), so it needs its own visibility, which defaults to Only me.

## What Changes

- **An activity lists its gear.** A new `gear` summary field holds up to 16 of the athlete's gear item URIs. Activities with no gear don't have it.
- **A new Gear detail category.** The `gear` field belongs to a new category, Gear, with its own visibility like Heart rate or Map, never wider than the activity. Unlike the other categories, a Gear default never set starts at **Only me**, not at the activity's visibility. A Gear choice narrower than the activity puts the field on that tier's `feed.activityDetails` record, as for every category.
- **A gear picker** on recording save, manual entry, import (single and batch) and edit. It lists the athlete's active items, those for the activity's sport first, and keeps any retired item already on the activity. The Gear visibility picker is offered whenever the activity has gear.
- **Defaults per activity type.** An athlete can mark an item "use by default" for activity types (for example ride, or ride/mountain). A new activity of that type starts with those items. For each gear type, a sub-sport default beats a sport default. Defaults are stored in a new private `gear.autoAssign` record in the athlete's `self` space, which `gear-sensors` extends later.
- **The activity page shows its gear** in its device section, to viewers who may see both the Gear category and each item.
- **The AppView indexes gear.** It indexes `gear.item` from repos (Jetstream) and tier spaces, and each activity's `gear` references from records and details. It serves an activity's gear as views of the items the viewer may see, and removes references to any other item from the record it serves.
- **Combining keeps gear.** A combined activity starts with the gear of its members, in member order, without duplicates.

Out of scope: usage totals (`gear-usage`), components (`gear-components`), collections (`gear-collections`) and sensor matching (`gear-sensors`).

## Capabilities

### New Capabilities
- `gear/activity-gear`: which gear an activity used, how the athlete picks it, defaults per activity type, and who sees an activity's gear.

### Modified Capabilities
- `feed/detail-visibility`: adds the Gear category, whose default never set is Only me, and offers it whenever an activity has gear.

## Impact

- **Lexicons:** `feed.activity` and `feed.activityDetails` gain `gear` (an array of at-uris). `actor.activityDefaults#details` gains `gear`. New `gear/autoAssign.json` (key `literal:self`, `sports` defaults) and `gear/defs.json#gearView`. `feed.defs#activityView` gains `gear` (an array of `gearView`). `space/athlete.json` lists `gear.autoAssign`.
- **core/lexicon:** `DetailCategory.GEAR` with a `SummaryField` for `gear`, `Nsid.GEAR_AUTO_ASSIGN`, and `gear.item` added to `Nsid.indexedCollections`.
- **core/data:** `ActivityDraft.gear`, `ActivityDefaults` reading the Gear default, a `GearDefaults` reader/writer for `gear.autoAssign`, and `ActivityCombiner` merging members' gear.
- **Screens:** a gear picker field in `RecordScreen` save, `ManualActivityScreen`, `ImportActivityScreen` and `EditActivityScreen`. "Use by default for" on `EditGearScreen`. A gear row in the activity page's `DetailSection.Device`.
- **server/appview:** a Flyway migration for `gear_items` and `activity_gear`. `Indexer` adds `gear.item` to repo and tier collections, and collects `gear` refs from activity and details rows. `Views` hydrates `activityView.gear`.
- **Dependencies:** `gear-items` (in flight).
