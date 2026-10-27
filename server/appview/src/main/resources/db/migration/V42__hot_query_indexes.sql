-- Indexes for lookups that scanned whole tables: dropping a track's efforts, finding an activity's segment track (every activity, details
-- or streams write asks), finding which streams rows still name a blob, and listing or dropping what one origin (a space) holds.
CREATE INDEX segment_efforts_track_idx ON segment_efforts (track_uri);
CREATE INDEX segment_tracks_activity_idx ON segment_tracks (activity_uri);
CREATE INDEX activity_streams_blob_idx ON activity_streams (blob_cid);
CREATE INDEX activities_origin_idx ON activities (origin);
CREATE INDEX likes_origin_idx ON likes (origin);
CREATE INDEX comments_origin_idx ON comments (origin);
