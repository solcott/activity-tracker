-- Kudos are now likes (app.activitytracker.feed.like). Every row here is an old feed.kudos record, which the AppView no longer
-- indexes or deletes, so they're dropped rather than left as counts that can never go down.
ALTER TABLE kudos RENAME TO likes;
ALTER INDEX kudos_subject_idx RENAME TO likes_subject_idx;
ALTER INDEX kudos_author_subject_idx RENAME TO likes_author_subject_idx;
DELETE FROM likes;
