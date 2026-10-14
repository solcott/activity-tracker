-- When each tier space last synced. The PDS may compact a space's op log, so a space that hasn't synced for a while is backfilled again
-- rather than pulled from its last revision, whose following ops may be gone. Null until the next sync, which then backfills.
ALTER TABLE space_sync ADD COLUMN synced_at BIGINT;
