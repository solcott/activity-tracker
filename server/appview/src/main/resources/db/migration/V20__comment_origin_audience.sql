-- Comments on non-public activities live in the commenter's app.activitytracker.space.athlete interactions space, which the AppView syncs.
-- As for likes, origin is where the row came from ('repo', or the space URI): only that origin's delete removes it, and dropping a space
-- removes only its comments. audience is who the comment was written for, a visibility normalised at index time ('public' when the record
-- has none); a comment is never shown wider than it, even after its activity is widened.
ALTER TABLE comments ADD COLUMN origin VARCHAR(512) DEFAULT 'repo' NOT NULL;
ALTER TABLE comments ADD COLUMN audience VARCHAR(16) DEFAULT 'public' NOT NULL;
