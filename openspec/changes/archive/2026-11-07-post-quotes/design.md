# Design

## Context

See proposal.md for why. Posts today are only proposal shares:
- **Indexing.** `upsertPost` (`index/PostIndex.kt`) accepts a `feed.post` only when its embed is `#proposal` naming the author's proposal under the post's rkey. It stores `posts.proposal_uri NOT NULL`.
- **Reads.** `postRows(viewer)` (`api/PostViews.kt`) is the one predicate every post read uses: the post's tier admits the viewer (`visibleTo`), no label hides it (`unhidden`), and its proposal is live (`listedProposal`). Counts (`postCounts`, `api/PostCounts.kt`), reposts (`api/Reposts.kt`), likes and comments (`Subject.POST`) all build on it.
- **Client writes.** `SegmentNames` writes proposal posts through the outbox by audience (repo, or the `followers` or `mutuals` space). `LocalPosts` lays sent, changed and removed posts over AppView reads until it catches up. `LocalReposts` does the same for repost counts.

The specs fix the behavior (feed/quote-posts, and the deltas to feed/posts and feed/post-page). This design covers how the AppView decides who is shown a quote and its embed, and where the client pieces live.

## Goals / Non-Goals

**Goals:**
- One predicate still answers "may this viewer see this post", for proposal posts and quotes alike, so likes, comments, reposts, counts and lists of quotes follow it with no changes of their own.
- Checking a quote's whole chain is a fixed, indexed query, not recursion at read time.

**Non-Goals:**
- Showing more than one level of a quoted post.
- Checking, when indexed, that a quote's audience is no wider than its quoted post's. The client limits it, and the serving rule makes a wider quote harmless.

## Decisions

### Quotes are rows in `posts`
A quote is a post: it has a tier, a feed position, an author and the same interactions. So it shares the `posts` table rather than getting its own.
- **Migration.** `proposal_uri` becomes nullable. New columns are `quoted_uri` and `quoted_cid`, each nullable. Exactly one of `proposal_uri` and `quoted_uri` is set.
- **Index.** `posts_quoted_sort_idx (quoted_uri, sort_at DESC, uri DESC)` serves quote counts and `getQuotes` pages.
- **Acceptance.** `upsertPost` branches on the embed. A quote needs:
  - non-blank text within the limit;
  - a subject in `app.activitytracker.feed.post`.
- **Alternative considered.** A separate `quote_posts` table. Every timeline, profile and count query would then union two tables. That is worse than one nullable column.

### A chain table: `post_quote_chains(post_uri, ancestor_uri, depth)`
The rule that a viewer who may not see the quoted post isn't shown the quote is transitive. Carol's quote of bob's quote of alice's Followers post must be hidden from someone outside alice's audience. Checking that needs the whole chain.
- **Contents.** The indexer keeps one row per ancestor, up to depth 9. Depth 9 is one past the spec's limit of 8, so an over-long chain is detectable.
- **Upserting quote P of S.** Insert `(P, S, 1)` plus S's own rows shifted by one. Then extend every descendant D that has a row `(D, P, k)` with P's rows shifted by k.
- **Rewriting P.** First drop each descendant's rows deeper than k, then rebuild them.
- **Deleting P.** Drop only P's own rows. A descendant's rows through P stay. P is then not served, which ends any check at P.
- **Out-of-order indexing.** It is handled by the same extension step: the quoted post's arrival extends its waiting descendants.
- **Alternatives considered.**
  - A recursive CTE inside `postRows`. Exposed has no CTE support, the SQL would sit in every post query, and recursive CTEs inside correlated subqueries are not reliably portable to H2.
  - Checking one level only. That leaks the middle quote's text, which is about a post its reader may not see.

### `postRows(viewer)` gains a chain clause
A post is shown when three things hold:
1. Its own tier admits the viewer, and no label hides it.
2. It is either a live proposal post, or a quote whose chain passes.
3. Its chain is no deeper than the limit: no chain row at depth 9.

A chain passes when there is no ancestor A at depth d that meets all three of these:
- A is served to anyone;
- A's own tier or labels don't admit the viewer;
- every ancestor shallower than d is served to anyone.

"Served to anyone" is `postServed(alias)`. It holds when the post is indexed, its account is served, it carries no `!takedown` or `!hide` for everyone, and, for a proposal post, its proposal is live. It doesn't check whether anyone is actually in the post's audience. An empty audience is treated as served, which errs toward hiding the quote.

Every predicate here works on an alias of `posts`, the way `laterTimelineRepost` aliases `reposts`.

### The embed is decided per page, in `postViews`
For the quotes on a page:
- **Step 1.** Read the quoted rows that pass `postRows(viewer)`.
- **Step 2.** Keep those that also pass `postRows(quoter)`. This is one query per distinct quoter on the page.
- **Step 3.** Build the embed from what is left:
  - A kept row becomes `#quotedPostView`: author, text, visibility, createdAt, and its own embed. That embed is either the proposal view, through the existing `proposalEmbed`, or `#quotedPostLink {uri}` when the row is itself a quote.
  - Anything else becomes `#quotedPostUnavailable` and carries nothing.

Because the viewer is only shown a quote whose live quoted post they may see, step 1 drops only quoted posts that are gone for everyone. Step 2 implements "the quoter may no longer see it".

### Counts and the list
- **Counts.** `PostCounts` gains `quotes`: rows where `quoted_uri IN (...)` and `postRows(viewer)`, grouped by `quoted_uri`.
- **List.** `feed.getQuotes(uri, limit, cursor)` first checks the post with `requireVisiblePost`. It then reads `postPage(Posts.quotedUri eq uri and postRows(viewer))` and hydrates through `postViews`.
- **Consistency.** Count and list use the same predicate, so they always agree.

### Lexicon shapes
- `feed.post#quote {subject: strongRef}` joins the embed union.
- `feed.defs#postQuoteEmbedView {quoted: union[#quotedPostView, #quotedPostUnavailable]}` joins the `postView.embed` union. Both unions are open, so older clients skip a quote's embed and still show its text.
- `postView.quoteCount` is optional.
- `feed.getQuotes` returns `{posts: postView[], cursor?}`.
- The Kotlin mirror gets `PostEmbed.Quote` and `PostEmbedView.Quote`, the latter with its own sealed `Quoted` (`Post`, `Unavailable`, `Unknown`), each through an `OpenUnionSerializer`.

### Client
- **Writes.** `QuotePosts` (`core/data/feed`, session-scoped) handles writing and deleting quotes.
  - `quote(post, text, visibility)` checks the space scope, as a like does (`requireSpacesFor`). It enqueues a `putRecord` of the quote at a new TID rkey in the repo or the tier space. It then puts the optimistic view into `LocalPosts`, so it shows first on the timeline and profile. Finally it bumps the quoted post's count through a `LocalQuotes` overlay, modelled on `LocalReposts`.
  - `delete(post)` enqueues the delete in the post's place and calls `LocalPosts.remove`.
  - `FeedRepository` lays `LocalQuotes` over `quoteCount` wherever it lays `LocalReposts`.
- **Quote sheet.** The sheet is a shared sub-state, `QuoteFields`, in `:app-common-api`.
  - It holds a text `TextFieldState`, `VisibilityFields` capped at the quoted post's visibility, and a `posting` flag.
  - Its holder is `rememberQuoting(...)` in `:app-common-presenter`, owned by the presenters that already host `rememberPostEventSink`: feed, post page, profile and quotes list.
  - `PostEvent` gains `Quote(post)` (opens the sheet), `OpenQuoted(uri)` and `Delete(post)`. The holder confirms a delete through an `AppAlertDialog`.
  - `PostCard` in `:app-common-ui` draws the quote button and count, the nested quoted card (`QuotedPost`) or "Post unavailable", and Delete in its menu for the viewer's own quote.
- **Quotes list.** The post page's list is a new screen, `PostQuotesScreen`, in `feed/post`. It is a cursor list of `PostCard`s through `rememberRetainedPaging`. It isn't a sheet like the likers list, because each item is a full card with its own actions.

## Risks / Trade-offs

- **Chain maintenance runs inside indexing writes.** A post with many descendants extends them all when its ancestor arrives late. → The depth cap bounds each chain at 9 rows, so the work is linear in the number of descendants.
- **Making `proposal_uri` nullable on H2 and Postgres.** Postgres takes `ALTER COLUMN ... DROP NOT NULL`, and the H2 version the tests use must accept the same statement. → `:server-appview:test` runs the migration on H2. If H2 rejects it, the migration uses `SET NULL`, the form H2 documents, as a dialect check in its own task.
- **`postServed` ignores whether the audience is empty.** A quote of a post nobody else can see stays hidden rather than showing "Post unavailable". → This errs toward privacy, and the spec only promises "Post unavailable" once the quoted post is gone.
- **Hiding a quote from a viewer who blocked the quoted author also hides the quoter's own words.** → This is consistent with reposts and accepted in the spec.
- **The extra query per distinct quoter on a page.** → Pages are at most 50 posts, and the query is a keyed lookup on `posts.uri`.

## Migration Plan

- **Rollout.** A single Flyway migration, `V60__post_quotes.sql`, makes the column nullable, adds the columns and index, and creates `post_quote_chains`. Existing rows are proposal posts, so no backfill is needed.
- **Clients.** An older client renders a quote's text and skips its unknown embed. Its post card would offer nothing to open the quoted post, which is acceptable.
- **Rollback.** Revert the AppView. Quotes indexed in the meantime stay as rows the old `postRows` drops, because their `proposal_uri` is null and the proposal join fails.
