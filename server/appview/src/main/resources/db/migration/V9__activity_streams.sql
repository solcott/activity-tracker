-- Activity streams records. TODO(spaces): they're public repo records for now, so visibility is enforced only in getActivityStreams; when
-- streams move to a private atproto Space, blob_cid and the cache below go (see BACKLOG.md).
CREATE TABLE activity_streams (
    uri VARCHAR(512) PRIMARY KEY,
    cid VARCHAR(255) NOT NULL,
    author_did VARCHAR(255) NOT NULL,
    activity_uri VARCHAR(512) NOT NULL,
    visibility VARCHAR(16) NOT NULL,
    channels TEXT NOT NULL,
    heart_rate_zones TEXT,
    power_zones TEXT,
    blob_cid VARCHAR(255) NOT NULL,
    indexed_at BIGINT NOT NULL
);
CREATE INDEX activity_streams_activity_idx ON activity_streams (activity_uri);
CREATE INDEX activity_streams_author_idx ON activity_streams (author_did);

-- The decoded stream data, fetched from the author's PDS the first time someone may see it. Keyed by blob CID, which names its content.
CREATE TABLE activity_stream_data (
    blob_cid VARCHAR(255) PRIMARY KEY,
    stream_data TEXT NOT NULL,
    fetched_at BIGINT NOT NULL
);
