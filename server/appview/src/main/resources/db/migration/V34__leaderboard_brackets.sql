-- Leaderboard brackets (segments/leaderboard-brackets). An athlete's age group, weight class and gender bucket, computed on their device
-- and indexed from their self space (actor.leaderboardBrackets) only while they've chosen these boards. Never the details themselves, and
-- never served: used only to filter leaderboards. A bracket the AppView doesn't know is NULL, as if absent.
CREATE TABLE leaderboard_brackets (
    did VARCHAR(255) NOT NULL PRIMARY KEY,
    age_group VARCHAR(16),
    weight_class VARCHAR(16),
    gender VARCHAR(16),
    updated_at BIGINT NOT NULL
);
