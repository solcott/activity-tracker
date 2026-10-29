# Spec Delta

## MODIFIED Requirements

### Requirement: Access check endpoint
The AppView SHALL serve `com.atproto.managedspace.checkUserAccess` as a query taking `space`, `user`, `access` and optional `clientId`, answering `{"authorized": <boolean>}`. Every access decision, including a denial, SHALL be a 200 response; errors are only for failed authentication or invalid parameters.

#### Scenario: Denial is a 200
- **WHEN** an authenticated callout asks about a user the space's tier doesn't admit
- **THEN** the AppView answers `200` with `{"authorized": false}`

#### Scenario: Missing parameter
- **WHEN** an authenticated callout omits `space`, `user` or `access`, or sends a `space` that isn't a space reference or a `user` that isn't a DID
- **THEN** the AppView answers `400 InvalidRequest`

#### Scenario: Client id is ignored
- **WHEN** a callout includes a `clientId`
- **THEN** the decision is the same as without it

#### Scenario: Old method name
- **WHEN** a request calls `com.atproto.simplespace.checkUserAccess`
- **THEN** the AppView doesn't serve it

### Requirement: Callouts authenticated as the space's authority
The endpoint SHALL require a service-auth JWT signed by the issuer's DID-document key, addressed to the managing-app id (`<serviceDid>#activitytracker_space`) and not expired, whose `lxm` is `com.atproto.managedspace.checkUserAccess`. A valid token whose issuer isn't the space's authority SHALL get `authorized: false`.

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
- **WHEN** a callout's token is from the space's authority and addressed to the managing-app id but has no `lxm`, or an `lxm` other than `com.atproto.managedspace.checkUserAccess` (the old `com.atproto.simplespace.checkUserAccess` included)
- **THEN** the AppView answers `401` and makes no decision

### Requirement: Only the authority writes
A `write` check, or a check with any `access` value other than `read`, SHALL be denied for every user and tier, the AppView's own service DID included. Under a `managedspace` policy the PDS asks about writes as well as reads, so these checks do arrive; a write check carries no `clientId`.

#### Scenario: Mutual asks to write
- **WHEN** a mutual of the authority is checked for `write` on any of the authority's spaces
- **THEN** the answer is `authorized: false`

#### Scenario: AppView asks to write
- **WHEN** the AppView's own service DID is checked for `write` on any of the authority's spaces
- **THEN** the answer is `authorized: false`

#### Scenario: Unknown access kind
- **WHEN** a check sends an `access` value other than `read` or `write`
- **THEN** the answer is `authorized: false`

#### Scenario: Authority writes without a check
- **WHEN** the authority writes to one of their own spaces
- **THEN** the PDS admits the write without asking the AppView, and the write succeeds
