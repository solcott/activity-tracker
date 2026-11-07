-- The timeline pages posts by feed position across the viewer and everyone they follow (feed/posts), newest first on (sort_at, uri):
-- posts_author_sort_idx leads with the author, so it can't serve that order.
CREATE INDEX posts_sort_idx ON posts (sort_at DESC, uri DESC);
