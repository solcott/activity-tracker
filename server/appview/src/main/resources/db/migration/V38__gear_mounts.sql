-- gear.item#mount: the periods each item was mounted on another of its athlete's items (gear/gear-components), from the held copy of the
-- item's record. Mounts the indexer refuses (another athlete's parent, an overlap, a loop, too deep) aren't stored. A mount whose parent
-- isn't indexed yet is kept and counts once it is. mounted_from and mounted_to are epoch milliseconds; mounted_to is null while mounted.
CREATE TABLE gear_mounts (
    component_uri VARCHAR(512) NOT NULL,
    parent_uri VARCHAR(512) NOT NULL,
    mounted_from BIGINT NOT NULL,
    mounted_to BIGINT,
    author_did VARCHAR(255) NOT NULL,
    PRIMARY KEY (component_uri, mounted_from)
);
CREATE INDEX gear_mounts_parent_idx ON gear_mounts (parent_uri);
CREATE INDEX gear_mounts_author_idx ON gear_mounts (author_did);
