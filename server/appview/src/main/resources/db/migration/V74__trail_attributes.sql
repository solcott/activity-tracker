-- segments/trail-attributes: what a staged candidate's and a path's source data says about the trail (feed.defs#trailAttributes), as
-- AtJson-encoded text cleaned by TrailAttributesSanitizer. Null when nothing is known. Segments keep theirs in their record.
ALTER TABLE segment_candidates ADD COLUMN trail TEXT;
ALTER TABLE paths ADD COLUMN trail TEXT;
