# Spec Delta

## ADDED Requirements

### Requirement: A failing space's sync records why
Each failed sync of a space the AppView keeps syncing SHALL record the failure's error text and time beside its count of consecutive failures, within the bounds `dev/worker-telemetry` sets for recorded errors. A successful sync SHALL clear them with the count. A space that stops being synced SHALL take its record with it.

#### Scenario: PDS unreachable
- **WHEN** a sync of alice's `followers` space fails because her PDS can't be reached
- **THEN** the space's sync state shows one failure, the connection error and its time, and the space is retried with backoff as before

#### Scenario: Recovery
- **WHEN** a later sync of that space succeeds
- **THEN** its failure count is zero and no error is recorded for it

#### Scenario: Refusal recorded
- **WHEN** alice's PDS answers the AppView's authorization of her space with `UpstreamFailure`
- **THEN** the recorded error names `UpstreamFailure`

#### Scenario: Space dropped
- **WHEN** a space answered `SpaceDeleted` stops being synced
- **THEN** nothing about its failures is kept
