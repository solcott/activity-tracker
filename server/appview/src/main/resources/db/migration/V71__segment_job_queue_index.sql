-- admin/job-queues: the admin site's list of retrying segment jobs, oldest failure first. A full rematch can make the queue hundreds of
-- thousands of rows, of which only a few are failing. The version is renumbered to the next free one if a sibling change lands first.
CREATE INDEX segment_match_jobs_failing ON segment_match_jobs (first_failed_at);
