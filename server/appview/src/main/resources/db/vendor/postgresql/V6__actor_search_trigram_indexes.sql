-- PostgreSQL only: DatabaseFactory.migrate loads db/vendor/<database> as well as db/migration, and H2 (the tests) has no trigram indexes. Versions are shared
-- across both folders, so the next migration in either one is V7 (MigrationVersionsTest checks this).
--
-- actor.searchActors matches anywhere in handles and display names (LIKE '%q%'), which a b-tree can't serve. Trigram GIN indexes can, for
-- queries of three or more characters. pg_trgm is a trusted extension (PostgreSQL 13+), so the database owner can create it without being
-- a superuser.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX actors_handle_trgm_idx ON actors USING gin (handle gin_trgm_ops);
-- The search lower-cases display names, and only an index on the same expression serves it.
CREATE INDEX actors_display_name_trgm_idx ON actors USING gin (lower(display_name) gin_trgm_ops);
