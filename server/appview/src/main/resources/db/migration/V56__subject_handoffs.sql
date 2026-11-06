-- Handoffs (segments/segment-handoffs, dive-sites/site-handoffs): an active segment or dive site the segment importer didn't create, kept
-- when it's removed so the importer can republish it. Only what the record held, which was public: never who rode or dived it. The adopted
-- names stay in segment_name_proposals under old_uri until a successor takes them.
CREATE TABLE subject_handoffs (
    id VARCHAR(64) PRIMARY KEY,
    subject_kind VARCHAR(16) NOT NULL,
    old_uri VARCHAR(512) NOT NULL,
    sport VARCHAR(64),
    segment_kind VARCHAR(16),
    line TEXT,
    lat_e7 INTEGER,
    lng_e7 INTEGER,
    radius_m INTEGER,
    original_name VARCHAR(1000) NOT NULL,
    successor_uri VARCHAR(512),
    staged_at BIGINT NOT NULL
);
CREATE UNIQUE INDEX subject_handoffs_old_uri_idx ON subject_handoffs (old_uri);
CREATE INDEX subject_handoffs_successor_idx ON subject_handoffs (successor_uri);
