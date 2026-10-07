-- The athlete's height (actor.defs#details height), in millimeters: public from the actor.profile record, and mutuals-only or private
-- from actor.putPrivateDetails. It shares weight_visibility with weight_g.
ALTER TABLE actors ADD COLUMN height_mm BIGINT;
ALTER TABLE actor_private_details ADD COLUMN height_mm BIGINT;
