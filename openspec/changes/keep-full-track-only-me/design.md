# Design

## Context

See proposal.md for why. Today the GPX goes onto the `feed.activity` put as `PendingBlob(gpx, Gpx.MIME_TYPE, field = "track")` in two places, both in `ActivityRepository`: `queue` (publish and batch import) and `mergeInto` (only when `current.track == null`). `ActivityManager.edit` and `queueUngroup` rewrite the record without a blob so it keeps the track. `OwnDives.mergeGains` offers `MergeGain.Track` when `existing.track == null`, and `LocalRewrites` treats `track` specially, since only the AppView's copy has the uploaded ref.

What already exists and is reused:
- `OutboxWriter.put` with a `PendingBlob` and a `space`: `OutboxProcessor` ensures the space, uploads the blob with `uploadBlob` and splices its ref into the record, for space writes as for repo ones. No outbox change is needed.
- `PdsActivityStreamsStore`: a record of its own under the activity's rkey, carrying a blob, with `put`/`move`/`delete`. The track store follows its shape without `move`.
- `Places.kt`: `readOwn` reads the athlete's own record from a space with the session.
- The AppView's `Indexer` indexes only `TIER_COLLECTIONS` from a tier space (`spaceCollections`), and only `Nsid.indexedCollections` from Jetstream, so a new collection is ignored unless added there. The AppView has no `track` handling today.

## Goals / Non-Goals

**Goals:**
- One place for the full track, independent of visibility, that only the athlete reads.
- The track follows the activity's lifecycle through the outbox, like streams.

**Non-Goals:**
- Reading or drawing the full track anywhere in the UI (privacy zones adds the owner's detail map; export adds the file).
- Any compatibility with records or sessions from before this change. Local data is wiped and athletes sign in again, so no code reads an old `track` field and no path handles a grant that lacks `feed.activityTrack`.
- Trimming the polyline (privacy zones).

## Decisions

### A separate `feed.activityTrack` record in `self`
Shape: `activity` (at-uri, shares the rkey), `track` (blob, `application/gpx+xml`, max 10 MB, as today), `createdAt`. Key `tid`, like `feed.activityStreams`.

Alternatives: keep the blob on `feed.activity` and rely on the activity's place. That still exposes it for Public, Followers and Mutuals activities, and the AppView syncs every tier. Placing the track at the activity's visibility like streams would need moves and still leaks it to the audience. A fixed `self` place needs neither: visibility changes never touch it.

### `ActivityTrackStore` in `core/data`
A session-scoped class next to `ActivityStreamsStore`:
- `put(activity, gpx)` queues `writer.put(Nsid.FEED_ACTIVITY_TRACK, rkey, ActivityTrackRecord.serializer(), record, PendingBlob(gpx, Gpx.MIME_TYPE, field = "track"), space = self)`. Callers queue the activity first, as with streams.
- `delete(activity)` queues a delete of the rkey in `self` only. It has never been anywhere else.
- `has(activity)`: true when `readOwn(FEED_ACTIVITY_TRACK, rkey, self)` finds it or the outbox holds a pending put of it.

`self` is `clients.tiers.uri(AthleteSpace.SELF)`. The store is an interface plus a PDS implementation, as streams are, so repository tests can fake it.

Alternative: fold it into `PdsActivityStreamsStore`. Their places and lifecycles differ (streams move with visibility; the track never does), so separate is simpler.

### Writes
- `ActivityRepository.queue`: the activity record is written with no blob; when `draft.gpx` is set, `tracks.put(uri, gpx)` follows it.
- `ActivityRepository.mergeInto`: queue the draft's track when `draft.gpx != null && !tracks.has(existing.uri)`. Unlike streams, the place is fixed, so this works for an activity still in the outbox too (the pending-put check covers it). The record is written without a blob.
- `ActivityManager.queueDelete`: also `tracks.delete(uri)`. Delete with recordings goes through `queueDelete` for each, so their tracks go too; ungroup doesn't touch tracks.
- `ActivityManager.edit`, `queueUngroup`, `setVisibility`: unchanged except removing the "keeps the track" comment.
- Publishing, batch publishing and merging a draft with GPX first check `requireSpacesFor(SELF, feed.activityTrack)` (`requireTrackSpace`). Every session after the reset passes it; the check only stops a session that couldn't write the track from queueing a row the PDS would refuse at the head of the outbox, as likes do. `delete` skips the track for such a session, which never wrote one.

### `MergeGain.Track` preview uses the polyline
`OwnDives.mergeGains` runs over every matched duplicate in a logbook, so it shouldn't read `self` per candidate. It offers `MergeGain.Track` when the logged activity has no `polyline` and the dive has samples. A polyline exists exactly when a track of two or more points was written, so this matches today's behavior. The merge itself checks `self` (`has`), so a wrong preview never overwrites a track.

Alternative: add `hasTrack` to `OwnActivity`, loaded from `self`. More reads for the same answer today.

### `LocalRewrites`
With no `track` on the record, `applyTo` compares records directly and `rewritten` uses the record as written. The blob-ref carve-out goes away.

### AppView
Nothing indexes the collection: `Nsid.FEED_ACTIVITY_TRACK` stays out of `indexedCollections` and `TIER_COLLECTIONS`. A test drives a `self`-space `feed.activityTrack` put through the `Indexer` and checks nothing is stored. Fixtures that build `feed.activity` JSON with `track` drop it. The space declaration's description says the AppView ignores `feed.activityTrack`.

## Risks / Trade-offs

- [The track lands in `self` but its activity's write fails] → Outbox rows go in order and stop at the first failure, so the track is never sent ahead of its activity; a later retry sends both.
- [Activity deleted while its track's put is still queued] → The delete is queued after the put, so the put lands then the delete removes it.
- [Privacy zones may trim a polyline to nothing while a track exists, making the `MergeGain.Track` preview wrong] → The merge still checks `self`. `add-privacy-zones` revisits the preview if that matters.
- [Track blob MIME type: the local PDS stores GPX as `application/xml`] → Unchanged by this change; the BACKLOG item still applies to the new record.

## Migration Plan

Breaking, no migration. Republish local lexicons (`docker/scripts/publish-lexicons.sh`, run by `scripts/dev.sh`), wipe the local stack's data and the apps' databases, and sign in again so the `space:` grant lists `feed.activityTrack`. Rollback is reverting the change and wiping again.
