# Spec Delta

## REMOVED Requirements

### Requirement: Signed space requests
**Reason**: The 2026-10-22 Spaces alpha no longer signs credential requests, and credentialed reads use a new scheme and signature label.
**Migration**: Replaced by "Credential requests carry their key in the body" and "Signed credentialed reads".

## ADDED Requirements

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

## MODIFIED Requirements

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
