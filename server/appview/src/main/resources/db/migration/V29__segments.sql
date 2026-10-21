-- Segments (segments/segment-records) and the efforts matched on them (segments/segment-efforts). A segment (feed.segment) is a public
-- record indexed from the repo only; segment_cells indexes the 0.01-degree cells its line passes through (SegmentCells), so tracks and map
-- boxes find it without PostGIS. segment_track_cells does the same for each usable segment track once matched, so a new segment finds
-- past tracks without fetching every blob. An effort is derived private data: it's served only to viewers admitted to its visibility, the
-- narrowest of its activity's and the activity's map and pace/speed categories. segment_match_jobs queues the matcher's work.
CREATE TABLE segments (
    uri VARCHAR(512) NOT NULL PRIMARY KEY,
    cid VARCHAR(255) NOT NULL,
    author_did VARCHAR(255) NOT NULL,
    segment_name VARCHAR(1000) NOT NULL,
    sport VARCHAR(64) NOT NULL,
    line TEXT NOT NULL,
    record TEXT NOT NULL,
    distance_m BIGINT NOT NULL,
    min_lat_e7 INTEGER NOT NULL,
    max_lat_e7 INTEGER NOT NULL,
    min_lng_e7 INTEGER NOT NULL,
    max_lng_e7 INTEGER NOT NULL,
    data_source VARCHAR(64) NOT NULL,
    license VARCHAR(64) NOT NULL,
    indexed_at BIGINT NOT NULL
);
CREATE INDEX segments_author_idx ON segments (author_did);

CREATE TABLE segment_cells (
    cell BIGINT NOT NULL,
    segment_uri VARCHAR(512) NOT NULL,
    PRIMARY KEY (cell, segment_uri)
);
CREATE INDEX segment_cells_segment_idx ON segment_cells (segment_uri);

CREATE TABLE segment_track_cells (
    cell BIGINT NOT NULL,
    track_uri VARCHAR(512) NOT NULL,
    PRIMARY KEY (cell, track_uri)
);
CREATE INDEX segment_track_cells_track_idx ON segment_track_cells (track_uri);

CREATE TABLE segment_efforts (
    id VARCHAR(1100) NOT NULL PRIMARY KEY,
    segment_uri VARCHAR(512) NOT NULL,
    activity_uri VARCHAR(512) NOT NULL,
    track_uri VARCHAR(512) NOT NULL,
    author_did VARCHAR(255) NOT NULL,
    sport VARCHAR(64) NOT NULL,
    e_bike BOOLEAN NOT NULL,
    start_offset_s BIGINT NOT NULL,
    elapsed_s BIGINT NOT NULL,
    visibility VARCHAR(16) NOT NULL,
    started_on VARCHAR(10) NOT NULL
);
CREATE INDEX segment_efforts_segment_idx ON segment_efforts (segment_uri, author_did, elapsed_s);
CREATE INDEX segment_efforts_activity_idx ON segment_efforts (activity_uri);
CREATE INDEX segment_efforts_author_idx ON segment_efforts (author_did);

CREATE TABLE segment_match_jobs (
    kind VARCHAR(16) NOT NULL,
    subject_uri VARCHAR(512) NOT NULL,
    enqueued_at BIGINT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (kind, subject_uri)
);
CREATE INDEX segment_match_jobs_enqueued_idx ON segment_match_jobs (enqueued_at);
