# Design

## Context

See proposal.md for why, and the spec deltas for what each viewer sees. This change follows the pattern that `comment-non-public-activities` and `delete-comments` set for comments.

- **Placement.** `ActivityRepository.comment` and `toggleLike` place a record with `clients.interactionPlaceOf(visibility)`: the repo for Public, otherwise the athlete's `interactions` space. They refuse up front with `requireSpacesFor(visibility, collection)`, and their deletes go to both places. The delete to the space is queued only when `canWriteToSpaces(collection)`.
- **Overlays.** `FeedRepository.activity` lays `LocalDeletes.applyTo(GetActivity.Output)` over the AppView's answer, which drops deleted comments and lowers the count only by the comments it actually dropped. `LocalLikes` overlays a per-activity toggle the same way.
- **AppView indexing.** The `Indexer` indexes from Jetstream (`Placement.REPO`) and from `interactions` spaces (`spaceCollections("interactions")` = `INTERACTION_COLLECTIONS`). It scopes deletes to the origin, and `dropSpace` and `deleteActor` remove rows by origin and by author.
- **AppView serving.** In `Views`, `admittedComments()` joins `Comments` to `Activities`. `commentsVisibleTo(viewer)` filters both the `getActivity` list and the grouped `commentCount` in `hydrate`.
- **Detail screen.**
  - `CommentActions` (presenter) handles `SendComment` and `DeleteComment` with `SingleFlight`.
  - `CommentMenu` (UI) is the "…" menu on a comment, shown today only on the viewer's own comments.
  - `ActivityDetailUi` counts `state.comments.size`.
- **SHA-256.** It's available only in `core/atproto` (`crypto/Encoding.kt`, `internal suspend fun sha256`). Both `core/data` and `:server-appview` depend on `core/atproto`, but `core/lexicon` doesn't.
- **Pending changes.** `paginate-comments` and `comment-replies` aren't implemented yet. Each of them must reuse the hidden filter added here.

## Goals / Non-Goals

**Goals:**
- One serving rule for hidden comments, in the same place as `commentsVisibleTo`, so the count and the list agree.
- A hide and an unhide that are idempotent and work offline, with no read before writing.
- The AppView can rebuild every hide from records alone.

**Non-Goals:**
- Hiding comments that are still only in the outbox. The athlete's own activity only lists comments the AppView has indexed.
- Feed cards showing exact counts after a refresh, before the AppView catches up. That's the same lag that new and deleted comments already have.
- Notifying the commenter. The hidden mark is all they get.

## Decisions

### 1. `feed.commentHide`, keyed by the comment

The record is `{ subject: at-uri (the comment), createdAt }`, with `key: "any"`, and its rkey is `commentHideRkey(comment)`. That function returns the first 20 bytes of SHA-256 over the comment's AT-URI string, as lowercase base32 with no padding: 32 characters, all valid in a record key. It lives in `core/atproto`, next to `sha256`, so that the client and the AppView share it. It suspends, because WebCrypto, the web's only SHA-256, has no blocking hash, so the Indexer computes it before opening its transaction. `CommentHideRecord` in `core/lexicon` documents the rule without computing it.

- Hiding is a `putRecord` at that rkey, so a retry or a second hide replaces the same record. Unhiding deletes that rkey, which needs no lookup, so it works offline and before the AppView has indexed anything.
- The Indexer skips a hide whose rkey isn't `commentHideRkey(subject)`, and also one whose subject isn't a `feed.comment` URI. This follows `FollowApprovalRecord.rkey`, and it keeps "unhide deletes one known rkey" true even for records written by other clients.
- *Alternative: `key: "tid"`, with the AppView returning the hide's URI on `CommentView`.* Rejected. Unhiding needs the AppView to have indexed the hide first. Duplicate hides would each need deleting.
- *Alternative: rkey = `<commenter DID>:<comment rkey>`.* Rejected, because a `did:web` with a port contains `%`, which a record key can't hold.
- *Alternative: also store the activity URI in the record.* Rejected. The comment row already names its activity, and `Activities.authorDid` is the athlete, so the field would be a second claim the AppView would have to check against the comment.

### 2. Placed like the athlete's likes and comments

`ActivityRepository.setCommentHidden(activity, comment, hidden)` does both. Hiding works as follows:

1. It returns early, writing nothing, unless the activity's author is the session's DID and the comment's author isn't.
2. It calls `requireSpacesFor(activity.visibilityOrPublic, Nsid.FEED_COMMENT_HIDE)`.
3. It queues `writer.put(FEED_COMMENT_HIDE, rkey, record, space = interactionPlaceOf(visibility))`.
4. It records the hide in the overlay, lowers the cached `commentCount` by one (`feed.updateCached`, floored at 0), and drains the outbox.

Unhiding queues `writer.delete` for the repo and, when `canWriteToSpaces(FEED_COMMENT_HIDE)`, for the `interactions` space. It then updates the overlay, raises the cached count, and drains.

- `space/athlete.json` adds `app.activitytracker.feed.commentHide` to `collections`, and its description says the AppView indexes hides from `interactions`.
- `Nsid.FEED_COMMENT_HIDE` joins `indexedCollections`, so Jetstream delivers repo hides.

### 3. `LocalCommentHides` overlay

This is a session-scoped `MutableStateFlow<Map<String, Boolean>>` of comment URI → hidden, held by `FeedRepository` next to `deletes`. `applyTo(GetActivity.Output)` sets `hidden` on each listed comment that has an entry. It then adjusts `activity.commentCount` by the difference from the AppView's answer:
- −1 for each comment marked locally hidden that the AppView didn't mark;
- +1 for each comment marked locally unhidden that the AppView did mark.

The count therefore never moves twice once the AppView catches up. It runs after `deletes.applyTo`, so a deleted comment isn't counted back in.

- *Alternative: fold it into `LocalDeletes`.* Rejected. A hide is a toggle that can be undone, while `LocalDeletes` is append-only and never expires. Its shape matches `LocalLikes`.
- Entries never expire within the session. A later change made on another device is overridden only for comments this session toggled, as `LocalLikes` already does.

### 4. Serving: one hidden predicate, two uses

`comment_hides` (`CommentHides`) has the columns `uri`, `origin`, `author_did`, `comment_uri`, `created_at` and `indexed_at`.
- The primary key is `(uri, origin)`, so a hide written in the repo and later again in the space are separate rows, and a delete from one place leaves the other.
- An index on `(comment_uri, author_did)` serves the lookup.

`Views` gains:

```
hiddenByAthlete = exists(CommentHides where comment_uri = Comments.uri and author_did = Activities.authorDid)
```

- **Count** (`hydrate`): `commentsVisibleTo(viewer) and not hiddenByAthlete`.
- **List** (`activity`): `commentsVisibleTo(viewer) and (not hiddenByAthlete or viewer = Activities.authorDid or viewer = Comments.authorDid)`. A signed-out viewer gets `not hiddenByAthlete`. The query selects `hiddenByAthlete` as a boolean column, and maps it to `CommentView.hidden = true`, or leaves it absent when false.

Checking the author against `Activities.authorDid` at serve time makes the result independent of indexing order. A hide indexed before its comment just waits in the table. It also needs no rewrite when an activity changes visibility. The `Indexer` stores every well-formed hide and doesn't check the author. A stranger's hide is an inert row, which `deleteActor` and `dropSpace` clean up.

- *Alternative: check the author at index time.* Rejected, because it would need the comment and the activity indexed first, which breaks backfill order.
- `paginate-comments` and `comment-replies` must use these same two predicates. A note goes in `.agents/rules/appview.md`.

### 5. Lexicon view

`feed/defs.json#commentView` gains an optional `hidden: boolean`: "The activity's athlete hid this comment. It's present only for the athlete and the commenter, the only viewers it's listed for." `CommentView.hidden: Boolean? = null`. `commentCount`'s description adds that hidden comments aren't counted.

### 6. Presenter and UI

- **Events.** `ActivityDetailEvent.HideComment(uri)` and `UnhideComment(uri)` go to `CommentActions`, which:
  - flips the comment's `hidden` in `comments` at once;
  - runs the repository call in `flights.launch(uri)`;
  - on failure, flips it back and sets `commentError`, using `interactionErrorOf(e)` (which turns `ScopeMissingError` into `SignInAgain`) or else a new `UiError.Kind.HideComment` or `UnhideComment`.

  No confirmation is needed, since a hide can be undone.
- **Menu.** `CommentMenu` takes the items it should show, and `ActivityDetailUi` decides them:
  - Delete when `comment.author.did == viewerDid`;
  - Hide or Unhide when `state.isOwn` and the comment is someone else's.

  The menu is shown only when there's at least one item.
- **Hidden comments.** These render dimmed with a "Hidden" label. The commenter sees "Hidden by <athlete>", using the activity author's display name.
- **Count.** `ActivityDetailUi` counts `state.comments.count { it.hidden != true }` instead of `size`.
- **New strings.** Hide, Unhide, the two hidden labels, and the two errors.

## Risks / Trade-offs

- **[Hides on Public activities are public]** Anyone reading alice's repo can see which comment she hid. → This was chosen deliberately, and it matches Bluesky's hidden replies. Hides on non-public activities stay in the `interactions` space.
- **[Signing in again]** Hiding on non-public activities needs the new declaration in the session's grant. → The refusal comes up front and asks the athlete to sign in again. Public hides keep working.
- **[A session without the `feed.commentHide` space scope can't unhide a hide that a newer session wrote to the space.]** The comment appears unhidden locally but stays hidden for everyone else. → This is the same trade-off as unliking and deleting a comment. The overlay lasts only for this session, and after the next sign-in, Unhide works.
- **[Extra cost on comment queries]** The `exists` subquery runs per comment. → It's an index lookup on `(comment_uri, author_did)`. Check it with `EXPLAIN` on the local Postgres, alongside the comment count.
- **[Orphaned hides]** A hide outlives a deleted comment. → It's a small inert row that `deleteActor`, `dropSpace` and unhiding clean up. A TID is never reused, so it can never hide a different comment.

## Migration Plan

1. Deploy the AppView. `V21__comment_hides.sql` creates the table and its index, and existing data needs nothing. The Jetstream filter picks up `feed.commentHide` from `Nsid.indexedCollections`.
2. Publish the updated space declaration with `docker/scripts/publish-lexicons.sh` (locally, `scripts/dev.sh` does this).
3. Ship the clients. Athletes sign in again before hiding comments on non-public activities.

Rollback:
1. Revert the clients. Hides already written keep working on the AppView.
2. If needed, revert the AppView's serving, which shows hidden comments again.
3. Leave `V21` in place, since migrations are append-only.
