# Spec Delta

## MODIFIED Requirements

### Requirement: Denied access is a result, not an error
When the authority's PDS refuses a viewer access to a space, the client SHALL report "no access" for that read as a distinct outcome from failures such as network errors.

#### Scenario: Non-member read
- **WHEN** a viewer the space's policy doesn't admit reads a record from it and the PDS answers `UserNotAuthorized`, `AppNotAuthorized` or `NotAuthorized`
- **THEN** the read returns a no-access outcome, not an exception, and the denial is not cached as a credential

#### Scenario: Refusal answered as not found
- **WHEN** a viewer the space's policy doesn't admit reads a record from it and the PDS answers `SpaceNotFound`, as the reference authority now does for a refused user
- **THEN** the read returns a not-found outcome, not an exception, and the client draws no conclusion about whether the space exists

#### Scenario: Network failure stays an error
- **WHEN** the authority's PDS can't be reached during a read
- **THEN** the read fails with an error, distinguishable from no access

### Requirement: Owner writes
The signed-in athlete SHALL be able to create one of their spaces, look it up, update its policy, and put and delete records in it, all authenticated with their session. A space is created and updated with a single `policy` (`com.atproto.space.createSpace`, `getSpace`, `updateSpace`); an update replaces the whole policy.

#### Scenario: Create a space
- **WHEN** the athlete creates a space of type `app.activitytracker.space.athlete` with a given key and a `com.atproto.managedspace.policy` naming the AppView as managing app
- **THEN** the space exists at `at://{did}/space/app.activitytracker.space.athlete/{key}`

#### Scenario: Create an existing space
- **WHEN** the athlete creates a space that already exists
- **THEN** the client reports that it exists rather than failing, so callers can create spaces lazily

#### Scenario: Update a space's policies
- **WHEN** the athlete's existing space has a policy other than a `managedspace` policy naming the current AppView (another managing app, another policy type, or published records turned on)
- **THEN** the client replaces it with that policy, and a later lookup of the space returns exactly it

#### Scenario: Matching policy left alone
- **WHEN** the athlete's existing space's policy, with the PDS's defaults filled in, already equals the one the client would set
- **THEN** the client sends no update

#### Scenario: Put is idempotent
- **WHEN** the athlete puts the same record at the same collection and rkey twice
- **THEN** both calls succeed and the space holds one record with that value

#### Scenario: Delete a record
- **WHEN** the athlete deletes a record from their space
- **THEN** a later owner read of it returns not found
