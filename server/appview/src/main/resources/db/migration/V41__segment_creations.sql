-- Each new or re-lined segment a creator had indexed, by when: the daily limit counts these rather than live segments, so deleting a
-- segment (or changing its line) doesn't free a slot. Rows older than the 24-hour window are pruned as new ones arrive.
CREATE TABLE segment_creations (
    author_did VARCHAR(255) NOT NULL,
    segment_uri VARCHAR(512) NOT NULL,
    created_at BIGINT NOT NULL
);
CREATE INDEX segment_creations_author_idx ON segment_creations (author_did, created_at);

INSERT INTO segment_creations (author_did, segment_uri, created_at)
SELECT author_did, uri, first_indexed_at FROM segments;
