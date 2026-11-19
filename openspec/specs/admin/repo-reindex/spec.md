# repo-reindex Specification

## Purpose
Lets an operator rebuild one account's indexed state from its source of truth, its repo and its spaces, when the AppView missed events, with bounded cost, no race with live events, and a recorded outcome for the admin site.

## Requirements

### Requirement: Reindex is an internal operator action
The AppView SHALL offer reindexing one account, by DID, only to its own code (the admin site's operator actions). It SHALL NOT expose reindexing through any XRPC method or public route. Each request SHALL record which admin asked for it.

#### Scenario: Requested from the admin site
- **WHEN** alice, an operator, asks to reindex bob's account
- **THEN** a run for bob is recorded with alice as its requester

#### Scenario: No public route
- **WHEN** anyone calls the AppView's XRPC or HTTP routes
- **THEN** none of them starts a reindex

### Requirement: The repo is fetched from the account's current PDS
A reindex SHALL resolve the account's DID document afresh, fetch the whole repo with `com.atproto.sync.getRepo` from the PDS that document names, and apply the AppView's outbound request rules for hosts learned from identities.

#### Scenario: Migrated account
- **WHEN** bob moved to a new PDS and the AppView's cached PDS for him is still the old one
- **THEN** the reindex fetches his repo from the new PDS named by his DID document

#### Scenario: PDS on a private address
- **WHEN** bob's DID document names a PDS that resolves only to a private address and private addresses aren't allowed
- **THEN** the run fails without fetching anything

### Requirement: Only a verified repo is applied
Before indexing anything, a reindex SHALL check that the repo's commit names the account, is signed by the account's current signing key, and that every block it reads hashes to its CID. A repo failing any check SHALL change nothing.

#### Scenario: Bad signature
- **WHEN** the PDS returns a repo whose commit signature doesn't verify against bob's signing key
- **THEN** the run fails and nothing indexed for bob changes

#### Scenario: Tampered block
- **WHEN** a record block's bytes don't match its CID
- **THEN** the run fails and nothing indexed for bob changes

### Requirement: Repo records are indexed as Jetstream would index them
A reindex SHALL index every record of the repo in a collection the AppView indexes, with the same rules as a record arriving from Jetstream, and SHALL ignore every other collection. A record already indexed with the same CID SHALL be left as is.

#### Scenario: Missed activity
- **WHEN** bob published a public activity while the AppView was down past its Jetstream rewind, and an operator reindexes bob
- **THEN** the activity is indexed and served as if Jetstream had delivered it

#### Scenario: Unchanged record
- **WHEN** bob's segment is indexed with the CID the repo holds
- **THEN** the reindex leaves it and what was derived from it untouched

#### Scenario: Collection not indexed
- **WHEN** bob's repo holds `app.bsky.feed.post` records
- **THEN** the reindex indexes none of them

#### Scenario: Undecodable record
- **WHEN** one of bob's activity records fails to decode
- **THEN** it is skipped and counted as invalid, and the rest of the repo is indexed

### Requirement: Records gone from the repo are removed
After indexing the repo, a reindex SHALL delete every record the AppView holds from the account's repo that the repo no longer holds, with the same effects as a delete from Jetstream. Records the AppView holds from the account's spaces SHALL NOT be affected.

#### Scenario: Missed delete
- **WHEN** bob deleted a public post while the AppView missed the event, and an operator reindexes bob
- **THEN** the post and what was derived from it are removed

#### Scenario: Space copies kept
- **WHEN** bob's activity is held from his `followers` space and not in his repo
- **THEN** the reindex keeps it

#### Scenario: Moved to a space
- **WHEN** bob's activity is in his `mutuals` space, a stale public copy of it is indexed from his repo, and his repo no longer holds it
- **THEN** the repo copy is removed and the `mutuals` copy is served

### Requirement: Live events during a reindex win when newer
A reindex SHALL NOT overwrite or remove a record whose live Jetstream event is newer than the fetched repo, and a live event the fetched repo already covers SHALL NOT replace what the reindex applied for that record.

#### Scenario: Created during the reindex
- **WHEN** bob creates an activity after his repo was fetched and Jetstream delivers it while the reindex is removing records
- **THEN** the activity stays indexed

#### Scenario: Edited during the reindex
- **WHEN** bob edits his profile after the fetch and Jetstream delivers the edit before the reindex reaches the profile
- **THEN** the edited profile is what stays indexed

#### Scenario: Lagging event
- **WHEN** Jetstream delivers, after the reindex applied bob's segment, an older edit of that segment that the fetched repo already includes
- **THEN** the segment stays as the reindex applied it

### Requirement: Reindex work is bounded
A reindex SHALL stop with a failure naming the limit when the repo exceeds the configured maximum size or block count, or the run exceeds the configured time. A run stopped before its repo was fully read and verified SHALL change nothing.

#### Scenario: Oversized repo
- **WHEN** bob's repo is larger than the configured maximum
- **THEN** the run fails naming the size limit, and nothing indexed for bob changes

#### Scenario: Slow PDS
- **WHEN** bob's PDS sends nothing for longer than the read timeout
- **THEN** the run fails and nothing indexed for bob changes

### Requirement: One reindex per account, few at once
The AppView SHALL run at most one reindex per account at a time and at most a configured number in total. A request for an account with a queued or running reindex SHALL return that run. Other requests SHALL wait their turn, oldest first.

#### Scenario: Asked twice
- **WHEN** an operator asks to reindex bob while bob's reindex is running
- **THEN** no second run starts and the running one is returned

#### Scenario: Global cap
- **WHEN** the cap is 2, two reindexes are running, and a third account is requested
- **THEN** the third is queued and starts when one of the two ends

### Requirement: Inactive and missing accounts are never changed by status
A reindex SHALL NOT change an account's stored status nor delete an account. When the PDS refuses the repo as taken down, suspended or deactivated, or the repo or DID isn't found, the run SHALL end as unavailable with the reason and change nothing indexed from the repo.

#### Scenario: Taken down at the PDS
- **WHEN** bob's PDS answers `getRepo` with `RepoTakendown`
- **THEN** the run ends unavailable with that reason, bob's indexed data and status are unchanged

#### Scenario: Inactive in the AppView
- **WHEN** bob is stored as `deactivated` but his PDS serves his repo
- **THEN** the reindex indexes it, bob stays `deactivated`, and his content stays unserved

#### Scenario: Repo not found
- **WHEN** bob's PDS answers `RepoNotFound`
- **THEN** the run ends unavailable and bob's account isn't deleted

### Requirement: A reindex also resyncs the account's spaces
A reindex SHALL, unless asked not to, force a full resync of the account's spaces through the space syncer, whether or not its repo could be fetched, and record how many spaces it queued.

#### Scenario: Spaces resynced
- **WHEN** an operator reindexes bob, whose `followers` and `interactions` spaces the AppView syncs
- **THEN** both are backfilled at once and the run records the spaces it queued

### Requirement: Each run's progress and outcome is recorded
The AppView SHALL record each reindex run durably with its account, requester, request, start and end times, state (queued, running, succeeded, failed or unavailable), current phase, the repo revision read, and counts of records seen, unchanged, indexed, invalid, removed and skipped for live events, and the error when it didn't succeed. Runs SHALL be readable by account and kept at least 90 days.

#### Scenario: Progress shown
- **WHEN** bob's reindex is applying records
- **THEN** reading bob's runs shows it running, in the applying phase, with counts so far

#### Scenario: Interrupted by restart
- **WHEN** the AppView restarts while bob's reindex is running
- **THEN** that run is recorded as failed, interrupted, and queued runs still start after the restart

### Requirement: Run records hold no record content
A reindex run's record SHALL hold only identifiers, the revision, counts, times, states and error codes with short messages, never the content of any record, public or private.

#### Scenario: Failure message
- **WHEN** a run fails while decoding bob's repo
- **THEN** its error names the failure and the limit or check involved, but quotes no record field
