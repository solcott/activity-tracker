-- segments/segment-candidates: whether a staged candidate's sport group may travel its line only this way (importer.defs#candidate.oneWay),
-- handed back by checks so publish writes it on the segment (segments/segment-direction).
ALTER TABLE segment_candidates ADD COLUMN one_way BOOLEAN NOT NULL DEFAULT FALSE;
