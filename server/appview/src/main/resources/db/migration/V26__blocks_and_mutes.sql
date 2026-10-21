-- Blocks between accounts, from two kinds of record: an athlete's app.activitytracker.graph.block in their interactions space (origin is
-- that space's URI) and their app.bsky.graph.block in their repo (origin 'repo'). A pair is blocked while any row names it in either
-- direction, whatever its origin, so deleting one kind keeps the other.
CREATE TABLE blocks (
    uri VARCHAR(512) NOT NULL,
    origin VARCHAR(512) NOT NULL,
    author_did VARCHAR(255) NOT NULL,
    subject_did VARCHAR(255) NOT NULL,
    created_at BIGINT NOT NULL,
    indexed_at BIGINT NOT NULL,
    PRIMARY KEY (uri)
);
CREATE INDEX blocks_pair_idx ON blocks (author_did, subject_did);
CREATE INDEX blocks_subject_idx ON blocks (subject_did, author_did);

-- An athlete's app.activitytracker.graph.mute records, from their interactions space only.
CREATE TABLE mutes (
    uri VARCHAR(512) NOT NULL,
    origin VARCHAR(512) NOT NULL,
    author_did VARCHAR(255) NOT NULL,
    subject_did VARCHAR(255) NOT NULL,
    created_at BIGINT NOT NULL,
    indexed_at BIGINT NOT NULL,
    PRIMARY KEY (uri)
);
CREATE INDEX mutes_pair_idx ON mutes (author_did, subject_did);

-- When the athlete's existing app.bsky.graph.block records were last fetched from their repo; null until the first backfill, which
-- covers athletes indexed before blocks existed.
ALTER TABLE actors ADD COLUMN bsky_blocks_synced_at BIGINT;
