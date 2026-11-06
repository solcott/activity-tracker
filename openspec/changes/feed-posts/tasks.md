# Tasks

## 1. Lexicons and the Kotlin mirror

- [x] 1.1 Add `lexicons/app/activitytracker/feed/post.json`, with optional `text` (10000 bytes, 1000 graphemes), an open-union `embed` whose first member is `#proposal` (the proposal's AT-URI), and `createdAt`. Run `lexicon-auditor`, which should report no mismatches.
- [x] 1.2 In `feed/defs.json`, add `#postView` and `#postProposalEmbedView` (the segment or site brief view, the proposal's URI, CID, name, `openedAt`, `status`, totals, `viewerVote` and `canVote`). Add `#postView` to the `getTimeline.json` union. Add `feed/getAuthorPosts.json` (actor, limit, cursor). Verify with `lexicon-auditor`.
- [x] 1.3 In `space/athlete.json`, add `app.activitytracker.feed.post` to `collections` and describe it (indexed from `followers` and `mutuals` only). Verify with `scripts/check-public-safety.sh` and `lexicon-auditor`.
- [x] 1.4 In `core/lexicon`, add `PostRecord` with its embed union, `Nsid.FEED_POST` (added to `indexedCollections`), the `AppCollections` entry, the `PostView` types, `TimelineItem.Post` registered in `TimelineItemSerializer.memberFor`, and `GetAuthorPosts`. Add a serialization test round-tripping a timeline with a post and an unknown member, next to `ImportBatchSerializationTest`, and verify it passes on jvm and iosSimulatorArm64.

## 2. AppView indexing

- [x] 2.1 Add Flyway `V57__posts.sql` and the `Posts` table in `Tables.kt`, with the author, rkey, proposal URI, text, `created_at`, `sort_at`, visibility, origin, record and CID, and an index on `(author_did, sort_at DESC, uri)`. Add the table to `HELD_ROWS`. Verify with `:server-appview:test`, where migrations run on H2.
- [x] 2.2 Index `feed.post` from the repo and from the `followers` and `mutuals` spaces (`TIER_COLLECTIONS` scoped so `self` is excluded). Validate the embed (same author, same rkey, a `feed.segmentNameProposal` URI) and the text length, place the narrowest copy via `place()` and `RecordShadows`, and add a delete branch. Cover each scenario of `feed/posts` "A post is stored by its audience", "A post embeds its author's own proposal" and "Changing a post's audience moves it", and the appview-syncer post scenarios, in a new `PostsTest`.

## 3. AppView views

- [x] 3.1 Hydrate `postView`. Join the proposal by URI and serve nothing when it's missing or hidden. Add the proposal status, totals, viewer vote and batched `canVote` from the segment-name and dive-site-name views, and the current segment or site name. Cover "A post is served only with its proposal" and "A post's proposal is shown live" in `PostsTest`.
- [x] 3.2 Merge posts into `cardedTimeline`: a second keyset query with the same authors and the `notMuted`, `visibleTo`, block and label filters, sorted by `sort_at` capped at index time, and skipped on filtered requests. Cover `feed/timeline-order` "Post position", `feed/feed-filter` "Posts in feed scopes", the `feed/posts` tier scenarios, and paging across a page boundary mixing posts, activities and an import card. Extend `ImportCardsTest`.
- [x] 3.3 Add the `getAuthorPosts` route, paged by `(sort_at, uri)`, with blocks applied and mutes not. Cover `graph/blocks` "Blocked accounts' posts are hidden", `graph/mutes` "Muted posts leave the muter's timeline", and `moderation/labels` post scenarios in `PostsTest` and `BlocksTest`.

## 4. Client data

- [x] 4.1 `SegmentNames.propose`, `edit` and `delete` take an optional share. Enqueue the post put, move or delete through the outbox at the proposal's rkey (repo or `AthleteSpaces` tier), and show it at once. Verify with `core-data` jvm tests for share, unshare, an audience change, a share-only edit that leaves the proposal unrewritten, and a delete removing both. Do the same for dive-site proposals.
- [x] 4.2 `FeedRepository` caches the post kind under a `post:` key in `timelineItems`, `refreshTimeline` and `toTimelineFeedItems`, with local block filtering. Add `authorPosts` paging for the profile. Verify with `TimelineCardsTest` and `FeedOutcomesTest` cases holding posts.

## 5. Client UI

- [x] 5.1 The `ProposePlaceName` screen, presenter and UI gain Share to feed (off by default), an audience picker of Public, Followers and Mutuals (Public only without Spaces, with the explanation), and text with a grapheme limit. Use the edit-mode rules from `segments/segment-names`. Add samples in `ProposePlaceNameSamples`, and verify with the presenter test and `:composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"`.
- [x] 5.2 Add a `PostCard` in `:app-common-ui` with the author, text, segment or site, proposal name, status, totals, vote buttons when `canVote`, a tap that opens the segment or site, and a menu with Report for others' posts. Wire it into `FeedUi.timelineItems`, with `FeedEvent`s for vote and open, and add post samples to `FeedSamples`. Verify with `FeedPresenterTest`, a UI test, and `:androidApp:updateDebugScreenshotTestDefaultTestSuite` references reviewed.
- [x] 5.3 The profile gets an Activities and Posts switch, with posts loaded on their own in the content-loading pattern, an empty state and paging. Add `ProfileSamples` states, and verify with the profile presenter and UI tests and the screenshot tests.
- [x] 5.4 Reporting a post sends the post's strongRef (`moderation/reports`). Verify with the report presenter test.

## 6. Integration

- [x] 6.1 Run `./gradlew check`, `composeStabilityCheck` and `checkModuleBoundaries`, and verify all pass.
- [x] 6.2 End to end with `scripts/dev.sh --seed alice` and a second account:
  - alice, a regular on a segment, proposes and shares with Followers and some text;
  - a follower sees the card in Following and votes from it;
  - a non-follower sees nothing on alice's Posts tab;
  - changing the audience to Mutuals hides it from the one-way follower;
  - deleting the proposal removes the post everywhere.

## Workflow follow-up

- Archive the change after review, then start `post-interactions`.
