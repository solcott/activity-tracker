# Design

## Context

See proposal.md for why, and the spec for behavior. After the earlier gear changes:

- `ImportedActivity.devices` (`ImportedDevices`: the recorder first, then each sensor, plus `activityProfile`) is available at import. `ImportedDevice` has kind, maker, model, serial, ANT number and transmission type, and connection. Each publish writes it as `feed.activityDevices` in `self`. `ActivityDevicesStore` only has `put`, `has` and `delete`.
- On the real Edge 840 ride every sensor had a serial and none had ANT IDs (`activity-devices` design), so serials are the key to rely on.
- `gear.autoAssign` (`self`, `literal:self`) holds the per-type defaults, and `GearDefaults.forType` resolves them per gear type.
- Items carry `mounts`, and `GearMounts` can answer "what was this mounted on at instant t" on the client.
- `GearField` holds the picked gear and a `gearTouched` flag. `GearCollections` stores collections.

## Goals / Non-Goals

**Goals:**
- Matching runs entirely on the client, at import, from data already on the device. No new AppView work, and serials never leave `self`.
- One resolver produces the starting gear for single imports, batches and the past-imports pass.

**Non-Goals:**
- No learning from history ("this strap has been on the Tarmac 90% of the time"). Links are explicit.
- No hashing of serials. As with `activity-devices`, `self` already holds the originals in the clear.

## Decisions

### Keys
`SensorKey(manufacturer, serialNumber?, antDeviceNumber?, antTransmissionType?, model?)` with `strength` = serial, ant or model, built by `SensorKey.of(device)` from the strongest identifiers present. Two keys match only at the same strength with equal fields, after manufacturer and model are compared trimmed and case-folded and serials as written. A device with nothing but a kind gets no key, and is neither offered nor matched.

### Record
`gear.autoAssign` gains:
- `sensors: [{ gear, key: #sensorKey }]` (max 200),
- `profiles: [{ name, gear }]` (max 50),
- `ignored: [#sensorKey]` (max 200).

The writer keeps keys unique across `sensors` and `ignored`: linking removes an ignore, and ignoring removes a link. One record means one read per import and one write per prompt round.

### Resolution
`GearAutoAssign.resolve(type, startedAt, devices, profile, items, autoAssign)`:
1. For each device, find its link's item, skipping retired and deleted items, and lift it to the top-level item it was mounted on at `startedAt` (`GearMounts.rootAt`). Group the results by gear type.
2. Add the profile link's item (lifted the same way) for gear types still empty.
3. Add `GearDefaults.forType(type)` items for gear types still empty.
4. Return the list in that order, deduplicated and capped at 16, plus the conflicts (gear types with two or more step-1 items).

The import presenter seeds `GearField` with the result (with `gearTouched` false, so a type change re-resolves). Batch import resolves per activity.

### Prompt flow
- `ImportActivityState.newDevices`: one card per unlinked, unignored key across the import or batch, in file order, recorder first. Each card's form is a small sub-circuit with name (`TextFieldState`, prefilled with "{maker} {model}"), type and kind (suggested by `SensorKind.suggestedGear`: power to component/power meter, shifting to component/derailleur, heart rate, speed, cadence, speed/cadence and foot pod to sensor, radar and light to device, trainer to trainer, the recorder to device), mount on (the athlete's items, default the bike picked for this activity if there is one), collections, and visibility (Only me).
- On save, in this order: create the items, write their mounts and collection memberships, write `gear.autoAssign` once with the new links and ignores, re-resolve the gear for each activity of the batch the athlete hasn't touched, then publish. A failure before publishing keeps the preview with an error. A failure after leaves the import published and says that the gear links weren't saved.
- These cards and the profile offer are hidden when the session can't write `gear.autoAssign` (an older session), since their writes would fail.

### Past imports
- `ActivityDevicesStore.list()` pages `feed.activityDevices` in `self`.
- After a link is saved, `PastImportsMatcher` counts the activities whose devices contain the key and whose effective gear (read from the athlete's own records) has no item of the target's gear type, using each activity's start for the mount lift. It offers the count. On accept it edits each activity through `ActivityRepository`'s edit path (the outbox), one at a time, so an interrupted pass resumes safely: an activity that already has the gear type is skipped on retry.

### Screens
- `GearScreen` (owner only): Linked sensors (maker, model, `…3426`, a "may match other units" note for weak keys) and Linked profiles, each with Unlink.
- `IgnoredSensorsScreen` from the gear list's overflow menu, with Un-ignore.

## Risks / Trade-offs

- [Weak model-only keys match every strap of that model, such as a partner's on a shared computer] → They're marked in the prompt and on the gear page. The athlete can ignore or unlink them.
- [The past-imports pass lists every devices record, which can be slow for a long history] → It runs only on the athlete's request, pages in the background, and shows progress. Each record is small.
- [Mounts that weren't updated give wrong bikes] → Conflicts are flagged, and the gear stays editable in the preview.
- [The prompt makes the import screen busier] → Cards are collapsible, and "Later" is the default when skipped.

## Migration Plan

Publish the updated `gear.autoAssign` lexicon, then ship clients. Existing `gear.autoAssign` records have no links and keep working. No AppView change.
