-- Every tier check (visibleTo, SpaceAccess, effectiveFollow's callers) asks whether one athlete follows another, and the serve-time like
-- rule asks it per like. The page indexes lead with one DID each, so a lookup ANDed two bitmaps; this makes it one index-only probe.
CREATE INDEX follows_pair_idx ON follows (author_did, subject_did);
