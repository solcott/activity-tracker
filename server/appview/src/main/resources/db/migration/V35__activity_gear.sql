-- Athletes' gear items (gear.item), from repos and tier spaces, placed by origin like activities: the narrower copy of an item mid-move
-- is the row, the other waits in record_shadows. Served only on activities that list them.
CREATE TABLE gear_items (
    uri         VARCHAR(512) NOT NULL,
    origin      VARCHAR(512) NOT NULL,
    cid         VARCHAR(255) NOT NULL,
    author_did  VARCHAR(255) NOT NULL,
    visibility  VARCHAR(16)  NOT NULL,
    item_name   VARCHAR(1000) NOT NULL,
    item_type   VARCHAR(64)  NOT NULL,
    item_kind   VARCHAR(64),
    brand       VARCHAR(1280),
    model       VARCHAR(1280),
    photo_cid   VARCHAR(255),
    photo_mime  VARCHAR(64),
    retired_at  BIGINT,
    record      TEXT         NOT NULL,
    indexed_at  BIGINT       NOT NULL,
    PRIMARY KEY (uri)
);
CREATE INDEX gear_items_author_idx ON gear_items (author_did);
CREATE INDEX gear_items_author_photo_idx ON gear_items (author_did, photo_cid);

-- The gear each activity lists (feed.activity#gear, and feed.activityDetails#gear when the gear category is narrower): one row per
-- reference, by the origin of the record listing it and which record that is (listed_in: record or details), served at visibility, that
-- record's tier.
CREATE TABLE activity_gear (
    activity_uri VARCHAR(512) NOT NULL,
    origin       VARCHAR(512) NOT NULL,
    listed_in    VARCHAR(16)  NOT NULL,
    gear_uri     VARCHAR(512) NOT NULL,
    sort_index   INT          NOT NULL,
    author_did   VARCHAR(255) NOT NULL,
    visibility   VARCHAR(16)  NOT NULL,
    PRIMARY KEY (activity_uri, origin, listed_in, gear_uri)
);
CREATE INDEX activity_gear_gear_idx ON activity_gear (gear_uri);
CREATE INDEX activity_gear_author_idx ON activity_gear (author_did);
