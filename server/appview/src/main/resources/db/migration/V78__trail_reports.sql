-- Trail reports (segments/trail-reports): riders' public condition tags, notes and photos on segments, from their repos.
CREATE TABLE trail_reports (
    uri VARCHAR(512) PRIMARY KEY,
    cid VARCHAR(255) NOT NULL,
    author_did VARCHAR(255) NOT NULL,
    segment_uri VARCHAR(512) NOT NULL,
    note TEXT,
    has_conditions BOOLEAN NOT NULL,
    indexed_at BIGINT NOT NULL,
    day_utc VARCHAR(10) NOT NULL
);
CREATE INDEX trail_reports_segment ON trail_reports (segment_uri, indexed_at);
CREATE INDEX trail_reports_author ON trail_reports (author_did, segment_uri, day_utc);

-- Each report's known condition tags.
CREATE TABLE trail_report_tags (
    report_uri VARCHAR(512) NOT NULL,
    tag VARCHAR(32) NOT NULL,
    PRIMARY KEY (report_uri, tag)
);

-- Each report's photos in order, which the media redirect serves while the report and its author are.
CREATE TABLE trail_report_blobs (
    report_uri VARCHAR(512) NOT NULL,
    sort_index INTEGER NOT NULL,
    author_did VARCHAR(255) NOT NULL,
    cid VARCHAR(255) NOT NULL,
    mime_type VARCHAR(64) NOT NULL,
    alt TEXT,
    width INTEGER,
    height INTEGER,
    PRIMARY KEY (report_uri, sort_index)
);
CREATE INDEX trail_report_blobs_blob ON trail_report_blobs (author_did, cid);
