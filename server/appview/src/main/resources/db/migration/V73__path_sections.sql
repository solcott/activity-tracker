-- segments/segment-paths: the sections of a trail path's family (feed.path#sections) and the importer's segments named after each, by
-- segment URI. section_order is the section's place in the record; a segment is in the first section naming it, so it appears once a path.
-- Replaced whenever the path is indexed again, and dropped with it.
CREATE TABLE path_section_segments (
    path_uri VARCHAR(512) NOT NULL,
    segment_uri VARCHAR(512) NOT NULL,
    section_name VARCHAR(1000) NOT NULL,
    section_order INT NOT NULL,
    PRIMARY KEY (path_uri, segment_uri)
);
