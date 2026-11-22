-- segments/path-profile: each covered line piece of a path, sampled against the terrain tiles as a segment's line is, and whether every
-- piece was covered when last sampled.
CREATE TABLE path_terrain (
    path_uri VARCHAR(512) NOT NULL,
    piece INTEGER NOT NULL,
    profile_dm TEXT NOT NULL,
    length_m BIGINT NOT NULL,
    gain_m BIGINT NOT NULL,
    loss_m BIGINT NOT NULL,
    max_grade INTEGER NOT NULL,
    PRIMARY KEY (path_uri, piece)
);
ALTER TABLE paths ADD COLUMN terrain_complete BOOLEAN NOT NULL DEFAULT FALSE;
