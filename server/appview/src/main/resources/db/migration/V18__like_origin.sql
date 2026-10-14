-- Likes on non-public activities live in the giver's app.activitytracker.space.athlete interactions space, which the AppView syncs. Like
-- activities, a like is indexed under its repo-style URI whichever it came from, and origin is where the row came from: 'repo', or the
-- space URI. Only that origin's delete removes the row, and dropping a space removes only its likes.
ALTER TABLE likes ADD COLUMN origin VARCHAR(512) DEFAULT 'repo' NOT NULL;
