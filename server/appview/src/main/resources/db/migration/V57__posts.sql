-- Posts in the feed (feed/posts), from authors' repos (public) and their followers and mutuals spaces. For now each shares one of its
-- author's name proposals, named by proposal_uri, and is served only while that proposal is indexed (joined when read). Like activities, a
-- post's narrowest copy is the row and wider ones wait in record_shadows. sort_at is its createdAt capped at index time.
CREATE TABLE posts (
    uri VARCHAR(512) PRIMARY KEY,
    cid VARCHAR(255) NOT NULL,
    author_did VARCHAR(255) NOT NULL,
    record TEXT NOT NULL,
    proposal_uri VARCHAR(512) NOT NULL,
    post_text VARCHAR(10000),
    created_at BIGINT NOT NULL,
    sort_at BIGINT NOT NULL,
    indexed_at BIGINT NOT NULL,
    visibility VARCHAR(16) DEFAULT 'public' NOT NULL,
    origin VARCHAR(512) DEFAULT 'repo' NOT NULL
);
CREATE INDEX posts_author_sort_idx ON posts (author_did, sort_at DESC, uri);
CREATE INDEX posts_proposal_idx ON posts (proposal_uri);
CREATE INDEX posts_origin_idx ON posts (origin);
