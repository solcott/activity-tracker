-- Segments and candidates gain a kind (segments/segment-lifts): run, every segment so far, or lift, an alpine ski lift.
ALTER TABLE segments ADD COLUMN kind VARCHAR(16) DEFAULT 'run' NOT NULL;
ALTER TABLE segment_candidates ADD COLUMN kind VARCHAR(16) DEFAULT 'run' NOT NULL;

-- Resort ski and snowboard days now match alpine segments (segments/segment-records), so every segment track is matched again. The
-- matcher skips tracks that are no longer usable, and those that match nothing new keep the efforts they had.
INSERT INTO segment_match_jobs (kind, subject_uri, enqueued_at, attempts)
SELECT 'track', st.uri, 0, 0 FROM segment_tracks st
WHERE NOT EXISTS (SELECT 1 FROM segment_match_jobs j WHERE j.kind = 'track' AND j.subject_uri = st.uri);
