-- The set hash of each synced space's authority repo (spaces/appview-syncer): the 2048-byte LtHash state as of repo_rev, checked against
-- each verified commit's data, so a cleared repo is resynced. A row without one is backfilled. not_found_since starts a SpaceNotFound
-- streak, epoch millis; the space is dropped only once it has lasted a day.
ALTER TABLE space_sync ADD COLUMN set_hash BYTEA;
ALTER TABLE space_sync ADD COLUMN not_found_since BIGINT;
