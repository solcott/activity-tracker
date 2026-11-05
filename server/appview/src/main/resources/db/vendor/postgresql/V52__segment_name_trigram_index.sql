-- PostgreSQL only (see V6): feed.searchSegmentNames matches anywhere in a segment's lower-cased current name (LIKE '%q%'), which a b-tree
-- can't serve. A trigram GIN index on the same expression can, for queries of three or more characters (segments/segment-records).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX segments_current_name_trgm_idx ON segments USING gin (lower(current_name) gin_trgm_ops);
