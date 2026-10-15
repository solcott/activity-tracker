# Tasks

## 1. Lexicon

- [x] 1.1 Add `lexicons/app/activitytracker/feed/activityZones.json`: a `tid`-keyed record with required `activity` (at-uri) and `createdAt`, optional `heartRateZones`/`powerZones`, and the `#zoneSnapshot` def moved from `activityStreams.json`. Its description says placement is its only visibility, at the narrower of charts and training. Remove `heartRateZones`, `powerZones` and `#zoneSnapshot` from `activityStreams.json`. Point `getActivityStreams.json`'s zone outputs at the new def. Update the `space.athlete` declaration's description so tier spaces' indexed records include `feed.activityZones`. Verify all JSON parses and nothing still refers to `feed.activityStreams#zoneSnapshot`.
- [x] 1.2 In `core/lexicon`: add `ActivityZonesRecord` and `Nsid.FEED_ACTIVITY_ZONES`, add it to `Nsid.indexedCollections`, move `ZoneSnapshot` and its limits to the new record's KDoc, and drop the zone fields from `ActivityStreamsRecord`. Add a serialization test for `ActivityZonesRecord`, and make `ActivityStreamsSerializationTest` show a streams record with stray zone fields still decoding. Verify with `./gradlew :core-lexicon:jvmTest`.

## 2. AppView

- [x] 2.1 Add Flyway `V23` (H2-portable): create `activity_zones` (uri, cid, author_did, activity_uri, visibility, heart_rate_zones, power_zones, record, indexed_at; indexed on activity_uri), and drop `heart_rate_zones`/`power_zones` from `activity_streams`. Update `Tables.kt`. Verify with `./gradlew :server-appview:test` (migrations run on H2).
- [x] 2.2 Index `feed.activityZones` from the repo (Jetstream) and tier spaces only, through the same placement and `RecordShadows` path as streams, with `boundedZones` moved from `StreamsIndex` to a zones index. Add it to `Indexer.spaceCollections` for tiers. Handle deletes, including promoting a shadow. Stop indexing zones from streams records. Tests: indexed from `mutuals` at Mutuals, ignored from `interactions`, a narrower copy wins mid-move, and deleting the narrower copy promotes the wider one. Update `SpaceSyncTest`'s "only activities and streams are stored" case.
- [x] 2.3 In `ActivityStreamsViews`, join `activity_zones` on `activity_uri` and include zones only when `visibleTo(viewer, zones.visibility, author)` passes. Remove `seesTraining` and the `publicDetails` lookup. Tests for the `feed/activity-zones` "shown only to viewers their place admits" scenarios: mutual yes, one-way follower no, self yes, stray streams fields ignored. Remove the old "follow public training" tests. Verify with `./gradlew :server-appview:test`.
- [x] 2.4 Update `.agents/rules/appview.md` for the new collection, table and gating. Verify with `scripts/check-agent-sync.sh`.

## 3. Client: write path

- [x] 3.1 Add a zones store in `core/data/.../activity` beside `PdsActivityStreamsStore`: `put` (queued through the outbox at a place), `move` (`moveOwn`), and `delete` from the repo and every tier. Add a single helper for the target, `narrower(charts, training)`. Unit-test the helper's table: Public/Public → repo, Public/Mutuals → mutuals, Followers/Public → followers, any/Only me → self.
- [x] 3.2 `ActivityRepository.publish`: stop passing zones to `ActivityStreams`/the streams record, read the training visibility fresh (not session-cached), and queue the zones record at the target when the draft has snapshots. `ActivityManager.delete`/`queueDelete` also delete zones everywhere. Tests in `ActivityRepositoryTest`: zones land at each target from the spec's publish scenarios, streams records have no zone fields, and a delete clears zones from every place.
- [x] 3.3 `setStreamsVisibility` and `ActivityManager.setVisibility` (wherever streams move) also move zones to `narrower(newCharts, training)`, under a session-scoped zones mutex. Tests: charts narrowed → zones to `self`; charts widened with Followers training → zones to `followers`.

## 4. Client: training backfill and sweep

- [x] 4.1 Narrowing training (`TrainingEdit` / `ProfileRepository.save` when training's tier narrows), under the zones mutex: drain the outbox, list own `feed.activityZones` in every place wider than the new tier, queue put-then-delete moves (skipping rkeys already queued), drain again, then save the profile. Widening or clearing training moves nothing. Tests with `FakeAthletePds`: Followers → Only me moves repo and `followers` zones to `self` before the profile write; a drain failure leaves training at its old tier and the save throws; a retry moves only what's left; widening and clearing leave zones in place.
- [x] 4.2 Session-start sweep (with the `space:` scope only, once per `SessionGraph`, under the zones mutex): list own `feed.activityZones` and `feed.activityStreams` in all places, queue moves for zones wider than their target, and delete copies not at the target when one exists there. Never widen. Tests for the spec's three "Session-start repair" scenarios, and that a session without the scope skips the sweep.
- [x] 4.3 Update `.agents/rules/data-and-auth.md` (zones placement, the backfill order, the sweep, the mutex). Verify with `scripts/check-agent-sync.sh`. Run `./gradlew :core-data:jvmTest :app-presenters:jvmTest`.

## 5. Docs and backlog

- [x] 5.1 Delete "Zones for Mutuals" from `BACKLOG.md`'s streams section (the section goes if it's left empty). Add a line under "Keep more of what activity files hold" (or a new item): recorded activities get no zone snapshots, only imports do. Verify by grepping `BACKLOG.md` for "Zones for Mutuals".

## 6. Integration

- [x] 6.1 Run the `lexicon-auditor` agent on `feed.activityZones` and `feed.activityStreams`, then `./gradlew check`. Both pass.
- [x] 6.2 On the local stack with alice (training Mutuals) and bob (mutual) and carol (one-way follower): import a Public activity with HR, and check that bob sees time in zone and carol sees charts without zones. Narrow alice's training to Only me, then check that bob no longer gets zones and the zones record is in alice's `self` space.

## Workflow follow-up

- Archive the change once it's implemented. The archive creates `openspec/specs/feed/activity-zones/spec.md`, removes "Stream zones follow public training" from `profile/private-details`, and updates "Only activity data is indexed from spaces" in `spaces/appview-syncer`.
