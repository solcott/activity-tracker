-- Activity invites (feed/activity-invites). An athlete's invites of followers to their own activities (feed.activityInvite), indexed
-- from their self space only; each activity's copyOf link (feed.activity#copyOf), untrusted; and the pairs of an invite's activity with
-- the invitee's first copy of it. Pairs from invites are kept apart from matched pairs (activity_group_pairs), so nothing that drops
-- matched pairs touches them. A pair row holds each side's partner tier, as activity_group_pairs does. Invites are served only to the
-- inviter and the invitee, and pairs only to viewers their tiers admit.
CREATE TABLE activity_invites (
    inviter_did VARCHAR(255) NOT NULL,
    rkey VARCHAR(512) NOT NULL,
    activity_uri VARCHAR(512) NOT NULL,
    invitee_did VARCHAR(255) NOT NULL,
    created_at BIGINT NOT NULL,
    indexed_at BIGINT NOT NULL,
    PRIMARY KEY (inviter_did, rkey)
);
CREATE INDEX activity_invites_invitee_idx ON activity_invites (invitee_did);
CREATE INDEX activity_invites_activity_idx ON activity_invites (activity_uri);
ALTER TABLE activities ADD COLUMN copy_of VARCHAR(512);
CREATE INDEX activities_copy_of_idx ON activities (copy_of);
CREATE TABLE activity_invite_pairs (
    original_uri VARCHAR(512) NOT NULL,
    copy_uri VARCHAR(512) NOT NULL,
    inviter_did VARCHAR(255) NOT NULL,
    invitee_did VARCHAR(255) NOT NULL,
    invite_rkey VARCHAR(512) NOT NULL,
    original_visibility VARCHAR(16) NOT NULL,
    copy_visibility VARCHAR(16) NOT NULL,
    paired_at BIGINT NOT NULL,
    PRIMARY KEY (original_uri, copy_uri)
);
CREATE UNIQUE INDEX activity_invite_pairs_invite_idx ON activity_invite_pairs (inviter_did, invite_rkey);
CREATE INDEX activity_invite_pairs_copy_idx ON activity_invite_pairs (copy_uri);
CREATE INDEX activity_invite_pairs_inviter_idx ON activity_invite_pairs (inviter_did);
CREATE INDEX activity_invite_pairs_invitee_idx ON activity_invite_pairs (invitee_did);
