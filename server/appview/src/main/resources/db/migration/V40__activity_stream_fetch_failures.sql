-- feed/detail-visibility: stream data the AppView couldn't fetch, so later views don't each fetch it again. Keyed by where it was fetched
-- from, since a missing or unreachable blob depends on its host. `content` is data that isn't valid stream data or is too large, never
-- fetched again under its CID (retry_at null); `unavailable` is fetched again from retry_at (epoch milliseconds) on.
CREATE TABLE activity_stream_fetch_failures (
    blob_cid VARCHAR(255) NOT NULL,
    author_did VARCHAR(255) NOT NULL,
    origin VARCHAR(512) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    failures INT NOT NULL,
    retry_at BIGINT,
    PRIMARY KEY (blob_cid, author_did, origin)
);
