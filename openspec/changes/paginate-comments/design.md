# Design

## Context

See proposal.md for why. The current state that shapes the approach:

- **AppView.** `Views.activity()` lists up to 500 comments, a cap written as `limit(HttpStatusCode.InternalServerError.value)`. It joins comments to the activity (`admittedComments`), filters with `commentsListedFor(viewer)`, orders by `created_at` and marks the hidden ones. `hydrate` counts with `commentsVisibleTo(viewer) and not(hiddenByAthlete())`. The two queries already share their predicates. `getLikes` is the model for a paged per-activity query: a `NotFound` check, `Paging.clamp`, and a `"<sort>::<key>"` cursor. `keysetAfter` (`ViewQueries.kt`) already does ascending keysets. `comments_subject_idx (subject_uri, created_at)` exists from `V1`.
- **Client overlays.** `FeedRepository.activity()` lays `LocalDeletes` and `LocalCommentHides` over `GetActivity.Output`. Each one corrects the count by comparing its changes with the comments *in that output*: a deleted comment the AppView still lists lowers the count, and a hide the AppView doesn't show yet moves it. They can only do that because the output holds every comment. Paging breaks this.
- **Sending.** `ActivityRepository.comment` bumps the cached feed card's count and queues the record. The detail screen shows nothing new until a reload after the AppView has indexed the comment. `CommentView` needs a `cid`, and the client can't compute one, since there's no DAG-CBOR on the client.
- **Detail screen.** `ActivityDetailPresenter` loads `feed.activity()` once per `loadKey`. `CommentActions` edits the screen's comment list directly for deletes and hides. `ActivityDetailUi` renders every comment inside one `LazyColumn` item, and works out the count by counting the listed comments that aren't hidden.
- **Serialization.** `AtJson` has `encodeDefaults = true`, so a Kotlin default of `emptyList()` is still written as `[]`.

## Goals / Non-Goals

**Goals:**
- One predicate pair serves both the count and every page, so the specs' `The count matches the pages` holds by construction and a test checks it.
- The client's own sends, deletes and hides show correctly on any page and in the count, whether or not the AppView has caught up. This holds without loading every page.
- The detail screen opens on the activity and one page, and loads more lazily.

**Non-Goals:**
- Caching comment pages in Room. They're fetched each time the screen opens, as `getActivity`'s comments are today.
- Paging the feed cards' counts, which still come from `hydrate`.
- Keeping the client's own changes across an app restart. The overlays stay session-only, as today.

## Decisions

### 1. `getComments`: one query, two modes

`app.activitytracker.feed.getComments` takes `uri` (required), `limit` (1–100, default 30), `cursor`, and `uris` (an array of at-uris, `maxLength: 25`). Its output is `{ uri, comments: [commentView], cursor? }`.

- **Page mode** (no `uris`): a keyset over `(created_at, uri)` ascending with `keysetAfter`. The cursor is `"<createdAt>::<uri>"`. A malformed cursor reads as the first page, as it does everywhere else.
- **Lookup mode** (`uris` given): `cursor` and `limit` are ignored, and the query adds `Comments.uri inList uris` under the same `subject_uri` and `commentsListedFor` filters. There's no cursor. More than 25 URIs is `InvalidRequest`.

`Views.comments(viewer, params)` serves both modes. Two helpers come out of today's `activity()`:
- `requireVisibleActivity(uri, viewer)` is the `NotFound` check that `likes` repeats, and returns the athlete's DID.
- `commentViews(rows, athlete)` does the profile lookup and hidden marking.

The next cursor comes from the **last row fetched**, not the last view built. `commentViews` drops a row whose author has no `Actors` row, and a cursor built from the views would then repeat or skip comments.

*Alternatives:* a separate `getCommentsByUri` query, which is more surface for the same filter. Putting the lookup on `getActivity` would tie it to the activity reload, and the presenter needs it independently.

### 2. `getActivity` keeps `comments`, always empty

In `getActivity.json`, `comments` leaves `required` and its description says it's deprecated and always empty. The Kotlin `GetActivity.Output` becomes `Output(activity, comments: List<CommentView> = emptyList())` with a deprecation KDoc. `AtJson` writes `[]`, so an older client still decodes the field. `Views.activity()` drops its comment query, along with the 500 cap.

*Alternative:* serve old clients the first page in `comments`. That's rejected in the proposal, and it would keep a second listing path alive that has to stay in step with `getComments`.

### 3. No new index

`comments_subject_idx (subject_uri, created_at)` already serves `subject_uri = ? AND created_at >= ? ORDER BY created_at`. The `uri` tie-break only re-sorts rows that share a millisecond. Confirming that the index is used is a manual `EXPLAIN` against the docker stack's Postgres, recorded in the task. H2 plans aren't representative, so CI doesn't check it. *Alternative:* `(subject_uri, created_at, uri)` in a `V22` migration. Add it only if the plan shows a sort over many rows.

### 4. Client overlays: desired state against reported state

The comment parts of `LocalDeletes` and `LocalCommentHides`, plus the new sent comments, merge into one session-scoped `LocalComments` exposed as `FeedRepository.localComments`. `LocalDeletes` keeps activities and recordings.

`LocalComments` holds one entry per comment the viewer changed this session:
- `activity`;
- what the client wants: `Present(hidden)` or `Gone`;
- what the AppView last reported: `Listed(hidden)` or `Absent`;
- for a sent comment, its text, its `createdAt` and the viewer as author.

A comment *counts* when it's `Present`/`Listed` and not hidden. An activity's count delta is the sum over its entries of `counts(desired) - counts(reported)`. An entry retires once the desired and reported states match.

| Action | desired | reported at the time |
|---|---|---|
| send | `Present(false)` | `Absent` |
| delete | `Gone` | the state the comment was listed with |
| delete a sent comment not yet indexed | `Gone` | `Absent` (delta 0) |
| hide / unhide | `Present(true/false)` | the state the comment was listed with |

An entry retires once the two states agree, with one exception: a `Gone` entry is kept for the session, as `LocalDeletes` kept deletes. A comment sent and then deleted before the AppView indexed it can still be indexed, and listed, before its delete is. While a page or lookup reports it `Listed`, it is filtered out and taken off the count again. Implementation found this: retiring at once let such a comment reappear.

This generalises today's "move the count by the difference from what the AppView reported" to a reported state that comes from anywhere:
- **pages:** every page sets `Listed(...)` for the entries it lists;
- **lookups:** `FeedRepository.activity(uri)` makes a lookup-mode `getComments` call, in parallel with `getActivity`, for that activity's unretired entries (at most the 25 newest). Each URI it doesn't return becomes `Absent`. Older entries past 25 are treated as caught up, since anything that old has almost certainly been indexed.

Only a lookup can show that a comment is gone, which is why it's needed. With a lookup, the `AppView caught up before the reload` scenario and send-then-refresh both come out right without paging to the end.

`FeedRepository.comments(activity, cursor)` returns a page with the overlays applied:
- `Gone` comments are dropped;
- hides are marked as desired;
- a sent comment that the page lists is shown in its place.

Sent comments the AppView hasn't listed yet come separately as `sentComments(activity): Flow<List<SentComment>>`. `SentComment` has `uri`, `text`, `createdAt` and `author`. It is not a `CommentView`, because there's no CID. The presenter shows them after the loaded comments.

*Alternatives:* re-fetching the page each comment was seen on, or settling only from the pages loaded. Both leave the count off by one after send-then-refresh. The user chose the lookup.

### 5. Overlays drive the screen's list and count

The presenter stops editing its comment list for deletes and hides. It keeps the raw pages it fetched, and *derives* what it shows from `localComments` as a `StateFlow`: the overlaid comments and the sent ones. A change of any kind, from this screen or from another one, then shows the same way through one path.

The count is the exception, and implementation changed it. `activity.commentCount + delta(now)` is wrong. A page loaded later can settle an entry, for example by listing a sent comment the AppView has since indexed. That moves `delta` even though the loaded AppView count predates the indexing, so the count drops by one. Only the lookup that `FeedRepository.activity` makes alongside `getActivity` describes the same moment as the count it returns. So:
- the loaded count already includes the delta from that lookup;
- the presenter holds it together with `commentMoves`, which is how far this screen's sends, deletes, hides and unhides have moved it since, in one state value, so a reload replaces both at once;
- `CommentActions` moves the count when an action starts, by +1, −1 (0 for a hidden comment) or ∓1, and moves it back if the action fails or a hide turns out to be a no-op;
- `FeedRepository.activity` reports the lookup to `localComments` as its last step before returning, after the cache write, so nothing suspends in between.

To make this work, `ActivityRepository.deleteComment` and `setCommentHidden` record the entry **before** queueing and remove it if queueing throws, so the screen moves at once and rolls back on failure. A scope refusal still throws before anything is recorded. `CommentActions` keeps the `SingleFlight` dedupe and the error reporting, but no longer edits the list.

`deleteComment` takes the comment's URI, so a `SentComment` can be deleted. It still refuses to delete someone else's comment.

`ActivityRepository.comment` now returns the new comment's URI, built from the DID, `feed.comment` and the TID, and records a sent entry. `updateCached` keeps the feed card's count in step. The feed card has no overlay, and its count catches up on the next feed refresh, as today.

*Alternative:* keep the presenter's optimistic edits and add more for sent comments. That means two sources of truth that a reload has to reconcile, which is the bug class this change is removing.

### 6. Screen contract and paging state

`ActivityDetailState.Loaded` gains:
- `sentComments: ImmutableList<SentComment>`;
- `comments: CommentsPaging`, with `items: ImmutableList<CommentView>`, `isLoading`, `endReached` and `error: UiError?` (a new `UiError.Kind.LoadComments`). This replaces the bare list.

The count shown is `activity.commentCount`, already adjusted by the presenter. The UI stops counting the list.

New events: `LoadMoreComments` and `RetryComments`. `LoadMoreComments` runs under `SingleFlight`, keyed on the event, so repeated triggers load the next page once. It does nothing once `endReached` is set or while there's an error.

`LaunchedEffect(loadKey)` loads the activity and the first page concurrently, and a comments failure doesn't fail the screen. A reload replaces the pages.

In `ActivityDetailUi`, the comments become `LazyColumn` items keyed by URI, followed by the sent comments, a loading or retry row, and the draft field. The UI sends `LoadMoreComments` when the last visible index is within 5 of the last comment item, using `snapshotFlow` on `LazyListState.layoutInfo`.

## Risks / Trade-offs

- [Older clients show no comments, and they count 0 because they count the list] → All our clients ship together, and the proposal accepts this. Nothing is broken beyond the missing list.
- [`createdAt` is client-set] → A backdated comment can land behind a viewer's cursor and be missed until a reload, and a future-dated one sorts last. The ordering used it before this change too, so paging adds only the "missed until reload" case.
- [A lookup on every detail load that has pending entries] → It costs one small request, only when the session changed comments on that activity, and it runs in parallel with `getActivity`.
- [An account deletion leaves comment rows without an `Actors` row, so `commentViews` drops them while `hydrate` still counts them] → This mismatch exists today. The cursor comes from rows, so paging stays correct. It's out of scope beyond keeping the cursor right.
- [Overlay entries recorded before queueing] → If the app dies between recording and queueing, the overlay is lost with the session anyway, and nothing was written.

## Migration Plan

No database migration. Deploy the AppView first: it adds `getComments`, and `getActivity` starts returning `comments: []`. Then release the clients.

To roll back, redeploy the previous AppView. New clients then get an error from `getComments` and show the activity with the comments' retry row, so nothing crashes.
