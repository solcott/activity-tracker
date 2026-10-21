# Design

## Context

See proposal.md (Why) and `specs/segments/segment-track/spec.md` for the behavior.

What exists today:
- **Full track.** Each GPS activity's full track is a `feed.activityTrack` GPX blob in `self`, written through the outbox by `PdsActivityTrackStore` (`core/data/.../activity/ActivityTrackStore.kt`). It is put from `ActivityRepository` (publish) and `ActivityManager` (combine), and deleted from `ActivityManager`.
- **Polylines.**
  - Trimmed with `TrackMath.trim` (`core/location/.../RouteTrim.kt`) through `PrivacyZones.forPublish()`.
  - `RouteTrimmer` re-trims every activity that has a track once the zones' `revision` differs from the `actor.routeTrim` marker. It is resumable, and runs on session start and on every zone save (`ActivityRepository.repairRoutes`, `repairRoutesOnSave`).
- **AppView.**
  - Joins `self` as a member (`SpaceAccess` admits its DID for all four tiers).
  - Indexes only `TIER_COLLECTIONS` from any tier (`Indexer.spaceCollections`, `server/appview/.../index/Indexer.kt:326-347`).
  - The latest migration on `main` is `V26__blocks_and_mutes.sql`.

Two constraints shape the design:
- **`TrackMath.trim` is wrong for matching as it stands.** It joins a middle pass through a zone with a straight line, which is fine for a drawn polyline. A matcher would treat that line as a real traversal and could even time a "segment" across someone's house.
- **The AppView must be able to tell consent from a stray record.** It must never rely on the presence of segment tracks alone to decide whether it may use them.

## Goals / Non-Goals

**Goals:**
- Give the AppView matchable geometry: full resolution, timestamps, privacy zones removed. Done in a way that resumes after interruption, as the polyline machinery does.
- Make consent explicit, revocable, and enforced on both sides: the client stops writing and deletes; the AppView drops its data and refuses to use stray tracks.

**Non-Goals:**
- Matching, segments, efforts or any new query. That is `segments-core`. The AppView here only indexes the records (URI, CID, activity) and the setting. It doesn't fetch segment-track blobs yet.
- The leaderboard opt-out and per-activity exclusion. Those come in `segment-leaderboards`, as optional fields added to `actor.segmentPrefs` and `feed.activity`.
- Changing `feed.activityTrack`, which stays for the athlete alone.

## Decisions

### 1. A separate trimmed copy, not AppView access to `feed.activityTrack`
The AppView already could read `self`, so indexing `feed.activityTrack` for consenting athletes would cost no new records. It is rejected because that track holds the inside of every privacy zone. A separate copy keeps the promise in `profile/privacy-zones` that nobody but the athlete sees inside a zone, the AppView included. The cost is roughly double the track storage on the PDS, plus a second re-trim target.

### 2. The record shape: JSON data blob, E7 integers, offsets, pieces
`feed.segmentTrack` has `activity` (at-uri), `zonesRevision` (tid, optional), `data` (blob, `application/json`) and `createdAt`. The blob's JSON is:

```json
{ "pieces": [ { "lat": [..E7..], "lng": [..E7..], "t": [..seconds from track start..], "alt": [..cm or null..] } ] }
```

- A JSON blob mirrors `feed.activityStreams.data`, which the AppView already fetches through a space member and caches by CID. Lexicons have no floats, and E7 integers match `actor.privacyZones`.
- Pieces make the breaks explicit, so no straight line is ever drawn across a removed stretch.
- **Offsets from the full track's first point**, not from the activity's `startedAt`. That way they don't depend on the Start time category, the AppView learns no wall-clock time, and later changes can still line efforts up with the activity's streams (whose `time` is also seconds from the start).
- Seconds are enough for 1 s timing. Sub-second precision isn't needed.
- **Rejected: GPX like `feed.activityTrack`.** GPX would need an XML parser on the AppView, carries ISO timestamps that would have to be stripped, and can express gaps only as `trkseg`s, which is weaker typing than pieces.

### 3. A splitting variant of trim
Add `TrackMath.trimPieces(points, circles, endsMeters): List<List<LocationSample>>` beside `trim`. It shares the hidden/crossing logic, but starts a new piece at each hidden-to-shown crossing instead of continuing the line. `trim` is unchanged, so polylines don't change. The existing `RouteTrimTest` cases get piece-shaped twins.

### 4. Per-record revision instead of a marker
Each segment track carries the `zonesRevision` it was trimmed by. The sweep lists `feed.segmentTrack` and `feed.activityTrack` in `self` (two `listOwn` calls, no blob reads) and writes a segment track for each activity that:
- has a track but no segment track, or
- has a segment track whose `zonesRevision` differs from the zones' revision.

This one rule covers turning matching on (backfill), zone changes (re-trim) and interruptions (whatever is left still differs), without an `actor.routeTrim`-style marker that would race with polyline sweeps. Turning matching off is the same sweep in reverse: delete every listed segment track. When the zones were never saved (`revision == null`), records carry no revision and match the never-saved state.
- **Rejected: extending `actor.routeTrim`.** Its revision means "polylines are done", and that would have to change meaning depending on the matching setting.

### 5. `SegmentTrackStore` beside `ActivityTrackStore`, hooked at the same call sites
- **Store:** a session-scoped `SegmentTrackStore` with `put(activity, route, zones)`, `delete(activity)` and `list()`. It writes through `OutboxWriter` with `PendingBlob`, exactly as `PdsActivityTrackStore` does, so a segment track is queued right after its activity track and drains in order.
- **Publish and combine:** the call sites that do `tracks.put` and `tracks.delete` (`ActivityRepository.kt:137`, `ActivityManager.kt:378-380, 446`) call a small `SegmentTracks` coordinator. It checks the setting and the publish's zones (`PublishTrim`: read fresh, or the last read this session, never `Unknown`) and queues or skips. A segment track trimmed by the last read rather than a fresh one sets a flag, and `ActivityRepository.sync` runs the sweep once it's set, which rewrites it if the saved zones' revision differs. That is the same fallback polylines use (`PrivacyZones.takeStale`).
- **Delete** is queued whatever the setting, when the store's list or the outbox shows a segment track exists. That way a track left behind by an earlier "on" period still goes.
- **When it skips** (zones never read this session, or offline before the setting was read), the sweep fills the gap later. A missing segment track widens nothing, so no list of skipped activities is kept.

### 6. The sweep runs where the route sweep runs
`SegmentTrackSweeper.sweep()` is called after `RouteTrimmer.sweep()`:
- in `repairRoutes` (once a session),
- after each zone save (`repairRoutesOnSave`),
- right after the setting is saved.

It holds its own mutex, reads full tracks four at a time (like `RouteTrimmer.CONCURRENT_TRACKS`), and drains only `feed.segmentTrack` writes.

### 7. The setting: `actor.segmentPrefs`, direct write, cached per session
- **Record:** `{ matching: boolean, updatedAt }`, rkey `self`. Written with `clients.spaces.putRecord` directly, like zones, never through the outbox: the spec says a failure is reported and the old value kept.
- **Repository:** a `SegmentPrefsRepository` reads it once per session and exposes `StateFlow<SegmentPrefs?>`. Null means unknown, and unknown counts as off for writing.
- **Ordering:** turning on saves first, then sweeps. Turning off saves first, then the delete sweep. The AppView purges on the setting alone, so the order is safe even if the deletes lag.
- **Asked once:** a missing record means never asked. The prompt is a dismissible card on the feed, shown only when the record is known to be missing (not on a read failure), the session has the scope, and the athlete has at least one GPS activity. Declining writes `matching: false`.
- **Scope check:** `clients.canWriteToSpaces(Nsid.FEED_SEGMENT_TRACK)` gates both the card and the Settings row. Without it, Settings shows a "sign in again to use segments" note.

### 8. AppView: self-only collections, rows without blobs, purge on off
- **`Indexer.spaceCollections`:** becomes `INTERACTIONS → INTERACTION_COLLECTIONS`, `SELF → TIER_COLLECTIONS + SELF_ONLY`, anything else → `TIER_COLLECTIONS`. `SpaceSyncer` already iterates `spaceCollections(skey)` for backfill, so it picks the new ones up.
- **Migration `V27__segment_tracks.sql`:**
  - `segment_tracks(uri PK, author_did, activity_uri, data_cid, zones_revision, indexed_at)`
  - `segment_prefs(did PK, matching BOOLEAN, updated_at BIGINT)`
  - Portable SQL, BIGINT millis.
- **Upserts:** keyed by URI or DID, as idempotency requires.
  - A segment-track row is stored whatever the setting, but `segments-core` must join `segment_prefs.matching = true` before fetching any blob. That join is the "never use while off" guarantee, checked when the data is used, as `validClaim` is.
  - **Rejected: skipping rows while matching is off.** Ops can arrive in any order across a backfill, and a row skipped while "off" would never come back after "on" without a full re-list.
- **Purge:** when a `segment_prefs` upsert sets `matching = false`, the same transaction deletes the author's `segment_tracks` rows, and in later changes their cached blobs and derived efforts.
- **Deletes:**
  - `Indexer.delete` handles both collections.
  - `dropSpace` for a `self` space removes both tables' rows for its authority.
  - `deleteActor` gains two lines.
- **Serving:** nothing is served. No route reads these tables in this change.

### 9. Documentation of the new trust boundary
- `.agents/rules/appview.md`'s first bullet and CLAUDE.md's data-flow paragraph say the AppView "stores nothing else private". Both gain one sentence: for athletes who turned segment matching on, the AppView also indexes their zone-trimmed segment tracks and the setting from `self`, never serves them, and drops them when matching goes off.
- The lexicon descriptions of `feed.activityTrack` and `space/athlete.json` gain the same sentence.
- The BACKLOG "consented member" item is narrowed to what's left (age and weight brackets).

## Risks / Trade-offs

- **[Duplicate storage on the PDS]** Segment tracks roughly double per-activity track storage. → JSON with E7 integers is compact. Gzipping the blob (`application/gzip`, like imports accept) can come later without a spec change, because the format is an implementation detail behind the record.
- **[Combined activities produce two segment tracks for one outing]** A combined activity and its members each have a full track, so each gets a segment track. → Acceptable for this change, since nothing matches yet. `segments-core` must skip validly claimed members (`api/Members.kt` `validClaim`) when matching.
- **[The AppView can be lied to]** A dishonest client can write any segment track. → Same trust as any record. Plausibility checks belong to matching and leaderboards (`segments-core`, `segment-leaderboards`).
- **[Re-sign-in needed]** New space collections need a new `space:` scope. → Gate the feature on the scope and show a sign-in-again note, as other space features do.
- **[Hide-ends distance from both ends]** The segment track omits the start and end, so segments at a ride's very start never match. → An intended privacy trade-off, and the same as the polyline.

## Migration Plan

- Additive only: two new lexicons, one new migration, new tables. Existing records, tables and specs keep their meaning.
- Deploy order: AppView first (it ignores unknown collections until it knows them), then clients. Publish the updated `space/athlete.json` declaration (`docker/scripts/publish-lexicons.sh` locally) before clients ask for the new scope.
- Rollback: clients stop writing. Rolling the AppView back leaves the tables unused. A fresh "off" from any client purges the rows. Because matching is opt-in, nothing is written for athletes who never turned it on.

## Open Questions

- Card copy and placement details for the one-time prompt (feed card vs. first-launch sheet) can be settled during UI review without changing the specs.
- Whether to gzip the data blob from the start. It is behind the record, so it can be decided when measuring real sizes.
