-- segments/segment-paths: the sport groups a path is open to (feed.path#sports), comma-separated; NULL for any, as a road. A segment of
-- another group never lies on it.
ALTER TABLE paths ADD COLUMN sports VARCHAR(255);
