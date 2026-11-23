-- segments/segment-mayors: when each effort started (epoch ms, its activity's start plus its offset), which orders equal Mayors counts
-- reached on one day. Never served. Efforts whose activity row is gone keep their offset alone, so they sort first and never tie.
ALTER TABLE segment_efforts ADD COLUMN started_at BIGINT NOT NULL DEFAULT 0;
UPDATE segment_efforts SET started_at =
    COALESCE((SELECT a.started_at FROM activities a WHERE a.uri = segment_efforts.activity_uri), 0) + start_offset_s * 1000;
