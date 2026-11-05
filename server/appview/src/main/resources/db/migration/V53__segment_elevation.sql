-- Segment elevation (segments/segment-elevation). A segment's gain and loss in meters from terrain tiles along its line, null when the tiles
-- don't cover it or it hasn't been sampled, and from its efforts: the median across athletes of each athlete's median, with how many
-- athletes that took. An effort's own gain and loss, from its track's altitude, are never served.
ALTER TABLE segments ADD COLUMN terrain_gain_m BIGINT;
ALTER TABLE segments ADD COLUMN terrain_loss_m BIGINT;
ALTER TABLE segments ADD COLUMN effort_gain_m BIGINT;
ALTER TABLE segments ADD COLUMN effort_loss_m BIGINT;
ALTER TABLE segments ADD COLUMN effort_athletes INT DEFAULT 0 NOT NULL;
ALTER TABLE segment_efforts ADD COLUMN elevation_gain_m BIGINT;
ALTER TABLE segment_efforts ADD COLUMN elevation_loss_m BIGINT;

-- Efforts now record their track's elevation, so every segment track is matched again.
INSERT INTO segment_match_jobs (kind, subject_uri, enqueued_at, attempts)
SELECT 'track', st.uri, 0, 0 FROM segment_tracks st
WHERE NOT EXISTS (SELECT 1 FROM segment_match_jobs j WHERE j.kind = 'track' AND j.subject_uri = st.uri);
