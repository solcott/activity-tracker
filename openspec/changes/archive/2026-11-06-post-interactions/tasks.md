# Tasks

## 1. Lexicons and the Kotlin mirror

- [x] 1.1 In `feed/like.json`, `feed/comment.json`, `feed/commentHide.json`, `feed/getLikes.json` and `feed/getComments.json`, reword the descriptions so a subject may be an activity or a `feed.post`, and a hide applies to the post's author. Verify by reading the JSON.
- [x] 1.2 Add `likeCount` and `commentCount` (integers, default 0) and an optional `viewer` (`#postViewerState { like? }`) to `postView` in `feed/defs.json`. Add a new query, `feed/getPost.json` (`uri` → `{ post: postView }`, error `NotFound`).
- [x] 1.3 Mirror 1.2 in `core/lexicon`:
  - `PostView` fields, defaulting to 0 and null;
  - `PostViewerState`;
  - `Nsid.FEED_GET_POST`;
  - `GetPost` in `Queries.kt`.

  Extend `RecordSerializationTest` (or the views test) so a `PostView` without the new fields decodes with zero counts and no viewer. Verify with `./gradlew :core-lexicon:jvmTest`.

## 2. AppView: one set of interaction rules for every subject

- [x] 2.1 Add `Subject` in `api/Subject.kt`, with `Activity` and `Post`. Thread it through:
  - `admittedLikes` and `likesShownTo`;
  - `admittedComments`, `commentsVisibleTo`, `rootAdmits`, `hiddenByAthlete` and `commentsListedFor`.

  The activity call sites pass `Subject.Activity`. Verify that the existing like, comment, block, mute and hide suites pass unchanged with `./gradlew :server-appview:test`.
- [x] 2.2 Add `requireVisiblePost(uri, viewer)`. In `Views.likes` and `Views.comments`, pick the subject from the URI's collection: posts use `Subject.Post` with no member expansion, and any other collection is `NotFound`. Add `PostInteractionsTest`, which indexes likes, comments, replies and hides on posts from repos and `interactions` spaces, and covers:
  - the giver rule on narrowing;
  - the audience rule on widening;
  - a reply capped by its root;
  - a stranger's hide being inert;
  - `NotFound` for viewers outside the audience and after the proposal is withdrawn;
  - a block or mute hiding a giver.
- [x] 2.3 Give `postViews` like and comment counts (grouped over the page) and the viewer's like. Add a test that, for mixed audiences and hidden comments, `postView.commentCount` equals the listed, unhidden comments across every page of `getComments`, and `likeCount` equals `getLikes`. Also check the counts in timeline and `getAuthorPosts` items.
- [x] 2.4 Add the `getPost` route (`Views.post`, through `postRows` then `postViews`). Test it for the author, a follower, a non-follower (`NotFound`), signed out, after the proposal is withdrawn, and under `!takedown`.

## 3. Client data: interactions on any subject

- [x] 3.1 Add `InteractionSubject` with `ActivityView.asSubject()` and `PostView.asSubject()`. Change `ActivityRepository.toggleLike`, `comment`, `deleteComment` and `setCommentHidden` to take it, and update their activity call sites. Verify that the existing `core-data` and presenter tests pass unchanged (`./gradlew :core-data:jvmTest`).
- [x] 3.2 Add `FeedRepository.updateCachedPost`, which rewrites cached `PostView` rows, and dispatch the optimistic count and like updates by the subject's collection. Add a `PostView` overload to `LocalLikes.current`, and lay `LocalLikes` over posts read from the timeline and `authorPostsPage`. Add `core-data` jvm tests that liking, commenting on and hiding a comment on a Followers post:
  - write to the `interactions` space;
  - change the cached post's counts at once;
  - survive a refresh;
  - are refused up front without the space scope.
- [x] 3.3 Add `FeedRepository.post(uri)`, an `outcomeFlow` over `getPost` that lays `SegmentNames.applyTo` and `LocalLikes` over the result. Point `comments`, `commentsPage` and `likes` at any subject URI. Test them with `MockEngine`.

## 4. Shared comment components (no behavior change)

- [x] 4.1 Move the comment contracts from `feature/detail` to `:app-common-api`, the presenter pieces (`Comments.kt`, `CommentActions.kt`, `CommentMoves.kt`, the likers loader) to `:app-common-presenter`, and the UI (`CommentsUi`, `CommentThreadUi`, `CommentMenuUi`, `CommentAction`, the likers sheet) to `:app-common-ui`. Parameterize them by `InteractionSubject`. Verify with:
  - `ActivityDetailPresenterTest`, the comment tests and the detail screenshot tests, all unchanged;
  - `./gradlew checkModuleBoundaries`;
  - `./gradlew composeStabilityCheck`.

## 5. Post card and post page

- [x] 5.1 Add `PostEvent.OpenPost` and `PostEvent.Like`, and handle them in `rememberPostEventSink` (navigating to `PostScreen`, and toggling the like through `ActivityRepository`). Give `PostCard` its like and comment counts and a like button, hidden on the viewer's own post. Make the card body open the post page, while the segment or site and the author keep their own targets. Update `PostCard` samples and screenshots (`updateDebugScreenshotTestDefaultTestSuite`). Extend the `FeedPresenterTest` and `ProfilePresenterTest` post cases, which should pass.
- [x] 5.2 Scaffold `feature/post` (`api`, `presenter`, `ui`) with `PostScreen(uri)` using the `new-screen` skill. Its parts:
  - The presenter loads `FeedRepository.post` in the content-loading pattern, hosts the shared comments and likers, and handles `PostEvent`s.
  - The UI shows the card, the comment field and the threads.
  - `NotFound` shows that the post isn't available.

  Register it in the app graphs and add samples to `:app-fixtures`. Verify with a presenter test (load, `NotFound`, retry, like, comment, reply, hide), `ContentLoadingConformanceTest` and new screenshots.
- [x] 5.3 Add any strings in `:ui-resources`, such as "This post isn't available" and the like and comment count labels. Verify with `./gradlew check`.

## 6. Integration

- [x] 6.1 Run `./gradlew format` then `./gradlew check`, and confirm `BUILD SUCCESSFUL`.
- [x] 6.2 Run end to end on the local stack (`scripts/dev.sh`). Have alice share a proposal with Followers, then check that:
  - carol, a follower, sees counts of zero, then likes and comments;
  - `getPost`, `getLikes` and `getComments` show both to alice and carol;
  - bob, a non-follower, gets `NotFound` from all three;
  - alice's hide of carol's comment leaves the count;
  - withdrawing the proposal makes all three `NotFound`;
  - the records the run created are deleted afterwards.

## Workflow follow-up

- Archive the change once it's reviewed, syncing `feed/post-page`, `feed/posts`, `graph/blocks` and `graph/mutes` into the main specs.
