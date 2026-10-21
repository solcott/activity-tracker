-- Segment leaderboards (segments/segment-leaderboards). Two private choices indexed from the athlete's self space, never served: hiding from
-- every leaderboard (actor.segmentPrefs) and excluding one activity (its feed.segmentTrack). Both, and the per-sport speed caps, are checked
-- when a board is read, so none needs a rematch. The index serves a board's grouping by athlete over one segment and e-bike kind.
ALTER TABLE segment_prefs ADD COLUMN hide_from_leaderboards BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE segment_tracks ADD COLUMN leaderboard_excluded BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX segment_efforts_board_idx ON segment_efforts (segment_uri, e_bike, elapsed_s);
