-- Efforts are now timed from start line to end line, leaving out a stop or a recording gap short of the start or past the end
-- (segments/segment-efforts), so every segment track is matched again. The matcher skips tracks that are no longer usable.
INSERT INTO segment_match_jobs (kind, subject_uri, enqueued_at, attempts)
SELECT 'track', st.uri, 0, 0 FROM segment_tracks st
WHERE NOT EXISTS (SELECT 1 FROM segment_match_jobs j WHERE j.kind = 'track' AND j.subject_uri = st.uri);
