# Spec Delta

## MODIFIED Requirements

### Requirement: Token bound to the called method
Every AppView endpoint that accepts service auth SHALL require the token's `lxm` claim to be present and equal to the NSID of the method being called. This SHALL hold for the AppView's own routes, the managing-app endpoint and `notifySpace` alike. The space host's calls to the syncer (`com.atproto.space.confirmRegistration`, `notifySpaceUpdate`) carry no service auth: they are authenticated by the token the AppView registered (`spaces/appview-syncer`).

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
- **WHEN** an `app.activitytracker.space.notifySpace` call carries a token from the athlete, addressed to the AppView, with no `lxm`
- **THEN** the AppView answers `401` and joins nothing

#### Scenario: Managing-app callout without lxm
- **WHEN** a `com.atproto.managedspace.checkAccess` callout carries a token from the space's authority, addressed to the managing-app id, with no `lxm`
- **THEN** the AppView answers `401` and makes no decision

#### Scenario: Service auth doesn't replace a syncer token
- **WHEN** a `notifySpaceUpdate` carries a valid service-auth token from the space's authority but not the token the AppView issued for the space
- **THEN** the AppView answers `400 InvalidToken` and fetches nothing
