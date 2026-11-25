# Spec Delta

## ADDED Requirements

### Requirement: Space URIs
Clients and the AppView SHALL write and accept space URIs only in the `at:space/` form of the 2026-11-24 Spaces alpha: a space as `at:space/{did}/{type}/{skey}`, a record in it as `at:space/{did}/{type}/{skey}/{repo}/{collection}/{rkey}`. The authority and repo SHALL be DIDs. The earlier `at://{did}/space/...` form, a handle authority and any other number of parts SHALL be refused. A member read SHALL check the record MAC against the record URI in this form.

#### Scenario: Space URI built
- **WHEN** the client names alice's `followers` space
- **THEN** it sends `at:space/did:plc:alice/app.activitytracker.space.athlete/followers`

#### Scenario: Old form refused
- **WHEN** a host or a caller supplies `at://did:plc:alice/space/app.activitytracker.space.athlete/followers`
- **THEN** it isn't accepted as a space URI

#### Scenario: Member read under the new form
- **WHEN** a member reads a record from alice's `followers` space
- **THEN** the read succeeds only if the `Atproto-Space-Record` header's `uri` is `at:space/did:plc:alice/app.activitytracker.space.athlete/followers/{repo}/{collection}/{rkey}` and its MAC verifies

## MODIFIED Requirements

### Requirement: Owner writes
The signed-in athlete SHALL be able to create one of their spaces, look it up, update its policy, and put and delete records in it, all authenticated with their session. A space is created and updated with a single `policy` (`com.atproto.space.createSpace`, `getSpace`, `updateSpace`); an update replaces the whole policy.

#### Scenario: Create a space
- **WHEN** the athlete creates a space of type `app.activitytracker.space.athlete` with a given key and a `com.atproto.managedspace.policy` naming the AppView as managing app
- **THEN** the space exists at `at:space/{did}/app.activitytracker.space.athlete/{key}`

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
