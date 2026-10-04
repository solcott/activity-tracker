-- Kudos on the author's own activity are no longer indexed; drop any indexed before that rule.
DELETE FROM kudos WHERE subject_uri LIKE 'at://' || author_did || '/%';
