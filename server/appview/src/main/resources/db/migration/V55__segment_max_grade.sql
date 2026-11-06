-- Segment maximum grade (segments/segment-elevation). A covered segment's steepest absolute grade over about 50 m of its smoothed terrain,
-- in tenths of a percent; null exactly when terrain_profile is. Nothing is queued here: the start-up terrain backfill samples again every
-- segment inside the tiles without one.
ALTER TABLE segments ADD COLUMN terrain_max_grade INTEGER;
