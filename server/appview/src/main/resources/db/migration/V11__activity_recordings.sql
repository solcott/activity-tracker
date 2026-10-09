-- An activity can be another device's recording of one of the athlete's own activities (feed.activity#recordingOf). Feeds show only the
-- activity it records, and that activity's view lists its recordings. Only same-author links are stored; the rest index as plain activities.
ALTER TABLE activities ADD COLUMN recording_of VARCHAR(512);
CREATE INDEX activities_recording_of_idx ON activities (recording_of);
