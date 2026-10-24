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

### Requirement: Credential exchange
To read a space it doesn't own, the client SHALL get a single-use delegation token for that space from the viewer's own PDS, then exchange it for a credential at the PDS of the space's authority. Each exchange SHALL bind the credential to a newly generated P-256 key that no other credential uses.

#### Scenario: Member gets a credential
- **WHEN** a viewer the space's policy admits asks to read the space
- **THEN** the client obtains a delegation token from the viewer's PDS, exchanges it at the authority's PDS, and holds a credential for that space

#### Scenario: Authority's PDS found by DID
- **WHEN** the space's authority is hosted on a different PDS than the viewer
- **THEN** the exchange and reads go to the authority's PDS as resolved from its DID document

#### Scenario: Each credential has its own key
- **WHEN** the viewer obtains credentials for two spaces, or a new credential for a space whose old one expired
- **THEN** each credential request names a different key, and each credential's reads are signed with its own key

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
The signed-in athlete SHALL be able to create one of their spaces, look it up, update its policies, and put and delete records in it, all authenticated with their session.

#### Scenario: Create a space
- **WHEN** the athlete creates a space of type `app.activitytracker.space.athlete` with a given key and the AppView as managing app
- **THEN** the space exists at `at://{did}/space/app.activitytracker.space.athlete/{key}`

#### Scenario: Create an existing space
- **WHEN** the athlete creates a space that already exists
- **THEN** the client reports that it exists rather than failing, so callers can create spaces lazily

#### Scenario: Update a space's policies
- **WHEN** the athlete updates their space's read policy to name a different managing app
- **THEN** a later lookup of the space returns the new read policy

#### Scenario: Put is idempotent
- **WHEN** the athlete puts the same record at the same collection and rkey twice
- **THEN** both calls succeed and the space holds one record with that value

#### Scenario: Delete a record
- **WHEN** the athlete deletes a record from their space
- **THEN** a later owner read of it returns not found

### Requirement: Credential requests carry their key in the body
A credential request SHALL be a JSON POST whose body holds the space, the delegation token and the P-256 `did:key` the credential will bind, and SHALL carry no `Authorization` header and no signature.

#### Scenario: Credential request is unsigned
- **WHEN** the client asks an authority's PDS for a space credential
- **THEN** the request body holds the space, the delegation token and a P-256 `did:key`, and the request has no `Authorization`, `Signature-Input` or `Signature` header

### Requirement: Signed credentialed reads
Each credentialed read SHALL carry an RFC 9421 signature labelled `atproto-space-credential` and tagged `atproto-space-credential`, made with the ECDSA P-256 key its credential is bound to.

#### Scenario: Credentialed read is signed
- **WHEN** the client reads a space record with a credential
- **THEN** the request carries `Authorization: Atproto-Space-Credential <credential>`, `Atproto-Space-Audience: <repo owner DID>` and a signature covering both headers with `tag="atproto-space-credential"`, made with the key that credential binds

#### Scenario: Signature matches the reference implementation
- **WHEN** the client signs a fixed request with a fixed key
- **THEN** the signature base and headers are byte-identical to those the pinned PDS image's `@atproto/space` code produces for the same input, with the signature in padded base64

### Requirement: Member record reads are MAC-verified
A member's record read SHALL ask the repo owner's PDS for a record MAC under a key agreed with the owner's `#atproto_kem` key, using a fresh key share for every request, and SHALL return the record only when the response's MAC is valid for the record's URI and CID.

#### Scenario: Valid MAC
- **WHEN** a member fetches a record and the response carries a valid record MAC
- **THEN** the client returns the record

#### Scenario: Retry after a rotated key
- **WHEN** a member's record read gets a MAC that doesn't verify
- **THEN** the client resolves the owner's DID document again and retries once with a new key share, returning the record if that MAC verifies

#### Scenario: MAC still wrong
- **WHEN** the retried read's MAC doesn't verify either, or the response has no MAC
- **THEN** the read fails with an error, not a found or no-access outcome, and the record isn't returned

#### Scenario: Owner reads need no MAC
- **WHEN** the signed-in athlete reads a record from their own space with their session
- **THEN** the request asks for no MAC and the read succeeds as before

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

#### Scenario: Visibility picker on an unsupported PDS
- **WHEN** an athlete whose PDS lacks Spaces support opens a form with a visibility picker
- **THEN** it offers only Public and says their server doesn't support private activities yet, not to sign in again

#### Scenario: Old scope on a supported PDS still asks to sign in
- **WHEN** an athlete whose PDS supports Spaces but whose session predates the `space:` scope makes a non-public save
- **THEN** the app asks them to sign in again, as before
