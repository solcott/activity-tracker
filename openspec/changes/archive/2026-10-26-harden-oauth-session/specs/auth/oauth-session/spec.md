# Spec Delta

## Purpose

Defines how the client keeps an athlete's OAuth session: when refreshing its tokens ends it and when it doesn't, how refreshing, signing in and signing out are kept from overwriting each other, what ending an expired session clears, how a rejected access token is refreshed, and what the athlete is told when their session expired.

## ADDED Requirements

### Requirement: Only a dead session is ended by a refresh
A token refresh SHALL end the session only when the authorization server refuses the grant (`invalid_grant`, `invalid_client` or `unauthorized_client`), when the session has no refresh token, or when the refresh returns a different account.

#### Scenario: Refresh token revoked
- **WHEN** a refresh is answered with `invalid_grant`
- **THEN** the session ends

#### Scenario: Refresh returns another account
- **WHEN** a refresh succeeds but names a different account than the session's
- **THEN** the session ends

### Requirement: Any other refresh failure keeps the session
A refresh that fails for any other reason SHALL keep the session, so that a later request refreshes again. The request that needed the refresh SHALL fail, reported as a problem on the server for a server error and as the device being offline when the authorization server couldn't be reached.

#### Scenario: Authorization server unavailable
- **WHEN** a refresh gets a `503` from the authorization server
- **THEN** the athlete stays signed in, the request fails saying the server had a problem, and the next request tries the refresh again

#### Scenario: Rate limited
- **WHEN** a refresh gets a `429`
- **THEN** the athlete stays signed in and the request fails

#### Scenario: Offline
- **WHEN** a refresh can't reach the authorization server
- **THEN** the athlete stays signed in and the request fails saying the device is offline

### Requirement: Refreshing, signing in and signing out don't overwrite each other
Signing out, completing a sign-in and refreshing SHALL each finish before the next starts. A refresh SHALL never save a session after a sign-out, and signing out SHALL revoke the newest refresh token the session holds.

#### Scenario: Sign out during a refresh
- **WHEN** the athlete signs out while a refresh is waiting for the authorization server, and the refresh then succeeds
- **THEN** the athlete stays signed out, the refreshed tokens are not saved, and the refresh token that was revoked is the newest one

#### Scenario: Sign-in completes during a refresh
- **WHEN** a sign-in completes while an earlier session's refresh is running
- **THEN** the signed-in session is the new one

### Requirement: An expired session is cleared like a sign-out
When a refresh ends the session, the app SHALL clear what a sign-out clears: the stored session, the cached feed and the cached responses. It SHALL NOT try to revoke the dead token. Requests made after the session ended SHALL fail without trying the refresh again.

#### Scenario: Expiry clears the cached feed
- **WHEN** an athlete's refresh is answered with `invalid_grant`
- **THEN** the app shows the login screen, and the feed cached for that session, including other athletes' Followers and Mutuals activities, is gone

#### Scenario: Requests in flight when the session expires
- **WHEN** several requests need a refresh at once and the refresh is answered with `invalid_grant`
- **THEN** the authorization server is asked once, and every request fails

### Requirement: A rejected access token is refreshed once
When the athlete's PDS rejects an access token as invalid, the app SHALL refresh and retry only if that token is still the session's current one. A request whose token was already replaced SHALL retry with the current token without refreshing again.

#### Scenario: Concurrent requests with a rejected token
- **WHEN** two requests sent with the same access token are both rejected as `invalid_token`
- **THEN** the app refreshes once and both retry with the new token

#### Scenario: Retry never reuses the rejected token
- **WHEN** a request's token is rejected after another request already refreshed
- **THEN** the request retries with the refreshed token, not the rejected one, and no further refresh is made

### Requirement: The login screen says the session expired
After a session ends because its refresh was refused, the login screen SHALL say that the session expired and that signing in again continues, and SHALL fill in that account's handle. While a recording is running, it SHALL also say the recording is still going. After a sign-out the athlete chose, the login screen SHALL show neither message nor the handle.

#### Scenario: Expired without a recording
- **WHEN** alice's session expires
- **THEN** the login screen says her session expired, with `alice.test` filled in

#### Scenario: Expired during a recording
- **WHEN** alice's session expires 20 minutes into a run
- **THEN** the login screen also says her recording is still running

#### Scenario: Chosen sign-out
- **WHEN** alice signs out from settings
- **THEN** the login screen shows no expiry message and an empty handle field
