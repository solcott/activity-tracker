-- Reposts of posts (feed/reposts), from reposters' repos (reposts of public posts) and their interactions spaces (any other). Like likes,
-- each row names its post by subject_uri and is served only while the reposter may see the post at its current tier. sort_at is the
-- record's createdAt capped at index time, the repost's feed position (feed/timeline-order).
CREATE TABLE reposts (
    uri VARCHAR(512) PRIMARY KEY,
    author_did VARCHAR(255) NOT NULL,
    subject_uri VARCHAR(512) NOT NULL,
    subject_cid VARCHAR(255) NOT NULL,
    created_at BIGINT NOT NULL,
    sort_at BIGINT NOT NULL,
    indexed_at BIGINT NOT NULL,
    origin VARCHAR(512) DEFAULT 'repo' NOT NULL
);
-- Counts, lists, and each post's latest repost in a timeline.
CREATE INDEX reposts_subject_sort_idx ON reposts (subject_uri, sort_at DESC);
-- A timeline's reposts by the viewer and those they follow, by feed position.
CREATE INDEX reposts_author_sort_idx ON reposts (author_did, sort_at DESC, uri DESC);
-- Dropping a deleted interactions space's rows.
CREATE INDEX reposts_origin_idx ON reposts (origin);
