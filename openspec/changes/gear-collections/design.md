# Design

## Context

See proposal.md for why, and the spec for behavior. After `activity-gear`:

- `GearRepository.items()` lists the athlete's items, and `GearField` is the one gear picker sub-circuit that every save and edit screen uses.
- An activity lists up to 16 item URIs in its Gear category.
- `self`-only records such as `actor.privacyZones` are written directly with the session (`clients.spaces.putRecord`).

## Goals / Non-Goals

**Goals:**
- Membership is edited from either side (the collection or the item) and stored in one place.
- Adding a collection reuses the picker's selection state, so the rest of the save path is unchanged.

**Non-Goals:**
- No visibility per collection. Collections are always private until sharing is designed with gear on profiles.
- No nested collections.

## Decisions

### Membership lives on the collection
`gear.collection.items: [at-uri]` (max 50), with one record per collection (`tid`) in `self`. "An item's collections" is computed on the client by scanning the athlete's collections, which number in the tens.
- *Alternative:* a `collections` list on each item. Rejected. Items can be Public, so membership would leak through public records, and reordering a collection would mean rewriting many items.

### Store
`GearCollections` (session scope): `all()` lists `gear.collection` in `self`. `save(collection)` and `delete(rkey)` follow the session's `canWriteToSpaces(Nsid.GEAR_COLLECTION)` check, with the same sign-in-again error as private gear. `GearRepository.delete(item)` then removes the item's URI from every collection that lists it. That's a best-effort pass after the item delete. `all()` also hides members whose items no longer exist, so a failed pass never shows a deleted item.

### Picker
`GearField` gains `collections` in its state and `CollectionAdded(rkey)` in its events. The presenter appends the members that pass the filters to the selection, sets `gearTouched`, and reports how many the limit dropped with a one-shot message. The selection stays a list of item URIs, so saving is unchanged.

### Screens
- `GearListScreen` gains a Collections section above the types, with a "New collection" action.
- `GearCollectionScreen(rkey)` lists members (tap opens `GearScreen` once `gear-usage` exists, else `EditGearScreen`), with Edit and Delete. `EditGearCollectionScreen(rkey?)` has the name and notes in `TextFieldState`, and a reorderable member list with "Add items" (a multi-select of the athlete's items).
- `EditGearScreen` and `GearScreen` gain a "Collections" chips field with an add menu.

## Risks / Trade-offs

- [Deleting an item and then failing to update its collections leaves a dangling URI] → It's hidden on read and dropped the next time that collection is saved.
- [Collections can't be shared] → Deliberate for now. Sharing needs a visibility story for the members, which belongs with gear on profiles.

## Migration Plan

Publish lexicons, then ship clients. Nothing to migrate, and the AppView doesn't change.
