# Design

## Context

See proposal.md for why, and the spec for behavior. Today:

- Non-public profile details live in tier spaces, one `actor.privateDetails` record per tier (`profile/private-details`). `ProfileRepository` writes the new place before deleting the old, and `AthleteSpaces` creates a tier space on first use with the AppView's managing-app id.
- A session can write a space collection only if its `space:` grant lists it (`SessionClients.canWriteToSpaces`). The PDS fills the grant from `space/athlete.json`'s `collections` at sign-in.
- Activities have one canonical URI wherever they're held: the AppView indexes a space-held activity at `at://{did}/app.activitytracker.feed.activity/{rkey}` with an `origin` column (`.agents/rules/appview.md`). Gear follows the same rule, so later records can point at an item by one URI.
- Blobs in the repo are public. Photos of non-public activities are blobs in their tier space, which the AppView reads through `SpaceBlobs` (`api/SpaceMedia.kt`).
- `PrivacyZonesScreen` under Settings is the closest list-of-own-things screen. `ui/adaptive` wrappers and the three-module screen split apply (`compose-screens.md`).

## Goals / Non-Goals

**Goals:**
- One record shape that later gear changes extend with optional fields, never break.
- Gear reads and writes work the same on Android, iOS, desktop and web.

**Non-Goals:**
- No AppView indexing or queries. `activity-gear` adds them when activities first need to show gear to other viewers.
- No offline queue. Gear saves are direct writes, like privacy zones and profile details. A failed save keeps the form and shows the error.
- No usage offsets, mounts or links (later changes add them as optional fields).

## Decisions

### One record per item, in a new `gear` namespace
`app.activitytracker.gear.item`, key `tid`. Later changes add `gear.collection`, `gear.autoAssign` and gear queries under the same namespace, which keeps the area together in the lexicon tree and in `Nsid`.
- *Alternative:* one list record per tier, like `privateDetails`. Rejected. Items are many, they move tiers one at a time, and later records need to point at each one.

### Record shape
- Required: `name` (100 graphemes), `type` (open `knownValues`), `createdAt`.
- Optional: `kind` (open, per type), `brand` and `model` (128 each), `sports` (sport tokens, up to 16, used later to order pickers and suggest defaults), `weight` (grams, integer), `notes` (1,000 graphemes), `photo` (reusing `feed.activity#image`: webp/jpeg, 2 MB, alt, aspect ratio), `acquiredAt` and `retiredAt` (datetimes).
- `GearType` and per-type kind lists live in `core/lexicon`, next to `Sport`. Unknown tokens round-trip unchanged (`lexicons.md` open enums).

### Storage and moves
`GearRepository` (session scope, `core/data/.../gear/`):
- `items()` lists `gear.item` from the repo and the three tier spaces in parallel, with the athlete's own session (no credential exchange). An rkey found in two places is kept at the narrower tier, which is where an interrupted move toward a narrower tier has already written.
- `save(item, visibility)` puts the record at the new place, then deletes it from the old place if that differs. The rkey is the item's id throughout. It creates a tier space through `AthleteSpaces` when needed.
- A photo is uploaded where the item goes: `uploadBlob` for the repo, the space's blob upload for a tier. On a move, the photo is uploaded again to the new place before the record is written there. Blobs are content-addressed, so this reuses the same bytes.
- `delete(item)` deletes the record at every place listing it.
- Before a non-Public write, `clients.canWriteToSpaces(Nsid.GEAR_ITEM)` is checked. If it's false, the save fails with a typed error that the presenter shows as "sign in again".

### Screens
- `GearListScreen` (from Settings, `SettingsEvent.OpenGear`), with its state holding items grouped by type and a retired group. Each row opens `EditGearScreen(rkey: String?)`, where null means a new item.
- `EditGearScreen`'s state keeps its text fields in `TextFieldState`s owned by the presenter, plus a type and kind picker (`AppMenu`), sports (the existing activity type picker, multi-select), a visibility picker, a photo picker (same re-encoding as activity media, which drops metadata), retire/un-retire and delete with a confirmation dialog whose buttons are Retire, Delete and Cancel.
- Labels for types and kinds are string resources keyed by token, with Other as the fallback.

## Risks / Trade-offs

- [A move that fails after the first write leaves two copies] → The list shows the narrower copy, and the next save of that item removes the stray one. Showing the narrower copy never shows an item wider than the athlete last chose.
- [Re-uploading a photo on every move] → Moves are rare, and the blob is at most 2 MB.
- [Older sessions can't save private gear, which is the default] → They get a clear "sign in again" message. It's a dev-stage app, as with `activity-devices`.
- [Without an AppView index, the list costs four `listRecords` calls] → They run in parallel, and an athlete has tens of items, not thousands.

## Migration Plan

Publish the updated lexicons (`docker/scripts/publish-lexicons.sh`) so the PDS resolves the new space collection, then ship clients. There is no data to migrate.
