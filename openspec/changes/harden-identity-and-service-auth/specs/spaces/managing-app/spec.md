# Spec Delta

## MODIFIED Requirements

### Requirement: Callouts authenticated as the space's authority
The endpoint SHALL require a service-auth JWT signed by the issuer's DID-document key, addressed to the managing-app id (`<serviceDid>#activitytracker_space`) and not expired, whose `lxm` is `com.atproto.simplespace.checkUserAccess`. A valid token whose issuer isn't the space's authority SHALL get `authorized: false`.

#### Scenario: Callout from the authority's PDS
- **WHEN** a PDS calls with a token whose `iss` is the space's authority and `aud` is the managing-app id
- **THEN** the AppView decides by the space's tier

#### Scenario: No token
- **WHEN** a request has no `Authorization` header
- **THEN** the AppView answers `401` and makes no decision

#### Scenario: Token for the AppView itself
- **WHEN** a token's `aud` is the AppView's service DID or `<serviceDid>#activity_appview`
- **THEN** the endpoint rejects it with `401`

#### Scenario: Managing-app token on other routes
- **WHEN** a token addressed to the managing-app id is sent to any other `app.activitytracker.*` route
- **THEN** that route rejects it with `401`

#### Scenario: Issuer isn't the authority
- **WHEN** a validly signed token's `iss` is a DID other than the space's authority
- **THEN** the AppView answers `{"authorized": false}`

#### Scenario: Token without lxm
- **WHEN** a callout's token is from the space's authority and addressed to the managing-app id but has no `lxm`, or an `lxm` other than `com.atproto.simplespace.checkUserAccess`
- **THEN** the AppView answers `401` and makes no decision
