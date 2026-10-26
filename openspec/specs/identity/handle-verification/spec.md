# identity/handle-verification Specification

## Purpose

Decides which handle the AppView and the client apps show for an account: a handle a DID document claims counts only when it resolves back to that DID, so no account can pass itself off under someone else's handle.

## Requirements

### Requirement: Handles verified both ways
The AppView SHALL store an account's handle only when the handle its DID document claims (`alsoKnownAs`) resolves, through the standard handle lookups, to that same DID. A claimed handle that resolves to another DID, or that no lookup resolves, SHALL be stored as `handle.invalid`. A DID document that claims no handle SHALL give `handle.invalid`.

#### Scenario: Matching handle
- **WHEN** alice's DID document claims `alice.example.com` and `alice.example.com` resolves to alice's DID
- **THEN** the AppView shows alice as `alice.example.com`

#### Scenario: Impersonating DID
- **WHEN** a DID document claims `alice.example.com` but `alice.example.com` resolves to a different DID
- **THEN** the AppView stores and shows that account's handle as `handle.invalid`

#### Scenario: Handle that resolves to nothing
- **WHEN** a DID document claims a handle for which every lookup answers that it has no DID
- **THEN** the AppView stores `handle.invalid`

### Requirement: When handles are checked
The AppView SHALL verify an account's handle the first time it indexes anything from that account, whenever a Jetstream `#identity` event names that account, and again in the background once the account's last check is more than 24 hours old.

#### Scenario: First record from an account
- **WHEN** the AppView indexes the first record from a DID it has never seen
- **THEN** it verifies that DID's claimed handle before storing the account

#### Scenario: Identity event
- **WHEN** a Jetstream `#identity` event arrives for a known account, with or without a handle
- **THEN** the AppView resolves the account's DID document and verifies its claimed handle again, rather than trusting the handle in the event

#### Scenario: Stale check
- **WHEN** an account's handle was last checked more than 24 hours ago
- **THEN** the AppView re-verifies it without waiting for any event, and updates the stored handle if the result changed

### Requirement: Unreachable lookups keep the last verified handle
When a handle check can't reach the DID document or every handle lookup fails to answer, the AppView SHALL keep the account's last verified handle and retry later. An account with no verified handle yet SHALL show `handle.invalid` until a check succeeds.

#### Scenario: Lookup down for a known account
- **WHEN** re-verifying alice's handle fails because the handle's DNS and well-known lookups can't be reached
- **THEN** alice keeps her last verified handle and is checked again later

#### Scenario: Lookup down for a new account
- **WHEN** the first check for a new account can't reach any handle lookup
- **THEN** the account is stored as `handle.invalid` and re-checked later, and shows its handle once a check succeeds

### Requirement: Lookups by handle match only verified handles
Every AppView query that takes an actor as a handle SHALL find an account only under a handle it verified. A query for `handle.invalid` SHALL find no one.

#### Scenario: Impersonated handle
- **WHEN** a viewer asks for the profile of `alice.example.com` and the only account claiming it failed verification
- **THEN** the AppView answers that the actor isn't found

#### Scenario: Query for handle.invalid
- **WHEN** a viewer asks for the profile of `handle.invalid`
- **THEN** the AppView answers that the actor isn't found

#### Scenario: Lookup by DID
- **WHEN** a viewer asks for an account by its DID while its handle is `handle.invalid`
- **THEN** the AppView returns the account, showing `handle.invalid`

### Requirement: Clients verify a DID sign-in's handle
When a person signs in by typing a DID, the client SHALL show the handle the DID document claims only if that handle resolves back to the same DID, and `handle.invalid` otherwise, including when the lookup can't be reached. Signing in SHALL NOT fail because the handle didn't verify.

#### Scenario: Sign in with a DID that claims a matching handle
- **WHEN** someone signs in with alice's DID and her DID document's handle resolves to that DID
- **THEN** the session shows `alice.example.com`

#### Scenario: Sign in with a DID that claims someone else's handle
- **WHEN** someone signs in with a DID whose document claims a handle that resolves to another DID
- **THEN** sign-in completes and the session shows `handle.invalid`
