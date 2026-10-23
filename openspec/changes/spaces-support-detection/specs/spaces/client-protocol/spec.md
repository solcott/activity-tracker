# Spec Delta

## ADDED Requirements

### Requirement: Spaces used only where the PDS supports them
A session SHALL use spaces only when its OAuth scope grants space access and the athlete's PDS reports Spaces support in `describeServer`. If that lookup fails, the session SHALL decide from the scope alone.

#### Scenario: Supported PDS
- **WHEN** an athlete signs in with the `space:` scope on a PDS whose `describeServer` reports `spacesAlpha: true`
- **THEN** non-public visibilities are available as before

#### Scenario: PDS without Spaces
- **WHEN** an athlete signs in on a PDS whose `describeServer` doesn't report `spacesAlpha: true`
- **THEN** the session writes nothing to spaces, and public features work as before

#### Scenario: Lookup fails
- **WHEN** the athlete's PDS doesn't answer `describeServer` at session start
- **THEN** the session's spaces access follows its scope alone

### Requirement: Unsupported PDS refused with its own message
On a PDS without Spaces support, a non-public save SHALL be refused before anything is written, with a message that the athlete's server doesn't support private data yet, and SHALL NOT be shown as a request to sign in again.

#### Scenario: Followers activity on an unsupported PDS
- **WHEN** an athlete whose PDS lacks Spaces support publishes an activity as Followers
- **THEN** nothing is written, and the app shows the unsupported-server message with no sign-in-again action

#### Scenario: Old scope on a supported PDS still asks to sign in
- **WHEN** an athlete whose PDS supports Spaces but whose session predates the `space:` scope makes a non-public save
- **THEN** the app asks them to sign in again, as before
