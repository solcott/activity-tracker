# Spec Delta

## MODIFIED Requirements

### Requirement: Inactive accounts close their tier spaces
While the AppView holds a `takendown`, `suspended` or `deactivated` status for an account, it SHALL answer `checkAccess` for that account's tier spaces with `read: false` for every account and key, `public` included. The AppView's own service DID SHALL still be admitted as usual, so it keeps what it indexed. Once Jetstream reports the account active again, readers SHALL be decided by tier again. The status of the account being checked SHALL NOT change the answer.

#### Scenario: Suspended athlete's followers space
- **WHEN** bob's account is `suspended` and bob's PDS asks `checkAccess` whether alice, his effective follower, may read his `followers` space
- **THEN** the answer is `read: false`

#### Scenario: Deactivated athlete's public space
- **WHEN** bob's account is `deactivated` and his PDS asks whether any account may read his `public` space
- **THEN** the answer is `read: false`

#### Scenario: AppView keeps syncing
- **WHEN** bob's account is `suspended` and the AppView's own service DID is checked for `read` on any of his tiers or his `interactions` space
- **THEN** the answer is `read: true`

#### Scenario: Reactivated
- **WHEN** bob's account was `deactivated` and Jetstream then reports it `active: true`
- **THEN** alice is admitted to bob's `followers` space again

#### Scenario: Inactive reader
- **WHEN** alice's account is `suspended` and bob's PDS asks whether alice, his effective follower, may read his `followers` space
- **THEN** the answer is `read: true`, as for an active alice
