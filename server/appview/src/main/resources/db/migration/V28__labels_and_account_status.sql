-- Labels from the trusted labelers (the app's own and Bluesky's), as the AppView applies them: the newest by cts per source, subject and
-- value. A negation deletes the row. uri is the subject: an at:// record URI, or an account's DID. cid pins a record label to one version
-- (null for any). expires_at is the label's exp: it stops applying (null for never); expired rows are skipped when read and swept.
CREATE TABLE labels (
    src VARCHAR(255) NOT NULL,
    uri VARCHAR(512) NOT NULL,
    val VARCHAR(128) NOT NULL,
    cid VARCHAR(255),
    cts BIGINT NOT NULL,
    expires_at BIGINT,
    indexed_at BIGINT NOT NULL,
    PRIMARY KEY (src, uri, val)
);
CREATE INDEX labels_subject_idx ON labels (uri, val);

-- Where each labeler's subscribeLabels stream was last read (its seq), to resume from.
CREATE TABLE label_cursors (
    src VARCHAR(255) NOT NULL,
    seq BIGINT NOT NULL,
    PRIMARY KEY (src)
);

-- Every moderator view of a non-public subject: who, which subject, the Ozone subject status it relied on, and when. Never the content.
CREATE TABLE moderation_access (
    moderator_did VARCHAR(255) NOT NULL,
    subject_uri VARCHAR(512) NOT NULL,
    report_ref VARCHAR(255) NOT NULL,
    accessed_at BIGINT NOT NULL
);
CREATE INDEX moderation_access_at_idx ON moderation_access (accessed_at);
CREATE INDEX moderation_access_subject_idx ON moderation_access (subject_uri, accessed_at);

-- The account's hosting status from Jetstream #account events while it isn't active (takendown, suspended, deactivated); null while it is.
ALTER TABLE actors ADD COLUMN status VARCHAR(32);
CREATE INDEX actors_status_idx ON actors (did, status);
