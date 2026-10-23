# Design

## Context

See proposal.md for why, and the delta specs for behavior. Today:

- `DetailCategory` (`core/lexicon`) is the one table that says which `ActivityRecord` field is in which category. The client splitter (`recordFor`, `detailsFor`), both mergers (`effectiveRecord`, the AppView's `activityView.details` merge) and `ActivityLayout` all read it, so a new `SummaryField` gets tier placement, narrowing, moves and per-viewer merging without new plumbing.
- `actor.activityDefaults#details` is an open set of per-category defaults. A category never set falls back to the activity default (`ActivityDefaults`).
- `gear-items` stores items in the repo or a tier space under a canonical URI `at://{did}/app.activitytracker.gear.item/{rkey}`. The AppView doesn't index them yet.
- The AppView indexes space-held records under their canonical URI with an `origin`. Details rows are keyed by `(uri, origin)`, one per tier. `visibleTo(viewer, visibility, author, …)` (`api/TierAccess.kt`) is the tier predicate for any table with a visibility column.

## Goals / Non-Goals

**Goals:**
- Gear placement, narrowing and moves come from the existing category machinery, with no gear-specific storage path.
- Every query that later reads an activity's gear (`gear-usage`) has one table to join, already filtered by the tier each reference is held at.

**Non-Goals:**
- No gear on feed cards or in the timeline. Only the activity page shows it.
- No suggestion from past activities ("you usually ride the Tarmac on Tuesdays").

## Decisions

### Gear is a summary field in a new category
`gear: [at-uri]` (max 16) on `feed.activity` and `feed.activityDetails`, and `DetailCategory.GEAR = "gear"` with one `SummaryField`. A Gear category narrower than the activity moves the field to that tier's details record, exactly as heart rate does.
- *Alternative:* a separate `feed.activityGear` record per activity, like `feed.activityDevices`. Rejected. It would need its own tier placement, its own move logic when the category changes, its own index table keyed by origin, and its own outbox step, all of which the category table already does. The cost is that changing an activity's gear rewrites its record or details record, which edits already do.

### Gear's default is Only me
`ActivityDefaults` returns Only me for `gear` when the record doesn't set it, before capping to the activity default. This is the one exception in the fallback, kept beside the table as `DetailCategory.defaultWhenUnset`, so pickers and the edit screen don't special-case it.

### Defaults per activity type in `gear.autoAssign`
`app.activitytracker.gear.autoAssign`, key `literal:self`, stored only in `self`: `sports: [{ sport, subSport?, gear: [at-uri] }]`. `gear-sensors` later adds sensor links, activity-profile links and ignored sensors to the same record, keeping every private auto-assign rule in one place.
- Resolution (`GearDefaults.forType(type, items)`): take the entries for the sport and for the sport plus sub-sport, drop retired and deleted items, and for each gear type keep the sub-sport entry's items if it has any of that type, else the sport entry's.
- `EditGearScreen` gains "Use by default for", the activity type picker in multi-select mode, writing the record on save.

### Picker state
- `ActivityDraft.gear: List<AtUri>`, filled at save from the form's `GearChoice`. The touched flag lives in the holder, not the draft, because drafts are built only at save: until the athlete picks or removes an item, `GearChoice.selected` is the defaults for the form's current type, so a type change re-resolves them; any pick fixes the list.
- One shared `GearField` is used by record save, manual entry, import, batch import and edit. It's a sub-state with its own sink, produced by an `@Inject` `GearChoices.produce(type)` (like `ActivityTypeField`/`ActivityTypeChoices`), not a SubCircuit: `compose-screens.md` keeps SubCircuits for children that need nothing pushed from the host, and the host pushes the activity type. It sorts by `sports` match and then by name, and shows retired items only if they're already selected. Edit seeds it from the activity's gear (`GearChoice.seed`). A batch import has one picker for every selected dive, starting at the defaults for the first dive's type.
- Gear counts as data for the visibility pickers once picked: manual entry and import offer what the draft holds (`heldCategories`, which sees `gear`), a recording offers Gear beside its fixed set, and edit's `EditVisibility` offers it while the form lists gear.
- Edit saves a gear change through `ActivityManager.setVisibility(activity, choice, gear)`, which rewrites every place the activity is stored (`ActivityPlaces.save` with the new whole record), so gear at a narrower tier never passes through the activity record. `ActivityManager.edit` alone writes only the activity-tier record.

### AppView
- Migration (V35): `gear_items(uri, origin, cid, author_did, visibility, item_name, item_type, item_kind, brand, model, photo_cid, photo_mime, retired_at, record, indexed_at)`, keyed by `(uri)`, with an origin and shadows handled like activities (`Indexer.place`, `heldCopy`). `activity_gear(activity_uri, origin, listed_in, gear_uri, sort_index, author_did, visibility)`, keyed by `(activity_uri, origin, listed_in, gear_uri)`: `listed_in` (`record` or `details`) tells the activity record's references, which follow its one indexed copy, from a details record's at the same origin. Column names avoid words Exposed quotes (`name`, `type`, `source`, `position`), which H2 then matches case-sensitively.
- `gear.item` joins `Nsid.indexedCollections` (repo, through Jetstream) and every tier's `spaceCollections`. The indexer validates name and type lengths and drops what breaks them.
- When an activity or details row is placed, its `gear` refs are replaced in `activity_gear` for that origin, keeping only URIs under the author's DID and `gear.item`. Deletes and `dropSpace` remove them by origin.
- `Views.hydrate` reads `activity_gear` rows the viewer's `visibleTo` admits, joined to `gear_items` rows the viewer's `visibleTo` admits and that are `served`. It builds `activityView.gear` from the narrowest admitted record's list (the one clients' merge lets win), in position order, and filters `record.gear` and each `details` entry's `gear` down to those URIs (`withGearOnly`).
- The new `gear.defs#gearView` holds uri, name, type, kind, brand, model and a photo URL. The `/img/media` route serves gear photos too (`gearPhotoRows`): a public item's redirects to the PDS, a non-public one's is signed for the viewer, re-checked against the item's tier and fetched from its space (`SpaceBlobs`).
- A Gear category that defaults to Only me would make every activity look split. The details-grant checks (`requireDetailsSpaceFor`, `setVisibility`) count only categories the activity has summary data in, so an activity with no gear needs no details grant.

### Combining
`ActivityCombiner.combine` sets the combined record's `gear` to the members' gear in member order, deduplicated and capped at 16. The Gear category starts at the narrowest Gear tier among the members.

## Risks / Trade-offs

- [Editing gear on many past activities rewrites many records] → There's no bulk edit in this change. `gear-sensors`' "apply to past imports" goes through the outbox, one activity at a time.
- [A viewer learns that an activity has gear they can't see from a missing field] → No: unseen URIs are removed from the served record, and a field with nothing left is dropped.
- [Item visibility and Gear category visibility can disagree (a Public category with an Only me item)] → The narrower one wins for each item. The picker labels each item with its visibility, so the athlete can see what others will see.
- [Older sessions lack the `gear.autoAssign` grant] → Defaults fail to save with the same "sign in again" message as private gear. Choosing gear on an activity still works, since it travels on activity and details records the session can already write.

## Migration Plan

Publish lexicons, deploy the AppView (the migration creates empty tables, and Jetstream and space sync fill them as records arrive), then ship clients. Existing activities have no `gear` and need nothing.
