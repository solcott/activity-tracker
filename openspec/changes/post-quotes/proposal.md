# Proposal

## Why

A repost passes a proposal on but can't add anything, such as "Yes, and the lower half should be its own segment." Quote posts let an athlete add their own words around someone else's post. These are the first posts an athlete writes themselves, so they're kept tied to an existing post, with no blank composer. This is the last of four post changes, after `feed-posts`, `post-interactions` and `post-reposts`.

## What Changes

- **`feed.post`'s embed union gains `#quote`,** a strongRef to another `feed.post`. Only posts can be quoted. Activities can't be quoted in this change.
- **A quote's text is required.** The AppView ignores a quote with no text after trimming.
- **A quote's audience can be no wider than the quoted post's.** The client offers only audiences no wider than the post as the quoter's view showed it.
- **A quote never widens the quoted post's audience.** While the quoted post is served to anyone, a viewer who may not see it isn't shown the quote at all, as with reposts. Once the quoted post is served to no one (deleted, taken down, or its proposal withdrawn), or the quoter may no longer see it, the quote shows with "Post unavailable" in place of the embed.
- **Quoting is offered only from a post card or post page,** as Quote next to Repost, on any post the viewer can see, their own included. There is still no free-standing composer.
- **An athlete deletes their own quote from its menu,** on the card or the post page. A quote's audience is fixed once written.
- `postView`'s embed view gains a quote member. It shows the quoted post one level deep, so a quote of a quote shows a link in place of the third post.
- `postView` gains `quoteCount`, shown with the other counts. A new query, `feed.getQuotes`, lists a post's quotes the viewer is shown, and the post page lists them.
- Quotes take likes, comments and reposts like any post. Blocks, mutes, labels and reports apply to them.

Out of scope: free-standing posts, quoting activities, and notifications.

## Capabilities

### New Capabilities
- `feed/quote-posts`: the quote embed, its required text, its audience limit, how unavailable quoted posts show, and where Quote is offered.

### Modified Capabilities
- `feed/posts`: the embed union gains quotes, the AppView accepts quote posts as well as proposal posts, clients write posts by quoting as well as sharing, the card gains Quote and the quote count, and the view gains `quoteCount`.
- `feed/post-page`: a quote is served on its own without a proposal, and the page lists quotes.

## Impact

- **Lexicons**: `feed/post.json` (the `#quote` member), `feed/defs.json` (`#postQuoteEmbedView`, `quoteCount`), and a new `feed/getQuotes.json`.
- **AppView**: post acceptance branches by embed kind, a quoted-post URI column with an index, quote hydration one level deep under the viewer's access, and quote counts.
- **Client**: a quote sheet (text plus an audience picker limited by the quoted post), the card's Quote action, Delete on one's own quote, the quoted-post embed in `PostCard`, fixtures, and screenshots.
