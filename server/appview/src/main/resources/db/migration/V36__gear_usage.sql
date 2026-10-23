-- Gear usage (gear.getGear, getActorGear) totals these in SQL: each activity's core summary fields, copied out of its record, and its
-- local start date. started_on is the epoch day of started_at shifted by the record's utcOffset (UTC without one), started_year that
-- day's year. V37 (ActivitySummaryBackfill) fills them for rows indexed before this.
ALTER TABLE activities ADD COLUMN elapsed_time BIGINT;
ALTER TABLE activities ADD COLUMN moving_time BIGINT;
ALTER TABLE activities ADD COLUMN distance BIGINT;
ALTER TABLE activities ADD COLUMN elevation_gain BIGINT;
ALTER TABLE activities ADD COLUMN elevation_loss BIGINT;
ALTER TABLE activities ADD COLUMN dive_bottom_time BIGINT;
ALTER TABLE activities ADD COLUMN started_on BIGINT;
ALTER TABLE activities ADD COLUMN started_year INT;

-- gear.item#startingDistance (meters) and startingTime (seconds): usage from before the athlete tracked the item.
ALTER TABLE gear_items ADD COLUMN starting_distance BIGINT;
ALTER TABLE gear_items ADD COLUMN starting_time BIGINT;
