-- Candidate segments the segment importer stages before publishing them (segments/segment-candidates). A candidate is a public line, such
-- as an OpenStreetMap trail, held only here: no feed query serves it, it gets no efforts and duplicates no segment. A region's candidates
-- are replaced by import run (revision). segment_candidate_cells indexes the cells its line passes through, as segment_cells does for
-- segments. segment_candidate_riders is derived private data, like efforts: one row per usable segment track that passes a candidate from
-- start to end, dropped with the track. Only ridden (enough distinct riders, then never undone) is revealed, to the importer.
CREATE TABLE segment_candidates (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    region VARCHAR(64) NOT NULL,
    revision VARCHAR(64) NOT NULL,
    sport VARCHAR(64) NOT NULL,
    candidate_name VARCHAR(1000) NOT NULL,
    line TEXT NOT NULL,
    distance_m BIGINT NOT NULL,
    min_lat_e7 INTEGER NOT NULL,
    max_lat_e7 INTEGER NOT NULL,
    min_lng_e7 INTEGER NOT NULL,
    max_lng_e7 INTEGER NOT NULL,
    data_source VARCHAR(64) NOT NULL,
    license VARCHAR(64) NOT NULL,
    ridden BOOLEAN NOT NULL DEFAULT FALSE,
    staged_at BIGINT NOT NULL
);
CREATE INDEX segment_candidates_revision_idx ON segment_candidates (region, revision);
CREATE INDEX segment_candidates_ridden_idx ON segment_candidates (region, ridden, id);

CREATE TABLE segment_candidate_cells (
    cell BIGINT NOT NULL,
    candidate_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (cell, candidate_id)
);
CREATE INDEX segment_candidate_cells_candidate_idx ON segment_candidate_cells (candidate_id);

CREATE TABLE segment_candidate_riders (
    candidate_id VARCHAR(64) NOT NULL,
    track_uri VARCHAR(512) NOT NULL,
    author_did VARCHAR(255) NOT NULL,
    PRIMARY KEY (candidate_id, track_uri)
);
CREATE INDEX segment_candidate_riders_track_idx ON segment_candidate_riders (track_uri);
CREATE INDEX segment_candidate_riders_author_idx ON segment_candidate_riders (author_did);
