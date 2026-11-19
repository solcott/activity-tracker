# Spec Delta

## Purpose

Gives the AppView's operators one admin page per account: what the AppView holds and knows about that account, as identifiers, statuses, counts and times only, and the repairs (handle re-verification, Bluesky block backfill, repo reindex, space resync) that fix it.

## ADDED Requirements

### Requirement: Look up an account by DID or verified handle
The account page SHALL find an account by its DID or by the verified handle the AppView stores for it, ignoring case and a leading `@`. A handle SHALL match only a verified stored handle, never `handle.invalid`. A DID with no indexed account SHALL show as not indexed; a handle with no match SHALL say so and name no account.

#### Scenario: By handle
- **WHEN** an admin looks up `@Alice.test` and alice's verified handle is `alice.test`
- **THEN** alice's account page opens, naming her DID

#### Scenario: Invalid handle
- **WHEN** an admin looks up `handle.invalid`
- **THEN** no account is found

#### Scenario: Unknown DID
- **WHEN** an admin looks up a well-formed DID the AppView has never indexed
- **THEN** the page shows the DID as not indexed

#### Scenario: Not a DID or handle
- **WHEN** an admin looks up text that is neither a DID nor a handle
- **THEN** the page says so and looks nothing up

### Requirement: Account status and handle
The account page SHALL show the account's hosting status (active, takendown, suspended or deactivated) as the AppView stores it, its stored handle, whether that handle is verified or `handle.invalid`, and when it is next checked.

#### Scenario: Deactivated account
- **WHEN** an admin opens the page of bob, stored as `deactivated`
- **THEN** the page shows `deactivated`

#### Scenario: Invalid handle shown
- **WHEN** carol's last handle check found her claimed handle resolves to another DID
- **THEN** the page shows `handle.invalid`, not verified, with her next check time

### Requirement: Indexed counts by collection and origin
The account page SHALL show, for each collection the AppView indexes, how many of the account's records it holds from the account's repo and from each of the account's spaces, naming the space. It SHALL show counts only, never which records they are.

#### Scenario: Activities in a tier space
- **WHEN** bob has two public activities and one in his `followers` space
- **THEN** the page shows activities: 2 from the repo, 1 from `followers`

#### Scenario: Nothing held
- **WHEN** bob has no records of a collection
- **THEN** that collection shows zero, or is left out

### Requirement: Space sync state
The account page SHALL list every space the AppView syncs for the account with its revision, last successful sync, consecutive failures, last recorded error and its time, next attempt, how long it has answered not found, and when its registration lapses. It SHALL NOT show the space's notification secret or set hash.

#### Scenario: Failing space
- **WHEN** bob's `mutuals` space has failed three syncs in a row
- **THEN** the page shows 3 failures, the last recorded error, and the next attempt time

#### Scenario: No secrets
- **WHEN** an admin opens any account page
- **THEN** no space's notification secret or set hash appears in it

### Requirement: Opt-ins shown as yes or no
The account page SHALL show whether the account has segment matching on and whether it has grouping on, each as yes or no only. It SHALL NOT show which grouping mode was chosen, the leaderboard choices, the leaderboard brackets or any other setting from the account's spaces.

#### Scenario: Grouping with connected athletes
- **WHEN** bob chose grouping with connected athletes
- **THEN** the page shows grouping: yes, and doesn't say "connected"

#### Scenario: Unknown grouping value
- **WHEN** bob's grouping choice is a value the AppView doesn't know
- **THEN** the page shows grouping: no

### Requirement: Bluesky block backfill state
The account page SHALL show when the account's Bluesky blocks were last backfilled, or that they never were, and while backfills are failing, how many in a row, the last recorded error and the next attempt.

#### Scenario: Backing off
- **WHEN** bob's block backfill failed twice and is waiting
- **THEN** the page shows 2 failures, the last recorded error and when it is tried next

### Requirement: Reindex runs shown
The account page SHALL list the account's reindex runs, newest first, each with its requester, state, phase, start and end times, repository revision, record counts, spaces queued and error code and message. While a run is queued or running, the list SHALL refresh on its own until the run ends.

#### Scenario: Run in progress
- **WHEN** bob's reindex is applying records
- **THEN** the list shows it running in the applying phase, and its counts update without reloading the page

#### Scenario: Run ended
- **WHEN** bob's reindex succeeded
- **THEN** the list shows `succeeded` with its counts and stops refreshing

### Requirement: Re-verify a handle now
An operator SHALL be able to check an indexed account's handle at once, with the same rules and outcome as a scheduled check, including when it is next checked. The action SHALL report the result: verified with the handle, invalid, or unreachable.

#### Scenario: Handle fixed
- **WHEN** alice fixed her DNS record and an operator re-verifies her handle
- **THEN** the page reports verified with `alice.example.com`, and the AppView shows that handle

#### Scenario: Lookups down
- **WHEN** an operator re-verifies alice's handle while her handle's lookups can't be reached
- **THEN** the action reports unreachable and alice keeps her last verified handle

#### Scenario: Not indexed
- **WHEN** an operator re-verifies the handle of a DID with no indexed account
- **THEN** the action reports that the account isn't indexed and stores nothing

### Requirement: Backfill Bluesky blocks now
An operator SHALL be able to backfill an indexed account's Bluesky blocks at once, whether or not it was backfilled before or is backing off. Success and failure SHALL be recorded as a scheduled backfill records them, and the action SHALL report how many blocks it indexed or the failure.

#### Scenario: New blocks
- **WHEN** bob blocked carol on Bluesky while the AppView missed the event and an operator backfills bob's blocks
- **THEN** the block between them applies and the action reports the blocks indexed

#### Scenario: PDS down
- **WHEN** an operator backfills bob's blocks and his PDS can't be reached
- **THEN** the action reports the failure, and bob's backoff counts it as one more failure

### Requirement: Reindex a repo from the account page
An operator SHALL be able to request a reindex of an account's repo, for any well-formed DID, choosing whether it also resyncs the account's spaces (yes by default). The request SHALL be recorded with the operator as requester, and a queued or running reindex SHALL be returned instead of starting another.

#### Scenario: Reindex requested
- **WHEN** an operator presses "Reindex repo" on bob's page
- **THEN** a run for bob is queued with the operator as requester and appears in the run list

#### Scenario: Already running
- **WHEN** an operator presses "Reindex repo" while bob's reindex is running
- **THEN** the action reports the running run and no second run starts

#### Scenario: Missed records
- **WHEN** bob created one record and deleted another while the AppView missed both events, and an operator reindexes him
- **THEN** once the run ends, the created record is indexed, the deleted one is gone, and the run reads `succeeded` with counts that match

### Requirement: Resync an account's spaces
An operator SHALL be able to force a resync of an account's spaces from its page, and the action SHALL report how many spaces were queued. When the AppView syncs no spaces, the page SHALL NOT offer the action.

#### Scenario: Resync
- **WHEN** an operator presses "Resync spaces" on bob's page
- **THEN** every space of bob's is due at once and the action reports how many were queued

#### Scenario: No space syncing
- **WHEN** the AppView runs without a signing key
- **THEN** bob's page offers no "Resync spaces" control

### Requirement: Account actions are audited operator actions
Each account action SHALL be an operator-only, forgery-protected action of the admin site, audited with the account's DID as its target and its settings as parameters. A viewer SHALL see the account page without the action controls, and a viewer's attempt SHALL be refused and audited.

#### Scenario: Audited reindex
- **WHEN** an operator reindexes bob without resyncing spaces
- **THEN** the audit log holds the operator's DID, the reindex action, bob's DID and the setting that spaces weren't resynced, with its outcome

#### Scenario: Viewer attempt
- **WHEN** a viewer posts the "Resync spaces" action for bob
- **THEN** it is refused with `403`, nothing is resynced and the refusal is audited

### Requirement: Account page reveals no private content
The account page, its fragments and its action results SHALL show only identifiers, statuses, counts and times as the admin site allows. For an account with non-public activities, comments, segment tracks or private settings, they SHALL show none of their content.

#### Scenario: Only me activity
- **WHEN** an admin opens the page of an athlete with an Only me activity and a comment
- **THEN** the page counts them, and shows no title, text, time, place or stats of either

#### Scenario: Segment track
- **WHEN** the athlete has a segment track indexed
- **THEN** the page shows no point, time or activity URI from it
