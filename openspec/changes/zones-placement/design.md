# Design

## Context

Zone snapshots are made only on import (`importDraft` → `ZoneSnapshots`), carried on `ActivityDraft`, and written inline in the streams record by `PdsActivityStreamsStore.put`. The AppView copies them into `activity_streams.heart_rate_zones`/`power_zones` (`StreamsIndex`), and `ActivityStreamsViews` returns them when `isSelf || publicDetails(actor)?.training != null`.

Training's visibility is the tier of the `actor.privateDetails` record holding it, or Public in `actor.profile`. The client knows it (`DetailsVisibility.training`), and `ProfileRepository.save` moves details by writing the new home before trimming the old one. The AppView never reads `privateDetails`.

Two copies of one record mid-move are already handled on the AppView: `ActivityIndex` keeps the narrower copy as the row and parks the wider one in `RecordShadows`, promoting it back with `takeShadow` when the narrower one is deleted. Spaces have no batch write, only `putRecord` and `deleteRecord`.

## Goals / Non-Goals

**Goals:**
- Every change that could widen zones relative to training fails toward narrower.
- The narrowing backfill survives app restarts and rate limits without saving its own progress.

**Non-Goals:**
- Zone snapshots for recorded (non-imported) activities.
- Re-widening old zones when training is widened.
- Recalling zones that firehose consumers copied while they were public. As with activities, those copies are gone.
- Changing `feed.getActivityStreams`' output shape.

## Decisions

### A record of its own, not fields on streams
Zones have a visibility that can be narrower than the charts' (training's), so they need a place of their own. `feed.activityZones` shares the activity's rkey, so a move, a delete or a lookup needs no index. The `zoneSnapshot` def moves to `activityZones.json`, and `getActivityStreams` refers to it there.
*Alternative:* the AppView learns which tier holds training and gates inline zones by it. That's less code and retroactive, but it would have the AppView read `privateDetails` and make zones the one record whose visibility isn't its placement. Rejected in exploration.

### Target tier is `narrower(charts, training)`
Zones are useless without the samples, so the charts' visibility, not the activity's, bounds them (charts are already capped at the activity's). An athlete with no training details gets no snapshots, so there's nothing to place. One helper computes the target for every caller (publish, charts move, backfill, sweep).

### Narrowing training: zones first, through the outbox, then the profile
```
saveTraining(narrower T)                  (under the zones mutex)
  1. outbox.drain()               -- queued publishes land before the scan
  2. scan places wider than T     -- owner listRecords of feed.activityZones in the repo / each wider tier
  3. queue per record: put(at narrower(place, T), body) then delete(at place)
                                    skipping rkeys that already have queued zones rows
  4. outbox.drain()               -- throws on failure: the save stops here
  5. ProfileRepository.save(...)  -- training moves to T
```
Every partial state leaves zones narrower than training, which only hides them. A retry recomputes the work from what's stored, so no progress is saved. The outbox gives retry, backoff and restart survival. If step 4 fails, the save reports the error, the queued moves finish on a later drain, and the athlete retries the save. Zones records carry no blob, so a move row is a plain put followed by a plain delete, and the outbox's in-order, stop-at-first-failure processing keeps the put ahead of the delete.
*Alternatives:* (a) direct calls like other profile saves: simpler, but a long run lost to a 429 or an app kill has to start over with the athlete waiting; (b) training first: any partial failure would leave old FTP-derived zones visible to audiences the training was just hidden from.

### Fresh training read at publish, plus a session-start sweep
`publish` reads the training visibility at enqueue time (Only me if it can't be read) instead of using a session-cached value. That shortens, but can't close, the window in which another device publishes at the old tier during a backfill. The sweep closes it and cleans up after any interrupted move. Once per session (`ActivityRepository.sync`, which the feed calls on first load), with the `space:` scope, it lists the athlete's own `feed.activityZones` and `feed.activityStreams` in every place, computes each activity's target, and queues a move for any zones wider than it. It also queues deletes for zones copies that aren't at their target when a copy exists there. The sweep, the backfill and charts moves share one session mutex, so two of them can't move the same record at once.
*Alternative:* no sweep, and accept the multi-device race as the backlog already does for delete-then-write. Rejected, because here the race would expose thresholds rather than show a narrower copy.

### Charts moves drag zones
`setStreamsVisibility` and `setVisibility` (wherever they move streams) also move zones to `narrower(newCharts, training)`. That can widen zones when the charts widen, but never past training. Like streams moves these are direct `moveOwn` calls, run under the zones mutex. `delete` removes zones from every place, as for streams.

### AppView: index like streams, reuse shadows
`feed.activityZones` joins `Nsid.indexedCollections` and the tier-space set in `Indexer.spaceCollections`. It is indexed through the same `place()`/`RecordShadows` path as activities and streams, into an `activity_zones` table keyed by URI with `activity_uri`, `author_did`, `visibility` (from placement), the two snapshots as bounded JSON (the `boundedZones` limits move with it) and `record`. `getActivityStreams` joins it on `activity_uri` and keeps the zones only when `visibleTo(viewer, zones.visibility, author)` passes. Streams visibility and activity visibility are already checked by then. `seesTraining` and the `publicDetails` lookup go.

### Migration: new table, drop columns
`V23` creates `activity_zones` and drops the two zone columns from `activity_streams`, in H2-portable SQL. No backfill: inline snapshots are discarded (see proposal).

## Risks / Trade-offs

- [A backfill over hundreds of activities is slow and may hit PDS rate limits] → It runs through the outbox, which backs off and resumes. The save waits only for the drain, and reports failure if the drain stops.
- [The athlete abandons a narrowing after step 3] → Zones stay narrower than training. That's harmless, and the sweep leaves them, since it only moves zones that are too wide.
- [Another device publishes at the old training tier mid-backfill] → The fresh read narrows the window; that device's next session-start sweep moves it. Until then those zones are visible to the old audience, which saw that training a moment ago.
- [The sweep costs listRecords pages at every session start] → Two collections across at most four places, owner reads, paged. Acceptable at current sizes. It can later run only after a training change or once a day if needed.
- [Clients built before this change write inline zones and no zones record] → Only local builds exist. The AppView ignores the inline fields.

## Migration Plan

Lexicon, AppView and client ship together. Local stacks lose existing zone snapshots; re-importing an activity restores them. Rolling back needs the dropped columns restored by a new migration, and data in them is not recoverable.
