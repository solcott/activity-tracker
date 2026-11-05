-- Segment elevation profile (segments/segment-elevation). A covered segment's smoothed terrain heights, evenly spaced from its start to its
-- end, in decimeters, comma-separated; null when terrain doesn't cover it or it hasn't been sampled since this change.
ALTER TABLE segments ADD COLUMN terrain_profile TEXT;

-- Terrain is now smoothed before gain and loss are counted, and covered segments get a profile, so every segment is sampled again.
INSERT INTO segment_match_jobs (kind, subject_uri, enqueued_at, attempts)
SELECT 'elevation', s.uri, 0, 0 FROM segments s
WHERE NOT EXISTS (SELECT 1 FROM segment_match_jobs j WHERE j.kind = 'elevation' AND j.subject_uri = s.uri);
