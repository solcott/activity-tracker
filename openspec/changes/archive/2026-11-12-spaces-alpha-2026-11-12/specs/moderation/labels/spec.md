# Spec Delta

## MODIFIED Requirements

### Requirement: Account takedown closes tier spaces
While an account DID carries `!takedown`, the AppView, as managing app of that account's tier spaces, SHALL refuse read access to everyone but the account. It SHALL admit readers again under the usual rules once the label is negated or expires.

#### Scenario: Taken-down account's Followers tier
- **WHEN** bob's DID carries `!takedown` and bob's PDS asks `checkAccess` whether alice, an effective follower, may read bob's Followers space
- **THEN** the AppView answers `read: false`

#### Scenario: Takedown negated
- **WHEN** the `!takedown` on bob's DID is negated
- **THEN** `checkAccess` admits alice to bob's Followers space again
