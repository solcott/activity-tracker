-- Activity visibility. A public activity is a record in the athlete's repo (from Jetstream); a followers, mutuals or self one is a record in
-- their app.activitytracker.space.athlete space for that tier, which the AppView syncs as a member. Either way it's indexed under its
-- repo-style URI, so a move between tiers keeps its URI. visibility is the tier it's served at; origin is where the row came from: 'repo',
-- or the space URI. Only that origin's delete removes the row.
ALTER TABLE activities ADD COLUMN visibility VARCHAR(16) DEFAULT 'public' NOT NULL;
ALTER TABLE activities ADD COLUMN origin VARCHAR(512) DEFAULT 'repo' NOT NULL;
ALTER TABLE activity_streams ADD COLUMN origin VARCHAR(512) DEFAULT 'repo' NOT NULL;
-- The raw record, so a streams row can step aside for a narrower copy mid-move (see record_shadows). Null on rows indexed before this.
ALTER TABLE activity_streams ADD COLUMN record TEXT;

-- An author's activities filtered by tier, newest first.
CREATE INDEX activities_author_visibility_sort_idx ON activities (author_did, visibility, sort_at);

-- Mid-move, one URI is briefly held by two origins: the narrower tier's copy is the row in activities or activity_streams, and the wider
-- one waits here, so it's promoted back if the narrower copy is deleted first.
CREATE TABLE record_shadows (
    uri        VARCHAR(512) NOT NULL,
    origin     VARCHAR(512) NOT NULL,
    collection VARCHAR(255) NOT NULL,
    visibility VARCHAR(16)  NOT NULL,
    cid        VARCHAR(255) NOT NULL,
    record     TEXT         NOT NULL,
    indexed_at BIGINT       NOT NULL,
    PRIMARY KEY (uri, origin)
);

-- The tier spaces the AppView syncs. Only the authority writes to an athlete's space, so one repo revision per space is enough.
CREATE TABLE space_sync (
    space_uri        VARCHAR(512) PRIMARY KEY,
    authority_did    VARCHAR(255) NOT NULL,
    -- The authority repo's last indexed revision in the space; null until the first backfill finishes.
    repo_rev         VARCHAR(32),
    -- When the notifyWrite registration expires; null when not registered.
    registered_until BIGINT,
    -- Consecutive failed syncs, for backoff.
    failures         INTEGER      DEFAULT 0 NOT NULL,
    -- Earliest time of the next sync attempt.
    next_attempt_at  BIGINT       NOT NULL,
    created_at       BIGINT       NOT NULL
);
CREATE INDEX space_sync_authority_idx ON space_sync (authority_did);
CREATE INDEX space_sync_next_attempt_idx ON space_sync (next_attempt_at);
