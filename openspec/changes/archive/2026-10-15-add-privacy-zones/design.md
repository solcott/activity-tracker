# Design

## Context

See proposal.md for why, and `specs/profile/privacy-zones/spec.md` for the behavior. What exists today:

- `ActivityDraft.polylineOf(samples)` simplifies (`TrackMath.simplify`, RDP at 5 m) and encodes (precision 5). `fromRecording` and `fromImport` call it in the presenters' process, so the draft reaches `ActivityRepository` with its polyline already built. `ImportActivityPresenter` also calls it for the owner-only import preview.
- `ActivityRepository.queue` writes `draft.toRecord(...)` at the draft's place and `tracks.put(uri, gpx)` to `self`; `mergeInto` fills gaps (`ActivityMerge.fillGaps`, polyline included) and rewrites in place; `ActivityManager.edit` reads the record from the PDS (`ownActivityRecord`) and rewrites it.
- `ActivityDefaults` is the pattern for a `self`-space setting: `literal:self` record, read with the owner's session, saved with a direct `spaces.putRecord`.
- `ActivityZonesStore.sweepOnce` is the pattern for a once-a-session repair: lists the athlete's records at every place (repo and tiers), queues rewrites through the outbox, best effort, run by `FeedPresenter` apart from the feed load.
- `feed.activityTrack` in `self` holds every activity's GPX (`PdsActivityTrackStore`). `SpaceClient.getBlob` reads a blob from a space; `GpxParser` (`core/activityfile`, already a `core/data` dependency) parses it back to samples.
- Space `putRecord` has no swap (compare-and-set), so two devices writing the same record race.
- "Zones" already means heart-rate and power zones in this codebase (`ActivityZonesStore`, `ZonesPlacement`). Everything new here says "privacy zone" or "route trim" so the two never mix.

## Goals / Non-Goals

**Goals:**
- One place trims polylines, in `core/data`, so no presenter or future write path can publish an untrimmed one by forgetting a call.
- Trimming fails toward hidden: an unknown zone set writes no polyline, never an untrimmed one.
- A re-trim survives the app closing and finishes on whichever device the athlete opens next.

**Non-Goals:**
- Recalling copies of earlier untrimmed public polylines that relays, Jetstream consumers or other AppViews already hold. Re-trimming fixes the athlete's repo, spaces and our AppView only.
- A map for drawing or dragging zones. The settings screen lists zones without a map; maps on every platform are a separate change.
- Showing zones as shaded circles on the owner's map.
- Trimming photos' EXIF positions (photos are already compressed to WebP by the UI, which drops EXIF).

## Decisions

### Lexicons: `actor.privacyZones` and `actor.routeTrim`, both `literal:self` in `self`
`app.activitytracker.actor.privacyZones`:
- `zones`: array, `maxLength` 10, of `#zone` { `latitudeE7`, `longitudeE7` (integers, 1e-7 degrees, required), `radiusMeters` (integer, 200–1600, required), `label` (string, `maxGraphemes` 32, `maxLength` 320) }.
- `hideEndsMeters`: integer, 0–1600; absent or 0 means off.
- `revision`: string, `format: tid`, required. A new TID on every save.

`app.activitytracker.actor.routeTrim`: `revision` (tid, required), `updatedAt`. It says which `privacyZones` revision every activity's polyline has been trimmed by and sent.

The done marker is a separate record because space writes have no swap: if the sweep wrote it into `privacyZones`, a sweep finishing on one device could overwrite a zone another device had just saved, silently dropping a zone. A separate record can only be wrong in the safe direction (a sweep runs again).

Both go in `space/athlete.json`'s `collections`, and its description says the AppView ignores them. They stay out of `Nsid.indexedCollections` and `TIER_COLLECTIONS`.

Alternatives: a device-local done marker (every device would re-download every track once per change); the marker inside `privacyZones` (the race above).

### `PrivacyZones` store in `core/data`, read fresh per publish
A session-scoped class shaped like `ActivityDefaults`, but read fresh for each publish, as `ActivityZonesStore.put` reads the training fresh, since a stale read publishes a route the athlete has since hidden:
- `forPublish(): RouteTrim` returns `Known(settings, revision)` from a fresh read, the last read this session when the read fails, or `Unknown` when nothing was read this session. No record (`NotFound`) is `Known(none)`; a session without `canUseSpaces` is `Known(none)` too. A record that doesn't decode is `Unknown`, so a bad write by another client fails toward hidden.
- `withReadOnce { }` reads once for a batch, like `withTrainingReadOnce`, for `publishAll`.
- `current: StateFlow<PrivacySettings?>` and `refresh()` for the settings screen.
- `save(settings)`: offsets any new zone's center (below), sets a new revision, `putRecord`s directly, updates `current`. Throws when the PDS can't be reached, and refuses a session without `canWriteToSpaces(actor.privacyZones)` with `ScopeMissingError` (shown as Sign in again).
- `add(point, radius)`: `save` with one more zone; refuses an 11th.

Radii read outside 200–1600 m are clamped, and the zones list is cut to 10, so a hand-written record can't make trimming throw.

### Offset within half the radius
A new zone's center moves a uniform random point in the disk of radius `r/2` (distance `r/2 · sqrt(u)`, bearing `2πv`) from the chosen point, before it's converted to E7. The chosen point never reaches a record or the outbox. Half, not the whole radius as the proposal first said: a full-radius offset can put the real place on the circle's edge, where the visible route would reach it. Half keeps the real place at least `r/2` inside. Existing zones keep their stored centers on later saves, so editing a label doesn't move a zone.

### Trimming in `TrackMath`, applied in `ActivityRepository`
`TrackMath.trim(points, circles, endsMeters): List<LocationSample>`:
1. Cumulative distance along the track gives each point's distance from the start and from the end.
2. A point is hidden if it's inside any circle (haversine `distanceMeters` to the center ≤ radius) or within `endsMeters` of either end.
3. Where consecutive points change between hidden and shown, the boundary point is interpolated on that segment (bisection on the segment for circles, linear by distance for the ends) and kept.
4. Shown runs are concatenated. A middle pass through a zone becomes a straight chord between where the track entered and left, which reveals nothing the gap's two ends don't.

`ActivityDraft` stops carrying a built polyline. It keeps `route: List<LocationSample>` (the samples, already in memory for the GPX), and `toRecord` takes the polyline as a parameter. `ActivityRepository` builds it in `queue` and `mergeInto`: `Known` → `polylineOf(trim(route, ...))` (null under two points); `Unknown` → null. `polylineOf(samples)` stays for the import preview, which only the athlete sees.

Alternative: pass zones into `fromRecording`/`fromImport` in the presenters. Every caller would need the store, and a forgotten one publishes an untrimmed route. Doing it where records are written can't be skipped.

Alternative for middle passes: a list of polyline segments. That changes `feed.activity`'s `polyline` and every renderer for a chord the eye barely notices.

### Re-trim sweep: `RouteTrimmer`
A session-scoped class with its own lock, run by `ActivityRepository.repairRoutes()`:
1. Read `privacyZones` and `routeTrim`. If revisions match, stop. If zones are `Unknown`, stop (next session).
2. List the athlete's `feed.activity` records at every place (repo and tier spaces, the listing `ActivityZonesStore` uses) and the `feed.activityTrack` rkeys in `self`.
3. Skip rkeys with a `feed.activity` write already queued in the outbox. For each other activity with a track: `getBlob` the GPX from `self`, `GpxParser` it, trim, encode. If the polyline differs from the record's, queue a put of the record with only `polyline` changed, at the place it was listed, and set it in `feed.rewrites` so the UI shows it.
4. Drain those rows (`outbox.drain { it.collection == FEED_ACTIVITY }`). If none are left pending, write `routeTrim.revision` = the revision used. Otherwise leave the marker, and a later sweep, on any device, skips what already matches.

Tracks are fetched one at a time with a small concurrency limit (4), so an athlete with hundreds of activities doesn't open hundreds of requests.

It runs after each `PrivacyZones.save` (`ActivityRepository.repairRoutesOnSave`, collected for the whole session from `App`, so a sweep isn't cancelled when the athlete leaves the settings screen, and a newer save replaces a running one) and once a session from `FeedPresenter`'s existing `LaunchedEffect`, after `repairZones`.

### Activities published with a different zone set this session
`PrivacyZones` remembers, for the session, the rkeys it trimmed with a revision other than the latest it has read (including `Unknown`). When a later read returns a newer revision, it queues those rkeys for the trimmer to re-trim individually, without waiting for the marker logic. This covers both the offline-before-read scenario and a second device that published while another changed zones. Losing that in-memory list (the app closes first) leaves those activities with no polyline or the older trimming until the next zone change: never wider than the zones they were trimmed with.

### Edits and merges see queued re-trims
`ActivityManager.edit` and `mergeInto` start from the PDS record, which doesn't yet reflect a queued re-trim, so an edit could put back the older polyline. Both take the record from the outbox's last pending `feed.activity` put for that rkey when there is one, as `PdsActivityTrackStore.has` reads the outbox before the PDS. `setVisibility` already drains first.

### Owner's map draws the full track
`ActivityTrackStore` gains `read(activity): List<LocationSample>?` (`getBlob` from `self` + `GpxParser`; null when absent, unreadable or offline). `ActivityDetailPresenter`, for the athlete's own activity, loads it apart from the activity, builds `polylineOf(samples)` and puts it in `State.ownRoute`; the UI draws `ownRoute ?: activity.polyline`. `ActivityMap` keeps taking an encoded polyline, so no platform map changes. The track is held for the screen only, never cached to disk.

### UI
- `PrivacyZonesScreen` (with the `new-screen` skill) under Settings: the zones (label, radius picker in 200 m steps, delete), the hide-start/end picker (Off, 200 m … 1.6 km), and "Add at current location" when `LocationTracker.isSupported` (one fix from `updates()`, asking permission as Record does), with a label `TextFieldState` for the new zone. Text says zones can also be added from an activity. Labels of saved zones aren't editable yet.
- Own activity detail with a track: "Hide this start" and "Hide this end" menu items add a 400 m zone at the track's first or last point (`PrivacyZones.add`), then start the re-trim. Hidden when the track can't be read.
- Settings shows a short line under the entry: how many zones, and hide-start/end if on.

## Risks / Trade-offs

- [Earlier public polylines are already out on the firehose and in other indexers] → Out of our control; the settings screen says re-trimming changes our copies, not others'.
- [A re-trim of hundreds of activities is slow and downloads every track] → Runs in the background with limited concurrency; resumable; skips activities already matching.
- [Two devices save zones at once] → Last write wins on `privacyZones` (as with defaults); both start sweeps; the marker's revision is only ever the one the finishing sweep used, so the losing revision's sweep never marks the winner's done.
- [Encoding at precision 5 (~1 m) can put a boundary vertex just inside a circle] → Negligible against a 200 m minimum; tests check with a 2 m tolerance.
- [`OwnDives.mergeGains` offers `MergeGain.Track` when an activity has no polyline, which a fully hidden route now also has] → The merge still checks `tracks.has`, so only the preview is wrong; acceptable, noted in code.
- [Unknown zones drop the polyline for activities published in a session's first moments offline] → Filled in on the session's first successful read; never widened.

## Migration Plan

Breaking, as the last few changes were: add the lexicons, republish local lexicons (`scripts/dev.sh` runs `docker/scripts/publish-lexicons.sh`), and sign in again so the `space:` grant lists `actor.privacyZones` and `actor.routeTrim`. No Room migration: nothing new is stored on the device beyond outbox rows of existing shapes. Existing activities are untouched until the athlete saves zones. Rollback: revert; records in `self` are ignored by the old code.
