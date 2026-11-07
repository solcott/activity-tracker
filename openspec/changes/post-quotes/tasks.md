# Tasks

## 1. Lexicons and the Kotlin mirror

- [x] 1.1 Update the lexicon JSON:
  - `feed/post.json`: add `#quote {subject: strongRef}` to the embed union, and update the record description, since a post may now share a proposal or quote a post.
  - `feed/defs.json`: add `#postQuoteEmbedView {quoted: union[#quotedPostView, #quotedPostUnavailable]}` to `postView.embed`, `#quotedPostView` (uri, cid, author, text, visibility, createdAt, and an embed union of `#postProposalEmbedView` and `#quotedPostLink {uri}`), `#quotedPostUnavailable {uri}`, and `postView.quoteCount` (default 0).
  - A new `feed/getQuotes.json`, shaped like `getAuthorPosts` with `uri` in place of `actor`, with error `NotFound`.

  Verify by reading the JSON and running `lexicon-auditor` after 1.2.
- [x] 1.2 Mirror 1.1 in `core/lexicon`:
  - `PostEmbed.Quote`, `PostEmbedView.Quote` and its sealed `Quoted` (`Post`, `Unavailable`, `Unknown`, through `OpenUnionSerializer`), and the quoted post's own embed (`Proposal`, `Link`, `Unknown`);
  - `PostView.quoteCount`;
  - `Nsid.FEED_GET_QUOTES` and `GetQuotes` in `Queries.kt`.

  Extend `PostSerializationTest` to check four things:
  - a quote record round-trips;
  - each quoted-embed member round-trips;
  - an unknown quoted member decodes to `Unknown` and re-encodes unchanged;
  - a `PostView` without `quoteCount` decodes with zero quotes.

  Verify with `./gradlew :core-lexicon:jvmTest`.

## 2. AppView: indexing quotes and their chains

- [x] 2.1 Add `V60__post_quotes.sql`:
  - make `posts.proposal_uri` nullable;
  - add `quoted_uri` and `quoted_cid`;
  - add `posts_quoted_sort_idx (quoted_uri, sort_at DESC, uri DESC)`;
  - add `post_quote_chains (post_uri, ancestor_uri, depth)`, with primary key `(post_uri, depth)` and an index on `(ancestor_uri)`.

  Mirror the changes in `Tables.kt` (`Posts`, `PostQuoteChains`). Verify that the migration runs on H2 in `./gradlew :server-appview:test`. If H2 rejects `DROP NOT NULL`, use the H2-and-Postgres form it accepts (Design, Risks).
- [x] 2.2 Branch `upsertPost` on the embed (Design: quotes are rows in `posts`):
  - a quote needs non-blank text within the limit and a `feed.post` subject;
  - an unknown embed still throws.

  Maintain `post_quote_chains` on upsert, rewrite and delete (Design: chain table), up to depth 9, and drop a post's chain rows in `deleteActor`. Add indexer tests for:
  - a quote from the repo, and one from a `followers` space;
  - quotes with blank text, missing text or an activity subject being ignored;
  - a three-quote chain getting rows at depths 1 to 3;
  - an ancestor indexed after its descendants extending their chains;
  - a rewrite that changes the subject rebuilding descendants' rows;
  - a delete dropping only the post's own rows.

  Verify with `./gradlew :server-appview:test`.

## 3. AppView: serving quotes

- [x] 3.1 Add `postServed(alias)` and the chain clause to `postRows(viewer)`, with the depth-9 cutoff (Design: `postRows` gains a chain clause). Keep proposal posts' rule unchanged. Add `QuotePostsTest`, covering:
  - carol, outside alice's Followers audience, isn't shown bob's quote in her timeline, `getPost`, `getAuthorPosts` or `getQuotes`, and its likes, comments and reposts aren't counted or listed for her;
  - narrowing alice's post after the quote hides the quote from the newly excluded viewers;
  - a block between the viewer and alice hides the quote;
  - a quote of a quote is hidden from a viewer outside the first post's audience;
  - a quote nine levels deep is served to no one;
  - a quoted post that is deleted, taken down or whose proposal is withdrawn leaves the quote served.

  Verify with `./gradlew :server-appview:test`.
- [x] 3.2 Build `#postQuoteEmbedView` in `postViews` (Design: the embed is decided per page). Show a quoted proposal through `proposalEmbed`, and a quoted quote as `#quotedPostLink`. Fall back to `#quotedPostUnavailable` when the quoted post is gone, or when the quoter may no longer see it (alice blocks bob, or bob unfollows alice). Extend `QuotePostsTest` to cover each case, and to check that an unavailable embed carries only the URI. Verify with `./gradlew :server-appview:test`.
- [x] 3.3 Give `PostCounts` quote counts, add `Views.quotes` and the `getQuotes` route through `requireVisiblePost`, and fill `quoteCount` in every `postView`. Extend `QuotePostsTest` to check:
  - `quoteCount` equals the `getQuotes` list across pages, in `getPost`, `getAuthorPosts` and timeline items, including the author's own quotes;
  - `getQuotes` is `NotFound` for viewers outside the audience and for a non-post subject;
  - paging returns each quote once.

  Verify with `./gradlew :server-appview:test`.
- [x] 3.4 Update `AGENTS.md`'s architecture paragraph and `.agents/rules/appview.md` for quotes:
  - quotes as posts;
  - the chain table;
  - the serving rule that a quote never widens its quoted post.

  Verify with `scripts/check-agent-sync.sh`.

## 4. Client data: writing, deleting and reading quotes

- [x] 4.1 Add `QuotePosts` in `core/data/feed`, holding:
  - `quote(post, text, visibility)`, which checks the space scope, enqueues the quote at a new TID rkey in the repo or the tier space, and puts its view into `LocalPosts`;
  - `delete(post)`, which enqueues the delete where the post is stored and calls `LocalPosts.remove`.

  Add `LocalQuotes` (modelled on `LocalReposts`) for the quoted post's count, laid over `quoteCount` wherever `FeedRepository` lays `LocalReposts`. Add `FeedRepository.quotes(uri, cursor)`, putting the viewer's unlisted quotes of that post first. Add `core-data` jvm tests covering:
  - a Public quote written to the repo, and a Followers one to the `followers` space;
  - a refusal up front without the space scope, with nothing changed;
  - the quote first on the timeline and profile, and the count one higher, at once and through a refresh;
  - a delete removing it at once and deleting the record where it was written;
  - `quotes(uri)` mapping `NotFound`.

  Verify with `./gradlew :core-data:jvmTest`.

## 5. Quote sheet, card and post page

- [x] 5.1 Add `QuoteFields` to `:app-common-api` and `rememberQuoting` to `:app-common-presenter` (Design: client). The sheet has a text `TextFieldState`, `VisibilityFields` capped at the quoted post's visibility and starting there, Post offered only for non-blank text, and the sending state from `rememberSingleFlight`. Add `PostEvent.Quote`, `OpenQuoted` and `Delete` to `rememberPostEventSink`, with Delete confirmed first. Host the sheet in the feed, profile and post page presenters. Add presenter tests in each host to check:
  - Quote opens the sheet with the right audiences;
  - a double Post writes once;
  - Delete asks, then removes.

  Verify with `./gradlew :feature-feed-presenter:jvmTest :feature-profile-presenter:jvmTest :feature-post-presenter:jvmTest`.
- [x] 5.2 In `PostCard` (`:app-common-ui`):
  - draw the quote button and count;
  - draw the quoted post as a nested card (`QuotedPost`) that opens it, a quote-of-a-quote link, or "Post unavailable";
  - add Delete to the menu on the viewer's own quote only.

  Add the quote sheet UI. Add fixtures to `:app-fixtures`: a quote of a proposal post, a quote of a quote, an unavailable quote, and the open sheet. Add UI tests in `PostsUiTest` and `PostPageUiTest` that check Quote opens the sheet, the quoted post opens it, "Post unavailable" opens nothing, and Delete appears only on one's own quote. Regenerate screenshots with `updateDebugScreenshotTestDefaultTestSuite` and look at them. Verify with the UI tests and `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite`.
- [x] 5.3 Add `PostQuotesScreen` to `feature/post`, across its api, presenter and ui modules:
  - a cursor list of `PostCard`s over `FeedRepository.quotes` through `rememberRetainedPaging`, with an empty state;
  - opened by tapping the post page's quote count;
  - hosting the post event sink and quote sheet like the other hosts.

  Add its samples, a presenter test (loads, pages, `NotFound`, the viewer's new quote first), a UI test, screenshots, and a `ContentLoadingConformanceTest` entry. Verify with those tests and `./gradlew checkModuleBoundaries`.
- [x] 5.4 Add the strings to `:ui-resources`:
  - "Quote";
  - the quote count (plural);
  - the sheet's title and Post button;
  - "Post unavailable";
  - the quoted-quote link label;
  - "Delete quote" and its confirmation;
  - the quotes screen title and empty state.

  Verify with `./gradlew composeStabilityCheck`.

## 6. Integration

- [x] 6.1 Run `./gradlew format`, then `./gradlew check`, and confirm `BUILD SUCCESSFUL`.
- [x] 6.2 Run end to end on the local stack (`scripts/dev.sh`). With alice sharing a proposal with Followers, check that:
  - bob, her follower, quotes it for Followers;
  - carol, who follows both, sees the quote with alice's proposal embedded, and the post's quote count is 1;
  - dave, who follows bob but not alice, isn't shown the quote, and gets `NotFound` from `getQuotes`;
  - after alice deletes her post, carol sees bob's quote with "Post unavailable";
  - bob deleting the quote removes it;
  - `EXPLAIN` for carol's timeline page on Postgres uses `posts_quoted_sort_idx` and the chain table's index;
  - the run's records are deleted afterwards.

## Workflow follow-up

- Archive the change once it's reviewed, syncing `feed/quote-posts`, `feed/posts` and `feed/post-page` into the main specs.
