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

### Requirement: Clients time service-auth tokens by the PDS's clock
A client SHALL ask the athlete's PDS for a service-auth token without naming an expiry, so the PDS sets it. The client SHALL reuse a token only for its lifetime as the token states it (its expiry minus its issue time, both set by the PDS), counted from when the token arrived and ending a few seconds early. The device's clock SHALL NOT make a token look expired or valid in any other way. A token whose times the client can't read SHALL be sent for the one call it was minted for and not reused.

#### Scenario: Device clock running slow
- **WHEN** the device's clock is two minutes behind the PDS's and the athlete opens their feed
- **THEN** the PDS mints a token and the AppView call succeeds

#### Scenario: Device clock running fast
- **WHEN** the device's clock is two minutes ahead of the PDS's and the athlete makes two AppView calls to the same method ten seconds apart
- **THEN** the second call reuses the first call's token

#### Scenario: Token lifetime used up
- **WHEN** a token's stated lifetime is 60 seconds and the athlete calls the same method again 55 seconds after it arrived
- **THEN** the client asks the PDS for a new token

#### Scenario: Token whose times can't be read
- **WHEN** the PDS returns a token the client can't decode
- **THEN** the client sends it for that call and asks for a new token on the next call
