-- segments/segment-pins: operators' pins on a segment's status or name, which beat the derived value. One row per segment and kind
-- ('status' or 'name'). Kept when the segment's record is deleted, so a pin applies again if its URI is indexed again. Operator data, never
-- athletes': no record, space or XRPC method carries it.
CREATE TABLE segment_pins (
    segment_uri VARCHAR(512) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    status VARCHAR(16),
    duplicate_of VARCHAR(512),
    pinned_name VARCHAR(1000),
    proposal_uri VARCHAR(512),
    proposer_did VARCHAR(255),
    prior_status VARCHAR(16),
    pinned_by VARCHAR(255) NOT NULL,
    pinned_at BIGINT NOT NULL,
    note VARCHAR(500),
    PRIMARY KEY (segment_uri, kind)
);

CREATE INDEX segment_pins_duplicate_of_idx ON segment_pins (duplicate_of);
