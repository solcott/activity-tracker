## ADDED Requirements

### Requirement: Conflicting DNS records make a handle unresolvable
When the DNS TXT lookup for `_atproto.<handle>` returns `did=` records naming more than one DID, the handle SHALL resolve to no account, with no fallback to the HTTPS `.well-known` lookup. Several records naming the same DID SHALL resolve to that DID.

#### Scenario: Two records name different DIDs
- **WHEN** `_atproto.alice.example.com` has TXT records `did=did:plc:alice` and `did=did:plc:mallory`
- **THEN** resolving `alice.example.com` finds no account, even if its `.well-known/atproto-did` answers with a DID

#### Scenario: Duplicate records name one DID
- **WHEN** `_atproto.alice.example.com` has two TXT records, both `did=did:plc:alice`
- **THEN** `alice.example.com` resolves to `did:plc:alice`

#### Scenario: Unrelated TXT records
- **WHEN** `_atproto.alice.example.com` has one `did=did:plc:alice` record and other TXT records that don't start with `did=`
- **THEN** `alice.example.com` resolves to `did:plc:alice`

#### Scenario: AppView check of a conflicting handle
- **WHEN** a DID document claims a handle whose DNS records name more than one DID
- **THEN** the AppView stores that account's handle as `handle.invalid`
