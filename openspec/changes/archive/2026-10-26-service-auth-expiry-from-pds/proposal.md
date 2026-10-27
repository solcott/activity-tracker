# Proposal

## Why

Every signed-in call to the AppView uses a service-auth token that the athlete's PDS mints. `ServiceAuthAuthenticator` asks for that token with an `exp` of the device's clock plus 60 seconds (review finding C5). A PDS refuses an `exp` that's already past by its own clock, so a phone running about a minute slow can't get a token, and every AppView call fails until its clock is fixed. Nothing in the app explains why. A clock running fast causes a smaller problem: the client counts its cached token as good for less time than it really is.

The same class also changes its token cache outside its mutex in `clear()` (part of finding C8), which nothing calls.

## What Changes

- The client stops sending `exp` when it asks for a service-auth token, so the PDS sets the token's lifetime by its own clock (60 seconds by default).
- The client works out how long to reuse a token from the token's own `iat` and `exp`, both set by the PDS, counted from when the token arrived on the device's clock. A device clock that is off by any amount no longer makes a token look expired, or valid when it isn't.
- If the client can't read a token's claims, it sends the token for that one call and doesn't cache it. The AppView still decides whether the token is accepted.
- `ServiceAuthAuthenticator.clear()` is removed. It has no callers, and it was the one place that changed the cache without holding the mutex.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `auth/service-auth`: adds a requirement that clients get and reuse service-auth tokens by the PDS's clock, never the device's.

## Impact

- `core/atproto`: `ServiceAuthAuthenticator`, with a new `ServiceAuthAuthenticatorTest`. It uses the existing `jwtPayload`, so no new dependency.
- Used by `core/data` (`SessionClients`, every app platform) and `server/segment-importer` (`Clients`). Neither constructor call changes.
- No lexicon, AppView or wire-format change: `exp` is already optional in `GetServiceAuth.Params`, and the AppView's checks stay the same.
- Test fakes that return a token that isn't a JWT (`"service-jwt"`, the importer's `svc:…`) keep working, because an unreadable token is sent uncached.
