# Spec Delta

## MODIFIED Requirements

### Requirement: Space access in the OAuth scope
Every client's OAuth request SHALL ask for `atproto transition:generic` plus a `space:` scope for the `app.activitytracker.space.athlete` space type that covers any authority, owner reads, record create/update/delete and space create/update/delete. The hosted client metadata SHALL declare the same scope.

#### Scenario: Sign-in grants space access
- **WHEN** an athlete signs in on any client against a PDS that resolves the space type's declaration
- **THEN** the granted scope includes the `space:app.activitytracker.space.athlete` permission and Spaces calls with that session aren't refused for a missing scope

#### Scenario: Hosted client metadata matches
- **WHEN** a PDS fetches the AppView's `/oauth/client-metadata.json`
- **THEN** its `scope` equals the scope the native clients request

#### Scenario: Session from before the scope existed
- **WHEN** a session signed in without the `space:` scope makes a Spaces call and the PDS answers `ScopeMissingError`
- **THEN** the call fails with an error that identifies a missing scope, and other features keep working on that session

#### Scenario: Grant without space deletion
- **WHEN** a session signed in before the scope covered space deletion
- **THEN** the Account page reports the sign-in as not current, and offers Sign in again

## ADDED Requirements

### Requirement: Owner deletes a space
A client SHALL be able to delete one of the signed-in athlete's own spaces by its URI. Deleting a space that no longer exists SHALL succeed as already done, so a repeated delete is harmless. A refusal for a missing scope SHALL be reported as such.

#### Scenario: Deleting a tier space
- **WHEN** alice's client deletes her Followers space
- **THEN** the PDS no longer has it, and the AppView, notified by her PDS, drops what it held from it

#### Scenario: Already deleted
- **WHEN** alice's client deletes her `self` space a second time
- **THEN** the call succeeds without error
