# spaces/client-protocol Specification

## Purpose

Defines how the app's clients authenticate to, read from and write to atproto spaces on athletes' PDSes, without an atproto SDK, so later changes can keep private data in spaces.

## Requirements

### Requirement: Space access in the OAuth scope
Every client's OAuth request SHALL ask for `atproto transition:generic` plus a `space:` scope for the `app.activitytracker.space.athlete` space type that covers any authority, owner reads, record create/update/delete and space create/update. The hosted client metadata SHALL declare the same scope.

#### Scenario: Sign-in grants space access
- **WHEN** an athlete signs in on any client against a PDS that resolves the space type's declaration
- **THEN** the granted scope includes the `space:app.activitytracker.space.athlete` permission and Spaces calls with that session aren't refused for a missing scope

#### Scenario: Hosted client metadata matches
- **WHEN** a PDS fetches the AppView's `/oauth/client-metadata.json`
- **THEN** its `scope` equals the scope the native clients request

#### Scenario: Session from before the scope existed
- **WHEN** a session signed in without the `space:` scope makes a Spaces call and the PDS answers `ScopeMissingError`
- **THEN** the call fails with an error that identifies a missing scope, and other features keep working on that session

### Requirement: Signed space requests
Each credential request and each credentialed read SHALL carry an RFC 9421 signature labelled `atproto-space`, made with an ECDSA P-256 key held by the client for the session. A credential request SHALL name that key as a `did:key` in `keyid`; a credentialed read SHALL be signed with the key the credential is bound to.

#### Scenario: Credential request is signed
- **WHEN** the client asks an authority's PDS for a space credential
- **THEN** the request carries `Authorization: Bearer <delegation token>` and a signature covering `authorization`, whose `keyid` is the client's P-256 `did:key`

#### Scenario: Credentialed read is signed
- **WHEN** the client reads a space record with a credential
- **THEN** the request carries `Authorization: Atproto-Space <credential>`, `Atproto-Space-Audience: <repo owner DID>` and a signature covering both headers, made with the key the credential binds

#### Scenario: Signature matches the reference implementation
- **WHEN** the client signs a fixed request with a fixed key
- **THEN** the signature base and headers are byte-identical to those the pinned PDS image's `@atproto/space` code produces for the same input

### Requirement: Credential exchange
To read a space it doesn't own, the client SHALL get a single-use delegation token for that space from the viewer's own PDS, then exchange it for a credential at the PDS of the space's authority.

#### Scenario: Member gets a credential
- **WHEN** a viewer the space's policy admits asks to read the space
- **THEN** the client obtains a delegation token from the viewer's PDS, exchanges it at the authority's PDS, and holds a credential for that space

#### Scenario: Authority's PDS found by DID
- **WHEN** the space's authority is hosted on a different PDS than the viewer
- **THEN** the exchange and reads go to the authority's PDS as resolved from its DID document

### Requirement: Credential reuse
The client SHALL reuse a space's credential until shortly before it expires, SHALL run at most one exchange per space at a time, and SHALL discard a credential the PDS reports as expired or revoked and obtain a new one.

#### Scenario: Repeated reads reuse the credential
- **WHEN** the viewer reads the same space several times within the credential's lifetime
- **THEN** only one delegation token and one exchange are made

#### Scenario: Concurrent reads share one exchange
- **WHEN** several reads of the same space start at once with no credential cached
- **THEN** exactly one exchange runs and every read uses its result

#### Scenario: Credential near expiry is refreshed
- **WHEN** a read starts less than 5 seconds before the cached credential expires
- **THEN** the client obtains a new credential before reading

#### Scenario: Revoked or expired credential
- **WHEN** a read with a cached credential fails with `JwtExpired` or `CredentialRevoked`
- **THEN** the client discards that credential, obtains a new one and retries the read once

#### Scenario: Sign-out clears credentials
- **WHEN** the athlete signs out
- **THEN** no cached credential or signing key from that session is used again

### Requirement: Denied access is a result, not an error
When the authority's PDS refuses a viewer access to a space, the client SHALL report "no access" for that read as a distinct outcome from failures such as network errors.

#### Scenario: Non-member read
- **WHEN** a viewer the space's policy doesn't admit reads a record from it and the PDS answers `UserNotAuthorized`
- **THEN** the read returns a no-access outcome, not an exception, and the denial is not cached as a credential

#### Scenario: Network failure stays an error
- **WHEN** the authority's PDS can't be reached during a read
- **THEN** the read fails with an error, distinguishable from no access

### Requirement: Owner reads without a credential
The client SHALL read the signed-in athlete's own spaces with the athlete's session, without a delegation token or credential.

#### Scenario: Owner reads their own record
- **WHEN** the signed-in athlete reads a record from a space they are the authority of
- **THEN** the read uses their session on their own PDS and makes no credential exchange

### Requirement: Member reads
With a credential, the client SHALL fetch a single record, list a collection's records with paging, and fetch a blob from a space on the repo owner's PDS.

#### Scenario: Fetch a record
- **WHEN** a member fetches a record by collection and rkey
- **THEN** the client returns its value, or a not-found outcome if there is no such record

#### Scenario: List records
- **WHEN** a member lists a collection with a limit
- **THEN** the client returns at most that many records and a cursor to continue

#### Scenario: Fetch a blob
- **WHEN** a member fetches a blob a space record references
- **THEN** the client returns the blob's bytes

### Requirement: Owner writes
The signed-in athlete SHALL be able to create one of their spaces, look it up, and put and delete records in it, all authenticated with their session.

#### Scenario: Create a space
- **WHEN** the athlete creates a space of type `app.activitytracker.space.athlete` with a given key and the AppView as managing app
- **THEN** the space exists at `at://{did}/space/app.activitytracker.space.athlete/{key}`

#### Scenario: Create an existing space
- **WHEN** the athlete creates a space that already exists
- **THEN** the client reports that it exists rather than failing, so callers can create spaces lazily

#### Scenario: Put is idempotent
- **WHEN** the athlete puts the same record at the same collection and rkey twice
- **THEN** both calls succeed and the space holds one record with that value

#### Scenario: Delete a record
- **WHEN** the athlete deletes a record from their space
- **THEN** a later owner read of it returns not found
