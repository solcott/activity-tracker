-- Group activities (feed/group-activities). An athlete's grouping choice (actor.groupPrefs), indexed from their self space only; the
-- leave mark on each segment track (feed.segmentTrack#groupExcluded); and the pairs of two athletes' activities done together, matched
-- from groupable segment tracks. A pair row holds each side's partner tier: the narrowest of its activity's visibility and its Start time
-- and Map categories'. Nothing here is served but the pairs, and those only to viewers their tiers admit.
CREATE TABLE group_prefs (
    did VARCHAR(255) NOT NULL PRIMARY KEY,
    mode VARCHAR(16) NOT NULL,
    updated_at BIGINT NOT NULL
);
ALTER TABLE segment_tracks ADD COLUMN group_excluded BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE activity_group_pairs (
    activity_a VARCHAR(512) NOT NULL,
    activity_b VARCHAR(512) NOT NULL,
    author_a VARCHAR(255) NOT NULL,
    author_b VARCHAR(255) NOT NULL,
    visibility_a VARCHAR(16) NOT NULL,
    visibility_b VARCHAR(16) NOT NULL,
    matched_at BIGINT NOT NULL,
    PRIMARY KEY (activity_a, activity_b)
);
CREATE INDEX activity_group_pairs_b_idx ON activity_group_pairs (activity_b);
CREATE INDEX activity_group_pairs_author_a_idx ON activity_group_pairs (author_a);
CREATE INDEX activity_group_pairs_author_b_idx ON activity_group_pairs (author_b);
