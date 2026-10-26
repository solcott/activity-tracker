# Spec Delta

## Purpose

Limits which hosts the AppView will contact when the address comes from a DID, a DID document or a handle, so that anyone who can make it resolve an identity cannot use it to reach hosts inside its own network.

## ADDED Requirements

### Requirement: No private addresses from identities
Every AppView request to a host taken from a DID, a DID document or a handle SHALL refuse to connect when the host resolves to a loopback, private, link-local, unique-local, unspecified or multicast address, IPv4 or IPv6. The address checked SHALL be the one connected to, so a name that resolves differently on a later lookup can't get past the check.

#### Scenario: did:web naming an internal address
- **WHEN** a request carries a token whose `iss` is `did:web:10.0.0.5`
- **THEN** the AppView opens no connection to `10.0.0.5` and answers `401`

#### Scenario: did:web host resolving to loopback
- **WHEN** a token's `iss` is a `did:web` whose host name resolves to `127.0.0.1` or `::1`
- **THEN** the AppView opens no connection and answers `401`

#### Scenario: PDS endpoint on a private address
- **WHEN** an account's DID document lists a `#atproto_pds` endpoint whose host resolves to `192.168.1.10`, and a viewer opens one of its photos or streams
- **THEN** the AppView doesn't fetch from that host and treats the blob as unavailable

#### Scenario: Handle well-known on a link-local address
- **WHEN** verifying a handle whose domain resolves to `169.254.169.254`
- **THEN** the AppView doesn't request its `/.well-known/atproto-did`, and that lookup counts as unanswered

#### Scenario: Public host
- **WHEN** an account's PDS endpoint resolves to a public address
- **THEN** the AppView fetches from it as before

### Requirement: Identifiers validated before any request
The AppView SHALL make no request for a DID that isn't syntactically valid for its method: a `did:plc` SHALL have only lowercase base32 characters (`a`–`z`, `2`–`7`) after the prefix, and a `did:web` SHALL name a valid host name, with an optional port, and no path. Other DID methods SHALL be refused.

#### Scenario: did:plc carrying a path
- **WHEN** the AppView is asked to resolve `did:plc:abc/../../export`
- **THEN** it refuses without a request

#### Scenario: did:web with a path
- **WHEN** the AppView is asked to resolve `did:web:example.com:users:alice`
- **THEN** it refuses without a request

#### Scenario: Unsupported method
- **WHEN** the AppView is asked to resolve `did:key:z6Mk...`
- **THEN** it refuses without a request

### Requirement: Configured endpoints are trusted
Requests to endpoints the operator configures (the PLC directory, the handle resolver, Jetstream, the labelers and Ozone, including endpoints found in the configured labelers' own DID documents) SHALL NOT be subject to the private-address rule.

#### Scenario: Internal PLC directory
- **WHEN** the configured PLC directory is on a private network address
- **THEN** the AppView resolves `did:plc` documents through it

#### Scenario: Configured labeler on the local network
- **WHEN** the configured labeler's DID document lists a labeler endpoint on a private address
- **THEN** the AppView subscribes to its labels

### Requirement: Private addresses allowed for local development
The AppView SHALL have a configuration setting, off by default, that lifts the private-address rule for addresses from identities, so it works against a local stack where every PDS and `did:web` is on `localhost`. The local development commands SHALL turn it on, and the AppView SHALL log a warning at startup while it is on.

#### Scenario: Default configuration
- **WHEN** the AppView starts without the setting
- **THEN** addresses from identities that resolve to private addresses are refused

#### Scenario: Local stack
- **WHEN** the AppView runs through the local development commands, with the setting on
- **THEN** it fetches blobs from `http://localhost:2583`, resolves `did:web:localhost%3A8081`, and logs that private addresses are allowed
