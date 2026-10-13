# Spec Delta

## ADDED Requirements

### Requirement: Access check endpoint
The AppView SHALL serve `com.atproto.simplespace.checkUserAccess` as a query taking `space`, `user`, `access` and optional `clientId`, answering `{"authorized": <boolean>}`. Every access decision, including a denial, SHALL be a 200 response; errors are only for failed authentication or invalid parameters.

#### Scenario: Denial is a 200
- **WHEN** an authenticated callout asks about a user the space's tier doesn't admit
- **THEN** the AppView answers `200` with `{"authorized": false}`

#### Scenario: Missing parameter
- **WHEN** an authenticated callout omits `space`, `user` or `access`, or sends a `space` that isn't a space reference or a `user` that isn't a DID
- **THEN** the AppView answers `400 InvalidRequest`

#### Scenario: Client id is ignored
- **WHEN** a callout includes a `clientId`
- **THEN** the decision is the same as without it

### Requirement: Callouts authenticated as the space's authority
The endpoint SHALL require a service-auth JWT signed by the issuer's DID-document key, addressed to the managing-app id (`<serviceDid>#activitytracker_space`) and not expired, whose `lxm`, if present, is `com.atproto.simplespace.checkUserAccess`. A valid token whose issuer isn't the space's authority SHALL get `authorized: false`.

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

### Requirement: Read access by tier
For a space of type `app.activitytracker.space.athlete`, a `read` check SHALL be decided by the space key: `public` admits any user; `mutuals` admits a user who follows the authority and is followed by them in this app's follow graph; `self` and every other key admit no one. Any other space type SHALL be denied.

#### Scenario: Public tier
- **WHEN** any user is checked for `read` on the authority's `public` space
- **THEN** the answer is `authorized: true`, even if neither follows the other

#### Scenario: Mutual reads the mutuals tier
- **WHEN** a user who follows the authority, and whom the authority follows, is checked for `read` on the `mutuals` space
- **THEN** the answer is `authorized: true`

#### Scenario: One-way follower
- **WHEN** a user follows the authority but the authority doesn't follow them back, or the reverse
- **THEN** a `read` check on the `mutuals` space answers `authorized: false`

#### Scenario: Unfollow takes effect
- **WHEN** either side of a mutual follow deletes their follow record and the AppView has indexed the deletion
- **THEN** a later `read` check on the `mutuals` space answers `authorized: false`

#### Scenario: Only-me tier
- **WHEN** any user other than the authority, mutual or not, is checked for `read` on the `self` space
- **THEN** the answer is `authorized: false`

#### Scenario: Unknown tier
- **WHEN** a `read` check names a space of type `app.activitytracker.space.athlete` with a key other than `public`, `mutuals` or `self`
- **THEN** the answer is `authorized: false`

#### Scenario: Other space type
- **WHEN** a check names a space of any other type
- **THEN** the answer is `authorized: false`

#### Scenario: Bluesky follows don't count
- **WHEN** two accounts follow each other only with `app.bsky.graph.follow` records
- **THEN** a `read` check on the `mutuals` space answers `authorized: false`

### Requirement: Only the authority writes
A `write` check, or a check with any `access` value other than `read`, SHALL be denied for every user and tier.

#### Scenario: Mutual asks to write
- **WHEN** a mutual of the authority is checked for `write` on any of the authority's spaces
- **THEN** the answer is `authorized: false`

#### Scenario: Unknown access kind
- **WHEN** a check sends an `access` value other than `read` or `write`
- **THEN** the answer is `authorized: false`

### Requirement: Managing-app spaces readable end to end
On the local stack with the tunnel, a viewer the tier admits SHALL be able to obtain a credential and read a record from an athlete's managing-app space, and a viewer it doesn't admit SHALL be refused.

#### Scenario: Mutual reads through the PDS
- **WHEN** alice creates her `mutuals` space with the AppView as managing app for reads, and bob, her mutual, reads a record from it
- **THEN** alice's PDS calls `checkUserAccess`, the AppView authorizes bob, and bob reads the record

#### Scenario: Non-mutual refused through the PDS
- **WHEN** carol, who isn't alice's mutual, reads from alice's `mutuals` space
- **THEN** the PDS refuses the credential and carol's client reports no access
