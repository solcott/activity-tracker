-- Replies (feed.comment#reply). Threads are one level deep: thread_uri is the top-level comment of a reply's thread (reply.root), and a
-- comment's own uri when it's top-level, so a top-level comment is a row where thread_uri = uri. parent_uri is the comment a reply answers.
-- thread_cid and parent_cid complete their strong refs, which views serve so a client can reply to a reply; all three are null on a
-- top-level comment. Every existing comment becomes a top-level thread.
ALTER TABLE comments ADD COLUMN thread_uri VARCHAR(512);
UPDATE comments SET thread_uri = uri;
ALTER TABLE comments ALTER COLUMN thread_uri SET NOT NULL;
ALTER TABLE comments ADD COLUMN thread_cid VARCHAR(255);
ALTER TABLE comments ADD COLUMN parent_uri VARCHAR(512);
ALTER TABLE comments ADD COLUMN parent_cid VARCHAR(255);
CREATE INDEX comments_thread_idx ON comments (subject_uri, thread_uri, created_at);
