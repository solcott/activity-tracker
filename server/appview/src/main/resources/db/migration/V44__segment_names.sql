-- Community names for segments (segments/segment-names). A segment's current name is derived here: its latest adopted proposal still in
-- effect, else its record's name (the original, kept in segment_name).
ALTER TABLE segments ADD COLUMN current_name VARCHAR(1000);
UPDATE segments SET current_name = segment_name;
ALTER TABLE segments ALTER COLUMN current_name SET NOT NULL;

-- Accepted name proposals, from proposers' repos. An open one deleted by its proposer is withdrawn and its row goes; an adopted one keeps
-- its row with record_gone set, so the name stays without its proposer. final_up and final_down are the totals when it closed.
CREATE TABLE segment_name_proposals (
    uri VARCHAR(512) PRIMARY KEY,
    cid VARCHAR(255) NOT NULL,
    segment_uri VARCHAR(512) NOT NULL,
    proposer_did VARCHAR(255) NOT NULL,
    proposed_name VARCHAR(1000) NOT NULL,
    proposal_state VARCHAR(16) NOT NULL,
    opened_at BIGINT NOT NULL,
    closed_at BIGINT,
    final_up INTEGER,
    final_down INTEGER,
    record_gone BOOLEAN DEFAULT FALSE NOT NULL
);
CREATE INDEX segment_name_proposals_segment_idx ON segment_name_proposals (segment_uri, proposal_state);
CREATE INDEX segment_name_proposals_proposer_idx ON segment_name_proposals (proposer_did);

-- Votes on proposals, from voters' interactions spaces. Each names the proposal version it was cast on; only the latest per voter and
-- proposal counts, and only while it names the proposal's current version.
CREATE TABLE segment_name_votes (
    uri VARCHAR(512) PRIMARY KEY,
    voter_did VARCHAR(255) NOT NULL,
    proposal_uri VARCHAR(512) NOT NULL,
    proposal_cid VARCHAR(255) NOT NULL,
    vote_up BOOLEAN NOT NULL,
    indexed_at BIGINT NOT NULL
);
CREATE INDEX segment_name_votes_proposal_idx ON segment_name_votes (proposal_uri, voter_did);
CREATE INDEX segment_name_votes_voter_idx ON segment_name_votes (voter_did);
