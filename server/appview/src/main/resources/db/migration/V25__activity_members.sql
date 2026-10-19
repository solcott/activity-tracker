-- A combined activity (feed.activity#combined) lists its members, other activities of the same athlete. Each row is one claim, written
-- when the combined record is indexed. Whether a claim counts (the member exists, has the combined activity's author and visibility, isn't
-- itself combined, and this is its earliest claim) is checked when reading, since a member may be indexed after its combined activity.
CREATE TABLE activity_members (
    member_uri VARCHAR(512) NOT NULL,
    combined_uri VARCHAR(512) NOT NULL,
    author_did VARCHAR(255) NOT NULL,
    PRIMARY KEY (member_uri, combined_uri)
);
CREATE INDEX activity_members_combined_idx ON activity_members (combined_uri);
CREATE INDEX activity_members_author_idx ON activity_members (author_did);
