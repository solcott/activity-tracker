-- PostgreSQL only (see V6): H2 has no partial indexes. Versions are shared across both folders, so the next migration in either is V50.
--
-- Each unfiltered timeline page groups its authors' backfilled rows by batch for import cards (api/ImportCards.kt, cardBatches), which
-- reads only rows with a batch posted after their feed position. This index holds just those, so the grouping never walks an author's
-- other activities, and the timeline's own pages keep using activities_author_batch_idx and the feed position indexes.
CREATE INDEX activities_backfilled_batch_idx ON activities (author_did, import_batch, posted_at)
    WHERE import_batch IS NOT NULL AND sort_at < posted_at;
