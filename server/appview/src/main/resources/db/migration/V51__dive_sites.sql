-- Dive sites (dive-sites/site-records): public feed.diveSite records from creators' repos, with a status derived here and never read
-- from the record, and the 0.01 degree cells their radius touches.
CREATE TABLE dive_sites (
    uri VARCHAR(512) PRIMARY KEY,
    cid VARCHAR(255) NOT NULL,
    author_did VARCHAR(255) NOT NULL,
    site_name VARCHAR(1000) NOT NULL,
    current_name VARCHAR(1000) NOT NULL,
    record TEXT NOT NULL,
    lat_e7 INTEGER NOT NULL,
    lng_e7 INTEGER NOT NULL,
    radius_m INTEGER NOT NULL,
    data_source VARCHAR(64) NOT NULL,
    license VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    duplicate_of VARCHAR(512),
    first_indexed_at BIGINT NOT NULL,
    indexed_at BIGINT NOT NULL
);
CREATE INDEX dive_sites_author_idx ON dive_sites (author_did);
CREATE INDEX dive_sites_status_idx ON dive_sites (status);
CREATE INDEX dive_sites_box_idx ON dive_sites (lat_e7, lng_e7);

CREATE TABLE dive_site_cells (
    cell BIGINT NOT NULL,
    site_uri VARCHAR(512) NOT NULL,
    PRIMARY KEY (cell, site_uri)
);
CREATE INDEX dive_site_cells_site_idx ON dive_site_cells (site_uri);

-- New sites per creator, for the daily limit; deleting a site frees nothing.
CREATE TABLE dive_site_creations (
    author_did VARCHAR(255) NOT NULL,
    site_uri VARCHAR(512) NOT NULL,
    created_at BIGINT NOT NULL
);
CREATE INDEX dive_site_creations_author_idx ON dive_site_creations (author_did, created_at);

-- A dive's visit to a site (dive-sites/site-visits), matched from its usable segment track: derived private data, served only to viewers
-- admitted to visibility (the narrowest of the activity's and its Map category's). The dive's conditions are copied here with the tier of
-- its Dive category, so conditions are a filtered aggregate over this table.
CREATE TABLE dive_site_visits (
    activity_uri VARCHAR(512) PRIMARY KEY,
    site_uri VARCHAR(512) NOT NULL,
    track_uri VARCHAR(512) NOT NULL,
    author_did VARCHAR(255) NOT NULL,
    visibility VARCHAR(16) NOT NULL,
    started_on VARCHAR(10) NOT NULL,
    min_temperature BIGINT,
    visibility_cm BIGINT,
    max_depth_cm BIGINT,
    dive_tier VARCHAR(16) NOT NULL
);
CREATE INDEX dive_site_visits_site_idx ON dive_site_visits (site_uri, started_on);
CREATE INDEX dive_site_visits_author_idx ON dive_site_visits (author_did);
CREATE INDEX dive_site_visits_track_idx ON dive_site_visits (track_uri);

-- Dives now visit sites, so every dive's segment track is matched again once sites exist.
INSERT INTO segment_match_jobs (kind, subject_uri, enqueued_at, attempts)
SELECT 'track', st.uri, 0, 0 FROM segment_tracks st
JOIN activities a ON a.uri = st.activity_uri
WHERE a.sport = 'dive'
  AND NOT EXISTS (SELECT 1 FROM segment_match_jobs j WHERE j.kind = 'track' AND j.subject_uri = st.uri);
