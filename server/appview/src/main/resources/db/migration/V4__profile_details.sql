-- Public profile details and the avatar, indexed from the actor.profile record.
ALTER TABLE actors ADD COLUMN avatar_cid VARCHAR(255);
ALTER TABLE actors ADD COLUMN avatar_mime VARCHAR(64);
ALTER TABLE actors ADD COLUMN locality TEXT;
ALTER TABLE actors ADD COLUMN region TEXT;
ALTER TABLE actors ADD COLUMN country VARCHAR(2);
ALTER TABLE actors ADD COLUMN birth_date VARCHAR(10);
ALTER TABLE actors ADD COLUMN gender TEXT;
ALTER TABLE actors ADD COLUMN weight_g BIGINT;

-- Details an athlete made mutuals-only or private. Repo records are public, so these never come from Jetstream: clients write them with
-- actor.putPrivateDetails, and views show each one only to the viewers its visibility allows.
CREATE TABLE actor_private_details (
    did VARCHAR(255) PRIMARY KEY,
    locality TEXT,
    region TEXT,
    country VARCHAR(2),
    birth_date VARCHAR(10),
    gender TEXT,
    weight_g BIGINT,
    location_visibility VARCHAR(16),
    birth_date_visibility VARCHAR(16),
    gender_visibility VARCHAR(16),
    weight_visibility VARCHAR(16),
    updated_at BIGINT NOT NULL
);
