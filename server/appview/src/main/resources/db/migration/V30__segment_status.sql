-- Segment statuses (segments/segment-records), derived by the AppView and never read from the record: a segment starts proposed, becomes
-- active once enough distinct athletes have efforts on it, duplicate when it duplicates an earlier proposed or active segment of its sport
-- group (duplicate_of names that one), and archived when still proposed long after it was first indexed. first_indexed_at is set once,
-- so an edit never changes which of two segments came first; indexed_at keeps meaning the last write.
ALTER TABLE segments ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'proposed';
ALTER TABLE segments ADD COLUMN duplicate_of VARCHAR(512);
ALTER TABLE segments ADD COLUMN first_indexed_at BIGINT NOT NULL DEFAULT 0;
UPDATE segments SET first_indexed_at = indexed_at;
CREATE INDEX segments_status_idx ON segments (status, sport);
CREATE INDEX segments_duplicate_of_idx ON segments (duplicate_of);
CREATE INDEX segments_author_first_indexed_idx ON segments (author_did, first_indexed_at);
