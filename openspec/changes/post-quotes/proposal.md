# Proposal

## Why

A repost passes a proposal on but can't add anything, such as "Yes, and the lower half should be its own segment." Quote posts let an athlete add their own words around someone else's post. These are the first posts an athlete writes themselves, so they're kept tied to an existing post, with no blank composer. This is the last of four post changes, after `feed-posts`, `post-interactions` and `post-reposts`.

## What Changes

- **`feed.post`'s embed union gains `#quote`,** a strongRef to another `feed.post`. Only posts can be quoted. Activities can't be quoted in this change.
- **A quote's text is required.** The AppView ignores a quote with no text after trimming.
- **A quote's audience can be no wider than the quoted post's.** The client offers only audiences no wider than the post as the quoter's view showed it. The AppView serves a quote only to viewers who may see both the quote and the quoted post. Otherwise the quote shows with the embed replaced by "Post unavailable", and if the quoted post is deleted it shows the same.
- **Quoting is offered only from a post card or post page,** as Quote next to Repost. There is still no free-standing composer.
- `postView`'s embed view gains a quote member. It shows the quoted post one level deep, so a quote of a quote shows a link in place of the third post.
- `postView` gains `quoteCount`. The post page lists quotes the viewer may see.
- Quotes take likes, comments and reposts like any post. Blocks, mutes, labels and reports apply to them.

Out of scope: free-standing posts, quoting activities, and notifications.

## Capabilities

### New Capabilities
- `feed/quote-posts`: the quote embed, its required text, its audience limit, how unavailable quoted posts show, and where Quote is offered.

### Modified Capabilities
- `feed/posts`: the embed union gains quotes, the AppView accepts quote posts as well as proposal posts, and the view gains `quoteCount`.
- `feed/post-page`: lists quotes.

## Impact

- **Lexicons**: `feed/post.json` (the `#quote` member), and `feed/defs.json` (`#postQuoteEmbedView`, `quoteCount`).
- **AppView**: post acceptance branches by embed kind, a quoted-post URI column with an index, quote hydration one level deep under the viewer's access, and quote counts.
- **Client**: a quote sheet (text plus an audience picker limited by the quoted post), the card's Quote action, the quoted-post embed in `PostCard`, fixtures, and screenshots.
