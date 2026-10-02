-- Kept to portable SQL so tests can run on H2 (MODE=PostgreSQL). Timestamps are epoch millis.

CREATE TABLE actors (
    did          VARCHAR(255) PRIMARY KEY,
    handle       VARCHAR(255) NOT NULL,
    display_name TEXT,
    description  TEXT,
    units        VARCHAR(16),
    indexed_at   BIGINT       NOT NULL
);
CREATE INDEX actors_handle_idx ON actors (handle);

CREATE TABLE activities (
    uri        VARCHAR(512) PRIMARY KEY,
    cid        VARCHAR(255) NOT NULL,
    author_did VARCHAR(255) NOT NULL,
    record     TEXT         NOT NULL,
    sport      VARCHAR(64)  NOT NULL,
    started_at BIGINT       NOT NULL,
    -- min(record.createdAt, indexed_at): ordering key that clients can't push into the future.
    sort_at    BIGINT       NOT NULL,
    indexed_at BIGINT       NOT NULL
);
CREATE INDEX activities_author_sort_idx ON activities (author_did, sort_at);
CREATE INDEX activities_sort_idx ON activities (sort_at);

CREATE TABLE kudos (
    uri         VARCHAR(512) PRIMARY KEY,
    author_did  VARCHAR(255) NOT NULL,
    subject_uri VARCHAR(512) NOT NULL,
    subject_cid VARCHAR(255) NOT NULL,
    created_at  BIGINT       NOT NULL,
    indexed_at  BIGINT       NOT NULL
);
CREATE INDEX kudos_subject_idx ON kudos (subject_uri);
CREATE INDEX kudos_author_subject_idx ON kudos (author_did, subject_uri);

CREATE TABLE comments (
    uri         VARCHAR(512) PRIMARY KEY,
    cid         VARCHAR(255) NOT NULL,
    author_did  VARCHAR(255) NOT NULL,
    subject_uri VARCHAR(512) NOT NULL,
    text        TEXT         NOT NULL,
    created_at  BIGINT       NOT NULL,
    indexed_at  BIGINT       NOT NULL
);
CREATE INDEX comments_subject_idx ON comments (subject_uri, created_at);

CREATE TABLE follows (
    uri         VARCHAR(512) PRIMARY KEY,
    author_did  VARCHAR(255) NOT NULL,
    subject_did VARCHAR(255) NOT NULL,
    created_at  BIGINT       NOT NULL,
    indexed_at  BIGINT       NOT NULL
);
CREATE INDEX follows_author_idx ON follows (author_did);
CREATE INDEX follows_subject_idx ON follows (subject_did);

CREATE TABLE jetstream_cursor (
    id      INT PRIMARY KEY,
    time_us BIGINT NOT NULL
);
