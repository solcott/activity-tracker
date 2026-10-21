# Tasks

## 1. Lexicons and Kotlin mirror

- [x] 1.1 Add `lexicons/app/activitytracker/feed/segmentTrack.json`: `activity`, optional `zonesRevision`, `data` blob (`application/json`), `createdAt`, key `tid`. Its description states self-only storage, the pieces/E7/offset JSON shape and AppView use only while matching is on. Verify it parses with the other lexicons (`./gradlew :core-lexicon:jvmTest`).
- [x] 1.2 Add `lexicons/app/activitytracker/actor/segmentPrefs.json` (`matching` boolean, `updatedAt`, key `literal:self`, self-only). Verify the same way.
- [x] 1.3 Add both collections to `space/athlete.json` and update its description. Update `feed/activityTrack.json`'s description to say the AppView reads only the separate zone-trimmed segment track. Verify `docker/scripts/publish-lexicons.sh` publishes them on the local stack.
- [x] 1.4 Mirror the changes in `core/lexicon`:
  - `Nsid.FEED_SEGMENT_TRACK` and `Nsid.ACTOR_SEGMENT_PREFS`, kept out of `indexedCollections` because they are space-only;
  - `SegmentTrackRecord` (with a `DATA_FIELD` blob constant) and `SegmentPrefsRecord` in `Records.kt`;
  - a `SegmentTrackData` serializable for the blob JSON.

  Verify round-trip tests in `RecordSerializationTest` (including that `$type` is written and unknown keys are ignored).
- [x] 1.5 Run the `lexicon-auditor` agent on the two NSIDs and fix every mismatch it reports.

## 2. Trimming into pieces

- [x] 2.1 Add `TrackMath.trimPieces` in `core/location/.../RouteTrim.kt`, sharing the hidden/crossing logic with `trim`, starting a new piece at each hidden-to-shown crossing and dropping empty pieces. Verify with new `RouteTrimTest` cases: start in a zone, a mid-route pass (two pieces, no joining point), hide-ends, everything hidden (empty), no zones (one piece equal to the input). The existing `trim` tests must still pass unchanged (`./gradlew :core-location:jvmTest`).
- [x] 2.2 Add an encoder from pieces to `SegmentTrackData`: E7 rounding, seconds offset from the full track's first sample, altitude in cm or null. Verify a unit test checks offsets are relative and no absolute time appears in the encoded JSON.

## 3. Segment matching setting (client)

- [x] 3.1 Add `SegmentPrefsRepository` (SessionScope) in `core/data`:
  - reads `actor.segmentPrefs` from `self` once per session into a `StateFlow` (null = unknown, missing record = never asked);
  - `save(matching)` writes it with a direct `putRecord`, throws on failure and keeps the old value;
  - `canUse` checks `canWriteToSpaces(Nsid.FEED_SEGMENT_TRACK)`.

  Verify jvmTests with `FakeAthletePds`: default off, a failed save keeps the old value, a missing scope reports unavailable.
- [x] 3.2 Add the Settings row: `SettingsState` fields, a `SegmentMatchingChanged` event, `SettingsPresenter` wiring, `SettingsUi` toggle with an explanation, and a "sign in again" note when the scope is missing. Use `ui/adaptive` components. Verify `SettingsPresenterTest` covers on, off, save failure (error shown, toggle reverts) and missing scope, and that `./gradlew composeStabilityCheck` passes.
- [x] 3.3 Add the one-time prompt card on the feed. It shows only when the record is known missing, the scope is present, and the athlete has a GPS activity. "Turn on" saves `true`; "Not now" saves `false`. Verify `FeedPresenterTest` cases: shown, hidden after either choice, hidden on a read failure, hidden without the scope. Check it visually on desktop with Compose Hot Reload.

## 4. Writing and deleting segment tracks (client)

- [x] 4.1 Add `SegmentTrackStore`, modelled on `PdsActivityTrackStore`: `put` queues a `feed.segmentTrack` with the JSON blob via `OutboxWriter` + `PendingBlob` in `self`; `delete` queues a delete; `list` returns the listed rkeys with each record's `zonesRevision`. Verify a jvmTest that put/delete go to `self` only and drain after the activity track.
- [x] 4.2 Add the `SegmentTracks` coordinator. It queues a segment track only when matching is on and the zones were read this session (`PrivacyZones.current`), skips when everything is hidden, and queues a delete whenever one exists (listed or queued). Hook it at the track call sites in `ActivityRepository` (publish) and `ActivityManager` (combine put/replace and delete). Verify `ActivityRepositoryTest` / `ActivityCombinerTest` cases:
  - publish with matching on writes activity, track and segment track (offline too);
  - matching off writes none;
  - zones unread writes none;
  - a manual entry writes none;
  - combining replaces it;
  - deleting removes it;
  - a visibility change leaves it untouched.
- [x] 4.3 Add `SegmentTrackSweeper`. It lists `feed.activityTrack` and `feed.segmentTrack` in `self`. With matching on, it writes a segment track for each activity missing one or with a stale `zonesRevision`, reading tracks four at a time. With matching off, it deletes every listed segment track. It drains only segment-track writes and has its own mutex. Verify jvmTests:
  - enabling backfills every GPS activity;
  - an interrupted backfill finishes in a new session;
  - a zone change rewrites stale ones only;
  - disabling deletes all, and an interrupted delete finishes later;
  - nothing is written with unknown zones.
- [x] 4.4 Wire the sweep: after `RouteTrimmer.sweep()` in `repairRoutes` and `repairRoutesOnSave`, and right after `SegmentPrefsRepository.save` (on → backfill, off → delete). Verify `PrivacyZonesTest` / `PrivacyZonesPresenterTest` cover adding a zone with matching on re-trimming segment tracks, and `SettingsPresenterTest` covers toggling starting the sweep.

## 5. AppView indexing

- [x] 5.1 Add migration `V27__segment_tracks.sql`:
  - `segment_tracks(uri PK, author_did, activity_uri, data_cid, zones_revision, indexed_at BIGINT)` with an index on `author_did`;
  - `segment_prefs(did PK, matching BOOLEAN, updated_at BIGINT)`.

  Use H2-portable SQL and add matching Exposed tables in `db/Tables.kt`. Verify `MigrationVersionsTest` and `./gradlew :server-appview:test` pass.
- [x] 5.2 Make `Indexer.spaceCollections` self-specific (`SELF` → tier collections + `feed.segmentTrack` + `actor.segmentPrefs`). Add upserts keyed by URI and DID that decode with `AtJson` and drop malformed records. A `matching = false` upsert purges the author's `segment_tracks` rows in the same transaction. Add delete branches, `dropSpace` cleanup for a `self` space and `deleteActor` lines. Update the KDoc on `spaceCollections`. Verify `AppViewTest` end to end on H2:
  - indexed from `self`;
  - ignored from `followers` and `interactions`;
  - `feed.activityTrack` and `actor.privacyZones` from `self` still not stored;
  - turning matching off purges rows;
  - a stray track while off is stored but flagged unusable by a `usableSegmentTracks()` helper that joins `segment_prefs.matching = true`;
  - space deletion and account deletion remove both;
  - replaying events is idempotent.
- [x] 5.3 Confirm no route or view reads the new tables (grep `api/`). Verify an `AppViewTest` case that `getActivity` / `getActivityStreams` for an activity with a segment track contain nothing from it.

## 6. Rules, docs and backlog

- [x] 6.1 Update the documents that describe what the AppView holds, saying it now also indexes consenting athletes' zone-trimmed segment tracks and the setting from `self`, never serves them, and purges them when matching goes off:
  - `.agents/rules/appview.md` (first bullet and the indexed-collections list);
  - the CLAUDE.md data-flow paragraph;
  - the `BACKLOG.md` "AppView as a consented member" item, narrowed to age and weight brackets.

  Verify `scripts/check-agent-sync.sh` passes and the `rules-check-drift` skill reports no stale statement about the AppView storing nothing else private.

## 7. Integration

- [x] 7.1 Run `./gradlew check` and verify it passes: all targets, lint, ktfmt, detekt, sorted deps.
- [x] 7.2 On the local stack (`scripts/dev.sh --seed alice`), with a zone around the sample route's start, sign in again, turn matching on, and import a sample GPX:
  - the `feed.segmentTrack` appears in alice's `self` space;
  - its pieces exclude the zone;
  - its JSON has no timestamps;
  - the AppView's `segment_tracks` has a row.

  Then turn matching off and verify the segment tracks are deleted and the AppView rows are gone.

## Workflow follow-up

- Archive the change with `/opsx:archive` once implemented and reviewed, then sync the `segments/segment-track`, `profile/privacy-zones` and `spaces/appview-syncer` specs.
- Start the next roadmap change, `segments-core`, which adds segment records, the matcher and efforts.
