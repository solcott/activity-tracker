# Tasks

## 1. Lexicons and Kotlin mirror

- [x] 1.1 Add the optional `importBatch` (`format: tid`) to `lexicons/app/activitytracker/feed/activity.json` and to `ActivityRecord`. Verify that a record with it and one without it round-trip through `AtJson` in a `core/lexicon` test.
- [x] 1.2 Add `#importCardView` and `#sportCount` to `feed/defs.json`, and replace `getTimeline`'s `activities` output with `feed`, a union of `#activityView` and `#importCardView`. Mirror both in `core/lexicon`, then verify the union decodes each item by `$type` and that an unknown `$type` doesn't fail the page.
- [x] 1.3 Add `feed/getImportBatch.json` (`actor`, `batch`, `limit`, `cursor` → `activities`, `cursor`), its `XrpcQuery` descriptor and `Nsid` entry. Verify with the `lexicon-auditor` agent that every layer matches.

## 2. AppView: feed position

- [x] 2.1 Write the Flyway migration (next free version): `posted_at`, `ended_at` and `import_batch`, backfill `posted_at`, `ended_at` and `sort_at` as design.md's Migration Plan says, and the `(author_did, import_batch)` index. Verify a migration test on H2 re-sorts a seeded 2019 activity below a recent one.
- [x] 2.2 Set `posted_at`, `ended_at` and `sort_at = min(posted_at, ended_at + 5d)` in `ActivityIndex`, from the row's own record only, and keep `import_batch` only when it parses as a TID. Verify with indexer tests covering the live, 3-days-late, 2019, future-`createdAt`, missing-elapsed and malformed-TID cases (`feed/timeline-order`, `feed/import-cards` "Activities carry their import batch").
- [x] 2.3 Order `authorActivities` on `(started_at, uri)` descending, filtered or not. Verify with a Views test that a 2019 import lists between its March 2019 neighbors and that paging has no gaps.
- [x] 2.4 Verify with a Views test that combining a copy into a year-old activity leaves it a year back on the timeline, and that a batch saved out of order lists by end time (`feed/timeline-order`).

## 3. AppView: import cards and the batch query

- [x] 3.1 Build the unfiltered timeline from activities merged with qualifying batches (design.md Decision 4), with one cursor across both. Filtered timelines stay activities only. Verify with Views tests for a 40-activity card, a viewer who sees 3, a 4-activity batch, a recent activity in a batch, a filtered timeline, and a card spanning pages with no duplicates.
- [x] 3.2 Hydrate `importCardView` from the counted rows: count, start range, sport counts, record distance, up to 4 polylines, post time. Verify with tests that 10 Only me activities in a batch, and a distance shared in a narrower tier, never reach a follower's card.
- [x] 3.3 Keep cards out for blocks and mutes, and leave labeled activities out of counts. Verify with Views tests for a muted athlete, a blocked athlete and a taken-down member.
- [x] 3.4 Add the `getImportBatch` view and route: the batch's visible activities, newest started first, combined activities in place of members, an empty list for an unknown batch, `ActorNotFound` for an unknown actor. Verify with route tests.
- [x] 3.5 Measure timeline page time with a seeded 5,000-activity import across several followed athletes. Record the numbers in design.md under Risks.

## 4. Client: tagging batches

- [x] 4.1 Add `importBatch` to `ActivityDraft` and its `toRecord`, keep it on the activity record in `ActivityLayout`, and carry it onto a combined record built within a batch (not one combined into a logged activity). Verify with `core/data` tests on the published records.
- [x] 4.2 Draw one `Tid` for each save in `BatchImport` and set it on every draft. Verify with a `saveBatch` presenter test that 38 stand-alone drafts and a combined pair share one identifier, a second save gets another, and combining into a logged ride leaves that ride untagged (`feed/batch-import` "Saving a batch").

## 5. Client: timeline items and cache

- [x] 5.1 Add `TimelineItem` (activity or import card), have `FeedRepository.timelineItems`/`refreshTimeline` read `GetTimeline.feed`, and cache cards in `feed_item` under an `importCard:` key (design.md Decision 7; no Room migration). Verify with `FeedRepository` tests (`MockEngine`, in-memory Room) that cards and activities are cached in order, and that a local block or mute drops a card.
- [x] 5.2 Update the feed presenter and every `timeline` call site to `TimelineItem`, leaving author feeds on `ActivityView`. Verify `:feature-feed-presenter` tests pass and `checkModuleBoundaries` passes.

## 6. Client: card and batch screen

- [x] 6.1 Add the import card UI in `feature/feed/ui` (athlete, count, date range, sport counts, distance in the athlete's units, a route-map grid), with no like or comment actions, and tapping it opening `ImportBatchScreen`. Add sample states to `:app-fixtures`. Verify with a UI test and a new screenshot reference.
- [x] 6.2 Scaffold `ImportBatchScreen(actor, batch)` with the `new-screen` skill across `:feature-feed-api`, `-presenter` and `-ui`, paging `getImportBatch`, grouped by month (reuse or move the batch form's month headers into `:app-common-ui`), each row opening the activity page. Verify with presenter and UI tests, `ContentLoadingConformanceTest` and screenshot references.
- [x] 6.3 Add the new strings to `:ui-resources`, and verify `composeStabilityCheck` passes for the new `State` classes.

## 7. Integration

- [x] 7.1 Run `./gradlew check` and `:server-appview:test` through the `gradle-runner` agent, and verify both pass.
- [ ] 7.2 On the local stack (`scripts/dev.sh --seed alice`), import a Strava sample archive as alice and follow her from a second account. Verify that the follower's timeline shows one import card at the top, that a recent activity in the batch shows on its own, that the card opens the batch screen, and that alice's profile lists the imports by start.

## Workflow follow-up

- Archive the change once implemented and reviewed (`/opsx:archive`), syncing `feed/timeline-order` and `feed/import-cards` into `openspec/specs/`.
