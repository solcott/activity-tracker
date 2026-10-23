# Proposal

## Why

Athletes use the same gear together: a race kit (bike, helmet, shoes, power meter), a winter MTB setup, a dive kit (regulator, BCD, computer, wetsuit). Garmin calls these gear collections. Picking five items one by one on every activity is slow, and athletes also want to browse their gear by setup rather than only by type. An item belongs in several setups at once, so membership has to be many-to-many.

## What Changes

- **A gear collection record**, `app.activitytracker.gear.collection`: a name, optional notes, and up to 50 of the athlete's items in order. An item can be in any number of collections.
- **Collections are private.** They're stored only in the athlete's `self` space, and the AppView ignores them.
- **Managing collections**: a Collections section on the gear list, a collection page listing its members, and screens to create, rename, reorder, add and remove members, and delete a collection. An item's edit form and gear page show its collections and can add it to or remove it from any of them.
- **Adding a collection to an activity.** The gear picker (record save, manual entry, import, batch import, edit) offers the athlete's collections. Choosing one adds its active members to the activity's gear, skipping items already on it, up to the activity's limit of 16. The activity keeps those items, not the collection. Later edits to the collection don't change past activities.
- **Deleting a collection** removes only the collection. Its items and every activity's gear stay as they are.

Out of scope: applying collections automatically (`gear-sensors` matches items, never collections), sharing collections or showing them on profiles, and totals per collection.

## Capabilities

### New Capabilities
- `gear/gear-collections`: named, private groups of gear items with many-to-many membership, managing them, and adding a collection's items to an activity.

### Modified Capabilities
None. `gear/activity-gear` and `gear/gear-items` are still in flight, so their screens' additions are specified here.

## Impact

- **Lexicons:** new `gear/collection.json` (key `tid`: name, notes, items, createdAt). `space/athlete.json` lists it, and its description says the AppView ignores it.
- **core/lexicon:** `GearCollectionRecord`, `Nsid.GEAR_COLLECTION`.
- **core/data:** a `GearCollections` store in `core/data/.../gear/` that reads and writes collections in `self` and drops members whose items are deleted.
- **Screens:** `GearCollectionScreen` and `EditGearCollectionScreen`. A Collections section on `GearListScreen`. A collections field on `EditGearScreen` and `GearScreen`. "Add collection" in the `GearField` sub-circuit.
- **server/appview:** none, beyond `Indexer.spaceCollections` documenting that it's never indexed.
- **Dependencies:** `gear-items`, `activity-gear` (in flight). Independent of `gear-usage` and `gear-components`.
