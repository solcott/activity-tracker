# Spec Delta

## MODIFIED Requirements

### Requirement: AppView joins as a member
To read a tier space, the AppView SHALL obtain a space credential from the space's authority PDS using a delegation token it signs as itself, addressed to the authority's `#atproto_pds` service, and SHALL reuse the credential until shortly before it expires.

#### Scenario: Credential for a tier space
- **WHEN** the AppView joins alice's `mutuals` space
- **THEN** alice's PDS asks `checkUserAccess` about the AppView's DID, the AppView admits itself, and the AppView holds a credential for that space

#### Scenario: Delegation token audience
- **WHEN** the AppView signs a delegation token for a space whose authority is alice
- **THEN** the token's `aud` is alice's DID followed by `#atproto_pds`, and its header `kid` is `#atproto`

## ADDED Requirements

### Requirement: Pulled ops indexed only under a verified commit
Every `listRepoOps` request the AppView makes as a syncer SHALL ask for a repo MAC under a key agreed with the space authority's `#atproto_kem` key, with a fresh key share each time. The AppView SHALL apply a pull's ops and record its revision only after the commit that ends the pull carries a valid MAC for that space and repo.

#### Scenario: Verified pull
- **WHEN** alice writes to her `followers` space and the AppView pulls the new ops with a valid commit MAC
- **THEN** the ops are indexed and the commit's revision becomes the space's last indexed revision

#### Scenario: Commit for another space
- **WHEN** a pull's commit names a different space or repo than the one requested
- **THEN** the AppView indexes none of that pull's ops and keeps the previous revision

#### Scenario: MAC fails twice
- **WHEN** a pull's commit MAC doesn't verify, and still doesn't after the AppView re-resolves alice's DID document and pulls again with a new key share
- **THEN** that space's sync fails and is retried with backoff, none of the pull's ops are indexed, and other spaces keep syncing

#### Scenario: Key rotated
- **WHEN** alice's `#atproto_kem` key changed since the AppView last resolved her DID document
- **THEN** the first MAC fails, the retry with her new key verifies, and the pull is indexed
