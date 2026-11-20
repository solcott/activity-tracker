-- dev/worker-telemetry: why background work failed, for operators. Error text is ErrorSummary's, at most 500 characters and never record
-- content.

-- Segment jobs that gave up after their retries, instead of being deleted. Cleared by a later success of the same job, the account's
-- deletion, or after 30 days.
CREATE TABLE segment_job_failures (
    kind VARCHAR(16) NOT NULL,
    subject_uri VARCHAR(512) NOT NULL,
    attempts INTEGER NOT NULL,
    last_error VARCHAR(500) NOT NULL,
    first_failed_at BIGINT NOT NULL,
    failed_at BIGINT NOT NULL,
    PRIMARY KEY (kind, subject_uri)
);
CREATE INDEX segment_job_failures_failed_at ON segment_job_failures (failed_at);

-- A job still retrying: its last error and when its current run of failures began.
ALTER TABLE segment_match_jobs ADD COLUMN last_error VARCHAR(500);
ALTER TABLE segment_match_jobs ADD COLUMN first_failed_at BIGINT;

-- spaces/appview-syncer: a failing space's last error, cleared on a successful sync.
ALTER TABLE space_sync ADD COLUMN last_error VARCHAR(500);
ALTER TABLE space_sync ADD COLUMN last_error_at BIGINT;

-- feed/detail-visibility: why a remembered stream fetch failed.
ALTER TABLE activity_stream_fetch_failures ADD COLUMN last_error VARCHAR(500);
ALTER TABLE activity_stream_fetch_failures ADD COLUMN last_failed_at BIGINT;

-- graph/blocks: actors whose Bluesky block backfill failed, backing off persistently instead of in memory.
CREATE TABLE bsky_block_backfill_failures (
    did VARCHAR(255) PRIMARY KEY,
    failures INTEGER NOT NULL,
    last_error VARCHAR(500) NOT NULL,
    next_attempt_at BIGINT NOT NULL
);
CREATE INDEX bsky_block_backfill_failures_next ON bsky_block_backfill_failures (next_attempt_at);
