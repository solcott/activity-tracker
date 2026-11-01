-- The timeline's feed position (feed/timeline-order): posted_at is when the activity was posted, capped at when it was indexed (sort_at
-- until now), ended_at its start plus its elapsed time from its own record, and sort_at the earlier of posted_at and 5 days after ended_at.
-- import_batch is its feed.activity#importBatch when that is a TID (feed/import-cards). Epoch millis.
ALTER TABLE activities ADD COLUMN posted_at BIGINT;
ALTER TABLE activities ADD COLUMN ended_at BIGINT;
ALTER TABLE activities ADD COLUMN import_batch VARCHAR(13);

UPDATE activities SET posted_at = sort_at, ended_at = started_at + COALESCE(elapsed_time, 0) * 1000;
UPDATE activities SET sort_at = LEAST(posted_at, ended_at + 432000000);

ALTER TABLE activities ALTER COLUMN posted_at SET NOT NULL;
ALTER TABLE activities ALTER COLUMN ended_at SET NOT NULL;

-- A timeline's import cards group an author's backfilled activities by batch.
CREATE INDEX activities_author_batch_idx ON activities (author_did, import_batch);
