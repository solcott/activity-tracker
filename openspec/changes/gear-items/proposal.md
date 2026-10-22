# Proposal

## Why

Athletes want to know how far their shoes have run, how many hours their fork has done, and how many days their skis have had. Every later gear feature needs a list of gear to work from: assigning it to activities, usage totals, components, collections and matching sensors. Today the app has none. The private `feed.activityDevices` records already wait for gear to match against (`feed/activity-devices`), and the backlog's real gearing teeth need a bike to belong to. This change adds the gear list only. Later changes use it.

## What Changes

- **A gear item record**, `app.activitytracker.gear.item`, one per item (TID key): name, type, an optional kind within the type, brand, model, the sports it's for, weight, notes, a photo, when it was acquired, and when it was retired.
- **Gear types for every sport the app tracks**, as an open set: bike, component, shoes, ski, snowboard, ski boots, snowboard boots, cross-country skis, skates, board (surf, SUP, kite, wake), boat (kayak, canoe, rowing shell), paddle, wetsuit, dive gear, helmet, trainer, wheelchair, stroller, sensor, device and other. Each type offers its own kinds, for example road, gravel and mountain bikes, or chain, tire and power meter components.
- **A visibility per item, defaulting to Only me.** A Public item is stored in the athlete's repo. A Followers, Mutuals or Only me item is stored in that tier's athlete space, like profile details. Changing an item's visibility moves it without ever losing it, and keeps its rkey, so later references to it by its canonical URI stay valid.
- **A "Gear" screen under Settings**: the athlete's items grouped by type, active ones first, then a Retired section. Screens to add, edit, retire, un-retire and delete an item. Deleting asks for confirmation and suggests retiring instead.
- **The `space.athlete` declaration lists `gear.item`.** Sessions signed in before this change can't write gear to their spaces. They can still save Public gear, and saving non-Public gear asks them to sign in again.

Out of scope: assigning gear to activities, usage totals, mounting components, collections, sensor matching (each a later change), showing gear on profiles, and service reminders.

## Capabilities

### New Capabilities
- `gear/gear-items`: an athlete's gear list: what an item records, the gear types, where each item is stored by its visibility, and how athletes add, edit, retire and delete items.

### Modified Capabilities
None.

## Impact

- **Lexicons:** new `lexicons/app/activitytracker/gear/item.json`, mirrored in `core/lexicon` (`GearItemRecord`, `GearType` and kind tokens, `Nsid.GEAR_ITEM`). `space/athlete.json` gains the collection, and its description says the AppView doesn't index it yet.
- **core/data:** a new `gear/GearRepository` (session-scoped) that reads the athlete's items from their repo and three tier spaces, and writes, moves and deletes them, following `ProfileRepository`'s write-before-delete order and `AthleteSpaces` for creating spaces.
- **Screens:** `GearListScreen` and `EditGearScreen` across `:app-screens`, `:app-presenters` and `composeApp`. Settings gains a "Gear" entry next to Privacy zones.
- **server/appview:** none. `Indexer` doesn't index `gear.item` until `activity-gear`.
- **Sign-in scope:** the PDS adds the new collection to a session's space grant at sign-in, so older sessions need to sign in again before they can save non-Public gear.
- **Dependencies:** none. This is the first of six gear changes (`activity-gear`, `gear-usage`, `gear-components`, `gear-collections`, `gear-sensors`).
