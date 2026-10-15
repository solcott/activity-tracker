# Tasks

## 1. Lexicons and models

- [x] 1.1 Remove `track` from `lexicons/app/activitytracker/feed/activity.json` and add `feed/activityTrack.json` (`activity` at-uri, `track` GPX blob max 10 MB, `createdAt`; key `tid`; description: stored only in the athlete's `self` space, never the repo, not indexed by the AppView); verify both parse as JSON
- [x] 1.2 Add `app.activitytracker.feed.activityTrack` to `space/athlete.json`'s collections and say in its description that the AppView ignores it; point `feed/activityStreams.json`'s "the activity's track holds those" at `feed.activityTrack`
- [x] 1.3 Add `Nsid.FEED_ACTIVITY_TRACK` (not in `indexedCollections`), drop `ActivityRecord.track` and add `ActivityTrackRecord` in `core/lexicon` `Records.kt`; update `core/lexicon` tests and verify `./gradlew :core-lexicon:jvmTest` passes
- [x] 1.4 Run the `lexicon-auditor` agent on `feed.activity`, `feed.activityTrack` and `space.athlete` and fix every mismatch it reports

## 2. Track store

- [x] 2.1 Add `ActivityTrackStore` (interface) and its PDS implementation next to `ActivityStreamsStore`: `put` queues the record with a `PendingBlob` (field `track`) in `self`, `delete` queues a delete in `self`, `has` checks `self` with `readOwn` and the outbox for a pending put; verify with jvm tests that a put targets the `self` space with the blob, a delete targets only `self`, and `has` is true for a queued put

## 3. Activity writes

- [x] 3.1 `ActivityRepository.queue`: write the activity without a blob and queue its track through the store when the draft has GPX; update `ActivityRepositoryTest` so a Public GPS publish leaves no `track` in the activity record and a `feed.activityTrack` put in `self`, and a manual entry queues none
- [x] 3.2 `ActivityRepository.mergeInto`: queue the draft's track only when `has` is false, for indexed and unsent activities alike; test that a merge fills a missing track, leaves an existing one, and adds none to an activity whose track is still queued
- [x] 3.3 `ActivityManager.queueDelete`: delete the track too; remove the "keeps the track" comment on `queueUngroup`; test delete alone, delete with recordings (all tracks go) and delete keeping recordings (theirs stay)
- [x] 3.4 Update `EditActivityPresenterTest` and `ImportActivityPresenterTest` for the record without `track` (import asserts the track goes to `feed.activityTrack`); verify `./gradlew :core-data:jvmTest :app-presenters:jvmTest` passes

## 4. Dive merge preview and local rewrites

- [x] 4.1 `OwnDives.mergeGains`: offer `MergeGain.Track` when the logged activity has no `polyline` and the dive has samples; verify the duplicate-notice presenter tests pass
- [x] 4.2 `LocalRewrites`: drop the `track` carve-out in `applyTo` and `rewritten`; verify its tests (and `FeedRepository` tests that cover rewrites) pass

## 5. AppView

- [x] 5.1 Drop `track` from AppView test fixtures that build `feed.activity` JSON; add a test that a `feed.activityTrack` put in a `self` space reaches the `Indexer` and stores nothing; verify `./gradlew :server-appview:test` passes

## 6. Docs

- [x] 6.1 Update `.agents/rules/data-and-auth.md` (GPX blobs, delete removes the track), `.agents/rules/activity-files.md` ("GPX track blob"), the "Track blob MIME type" bullet in `BACKLOG.md`, and `CLAUDE.md`'s Architecture if it names the track; verify `grep -rn "feed.activity.*track\|track blob" .agents CLAUDE.md BACKLOG.md` finds no stale claim, and `scripts/check-agent-sync.sh` passes

## 7. Integration

- [x] 7.1 Run `./gradlew check` and verify it passes
- [x] 7.2 Reset the local stack (republish lexicons, wipe data, sign in again), record or import a GPS activity as Public, and verify the repo's `feed.activity` has no `track` while `self` holds its `feed.activityTrack`; then delete it and verify the track is gone

## Workflow follow-up

- Archive the change once implemented and reviewed, syncing `feed/activity-track` and the `spaces/appview-syncer` delta into the main specs.
