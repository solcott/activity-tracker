-- Segment matching (segments/segment-track). An athlete's setting (actor.segmentPrefs) and their segment tracks (feed.segmentTrack), each
-- a copy of an activity's track trimmed by their privacy zones, indexed from their self space only. A segment track's points stay in its
-- blob, which is fetched only while the athlete's setting says matching is on; turning it off deletes their rows here. Nothing is served.
CREATE TABLE segment_prefs (
    did VARCHAR(255) NOT NULL PRIMARY KEY,
    matching BOOLEAN NOT NULL,
    updated_at BIGINT NOT NULL
);
CREATE TABLE segment_tracks (
    uri VARCHAR(512) NOT NULL PRIMARY KEY,
    author_did VARCHAR(255) NOT NULL,
    activity_uri VARCHAR(512) NOT NULL,
    data_cid VARCHAR(255) NOT NULL,
    zones_revision VARCHAR(32),
    indexed_at BIGINT NOT NULL
);
CREATE INDEX segment_tracks_author_idx ON segment_tracks (author_did);
