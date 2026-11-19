# Spec Delta

## MODIFIED Requirements

### Requirement: Existing Bluesky blocks are backfilled
After the AppView first indexes a record from an athlete, and once for every athlete it already knew when this capability shipped, it SHALL fetch every `app.bsky.graph.block` record in that athlete's repo and index them. A failed backfill SHALL be retried later and SHALL NOT hold up indexing. The pause before each retry SHALL start at ten minutes and double with each consecutive failure, up to one day, and SHALL survive a restart of the AppView; each failure's error text and time SHALL be recorded, and a successful backfill or the athlete's deletion SHALL clear them.

#### Scenario: Blocks made before joining
- **WHEN** alice blocked bob on Bluesky last year and then creates her athlete profile today
- **THEN** once the AppView has indexed her profile and backfilled, the block between alice and bob applies

#### Scenario: Athletes from before blocks shipped
- **WHEN** the AppView starts with this capability for the first time and alice was already indexed
- **THEN** it backfills alice's Bluesky blocks too

#### Scenario: Backfill failure
- **WHEN** fetching alice's Bluesky blocks fails because her PDS is unreachable
- **THEN** alice's profile is still indexed, and the backfill is tried again later

#### Scenario: Repeated failures back off
- **WHEN** alice's backfill has failed three times in a row
- **THEN** it is not tried again for forty minutes, and the third failure's error is recorded

#### Scenario: Restart during backoff
- **WHEN** the AppView restarts while alice's backfill is waiting out its pause
- **THEN** her backfill is not tried until that pause ends

#### Scenario: Backoff does not crowd out others
- **WHEN** more athletes are backing off than one round handles
- **THEN** athletes who are due are still backfilled in that round

#### Scenario: Success clears the failure
- **WHEN** alice's backfill succeeds after failing
- **THEN** no failure is recorded for her and she is not backfilled again
