-- Keyset pagination for getFollowers / getFollows: (did, indexed_at DESC, uri DESC). Each composite index starts with the column of the
-- single-column V1 index it replaces, so those are dropped.
CREATE INDEX follows_subject_page_idx ON follows (subject_did, indexed_at, uri);
CREATE INDEX follows_author_page_idx ON follows (author_did, indexed_at, uri);
DROP INDEX follows_subject_idx;
DROP INDEX follows_author_idx;
