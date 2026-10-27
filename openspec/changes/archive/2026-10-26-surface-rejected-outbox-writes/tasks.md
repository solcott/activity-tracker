# Tasks

## 1. Store rejections (core/data)

- [x] 1.1 Add a nullable `rejectedAt: Long?` to `OutboxEntity`, bump `AppDatabase` to version 6 with a `MIGRATION_5_6` (`ALTER TABLE outbox ADD COLUMN rejectedAt INTEGER`) in `addAppMigrations`, and commit the regenerated schema JSON in `core/data/schemas/`; verify `./gradlew :core-data:jvmTest` passes, including any migration test, and the schema file for version 6 exists
- [x] 1.2 Add `OutboxDao` queries: mark a row rejected, clear a row's mark, a `pendingCount(did)` flow (rows not rejected whose rkey no rejected row shares), a `rejected(did)` flow, a group's rows by did and rkey, and deleting a group; verify with a DAO test on an in-memory database covering held rows and the two counts

## 2. Drain classifies failures (core/data)

- [x] 2.1 Classify failures in `OutboxProcessor`: transient for network failures (the `Network` test in `toDataError`), XRPC 5xx, 401, 408 and 429; rejected for everything else, after the existing too-large-original drop; verify with `OutboxProcessor` tests in `core/data` `jvmTest` (`FakeAthletePds`): a 400 `InvalidRecord` is marked rejected and not sent on the next drain, a 500 and a network failure stop the drain with nothing marked, a 401 isn't rejected
- [x] 2.2 Skip held writes in `drain`: collect the rejected rkeys from all the account's rows, skip rejected and held rows, keep going after a rejection, and return the selected writes not applied (rejected and held included); verify a test where a rejected activity holds back its details and streams while a later like is published, and one where a filtered drain (`only`) doesn't send a write held behind a row it didn't select
- [x] 2.3 Check every `drain` caller against the count (`RouteTrimmer.drainActivities`, `ActivityZonesStore.drainOrThrow`, `SegmentTrackSweeper`, `ActivityRepository`, `ActivityManager`, `BlockRepository`, `SegmentStars`, `SegmentPublisher`); verify with a `RouteTrimmer` test that a rejected re-trim leaves the route repair unmarked
- [x] 2.4 Update the `OutboxProcessor` and `OutboxEntity` KDoc and the "Writes go through the outbox" paragraph in `.agents/rules/data-and-auth.md` to describe rejection and held writes

## 3. Rejected changes, retry and discard (core/data)

- [x] 3.1 Add a session-scoped `RejectedChanges` exposing a flow of items (one per rejected rkey: the kind from the rejected row's collection, the activity's name and start from the group's `feed.activity` put when there is one, the rejection time) and switch `ActivityRepository.pendingWrites` to `pendingCount`; verify with a test that a rejected activity and a rejected like give two items with the right kinds and the pending count excludes them
- [x] 3.2 Add `retry(item)`, which clears the mark and runs a full drain; verify a test where the fake PDS accepts on retry (the activity, then its details, are published and the item is gone) and one where it refuses again (the item stays, its held writes stay queued)
- [x] 3.3 Add `discard(item)`, which deletes the group and unreferenced shared blobs in one transaction, then forgets the record's overlays (add `forget` methods to `LocalRewrites`, `LocalDeletes`, `LocalLikes` and `LocalComments` as needed) and drops the activity's cached feed items and refreshes those feeds; verify a test where a discarded new activity is gone from the timeline and the athlete's own feed, and one where a discarded edit shows the published record

## 4. Unsent changes screen (app)

- [x] 4.1 Scaffold `UnsentChangesScreen` with the `new-screen` skill: `State` (an `ImmutableList` of items, each with kind, activity name, start), `Event` (Retry, Discard, ConfirmDiscard, Back), the presenter over `RejectedChanges` with single-flight actions and errors through `ui/error-messages`, the UI from `ui/adaptive` wrappers with a confirmation before discarding, string resources for each kind ("A like", "A comment", "A block", "A mute", "A gear item", "A segment", "A change"…) and "The server refused it", and sample states in `:app-fixtures`; verify the presenter test (list, retry, discard after confirming, a failed retry's error) and the UI test pass, and `ContentLoadingConformanceTest` if the screen loads content
- [x] 4.2 Add `rejectedChanges` to `FeedState` and a tappable "N changes couldn't be published" row in `FeedUi` beside the "Uploading" row, navigating to the new screen; update `FeedSamples`; verify a `FeedPresenterTest` case for the count and the navigation, and the feed UI test
- [x] 4.3 Record screenshot references for the new screen and the feed's new row (`./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite`), and check the new images by eye; verify `:androidApp:testDebugScreenshotTestDefaultTestSuite` passes
- [x] 4.4 Check the new screen in the desktop app with Compose Hot Reload, using a fake rejected write in a test build or the local stack, and confirm the row, the list, retry and discard work

## 5. Integration

- [x] 5.1 Run `./gradlew check` and `./gradlew composeStabilityCheck` and confirm both pass
- [x] 5.2 Remove C4 from `docs/reviews/2026-10-23-codebase-review.md`, listing it under "Fixed since the review" with its commits; verify the doc no longer lists it as open

## Workflow follow-up

- Commit only when asked, as small commits by concern: the outbox's storage and drain (groups 1 and 2), rejected changes in `core/data` (group 3), the screen and feed row (group 4).
- Archive the change once it is committed.
