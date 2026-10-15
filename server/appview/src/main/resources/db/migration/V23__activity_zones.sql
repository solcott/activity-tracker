-- An activity's zone snapshots, a record of their own (feed.activityZones) since they can be narrower than its streams: they're stored at the
-- narrower of the charts' and the training's visibility, and served at the tier where they're stored. Placed by origin like activities and
-- streams, so mid-move the wider copy waits in record_shadows.
CREATE TABLE activity_zones (
    uri VARCHAR(512) PRIMARY KEY,
    cid VARCHAR(255) NOT NULL,
    author_did VARCHAR(255) NOT NULL,
    activity_uri VARCHAR(512) NOT NULL,
    visibility VARCHAR(16) NOT NULL,
    origin VARCHAR(512) NOT NULL,
    heart_rate_zones TEXT,
    power_zones TEXT,
    record TEXT NOT NULL,
    indexed_at BIGINT NOT NULL
);
CREATE INDEX activity_zones_activity_idx ON activity_zones (activity_uri);
CREATE INDEX activity_zones_author_idx ON activity_zones (author_did);

-- Zones inline in a streams record are no longer served; nothing outside local development had any.
ALTER TABLE activity_streams DROP COLUMN heart_rate_zones;
ALTER TABLE activity_streams DROP COLUMN power_zones;
