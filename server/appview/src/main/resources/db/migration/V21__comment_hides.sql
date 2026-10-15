-- An athlete's hides of comments on their own activities (app.activitytracker.feed.commentHide), from their repo or their interactions
-- space. origin is where the row came from ('repo', or the space URI), so the same hide in both places is two rows and a delete from one
-- leaves the other. Whether the author is the activity's athlete is checked when serving, so a hide can be indexed before its comment.
CREATE TABLE comment_hides (
    uri         VARCHAR(512) NOT NULL,
    origin      VARCHAR(512) NOT NULL,
    author_did  VARCHAR(255) NOT NULL,
    comment_uri VARCHAR(512) NOT NULL,
    created_at  BIGINT       NOT NULL,
    indexed_at  BIGINT       NOT NULL,
    PRIMARY KEY (uri, origin)
);
CREATE INDEX comment_hides_comment_idx ON comment_hides (comment_uri, author_did);
