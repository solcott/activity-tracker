-- getAuthorActivitiesByStart reads an athlete's activities by when they happened (an import's duplicates), not when they were posted.
CREATE INDEX activities_author_started_idx ON activities (author_did, started_at);
