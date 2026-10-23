# Proposal

## Why

A bike wears out in parts. The chain, tires, cassette, brake pads, fork and power meter each need replacing or servicing on their own schedule, and they move between bikes. Listing every part on every activity would be tedious, and it would be wrong as soon as a chain is swapped. Athletes need to mount a component on a bike (or on skis, a wheelset, a board) for a period, and have the component count every activity of its parent during that period. That's also how `gear-sensors` will get from a power-meter crank to the bike it's on.

## What Changes

- **Mounts with dates.** A gear item can be mounted on another of the athlete's items from a date, and optionally until a date. It can be mounted on one parent at a time, and keeps a history of past mounts. Mounts can nest (tires on a wheelset on a bike) up to three levels deep, and never in a loop.
- **Components count their parent's activities.** An item's usage also counts each activity that lists an item it was mounted on (directly or through a nested mount) at the activity's start. Each activity counts once, however it was reached. Activities are never rewritten when parts are swapped.
- **Access follows the parent too.** Another viewer's totals for a component count an activity reached through a parent only if they may also see that parent.
- **Mount, unmount and replace** on the gear page. Replace ends the current component's mount today and mounts a new or existing item in its place from today. Retiring a mounted item ends its mount on the retirement date.
- **The gear page lists components**: what's mounted now, with each component's main total, and what was mounted before. A component's page shows its mount history.

Out of scope: listing components on the activity page (it keeps showing the gear the activity lists), bike gearing (chainring and cassette teeth, a later follow-up for the backlog's gear display), and service reminders.

## Capabilities

### New Capabilities
- `gear/gear-components`: mounting an item on another for a period, how mounted items count their parent's activities, and how mounts are managed and shown.

### Modified Capabilities
None. Totals and their viewer rules stay as `gear/gear-usage` defines them. This change adds the activities reached through mounts.

## Impact

- **Lexicons:** `gear/item.json` gains `mounts` (up to 50 `#mount`: `parent` at-uri, `from` datetime, optional `to` datetime).
- **core/lexicon:** the `GearMount` model and validation helpers (no overlap, no loops, depth of three at most).
- **core/data:** `GearRepository` mount, unmount and replace, written to the component's own record. Retiring ends an open mount.
- **server/appview:** a migration for `gear_mounts(component_uri, parent_uri, mounted_from, mounted_to)`, filled from item records. The indexer drops mounts that overlap, loop, go deeper than three levels, or name another athlete's item. `GearViews.countedFor` gains the mounted path. `getGear` lists current and past components.
- **Screens:** `GearScreen` gains a Components section with Mount, Unmount and Replace. A component's page shows its mount history. `EditGearScreen` shows the current parent.
- **Dependencies:** `gear-items`, `activity-gear`, `gear-usage` (all in flight).
