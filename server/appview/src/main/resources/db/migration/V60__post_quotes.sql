-- Quote posts (feed/quote-posts): a post that quotes another post instead of sharing a proposal. Exactly one of proposal_uri and
-- quoted_uri is set. quoted_uri/quoted_cid name the quoted post (a strong ref); posts_quoted_sort_idx serves quote counts and
-- feed.getQuotes pages.
ALTER TABLE posts ALTER COLUMN proposal_uri DROP NOT NULL;
ALTER TABLE posts ADD COLUMN quoted_uri VARCHAR(512);
ALTER TABLE posts ADD COLUMN quoted_cid VARCHAR(255);
CREATE INDEX posts_quoted_sort_idx ON posts (quoted_uri, sort_at DESC, uri DESC);
-- Each quote's chain: one row per post it quotes directly (depth 1) or through other quotes (depth 2 and on), up to depth 9, one past
-- the deepest chain served, so a quote too far from its first post is detectable. Kept by the indexer as quotes arrive, change and go
-- (index/PostIndex.kt), so serving checks a whole chain with one indexed lookup.
CREATE TABLE post_quote_chains (
    post_uri VARCHAR(512) NOT NULL,
    ancestor_uri VARCHAR(512) NOT NULL,
    chain_depth INT NOT NULL,
    PRIMARY KEY (post_uri, chain_depth)
);
-- Extending and rebuilding a post's descendants' chains when it arrives or changes.
CREATE INDEX post_quote_chains_ancestor_idx ON post_quote_chains (ancestor_uri);
