-- segments/segment-groups: what V63 called a route is a segment group, so "route" stays free for planned routes.
ALTER TABLE segments RENAME COLUMN route_uri TO group_uri;
ALTER INDEX segments_route_uri_idx RENAME TO segments_group_uri_idx;

-- segments/segment-paths: trails and roads the segment importer publishes (feed.path), indexed only from its repo. path_cells indexes the
-- 0.01-degree cells each line piece passes through, as segment_cells does for segments, and path_segments holds which run segments lie on
-- which path (PathOverlap in core/location), whatever their status: queries keep only live ones.
CREATE TABLE paths (
    uri VARCHAR(512) PRIMARY KEY,
    cid VARCHAR(255) NOT NULL,
    author_did VARCHAR(255) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    path_name VARCHAR(1000) NOT NULL,
    lines TEXT NOT NULL,
    length_m BIGINT NOT NULL,
    min_lat_e7 INTEGER NOT NULL,
    max_lat_e7 INTEGER NOT NULL,
    min_lng_e7 INTEGER NOT NULL,
    max_lng_e7 INTEGER NOT NULL,
    data_source VARCHAR(64) NOT NULL,
    license VARCHAR(64) NOT NULL,
    attribution VARCHAR(1000),
    indexed_at BIGINT NOT NULL
);

CREATE TABLE path_cells (
    cell BIGINT NOT NULL,
    path_uri VARCHAR(512) NOT NULL,
    PRIMARY KEY (cell, path_uri)
);
CREATE INDEX path_cells_path_idx ON path_cells (path_uri);

CREATE TABLE path_segments (
    path_uri VARCHAR(512) NOT NULL,
    segment_uri VARCHAR(512) NOT NULL,
    PRIMARY KEY (path_uri, segment_uri)
);
CREATE INDEX path_segments_segment_idx ON path_segments (segment_uri);
