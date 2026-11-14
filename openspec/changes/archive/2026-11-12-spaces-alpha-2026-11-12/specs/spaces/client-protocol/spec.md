# Spec Delta

## MODIFIED Requirements

### Requirement: Denied access is a result, not an error
When the authority's PDS refuses a viewer access to a space, the client SHALL report "no access" for that read as a distinct outcome from failures such as network errors.

#### Scenario: Non-member read
- **WHEN** a viewer the space's policy doesn't admit reads a record from it and the PDS answers `AccountNotAuthorized`, `AppNotAuthorized` or `NotAuthorized`
- **THEN** the read returns a no-access outcome, not an exception, and the denial is not cached as a credential

#### Scenario: Refusal answered as not found
- **WHEN** a viewer the space's policy doesn't admit reads a record from it and the PDS answers `SpaceNotFound`, as the reference authority does for a refused account
- **THEN** the read returns a not-found outcome, not an exception, and the client draws no conclusion about whether the space exists

#### Scenario: Network failure stays an error
- **WHEN** the authority's PDS can't be reached during a read
- **THEN** the read fails with an error, distinguishable from no access

### Requirement: Credential requests carry their key in the body
A credential request (`com.atproto.space.authorizeSpaceSync`) SHALL be a JSON POST whose body holds the space, the delegation token and the P-256 `did:key` the credential will bind (`credentialKey`), and SHALL carry no `Authorization` header and no signature. A client's request SHALL carry no `notify`, so it registers for no notifications.

#### Scenario: Credential request is unsigned
- **WHEN** the client asks an authority's PDS for a space credential
- **THEN** the request body holds the space, the delegation token and a P-256 `did:key` as `credentialKey`, and the request has no `Authorization`, `Signature-Input` or `Signature` header

#### Scenario: Clients don't register
- **WHEN** an app client asks an authority's PDS for a space credential
- **THEN** the request body has no `notify`

### Requirement: Member record reads are MAC-verified
A member's record read SHALL ask the repo owner's PDS for a record MAC (`Accept-Signature`, profile `atproto-space-record`) under a key agreed by HPKE secret export with the owner's `#atproto_kem` key, sending a fresh key share as the `keyid` for every request. It SHALL return the record only when the response carries a valid MAC: an RFC 9421 `hmac-sha256` signature covering exactly the `Atproto-Space-Record` header, whose `uri` and `cid` are the URI asked for and the body's CID.

#### Scenario: Valid MAC
- **WHEN** a member fetches a record and the response carries a valid record MAC
- **THEN** the client returns the record

#### Scenario: Signed header disagrees with the body
- **WHEN** the `Atproto-Space-Record` header a valid MAC covers names another URI, or a CID other than the body's
- **THEN** the MAC check fails as if the MAC were wrong

#### Scenario: Signature over other components
- **WHEN** the response's signature tagged `atproto-space-record` covers any components other than exactly `("atproto-space-record";sf)`, or names a `keyid` or `alg` other than the request's key share and `hmac-sha256`
- **THEN** the MAC check fails

#### Scenario: Signature under another label
- **WHEN** the host answers with the MAC under a label other than the one the client asked for
- **THEN** the client finds it by its `atproto-space-record` tag and checks it as usual

#### Scenario: MAC matches the reference implementation
- **WHEN** the client asks for and checks a MAC with a fixed author key and key share
- **THEN** its `Accept-Signature` and shared secret (HPKE base mode, DHKEM P-256, HKDF-SHA256, export only, `info` `atproto-mac`) are byte-identical to those the pinned release's `@atproto/space` produces for the same input, and it accepts the commit and record MAC headers that code makes under that secret

#### Scenario: Retry after a rotated key
- **WHEN** a member's record read gets a MAC that doesn't verify
- **THEN** the client resolves the owner's DID document again and retries once with a new key share, returning the record if that MAC verifies

#### Scenario: MAC still wrong
- **WHEN** the retried read's MAC doesn't verify either, or the response has no MAC
- **THEN** the read fails with an error, not a found or no-access outcome, and the record isn't returned

#### Scenario: Owner reads need no MAC
- **WHEN** the signed-in athlete reads a record from their own space with their session
- **THEN** the request asks for no MAC and the read succeeds as before

### Requirement: Owner deletes a space
A client SHALL be able to delete one of the signed-in athlete's own spaces by its URI. Deleting a space that no longer exists SHALL succeed as already done, so a repeated delete is harmless. A refusal for a missing scope SHALL be reported as such.

#### Scenario: Deleting a tier space
- **WHEN** alice's client deletes her Followers space
- **THEN** the PDS no longer has it, and the AppView, prompted by her PDS's notice and confirmed by its next authorization of the space, drops what it held from it

#### Scenario: Already deleted
- **WHEN** alice's client deletes her `self` space a second time
- **THEN** the call succeeds without error
