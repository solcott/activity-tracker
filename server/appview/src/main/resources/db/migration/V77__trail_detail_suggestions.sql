-- Crowd-sourced trail details (segments/trail-detail-suggestions): riders' suggested values from their repos, their votes and
-- confirmations from their interactions spaces, and each segment's adopted community values, kept for the views.
CREATE TABLE trail_detail_suggestions (
    uri VARCHAR(512) PRIMARY KEY,
    cid VARCHAR(255) NOT NULL,
    segment_uri VARCHAR(512) NOT NULL,
    author_did VARCHAR(255) NOT NULL,
    detail VARCHAR(32) NOT NULL,
    detail_value TEXT NOT NULL,
    suggestion_state VARCHAR(16) NOT NULL,
    opened_at BIGINT NOT NULL,
    closed_at BIGINT,
    final_up INTEGER,
    final_down INTEGER,
    record_gone BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX trail_detail_suggestions_segment ON trail_detail_suggestions (segment_uri, detail, suggestion_state);
CREATE INDEX trail_detail_suggestions_author ON trail_detail_suggestions (author_did);
CREATE INDEX trail_detail_suggestions_state ON trail_detail_suggestions (suggestion_state);

CREATE TABLE trail_detail_votes (
    uri VARCHAR(512) PRIMARY KEY,
    voter_did VARCHAR(255) NOT NULL,
    suggestion_uri VARCHAR(512),
    suggestion_cid VARCHAR(255),
    segment_uri VARCHAR(512),
    detail VARCHAR(32),
    detail_value TEXT,
    direction VARCHAR(16) NOT NULL,
    indexed_at BIGINT NOT NULL
);
CREATE INDEX trail_detail_votes_suggestion ON trail_detail_votes (suggestion_uri);
CREATE INDEX trail_detail_votes_segment ON trail_detail_votes (segment_uri, detail);
CREATE INDEX trail_detail_votes_voter ON trail_detail_votes (voter_did);

-- Each detail's latest adopted value still in effect, as JSON, recomputed by the adoption pass; null when none.
ALTER TABLE segments ADD COLUMN trail_community TEXT;
