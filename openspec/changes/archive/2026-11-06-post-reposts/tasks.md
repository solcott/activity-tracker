# Tasks

## 1. Lexicons and the Kotlin mirror

- [x] 1.1 Add `feed/repost.json` (a `subject` strongRef and `createdAt`, key `tid`) and `feed/getReposts.json` (shaped like `getLikes`, error `NotFound`). In `feed/defs.json`, add `#repostedPostView` and `#reasonRepost`, `postView.repostCount` (default 0) and `#postViewerState.repost`. Add `#repostedPostView` to `getTimeline`'s feed union. List `app.activitytracker.feed.repost` in `space/athlete.json`'s collections and its interactions-space description. Verify by reading the JSON and with `lexicon-auditor` after 1.2.
- [x] 1.2 Mirror 1.1 in `core/lexicon`:
  - `RepostRecord`;
  - `Nsid.FEED_REPOST` (in `indexedCollections`) and `Nsid.FEED_GET_REPOSTS`;
  - `GetReposts` in `Queries.kt`;
  - `PostView.repostCount` and `PostViewerState.repost`;
  - `RepostedPostView`, `ReasonRepost`, and `TimelineItem.Repost` with its `TimelineItemSerializer` member;
  - `FEED_REPOST` in `AthleteSpaceDeclaration` and `AppCollections`.

  Extend the serialization tests so that a `PostView` without the new fields decodes with zero reposts and no viewer repost, a `#repostedPostView` round-trips, and an older timeline item still decodes. Verify with `./gradlew :core-lexicon:jvmTest`.

## 2. AppView: indexing and serving reposts

- [x] 2.1 Add the `Reposts` table and the V59 migration (`reposts` with `(subject_uri, sort_at DESC)` and `(author_did, sort_at DESC, uri DESC)` indexes, H2-portable). Add the `FEED_REPOST` branch to `Indexer.upsert`: skip a subject that isn't a `feed.post` or is the reposter's own, and cap `sort_at` at index time. Add `Reposts` to `HELD_ROWS` and `FEED_REPOST` to `INTERACTION_COLLECTIONS`. Add indexer tests for:
  - a repost from the repo, and one from an `interactions` space;
  - skipped activity and own-post subjects;
  - a record delete, the space being reported deleted, and account deletion each removing the row.

  Verify with `./gradlew :server-appview:test`.
- [x] 2.2 Add `api/Reposts.kt` with `admittedReposts()`, `repostsShownTo(viewer)` and `timelineReposts(viewer)` (Design 2). Give `PostCounts` repost counts and the viewer's repost. Add `Views.reposts` and the `getReposts` route through `requireVisiblePost`. Add `PostRepostsTest`, covering:
  - the reposter rule on narrowing and on unfollow;
  - `NotFound` for viewers outside the audience, for a non-post subject and after the post is deleted or its proposal withdrawn;
  - blocks between viewer and reposter, and between reposter and author;
  - an account `!hide` label on the reposter, and a `!takedown` on the post;
  - `repostCount` equalling the `getReposts` list across pages, in `getPost`, `getAuthorPosts` and timeline items;
  - mutes leaving counts and the list unchanged.
- [x] 2.3 Change `cardedTimeline`'s post source to the originals and reposts sources (Design 3), with each repost entry's `#reasonRepost` (the latest reposter and the count of others). Add `TimelineRepostsTest`, covering:
  - a non-followed author's post reposted by a followed athlete, at the repost's position;
  - the viewer's own repost;
  - an original and a later repost showing once, at the repost;
  - an undone repost returning the post to its own position;
  - a tie going to the original;
  - two reposters, named latest and "1 other";
  - a follower of the reposter outside the post's audience seeing nothing;
  - a muted reposter and a muted author;
  - a blocked reposter and a blocked author;
  - paging with a small `limit` showing each post on exactly one page;
  - a filtered timeline (type, since or until) showing no reposts, as it shows no posts.

  Verify with `./gradlew :server-appview:test`.

## 3. Client data: repost writes and reads

- [x] 3.1 Add `FEED_REPOST` to `GrantStatus`'s `PrivateInteractions` and a `Repost` kind to `RejectedChanges`. Add `LocalReposts`, sharing the renamed `toggleDelta` helper with `LocalLikes`, and `ActivityRepository.toggleRepost(post: PostView)` (Design 6). Add `core-data` jvm tests that reposting and undoing on a Public post write to and delete from the repo, and that on a Followers post they:
  - write to the `interactions` space;
  - change the cached post's count and viewer repost at once;
  - survive a refresh;
  - are refused up front without the space scope;
  - are skipped on the viewer's own post.

  Verify with `./gradlew :core-data:jvmTest`.
- [x] 3.2 Cache `TimelineItem.Repost` under a `repost:` key prefix in `FeedRepository`'s per-kind places, and make `updateCachedPost` rewrite rows under both prefixes. Lay `LocalReposts` and `LocalLikes` over posts read from the timeline, `getPost` and `authorPostsPage`. Add `FeedRepository.reposts(uri)`. Test with `MockEngine`, covering:
  - a timeline holding a repost item caches and reloads it;
  - a like on a reposted item updates its cached row;
  - `reposts(uri)` maps `NotFound`.

## 4. Card and post page

- [x] 4.1 Add `PostEvent.Repost` and `PostEvent.OpenReposter`, handled in `rememberPostEventSink`. Give `PostCard` an optional `ReasonRepost`, shown as a "Reposted by …" (and "and N others") header that opens the reposter's profile, and a repost button and count, with no button on the viewer's own post. Render `TimelineItem.Repost` in the feed through `PostCard` with its reason. Update the `PostCard` samples in `:app-fixtures`, add a reposted-card sample, and regenerate the screenshots (`updateDebugScreenshotTestDefaultTestSuite`). Extend the `FeedPresenterTest` and `ProfilePresenterTest` post cases with repost and open-reposter, and confirm they pass.
- [x] 4.2 Generalize `rememberLikesList` over its loader, and keep the activity page's tests passing unchanged. On the post page:
  - add `PostPageEvent.ToggleRepostsList` and a reposters sheet over `FeedRepository.reposts`, listing the viewer at once after they repost;
  - handle `PostEvent.Repost`;
  - add samples to `PostPageSamples`.

  Verify with `PostPagePresenterTest` (repost, undo, reposters list, `NotFound`), `ContentLoadingConformanceTest` and new screenshots.
- [x] 4.3 Add the strings to `:ui-resources`: "Repost", "Undo repost", "Reposted by %s", "Reposted by %s and %d others" (plural), "Reposted by you", the repost count label and the reposters sheet title. Verify with `./gradlew composeStabilityCheck checkModuleBoundaries`.

## 5. Integration

- [x] 5.1 Run `./gradlew format`, then `./gradlew check`, and confirm `BUILD SUCCESSFUL`.
- [x] 5.2 Run end to end on the local stack (`scripts/dev.sh`, after republishing the space declaration). With alice sharing a proposal with Followers, check that:
  - bob, her follower, reposts it into his `interactions` space;
  - dave, who follows bob but not alice, doesn't see it in his timeline;
  - carol, who follows both alice and bob, sees it once, "Reposted by bob", and `getReposts` lists bob for her;
  - bob's undo returns carol's item to alice's position;
  - `EXPLAIN` for carol's timeline page on Postgres uses both `reposts` indexes;
  - the records the run created are deleted afterwards.

## Workflow follow-up

- Archive the change once it's reviewed, syncing `feed/reposts`, `feed/posts`, `feed/post-page`, `feed/timeline-order`, `spaces/appview-syncer`, `graph/blocks`, `graph/mutes` and `moderation/labels` into the main specs.
