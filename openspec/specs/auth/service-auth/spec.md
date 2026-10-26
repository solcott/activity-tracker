# auth/service-auth Specification

## Purpose

Defines how the AppView authenticates inter-service JWTs (from a user's PDS, a proxying PDS, or a space authority's PDS): which claims it requires and how it obtains the issuer's key without being turned against its own network.

## Requirements

### Requirement: Token bound to the called method
Every AppView endpoint that accepts service auth SHALL require the token's `lxm` claim to be present and equal to the NSID of the method being called. This SHALL hold for the AppView's own routes, the managing-app endpoint and the syncer callouts alike.

#### Scenario: Token without lxm
- **WHEN** a correctly signed, unexpired token addressed to the AppView has no `lxm` claim
- **THEN** the AppView answers `401` and the route does not run

#### Scenario: Token for another method
- **WHEN** a correctly signed token whose `lxm` is `app.activitytracker.feed.getTimeline` is sent to `app.activitytracker.feed.getActivity`
- **THEN** the AppView answers `401`

#### Scenario: Token for the called method
- **WHEN** a correctly signed, unexpired token addressed to the AppView names the called method in `lxm`
- **THEN** the route runs as the token's issuer

#### Scenario: Syncer callout without lxm
- **WHEN** a `com.atproto.space.notifyWrite` callout carries a token from the space's authority, addressed to the syncer service, with no `lxm`
- **THEN** the AppView answers `401` and fetches nothing

### Requirement: Claims checked before the issuer is resolved
The AppView SHALL reject a token whose header or payload is malformed, whose `iss` is not a syntactically valid DID, whose `aud` is not one the route accepts, whose `exp` is missing or past, or whose `lxm` is wrong, without resolving the issuer's DID or making any outbound request.

#### Scenario: Wrong audience
- **WHEN** a token's `aud` is not one of the route's audiences
- **THEN** the AppView answers `401` and makes no outbound request

#### Scenario: Malformed issuer
- **WHEN** a token's `iss` is `did:plc:abc/../../admin` or any other string that isn't a valid DID
- **THEN** the AppView answers `401` and makes no outbound request

### Requirement: Failed issuer resolution is remembered
When resolving an issuer's DID document fails, the AppView SHALL answer `401` and SHALL NOT try to resolve that DID again for a short period (minutes), so repeated tokens naming the same unresolvable issuer cause at most one outbound request in that period.

#### Scenario: Repeated tokens from an unresolvable issuer
- **WHEN** many requests within a minute carry tokens whose `iss` names a DID whose document can't be fetched
- **THEN** each answers `401` and the AppView fetches that DID document once

#### Scenario: Issuer becomes resolvable
- **WHEN** a DID whose resolution failed is resolvable again after the period has passed
- **THEN** the next correctly signed token from it is accepted
