# Spec Delta

## ADDED Requirements

### Requirement: Recent reindex runs across accounts
The accounts page SHALL list, before any account is looked up, the reindex runs queued or running now and those that failed in the last 24 hours, across every account, newest first. Each SHALL show the account's DID, the requester, its state, phase and times, and its error code. Each SHALL link to its account's page. Runs that ended `unavailable` SHALL NOT be listed as failed.

#### Scenario: Failed run listed
- **WHEN** a reindex of bob's repo failed an hour ago
- **THEN** the accounts page lists it with bob's DID, `failed` and its error code, linking to bob's account page

#### Scenario: Unavailable repo
- **WHEN** a reindex ended `unavailable` because the PDS refused the repo
- **THEN** it isn't listed as failed

### Requirement: Accounts page summary
The accounts page SHALL offer a summary with two tiles. Reindex runs in flight: the queued and running runs, with no status. Failed reindex runs: those that failed in the last 24 hours, `warn` when any, else `ok`. Both SHALL link to the recent runs list.

#### Scenario: Run failed
- **WHEN** one reindex failed in the last 24 hours and two are running
- **THEN** the in-flight tile shows 2 with no status and the failed tile shows 1 and is `warn`

#### Scenario: Old failure
- **WHEN** the only failed run ended two days ago
- **THEN** the failed tile shows 0 and is `ok`
