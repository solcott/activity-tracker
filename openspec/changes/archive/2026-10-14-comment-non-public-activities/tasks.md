# Tasks

## 1. Lexicon

- [x] 1.1 Update three lexicon files:
  - In `lexicons/app/activitytracker/feed/comment.json`, add an optional `audience` string, an open enum with `knownValues` from `actor.defs#visibility`, described as the visibility the commenter's view showed when they wrote it, with absent meaning Public.
  - In `space/athlete.json`, add `app.activitytracker.feed.comment` to `collections`, and say in its description that the AppView indexes `feed.like` and `feed.comment` from `interactions`.
  - In `feed/defs.json`, drop "Unless it is public, commentCount is 0" and describe `commentCount` as the comments the viewer may see.

  Verify with the `lexicon-auditor` agent.
- [x] 1.2 In `core/lexicon`:
  - add `CommentRecord.audience: String? = null`;
  - remove `ActivityView.acceptsComments`, and update the KDoc of `commentCount`.

  Replace the `acceptsComments` assertions in `RecordSerializationTest` with a round trip of a comment with and without `audience`. Verify with `./gradlew :core-lexicon:jvmTest`.

## 2. AppView: indexing comments from `interactions`

- [x] 2.1 Add `V20__comment_origin_audience.sql`, two H2-portable `ALTER TABLE comments ADD COLUMN` statements:
  - `origin VARCHAR(512) DEFAULT 'repo' NOT NULL`;
  - `audience VARCHAR(16) DEFAULT 'public' NOT NULL`.

  Add both columns to `Comments` in `Tables.kt`. Verify that `./gradlew :server-appview:test` migrates on H2, and that the AppView starts against the local Postgres (`scripts/dev.sh`).
- [x] 2.2 In `Indexer`:
  - add `Nsid.FEED_COMMENT` to `INTERACTION_COLLECTIONS`;
  - make the comment upsert write `placement.origin` and `Visibility.known(record.audience)`;
  - scope the comment delete to `uri` and `origin`;
  - make `dropSpace` delete comments by origin.

  Verify with indexer and `SpaceSyncer` tests (`FakeSpacesPds`) for every scenario in "Comments indexed from interactions spaces" and "Comment in a tier space ignored", including that backfill picks up existing comments with no `SpaceSyncer` change.

## 3. AppView: who sees a comment

- [x] 3.1 Add `admittedComments()` in `Views.kt`, joining `comments` to `activities` on the subject, and filter every comment read by `visibleTo(viewer)`, `visibleTo(Comments.authorDid)` and `visibleTo(viewer, Comments.audience, Activities.authorDid)`:
  - use it for `hydrate`'s `commentCount`, as a grouped count that replaces `countBySubject(Comments…)` and the per-row zeroing;
  - use it for `activity()`'s comment list, keeping oldest-first order and the existing limit;
  - delete `acceptsComments` from `TierAccess.kt`.

  Verify with `ActivityVisibilityTest` cases for every scenario in "Who sees a comment": widened, narrowed, commenter unfollows and refollows, narrower audience than the tier, always Public, can't see the activity, and count matches list. Include an unknown-audience case from "A comment records its audience".
- [x] 3.2 Check the comment count's plan on the local Postgres: run `EXPLAIN ANALYZE` on a page of Followers activities with seeded follows and comments, next to the likes count. Verify that it uses the follow indexes and `comments_subject_idx`, and record the result in the commit message or PR description.
- [x] 3.3 Update `.agents/rules/appview.md`: comments are served by the three-part rule, with `comments.origin` and `comments.audience`, and the "Non-public activities take no comments" sentence goes. Verify with `scripts/check-agent-sync.sh`.

## 4. Client: commenting at every visibility

- [x] 4.1 In `Places.kt`, rename `likePlaceOf` to `interactionPlaceOf`, and update `toggleLike`. In `ActivityRepository.comment`:
  - drop the `acceptsComments` guard;
  - call `requireSpacesFor(visibility, Nsid.FEED_COMMENT)` before the optimistic count bump;
  - set `audience` to the view's visibility when it isn't Public;
  - queue the put with `space = interactionPlaceOf(visibility)`.

  Verify with `core/data` jvm tests for:
  - a Public comment, which goes to the repo with no `audience`;
  - a Followers comment, which goes to `interactions` with `audience = followers`;
  - a stale view;
  - a session whose scope lacks `feed.comment`, where nothing is queued and `ScopeMissingError` is thrown.

  Also update the `ActivityVisibilityTest.kt:455` filter.
- [x] 4.2 Make `ActivityManager.withVisibility` stop zeroing `commentCount`, and update its KDoc. Verify with a `core/data` jvm test narrowing an activity that has comments.
- [x] 4.3 Wire the comment error through the detail screen:
  - generalise `likeErrorOf` (`ActivityListEvents.kt`) into one mapping used for likes and comments;
  - add `commentError: UiError?` to `ActivityDetailState.Loaded`;
  - in `sendComment`, clear `commentError` on send, and on failure restore the text if the draft is still empty and set `commentError`.

  Verify with `ActivityDetailPresenterTest` cases:
  - a comment on a Followers activity is sent;
  - a scope-less session gets `SignInAgain` with the draft restored;
  - a double tap still sends once.

  Replace the `assertFalse(loaded.activity.acceptsComments)` check.
- [x] 4.4 Remove the `acceptsComments` checks from `ActivityCard` and `ActivityDetailUi`, and show `commentError` under the comment field with `errorText`. Verify with `./gradlew composeStabilityCheck`. Then use the desktop app with Compose Hot Reload to comment on a Followers activity and check that the count and field show on the card and the detail screen.
- [x] 4.5 Update the docs:
  - `.agents/rules/data-and-auth.md`: comments follow likes' placement through `interactionPlaceOf`, carry `audience`, are refused without `canWriteToSpaces(feed.comment)`, and "Comments stay public-only" goes.
  - `CLAUDE.md`'s data-flow paragraph: the AppView also syncs comments from `interactions` spaces and stores them as private data.

  Verify with `scripts/check-agent-sync.sh`.

## 5. End to end and backlog

- [x] 5.1 On the local stack (`scripts/dev.sh --seed alice`, plus bob, alice's effective follower, and carol, a non-follower):
  1. Republish the declaration, then sign bob in again.
  2. Bob comments on alice's Followers activity. Verify that bob's repo has no `feed.comment`, his `interactions` space has one with `audience: followers`, and alice and bob see it while carol gets `NotFound`.
  3. Widen the activity to Public. Verify that carol sees the activity with neither bob's comment nor its count, while alice and bob still see it.
- [x] 5.2 Edit `BACKLOG.md`:
  - delete the "Comments" entry;
  - in "Before deploying anywhere shared", add comments on non-public activities to what the AppView database holds.

  Verify by reading the result.
- [x] 5.3 Run `./gradlew check`, and verify that it passes.

## Workflow follow-up

- Archive this change before starting the dependent changes (`delete-comments`, `hide-comments-on-own-activities`, `paginate-comments`, `comment-replies`), so their delta specs can modify the `feed/comments` capability this change creates.
