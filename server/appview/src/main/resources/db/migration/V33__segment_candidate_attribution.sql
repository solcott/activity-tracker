-- Who to credit for a staged candidate's line, such as a public agency (segments/segment-candidates); null when it names nobody.
ALTER TABLE segment_candidates ADD COLUMN attribution VARCHAR(1000) NULL;
