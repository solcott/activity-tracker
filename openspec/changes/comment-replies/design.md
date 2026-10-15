# Design

## Context

See proposal.md for why, and `specs/feed/comments/spec.md` for the behaviour. The current state that shapes the approach:

- **Record.** `CommentRecord(subject, text, createdAt, audience?)`. `ActivityRepository.comment` sets `audience` from the activity's visibility, and places the record with `clients.interactionPlaceOf(visibility)` after `clients.requireSpacesFor(visibility, Nsid.FEED_COMMENT)`. Both already take a visibility, not an activity.
- **AppView table.** `comments(uri, cid, author_did, subject_uri, text, created_at, indexed_at, origin, audience)`, with `comments_subject_idx (subject_uri, created_at)`. The last migration is `V21__comment_hides.sql`.
- **Serving.** `Views.comments` runs `admittedComments()` (comments joined to `Activities`) filtered by `commentsListedFor(viewer)`, as a keyset on `(created_at, uri)` or a `uris` lookup, and builds views with `commentViews(rows, athlete, avatars)`. `hydrate` counts `commentsVisibleTo(viewer) and not(hiddenByAthlete())`. The two share their predicates, which is how the count matches the pages.
- **Client.** `LocalComments` holds per-comment desired and reported states, plus sent comments (`SentComment`, which has no CID). `Comments.kt` in the presenter keeps the raw pages and derives what's shown from `localComments`. `CommentsUi.kt` renders the comments as `LazyColumn` items keyed by URI. `CommentsPaging` holds `items: ImmutableList<CommentView>`.

## Goals / Non-Goals

**Goals:**
- Replies use the same visibility, hide, delete and count machinery as comments. A reply is just a comment row with a thread.
- The count still matches the pages by construction: one predicate serves the count, the thread pages and the reply pages.
- Grouping replies needs nothing indexed in a particular order.

**Non-Goals:**
- Showing which reply a reply answers ("replying to carol"). `parent` is stored and served, so a later change can add it.
- Caching threads in Room. Pages are fetched each time the screen opens, as now.
- Reply counts on feed cards. They show the activity's comment count, which includes replies.

## Decisions

### 1. The record names both `root` and `parent`

`feed.comment` gains an optional `reply: #replyRef`, with `root` and `parent`, both `com.atproto.repo.strongRef`. `root` is the thread's top-level comment and `parent` the comment answered. For a reply to a top-level comment they're equal. The Kotlin mirror is `ReplyRef(root: StrongRef, parent: StrongRef)` and `CommentRecord.reply: ReplyRef? = null`.

Carrying `root` lets the AppView group a reply into its thread from the record alone, even if the parent isn't indexed yet or is deleted, so single-level threads hold whatever order things arrive in. *Alternative:* `parent` only, with the AppView walking up to the root. That needs the parent indexed first, and breaks when a middle reply is deleted. This is the same shape as `app.bsky.feed.post#replyRef`.

Strong refs need the parent's CID. A `SentComment` has none, so Reply isn't offered on it until the AppView lists it (spec: `Reply to a comment`). *Alternative:* plain at-uris. That breaks the convention of strong refs for subjects, and replying to an unindexed comment is a rare case.

### 2. Columns `thread_uri` and `parent_uri`

Migration `V22__comment_threads.sql`:
- adds `thread_uri VARCHAR(512)` and backfills it with `uri`, then sets it `NOT NULL`;
- adds `parent_uri VARCHAR(512)` (null for a top-level comment);
- adds `thread_cid` and `parent_cid VARCHAR(255)` (null for a top-level comment), so views can serve `root` and `parent` as strong refs (decision 5) even when the root is deleted or not indexed;
- adds `comments_thread_idx (subject_uri, thread_uri, created_at)`.

`ALTER TABLE … ALTER COLUMN … SET NOT NULL` works in both Postgres and H2's PostgreSQL mode. The `Indexer` sets `thread_uri` to `reply.root.uri` for a reply and to the comment's own URI otherwise, and the other three from `reply`.

It skips the record (as `requireHideKey` does) when `root` or `parent` isn't a `feed.comment` URI with an rkey. Nothing checks at index time that `root` is on the same activity, since it may not be indexed yet. Serving handles that instead: threads are grouped within `subject_uri`, so a stray root is simply never listed on this activity and the reply sits under a placeholder (spec: `A malformed reply is skipped`). A top-level comment is a row where `thread_uri = uri`.

### 3. Serving: the root's audience caps its replies

`commentsVisibleTo(viewer)` gains a clause for replies: when a row with `uri = thread_uri` exists for the same subject, the viewer must also be admitted to *that* row's audience. This is a left join of `Comments` aliased as `Roots` inside `admittedComments()`, and the extra `visibleTo(viewer, visibility = Roots.audience, author = Activities.authorDid)` applies only where `Roots.uri` is not null and the row is a reply.

It covers the root, not every ancestor. The client caps each reply by its parent, so an honest chain only narrows. A dishonest client can at worst widen its own reply's text within the root's audience. It could post that text as a top-level comment anyway.

The count (`hydrate`) uses the same predicate, so `The count matches the pages` still holds by construction.

### 4. `getComments` modes and output

The params gain `root: at-uri?`. The output gains `threads: [#threadView]`. In `defs.json`:
- `#threadView` is `{ uri (the top-level comment's), root?: #commentView, replies: [#commentView], replyCount: integer, repliesCursor?: string }`. An absent `root` is the placeholder, and nothing else in the thread view describes the missing comment (spec: `Unavailable top-level comment`).
- `#commentView` gains `root?` and `parent?` (strong refs, present on replies; see decision 5) and `audience?` (absent means public), which the client needs to cap a reply. Revealing a comment's audience to a viewer already admitted to it tells them nothing new.

The three modes:

| Mode | Input | `threads` | `comments` | cursor |
|---|---|---|---|---|
| Page | `cursor`, `limit` | threads of the page | the page's listed top-level comments, for clients from before replies | next page of threads |
| Lookup | `uris` | — | the listed comments and replies asked for | none |
| Thread | `root`, `cursor`, `limit` | — | that thread's replies, oldest first | next page of replies |

`uris` and `root` together are `InvalidRequest`. `comments` stays `required`, so older clients still decode a page.

**Thread pages.** Over the rows listed for the viewer, group by `thread_uri` and order threads by `(MIN(created_at), thread_uri)`, as a keyset with `HAVING`. The cursor is `"<minCreatedAt>::<threadUri>"`. The oldest listed comment of a thread is normally its root. For a placeholder it's the oldest listed reply, which places it without revealing the root's time (spec: `Placeholder's place`). A new reply can't lower a thread's minimum, so pages don't repeat a thread when replies arrive.

Then, for the page's thread URIs, one query fetches the listed rows of those threads. It splits them into roots (`uri = thread_uri`) and replies, and keeps the three oldest replies per thread, plus each thread's reply count. Doing this in Kotlin keeps the SQL portable: at most 100 threads, and their listed replies. If that proves heavy, a `ROW_NUMBER() OVER (PARTITION BY thread_uri …)` window works in both databases. `repliesCursor` is the third reply's `"<createdAt>::<uri>"` when `replyCount > 3`.

**Thread mode** is today's page keyset with `thread_uri = root and uri <> root` added.

**Lookup mode** is unchanged, except that its views now carry `root` and `parent`.

*Alternatives:*
- Ordering threads by the root's `created_at`, or the time in its TID. That leaks the time of a placeholder's comment, or needs the root indexed.
- A separate `getReplies` query. The modes already share every filter, as `uris` did in `paginate-comments`.

### 5. Placement and audience by audience

`ActivityRepository.comment(activity, text, replyTo: CommentView? = null)`:
- the audience is `activity.visibilityOrPublic`, narrowed by `Visibility.narrower(it, replyTo.audience)` for a reply, a new helper over the order Public > Followers > Mutuals > Only me;
- `requireSpacesFor` and `interactionPlaceOf` take that audience instead of the activity's visibility;
- the record's `reply` is `ReplyRef(root = replyTo.root's strong ref or replyTo's own, parent = replyTo's)`.

For a reply to a reply, the root's CID isn't in the reply's view. So `#commentView.root` is a strong ref, not a bare URI, and `parent` likewise. The Kotlin `CommentView` gets `root: StrongRef?` and `parent: StrongRef?`.

For a top-level comment the audience equals the visibility, so the change in placement only matters for replies.

### 6. Client overlays and sent replies

`SentComment` gains `root: AtUri?`. `LocalComments` needs no other change: replies are entries like any comment, lookups return replies, and the delta still sums per activity. `LocalComments.sent(activity)` keeps returning every sent comment, and the presenter splits them by `root`.

The presenter's raw state becomes a list of `ThreadView` pages plus, per thread, the extra reply pages loaded with `root`. What's shown is derived as today: overlays apply to roots and replies alike, and a `Gone` root turns into a placeholder locally if any replies remain. Sent top-level comments go after the threads, and sent replies at the end of their thread when that thread is listed.

### 7. Screen contract and UI

In `:app-screens`:
- `CommentsPaging.items` becomes `ImmutableList<CommentThread>`. `CommentThread` is a presentation type with `uri`, `root: CommentView?`, `replies: ImmutableList<CommentView>`, `sentReplies`, `moreReplies: Int` and `repliesState` (idle, loading or error).
- `ActivityDetailState.Loaded` gains `replyingTo: CommentView?`.
- New events: `ReplyTo(uri)`, `CancelReply` and `LoadMoreReplies(thread)`. `LoadMoreReplies` runs under `SingleFlight`, keyed on the thread.
- `SendComment` sends a reply when `replyingTo` is set, and clears it on success. On a scope refusal it keeps both the text and `replyingTo`.

In the UI, each thread is a run of `LazyColumn` items: the root, or a placeholder row; its indented replies; sent replies; and a "View n more replies" row (or retry). The comment field shows "Replying to @handle" with a cancel control while `replyingTo` is set. `commentActions` adds Reply under the spec's conditions. `LoadMoreNearTheEnd` counts thread items, not comments.

## Risks / Trade-offs

- [A thread page fetches all listed replies of its threads to keep three] → It's bounded by the page's threads, and a window function is the fallback (decision 4).
- [`GROUP BY … HAVING` keyset on H2 versus Postgres] → `CommentPagingTest` runs on H2, and a task runs the same query against the docker stack's Postgres and records its `EXPLAIN`.
- [A reply wider than its parent (not its root) from a dishonest client] → The reach is limited to the root's audience, and it's the replier's own text (decision 3).
- [Older clients see top-level comments only, and count the replies they can't see] → All our clients ship together. The count doesn't break anything for them.
- [The athlete hides a root, and replies stay visible under a placeholder] → This is the chosen behaviour. The athlete can hide replies one by one.

## Migration Plan

1. Deploy the AppView with `V22`. The backfill makes every existing comment a top-level thread, so pages are unchanged until the first reply.
2. Release the clients.

To roll back, first run `ALTER TABLE comments ALTER COLUMN thread_uri DROP NOT NULL` by hand, since the old indexer doesn't set it, and then redeploy the previous AppView. `V22`'s columns stay, and the old code ignores them. Replies written meanwhile show as top-level comments, because the old indexer ignores `reply`. Rolling forward again needs `UPDATE comments SET thread_uri = uri WHERE thread_uri IS NULL` and the `NOT NULL` restored. Replies indexed during the rollback stay top-level until their spaces or repos are re-synced.
