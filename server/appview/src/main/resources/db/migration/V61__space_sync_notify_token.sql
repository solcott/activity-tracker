-- The Nov 12 Spaces alpha registers for notifications inside authorizeSpaceSync, authenticated by an opaque token the syncer issues per
-- space instead of service auth. Null until the space's next sync creates one; kept stable after that.
ALTER TABLE space_sync ADD COLUMN notify_token VARCHAR(64);
