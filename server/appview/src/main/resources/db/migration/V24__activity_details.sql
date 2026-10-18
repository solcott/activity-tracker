-- Per-category detail visibility. An activity's summary fields in a category narrower than the activity are a feed.activityDetails record
-- in the athlete's tier space for that category, at most one per tier, all under the activity's rkey. So one URI is normally held by
-- several origins at once: rows are keyed by (uri, origin), and each is served at its own tier. Indexed only from tier spaces.
CREATE TABLE activity_details (
    uri VARCHAR(512) NOT NULL,
    origin VARCHAR(512) NOT NULL,
    cid VARCHAR(255) NOT NULL,
    author_did VARCHAR(255) NOT NULL,
    activity_uri VARCHAR(512) NOT NULL,
    visibility VARCHAR(16) NOT NULL,
    record TEXT NOT NULL,
    indexed_at BIGINT NOT NULL,
    PRIMARY KEY (uri, origin)
);
CREATE INDEX activity_details_activity_idx ON activity_details (activity_uri);
CREATE INDEX activity_details_author_idx ON activity_details (author_did);

-- Streams are split the same way, one record per tier, so they're keyed by (uri, origin) too and leave record_shadows. The table is rebuilt
-- because the primary key's constraint name differs between PostgreSQL and H2. Existing rows each have one origin, so they carry over.
CREATE TABLE activity_streams_by_origin (
    uri VARCHAR(512) NOT NULL,
    origin VARCHAR(512) DEFAULT 'repo' NOT NULL,
    cid VARCHAR(255) NOT NULL,
    author_did VARCHAR(255) NOT NULL,
    activity_uri VARCHAR(512) NOT NULL,
    visibility VARCHAR(16) NOT NULL,
    channels TEXT NOT NULL,
    blob_cid VARCHAR(255) NOT NULL,
    record TEXT,
    indexed_at BIGINT NOT NULL,
    PRIMARY KEY (uri, origin)
);
INSERT INTO activity_streams_by_origin (uri, origin, cid, author_did, activity_uri, visibility, channels, blob_cid, record, indexed_at)
    SELECT uri, origin, cid, author_did, activity_uri, visibility, channels, blob_cid, record, indexed_at FROM activity_streams;
-- A wider copy that waited mid-move becomes a row of its own.
INSERT INTO activity_streams_by_origin (uri, origin, cid, author_did, activity_uri, visibility, channels, blob_cid, record, indexed_at)
    SELECT s.uri, s.origin, s.cid, a.author_did, a.activity_uri, s.visibility, a.channels, a.blob_cid, s.record, s.indexed_at
    FROM record_shadows s JOIN activity_streams a ON a.uri = s.uri
    WHERE s.collection = 'app.activitytracker.feed.activityStreams';
DELETE FROM record_shadows WHERE collection = 'app.activitytracker.feed.activityStreams';
DROP TABLE activity_streams;
ALTER TABLE activity_streams_by_origin RENAME TO activity_streams;
CREATE INDEX activity_streams_activity_idx ON activity_streams (activity_uri);
CREATE INDEX activity_streams_author_idx ON activity_streams (author_did);
