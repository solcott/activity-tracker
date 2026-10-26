# Proposal

## Why

The 2026-10-23 codebase review found two ways the app signs athletes out, or back in, when it shouldn't. Any failed token refresh ends the session, so a 503 or 429 from the authorization server drops the athlete to the login screen, mid-run included (C1). And sign-out doesn't wait for a refresh that's running, so the refresh can finish afterwards and sign the athlete back in with a token nobody revoked (C2). Exploring those turned up two more: a session that ends because its refresh was refused skips everything a sign-out cleans up (the recording's sign-out handling, the cached feed of other athletes' private activities, the HTTP cache), and concurrent requests share one "token rejected" flag, so they refresh more than they need to or retry with the token just refused (C6).

## What Changes

- **Only a dead session signs out.** A refresh ends the session only when the authorization server refuses the grant (`invalid_grant`, `invalid_client`, `unauthorized_client`), the session has no refresh token, or the refresh returns another account. Any other failure (a server error, a rate limit, a response that isn't an OAuth error) keeps the session, and the request that needed the refresh fails as a server problem, or as offline when the device couldn't connect, as it already does.
- **Sign-out and sign-in wait for a refresh in progress.** Signing out, completing a sign-in and refreshing take the same lock, so a refresh can't save a session after a sign-out, and sign-out revokes the newest refresh token.
- **An expired session ends the way a sign-out does.** The feed cache and HTTP cache are cleared and the session forgotten, through the same path as signing out, with no revocation (the token is already dead). Requests after the expiry fail at once instead of refreshing again.
- **An expiry doesn't stop a recording.** A recording in progress keeps going when the session expires. Signing back in as the same account returns to it; signing in as another account stops it and keeps it for its account, as signing out does.
- **The login screen says the session expired.** After an expiry, the login screen says so (and that the recording is still running, if one is), with the expired account's handle filled in. After a sign-out the athlete chose, it says nothing.
- **A rejected token is refreshed once.** When the PDS rejects an access token, the app refreshes only if that token is still the current one, so concurrent requests share one refresh and none retries with a token already replaced.

## Capabilities

### New Capabilities
- `auth/oauth-session`: How the client keeps an OAuth session: when a refresh ends it and when it doesn't, how refreshing, signing in and signing out are serialized, what an expired session clears, how rejected tokens are refreshed, and what the login screen says after an expiry.

### Modified Capabilities
- `record/gps-recording`: "Signing out keeps the recording for its account" now distinguishes an expired session, which leaves the recording running, from a sign-out, and covers another account signing in while a recording runs.

## Impact

- **core/atproto** (`oauth/`): `OAuthClient.refresh` classifies failures (a dead grant versus a server or transport failure); `OAuthSessionManager` takes its mutex in `signOut` and `completeSignIn`, marks a dead session, reports the expiry instead of clearing it itself, and refreshes on a rejected token only while that token is current. `RequestAuthenticator`'s contract is unchanged.
- **core/data**: `AuthRepository` ends an expired session through the same cleanup as `signOut` (no revocation, the recording left running) and remembers the expiry for the login screen; `toDataError` classifies the new server-failure exception as `DataError.Http`; `AuthCoordinator` exposes the expiry.
- **app and composeApp**: `LoginPresenter`/`LoginState` carry the expiry and pre-fill the handle; `LoginUi` shows the message; `App` stops another account's running recording before offering the new account's. New strings in `composeResources/values`.
- **Tests**: `OAuthFlowTest` and new `OAuthSessionManager` tests on `MockNetwork`, `AuthRepository`/`AuthCoordinatorTest`, `LoginPresenterTest`, login screenshot samples.
- No server, lexicon or dependency changes.
