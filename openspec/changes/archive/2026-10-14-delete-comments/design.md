# Design

## Context

- `ActivityRepository.comment` places a comment with `interactionPlaceOf`: the repo for a Public activity, else the commenter's `interactions` space. The comment's URI is repo-style (`at://<did>/app.activitytracker.feed.comment/<rkey>`) either way, and `CommentView` doesn't say where it's stored.
- `toggleLike` already solves the same problem for unliking: it queues `writer.delete` of the rkey for the repo and, when `canWriteToSpaces(feed.like)`, for the `interactions` space. `OutboxProcessor` treats a missing record or `SpaceNotFound` as done (`deleteFromSpace`), and the repo's `deleteRecord` is idempotent.
- `ActivityDetailPresenter` loads `comments` once per `loadKey` from `FeedRepository.activity`, which lays `LocalLikes`, `LocalRewrites` and `LocalDeletes` over the AppView's answer. The detail UI counts `state.comments.size`; feed cards show `ActivityView.commentCount`, which `comment` bumps through `feed.updateCached`.
- Activity deletion confirms in the UI (`ManageActivityUi`'s `confirming` flag and `AppAlertDialog`) and only then sends `ActivityDetailEvent.Delete`.
- The AppView's `Indexer.delete` already removes a `Comments` row only when the delete comes from that row's `origin` (`repo` or the space URI), and `SpaceSyncer` passes space deletes to it. Nothing there changes.

## Goals / Non-Goals

**Goals:**
- One repository call that removes the viewer's comment from wherever it might be, through the outbox, with the same scope rule as unliking.
- The deleted comment stays hidden, and the count stays one lower, across reloads until the AppView catches up.

**Non-Goals:**
- Deleting comments that are still only in the outbox. They aren't listed until the AppView indexes them, so the UI can't offer it, and outbox order would apply a put then its delete correctly anyway.
- Making feed cards exact after a refresh before the AppView catches up. A comment's +1 already lags a refresh the same way.

## Decisions

### Delete from both places, as unliking does
`ActivityRepository.deleteComment(activity, comment)` queues `writer.delete(feed.comment, rkey)` for the repo and, when `canWriteToSpaces(feed.comment)`, for `tiers.uri(AthleteSpace.INTERACTIONS)`, then drains.
- *Alternative: read both places first and delete where found.* Needs a connection, so it can't queue offline, and adds two reads to every delete. Rejected.
- *Alternative: guess the place from the activity's current visibility.* Wrong once the activity has moved since the comment was written (`Where a comment is stored`). Rejected.
- It returns early, writing nothing, when the comment's author isn't the session's DID: the UI only offers Delete on own comments, but the repository is the boundary.

### Hide it locally until the AppView catches up
`LocalDeletes` gains the deleted comment URIs (`deleteComment(uri)`). `FeedRepository.activity` drops those comments from `output.comments` and lowers `activity.commentCount` by the number it dropped, so the count only drops while the AppView still has the comment and never twice. `deleteComment` also lowers the cached `commentCount` by one (`feed.updateCached`, floored at 0), as `comment` raises it. A TID is never reused, so the set never needs to expire.
- *Alternative: a separate `LocalCommentDeletes`.* Same lifetime and purpose as `LocalDeletes`; one session-scoped overlay is simpler. Rejected.

### Event carries the comment, confirmation stays in the UI
`ActivityDetailEvent.DeleteComment(val uri: AtUri)` is sent only after the user confirms in an `AppAlertDialog`, as `Delete` is. The presenter removes the comment from its `comments` at once, then runs `activities.deleteComment` in `flights.launch(event.uri)`, keyed by the comment's URI, so a double confirm writes once while deletes of different comments don't block each other. A failure puts the comment back where it was and sets `commentError` to a new `UiError.Kind.DeleteComment`. The repository adds the URI to `LocalDeletes` only after both deletes are queued, so a failure leaves nothing hidden.
- `Loaded` gets `viewerDid: Did` (`profiles.did`), and the UI offers Delete where `comment.author.did == viewerDid`; the presenter doesn't precompute a per-comment flag, so `comments` stays the AppView's `CommentView`s.
- *Alternative: confirmation state in the presenter.* The activity delete keeps it in the UI, and it's pure UI state. Rejected for consistency.

### UI
Each own comment gets an `AppMenu` with one destructive `AppMenuItem(AppIcon.Delete)`, matching `ManageActivityUi` and leaving room for `hide-comments-on-own-activities` and `comment-replies` to add items; choosing it opens the confirmation for that comment. New strings: the action label/content description, the dialog title and message, and the `DeleteComment` error.

### AppView: tests only
Indexer behaviour already matches the spec (origin-scoped delete). Add tests in `SpaceSyncTest` (delete from the interactions space; the repo delete of the same URI changes nothing) and `AppViewTest` (a Jetstream delete of a repo comment drops it from `getActivity` and its count; a space-origin delete leaves a repo comment).

## Risks / Trade-offs

- [A session without the `feed.comment` space scope can't delete a space comment written by a newer session of the same account; it vanishes locally but stays for its audience.] → Same as unliking, and such sessions are already asked to sign in again when they comment on a non-public activity. The local hide lasts only for this session, so it reappears on the next sign-in, where Delete works.
- [The comment reappears briefly in the feed card count after a timeline refresh before the AppView indexes the delete.] → The detail screen is exact; the card catches up with the AppView, as for new comments.
- [The space delete fails with something other than `SpaceNotFound` and holds up the outbox.] → Same path and handling as unlike deletes; the outbox retries in order.
