-- PostgreSQL only (see V6): feed.searchDiveSiteNames matches anywhere in a site's lower-cased current name (LIKE '%q%'), as segment name
-- search does (V52), so it gets the same trigram GIN index (dive-sites/site-records).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX dive_sites_current_name_trgm_idx ON dive_sites USING gin (lower(current_name) gin_trgm_ops);
