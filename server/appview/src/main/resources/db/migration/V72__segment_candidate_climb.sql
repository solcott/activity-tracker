-- segments/segment-candidates: whether a staged candidate's line climbs or descends as its trail does (importer.defs#candidate.climb,
-- 'uphill' or 'downhill'), handed back by checks so publish writes it on the segment (segments/segment-direction). Null for none.
ALTER TABLE segment_candidates ADD COLUMN climb VARCHAR(16);
