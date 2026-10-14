-- Follow requests. An athlete whose actor.profile has followPolicy "approve" (the only value stored; null means open) counts a follow only
-- once they publish a graph.followApproval for the follower, at rkey = the follower's DID. Declines live in the athlete's self space and
-- never reach the AppView.
ALTER TABLE actors ADD COLUMN follow_policy VARCHAR(16);

CREATE TABLE follow_approvals (
    uri         VARCHAR(512) PRIMARY KEY,
    author_did  VARCHAR(255) NOT NULL,
    subject_did VARCHAR(255) NOT NULL,
    created_at  BIGINT       NOT NULL,
    indexed_at  BIGINT       NOT NULL
);

-- The effective-follow check looks up (approver, follower) for each follow.
CREATE INDEX follow_approvals_pair_idx ON follow_approvals (author_did, subject_did);
