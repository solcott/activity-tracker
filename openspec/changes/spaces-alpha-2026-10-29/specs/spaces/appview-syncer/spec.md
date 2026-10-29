# Spec Delta

## MODIFIED Requirements

### Requirement: Space sync failures are contained
A failure to sync one space, including a revoked or refused credential or a deleted space, SHALL NOT stop the AppView from syncing other spaces or from indexing Jetstream. A space reported as deleted (`SpaceDeleted`) SHALL stop being synced, and its records SHALL be removed from the index. Because the authority answers a refusal as `SpaceNotFound` too, a space answered `SpaceNotFound` SHALL keep its indexed records and be retried with backoff, and SHALL be dropped only once every sync for 24 hours has been answered `SpaceNotFound`.

#### Scenario: Space deleted
- **WHEN** alice deletes her `mutuals` space
- **THEN** the AppView stops syncing it and no longer serves activities from it

#### Scenario: PDS unreachable
- **WHEN** one athlete's PDS can't be reached during reconcile
- **THEN** other spaces reconcile normally, and that space is retried later

#### Scenario: Refusal while the managing app is unreachable
- **WHEN** alice's PDS can't reach the AppView's `checkUserAccess` for a while and answers the AppView's credential requests for her `followers` space with `SpaceNotFound`
- **THEN** the AppView keeps serving the activities it indexed from that space to the viewers its tier admits, retries with backoff, and resumes syncing once a credential is issued again

#### Scenario: Space missing for a day
- **WHEN** every sync of a space has been answered `SpaceNotFound` for 24 hours
- **THEN** the AppView stops syncing it and removes everything indexed from it

#### Scenario: Not-found streak broken
- **WHEN** a space answered `SpaceNotFound` and a later sync succeeds or fails with another error
- **THEN** the 24 hours start over at the next `SpaceNotFound`

## ADDED Requirements

### Requirement: Synced spaces verified against the commit's set hash
The AppView SHALL keep, for each synced space, the set hash of the authority's repo in that space as upstream `@atproto/space` defines it (an LtHash over `{collection}/{rkey}/{cid}` of every record, whose sha256 is the commit's `data`), over every record in the repo, indexed or not. It SHALL index a pull's ops, or a backfill's records, and record the revision only when the resulting digest equals the `data` of the MAC-verified commit.

#### Scenario: Pull matches
- **WHEN** alice writes to her `followers` space and the pulled ops, folded into the stored set hash, give the commit's `data`
- **THEN** the ops are indexed and the commit's revision and set hash are recorded

#### Scenario: Records outside the indexed collections
- **WHEN** alice's space holds records of a collection the AppView doesn't index from that space
- **THEN** they still count toward the set hash, and are still not indexed

#### Scenario: Backfill matches the head commit
- **WHEN** the AppView backfills a space and the listed records fold to the head commit's `data`
- **THEN** the records are indexed and the head revision and set hash are recorded

#### Scenario: Write during a backfill
- **WHEN** alice writes while the AppView is listing her space, so the listed records don't fold to the head commit it read first
- **THEN** the AppView sees the head revision moved and backfills again, recording a revision only once a listing matches its head

#### Scenario: Backfill never matches
- **WHEN** a backfill's records don't fold to the head commit's `data` and the head revision hasn't moved
- **THEN** the sync fails and is retried with backoff, nothing indexed from the space is removed, and no revision is recorded

#### Scenario: Space synced before set hashes were kept
- **WHEN** a space has a recorded revision but no stored set hash
- **THEN** its next sync is a backfill

### Requirement: Cleared space repos resynced
When a pull's verified commit `data` doesn't match the stored set hash after folding the pulled ops, as after the authority clears their repo with `com.atproto.space.clearRecords`, the AppView SHALL resync the space from a full listing and remove every indexed record the space no longer holds. A commit with no ops and an unchanged `data`, as after the authority's account migrates, SHALL just advance the revision.

#### Scenario: Space cleared
- **WHEN** alice clears her repo in her `followers` space with `clearRecords`
- **THEN** after its next sync the AppView no longer serves any activity, like or other record indexed from that space, and keeps syncing the space

#### Scenario: Cleared then written
- **WHEN** alice clears her space and writes a new activity to it before the AppView pulls
- **THEN** after the next sync the AppView serves only the new activity from that space

#### Scenario: Account migrated
- **WHEN** alice's account moves to another PDS and a pull returns a commit at a new revision with no ops and the same `data`
- **THEN** the AppView records the new revision and removes nothing
