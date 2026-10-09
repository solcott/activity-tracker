-- The photos on each activity (feed.activity#media), so /img/media/{did}/{cid} serves only blobs an indexed activity of that athlete shows.
CREATE TABLE activity_media (
    activity_uri VARCHAR(512) NOT NULL,
    sort_index   INT          NOT NULL,
    author_did   VARCHAR(255) NOT NULL,
    cid          VARCHAR(255) NOT NULL,
    mime_type    VARCHAR(64)  NOT NULL,
    PRIMARY KEY (activity_uri, sort_index)
);
CREATE INDEX activity_media_author_cid_idx ON activity_media (author_did, cid);
