# Design

## Context

See proposal.md for the motivation and the specs for the behavior. This section covers what already exists that shapes the approach.

- **Indexing doesn't care about the subject.**
  - The `feed.like`, `feed.comment` and `feed.commentHide` branches in `Indexer.upsert` store `subject_uri` (or `comment_uri`) whatever the subject is.
  - `SPACE_RKEYS`, `HELD_ROWS` and `spaceCollections` already list all three, so the space syncer indexes them from `interactions` spaces unchanged.
  - The only subject check is the like's "not your own" check, which compares the subject's authority with the author's DID. That already covers posts.
- **Serving assumes an activity.** These pieces join likes and comments to `Activities`, and `visibleTo`'s column defaults are `Activities.*`:
  - `admittedLikes` and `likesShownTo` (`api/Views.kt`);
  - `admittedComments`, `commentsVisibleTo`, `rootAdmits`, `hiddenByAthlete` and `commentsListedFor` (`api/CommentQueries.kt`);
  - `requireVisibleActivity` and `interactionSubjects` in `Views.likes` and `Views.comments`;
  - `hydrate`'s counts.
- **Posts are read through `postRows(viewer)`** (`api/PostViews.kt`). It applies the tier, labels, and a check that the post's proposal is still listed. `postViews` builds the views.
- **Client writes take an `ActivityView`.**
  - `ActivityRepository.toggleLike`, `comment`, `deleteComment` and `setCommentHidden` use only its `uri`, `cid`, `author.did` and `visibilityOrPublic`.
  - They update `FeedRepository.updateCached`, which rewrites cached activities only.
  - `LocalLikes` and `LocalComments` are keyed by subject URI.
  - Posts are cached in the same `FeedItemEntity` rows, as `PostView` JSON under `POST_KEY`, and `SegmentNames.applyTo(post)` lays the viewer's own votes over them.
- **The comment UI lives in `feature/detail`.** These are the presenter's `Comments.kt`, `CommentActions.kt` and `CommentMoves.kt`, and the UI's `CommentsUi`, `CommentThreadUi`, `CommentMenuUi` and `CommentAction`. The likers sheet is part of `ActivityDetailPresenter`.

## Goals / Non-Goals

**Goals:**
- One set of like and comment rules for activities and posts. The audience, giver, block, mute, hide and count checks are written once and parameterized by the subject.
- No migration and no new record types.
- The post page reuses the activity page's comment components, without duplicating them.

**Non-Goals:**
- Combined-activity subject expansion for posts. Posts have no members, so `interactionSubjects` stays activity-only.
- Showing comments inline on the timeline card. The card shows counts only.

## Decisions

### 1. A `Subject` describes what a like or comment is on, and every interaction query takes one
`api/Subject.kt` adds a small value naming the table, `uri`, `cid`, author and visibility columns, and the extra filter for that kind of subject:

```
Subject.Activity : Activities, Activities.uri/cid/authorDid/visibility, extra = Op.TRUE
Subject.Post     : Posts,      Posts.uri/cid/authorDid/visibility,      extra = postRows(viewer = giver)
```

These functions all take a `Subject` and pass its columns to `visibleTo`, which is already parameterized by column:
- `admittedLikes(subject)` and `likesShownTo(viewer, subject)`;
- `admittedComments(subject)`, `commentsVisibleTo(viewer, subject)`, `rootAdmits` and `hiddenByAthlete(subject)`;
- `commentsListedFor(viewer, subject)`.

`Views.likes` and `Views.comments` pick the subject from `params.uri.collection`:
- `feed.activity` keeps today's path, with `requireVisibleActivity` and `interactionSubjects`.
- `feed.post` uses `requireVisiblePost(uri, viewer)`, which runs `postRows(viewer)` and returns the author, otherwise `NotFound`.
- Anything else is `NotFound`.

*Alternatives considered:*
- **A union of activity and post rows in a subquery.** The SQL is simpler to call, but the planner can't use `comments_subject_idx` through a `UNION` on H2, and every count would scan both tables.
- **Copying the comment queries for posts.** Two copies of the audience and hide rules would drift. The rule in `appview.md`, that every comment query applies both filters, is easier to keep with one implementation.

### 2. The giver check for posts is the tier, not the whole of `postRows`
A like or comment counts while its giver may see the post at its current tier, through `visibleTo(Likes.authorDid, Posts.visibility, Posts.authorDid, Posts.uri, Posts.cid)`. This mirrors the activity rule. The viewer's side still goes through `postRows(viewer)`, so a withdrawn proposal or a takedown hides the whole post and its interactions with it, as the spec's "Interactions go with their post" requires.

Running `listedProposal` per giver as well would add a correlated subquery to every count, for no visible difference. The proposal's listing doesn't depend on the giver, except that a proposer's own `!hide` exemption applies only to them.

### 3. Post views carry counts from one grouped query per page
`postViews` gains two grouped counts over the page's post URIs:
- likes through `admittedLikes(Subject.Post)` and `likesShownTo`;
- comments through `admittedComments(Subject.Post)`, `commentsVisibleTo` and `not(hiddenByAthlete)`.

It also does one lookup of the viewer's likes. That's three queries per page, the same as `hydrate` does for activities.

In `lexicons/.../feed/defs.json`:
- `postView` gains `likeCount` and `commentCount`, both integers that default to 0.
- It also gains an optional `viewer`, a `#postViewerState` holding `like`.

Older clients ignore all three.

### 4. `feed.getPost` is a plain query
Its params are `{ uri }` and it returns `{ post: postView }`. It reads `postRows(viewer)` for the one URI, then `postViews`. It's `NotFound` otherwise, so a refusal never reveals anything. The post page needs it because a link or a back stack can outlive the timeline page the post came from.

### 5. The client's interaction writes take an `InteractionSubject`
`core/data` adds `InteractionSubject(uri, cid, author, visibility)` with two constructors, `ActivityView.asSubject()` and `PostView.asSubject()`.

`toggleLike`, `comment`, `deleteComment` and `setCommentHidden` take it in place of `ActivityView`. The activity call sites wrap their view, so the behavior on activities doesn't change. Placement already goes through `interactionPlaceOf(visibility)` and `commentAudience`, and both work unchanged for a post's visibility.

The optimistic count update dispatches on the subject's collection:
- an activity goes to `updateCached`;
- a post goes to a new `updateCachedPost(uri, transform)`, which rewrites the cached `PostView` JSON rows the same way.

`LocalLikes.current` and `LocalComments` already work by URI, so they need only a `PostView` overload of `current`.

*Alternative considered:* separate `PostInteractions` methods. They would copy the scope checks, the outbox placement and the undo-on-failure logic, which are the parts most worth keeping in one place.

### 6. The comment list moves to the shared modules, and the post page is a new feature
These comment pieces of `feature/detail` move:
- Contracts go to `:app-common-api`: the comment state and events, and the `CommentAction` values.
- The presenter go to `:app-common-presenter`: `rememberComments`, its actions and the moves.
- The UI go to `:app-common-ui`: `CommentsUi`, `CommentThreadUi`, `CommentMenuUi` and the likers sheet.

They're parameterized by `InteractionSubject` rather than `ActivityView`. `feature/detail` keeps using them through the same calls, so its tests and screenshots should pass unchanged. That shows the move kept behavior.

`feature/post` (`:feature-post-api`, `-presenter`, `-ui`) adds:
- `PostScreen(uri)`;
- a presenter that loads `getPost` through `outcomeFlow`, lays `SegmentNames.applyTo(post)` and `LocalLikes` over it, and hosts the shared comments;
- a UI of the `PostCard` followed by the comment field and threads.

`PostEvent.Open` keeps its meaning of opening the segment or site. A new `PostEvent.OpenPost` opens the page, and `PostEvent.Like` toggles the like. `rememberPostEventSink` handles all three, so the feed and profile hosts gain them without changes of their own.

*Alternative considered:* putting the post page in `feature/detail`. `checkModuleBoundaries` would allow it, but it would grow an activity feature with post code and leave the comment UI unshared all the same, since profiles would still need the card's like button.

## Risks / Trade-offs

- **[Risk] The comment move is a large diff, and it could slip a behavior change into the activity page.** → It goes first, in its own task group, with no behavior change. The existing `ActivityDetailPresenterTest`, comment tests and screenshots must pass before any post work starts.
- **[Risk] A post-subject branch could miss a filter, and its count would stop matching its list.** → An AppView test asserts, for the mixed-audience and hidden-comment fixtures, that `postView.commentCount` equals the listed, unhidden comments across pages. It mirrors the activity test.
- **[Risk] The `Subject` refactor of the activity queries could change their SQL.** → The existing `AppViewTest` comment, like, block and mute suites run unchanged after the refactor and before posts are added.
- **[Trade-off] A like or comment written while a post was served keeps its row after the proposal is withdrawn.** It's just never served, and it comes back if a post at the same URI is served again. This matches how interactions on a deleted and rewritten activity behave.

## Migration Plan

- There's no database migration. `likes_subject_idx` and `comments_subject_idx`, which are already indexed, cover post subjects.
- AppView first: older clients ignore the new view fields and the new query.
- Clients from before this change keep opening the segment from the card. They don't show counts, but nothing breaks.
