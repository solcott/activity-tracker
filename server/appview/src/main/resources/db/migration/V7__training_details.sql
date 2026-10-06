-- The athlete's training thresholds and zone models (actor.defs#training), stored as its JSON. Nothing filters or sorts on them, so one
-- column each: public ones indexed from the actor.profile record, and mutuals-only or private ones written by actor.putPrivateDetails.
ALTER TABLE actors ADD COLUMN training TEXT;
ALTER TABLE actor_private_details ADD COLUMN training TEXT;
ALTER TABLE actor_private_details ADD COLUMN training_visibility VARCHAR(16);
