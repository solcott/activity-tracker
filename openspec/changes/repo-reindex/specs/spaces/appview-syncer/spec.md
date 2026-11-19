# Spec Delta

## ADDED Requirements

### Requirement: Forced resync of an account's spaces
On an operator's request, the AppView SHALL make every space it syncs for an account due at once and backfill each in full, dropping indexed records its verified listing no longer holds, as a backfill after an hour without syncing does. It SHALL also start syncing each of the account's standard athlete spaces (`followers`, `mutuals`, `self`, `interactions`) it doesn't sync yet, as if announced. Each space's outcome SHALL follow the usual sync and failure rules.

#### Scenario: Missed space writes
- **WHEN** the AppView missed a delete in bob's `interactions` space and an operator forces a resync of bob's spaces
- **THEN** the space is backfilled at once and the deleted like is no longer served

#### Scenario: Space never announced
- **WHEN** bob has a `mutuals` space his client never announced and an operator forces a resync of his spaces
- **THEN** the AppView starts syncing it and indexes what it holds

#### Scenario: Space that doesn't exist
- **WHEN** bob has no `mutuals` space and an operator forces a resync of his spaces
- **THEN** that space is answered `SpaceNotFound`, backs off, and is dropped after 24 hours of such answers, removing nothing else

#### Scenario: Suspended authority
- **WHEN** bob's spaces answer `RepoTakendown` during a forced resync
- **THEN** the AppView keeps what it indexed from them and backs off as for any sync
