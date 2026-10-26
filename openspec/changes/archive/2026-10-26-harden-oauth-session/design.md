# Design

## Context

See proposal.md for why. What the code does today:

- **Refresh errors.** `OAuthClient.postWithDpop` throws `OAuthException` for every non-2xx answer, keeping the OAuth `error` only in its message. `refresh` also throws it for a session with no refresh token and for a refresh naming another account. A transport failure is an `IOException` and passes through untouched.
- **`OAuthSessionManager.freshSession`** runs under `mutex` and catches any `OAuthException` from `refresh`: it clears the store and sets `session` to null, which makes `App` drop the `SessionGraph` and show `LoginScreen`. Nothing else is cleared.
- **`signOut` and `completeSignIn`** write the store and `session` without `mutex`. `signOut` revokes `session.value` as it is when called.
- **`pdsAuthenticator`** keeps one `lastTokenRejected` flag for all requests: `shouldRetry` sets it on an `invalid_token` challenge, and the next `authenticate` from any request consumes it as `forceRefresh`. `XrpcClient` retries up to twice per request (`MAX_RETRIES`).
- **`AuthRepository.signOut`** (`core/data`, `AppScope`) runs `recorder.suspendForSignOut()`, `sessionManager.signOut()`, `db.feed().clearAll()` and `HttpCache.clearAllCaches()`. It's the only cleanup, and the expiry path never reaches it.
- **Errors.** `toDataError` maps `XrpcException` to `Api`/`Http`, `IOException` to `Network`, anything else to `Unknown`; `ErrorText` names a server problem for `Http` with a 5xx code.
- **Recording.** `ActivityRecorder` is `AppScope` and keys its journal by account DID (`RecordingState.did`). `App` calls `recover(did)` when a session's graph starts, and `recover` does nothing while any recording is running, so today another account signing in during a running recording would see it.
- **Login.** `LoginPresenter` builds an empty `TextFieldState` and shows `AuthCoordinator.status`. `OAuthSession` carries the account's `handle`.

## Goals / Non-Goals

**Goals:**
- One place decides that a session is dead (the refresh), and one place ends a session (`AuthRepository`), whatever ended it.
- Every write of the session (refresh, nonce update, sign-in, sign-out) under the one mutex.
- No new shared mutable state per request.

**Non-Goals:**
- Retrying a failed refresh in the background or with back-off: the next request that needs it tries again.
- Changing how sign-in itself reports failures (`AuthCoordinator.Status.Failed`).
- The refresh-token grace period after a lost response (the PDS's concern, not the client's).
- The other review findings in this file (C4, C5, C8's `ServiceAuthAuthenticator` race).

## Decisions

### 1. Classify refresh failures where they're made

`postWithDpop` throws `OAuthErrorException(status, error)` (a subclass of `OAuthException`, so sign-in's handling is unchanged) for a non-2xx answer, with `error` from the OAuth error body when there is one. `refresh` turns that into one of two outcomes:
- **`SessionExpiredException`** (an `OAuthException`): the answer's `error` is `invalid_grant`, `invalid_client` or `unauthorized_client`, the session has no refresh token, or the refreshed `sub` is another account.
- **`OAuthServerException(status)`**: any other non-2xx answer, including a non-OAuth error page and `use_dpop_nonce` twice in a row.

`IOException` still passes through as itself. `toDataError` (`core/data`) gets one branch: `OAuthServerException` becomes `DataError.Http(status)`, so a 5xx reads as a server problem and anything else as only what failed, under the existing `ErrorText` rules.

*Alternative:* treat every 4xx as dead. A `400 invalid_request` or `invalid_dpop_proof` from a misbehaving server would then still sign everyone out. The OAuth error codes are the only reliable signal.

### 2. One mutex for every session write

`signOut` and `completeSignIn` run under `mutex`, as `freshSession` and `updatePdsNonce` already do. `signOut` reads the session inside the lock, so it revokes the newest refresh token and a refresh that finished first can't be undone by it, and one that's still waiting runs after and finds no session. `signOut` takes `revoke: Boolean = true`, false for an expired session.

The cost is that signing out waits for a refresh in flight, bounded by the HTTP timeout. That's acceptable for a deliberate action.

### 3. The manager marks a session dead; `AuthRepository` ends it

`OAuthSessionManager` can't call `AuthRepository` (the dependency runs the other way). On `SessionExpiredException` from `refresh`, `freshSession`:
- keeps `session` as it is, sets `expired: StateFlow<OAuthSession?>` to it, and logs once;
- rethrows. Every later `freshSession` while `expired` is set throws `SessionExpiredException` at once, without calling the authorization server.

`AuthRepository.endExpiredSessions()`, a suspend function that collects `sessionManager.expired`, runs `endSession(expired = true)` for each one. It's launched once from `App`'s root, next to `restore()`, the way `App` already runs session-long work. `endSession` is the body `signOut` shares:
- the recorder is stopped only for a chosen sign-out (decision 5);
- `sessionManager.signOut(revoke = !expired)`, which also clears `expired`;
- `db.feed().clearAll()` and the HTTP cache;
- for an expiry, `lastExpiry: StateFlow<ExpiredSession?>` is set to the account's handle and DID, and cleared by the next completed sign-in or chosen sign-out.

`session` turns null only inside `signOut`, after the store is cleared and before the caches are, so `App` drops the `SessionGraph` with the session already gone. If the app is killed between marking and ending, the store still holds the dead session; the next launch's first refresh gets `invalid_grant` again and ends it then.

*Alternative:* a callback from the manager into `core/data`. That needs a late-bound hook to break the cycle, while a `StateFlow` is the pattern `session` already uses.

### 4. Refresh on a rejected token only while it's current

`lastTokenRejected` goes away. On an `invalid_token` challenge, `shouldRetry` reads the access token the request carried from `response.request.headers[Authorization]` (`DPoP <token>`) and calls `refreshIfCurrent(token)`, which, under the mutex, refreshes only when `session.value?.accessToken == token`. It then returns true, and the retry's `authenticate` reads the current session through `freshSession()` as usual. Two requests rejected with the same token cause one refresh; a request whose token was already replaced retries with the new one and refreshes nothing. A refresh that fails here throws from `shouldRetry`, so the request fails with that cause (decision 1).

### 5. An expiry leaves the recording running; another account stops it

`endSession(expired = true)` doesn't call `suspendForSignOut`. The recorder keeps sampling, its notification stays, and the journal keeps every event under the account's DID. `ActivityRecorder.recover(did)` changes:
- when a recording of another account is running or paused, it first runs `suspendForSignOut()` (kept for its account, offered back when that account returns), then recovers `did`'s unfinished recording as today;
- when the running recording is `did`'s own, it leaves it as it is.

`App` opens `RecordScreen` at sign-in when `recover` recovered a recording, as today, or when the recorder holds a running recording of this account, so signing back in after an expiry lands on the live run. A recording with no DID (`start(did = null)`) counts as nobody's and is left alone.

### 6. The login screen reads the expiry

`LoginState` gains `expired: Expiry?` (`recording: Boolean`). `LoginPresenter` takes `AuthRepository.lastExpiry` and `ActivityRecorder.state`: with an expiry, it starts the handle field with the expired handle (`rememberTextFieldState(initialText)`, keyed on the expiry), and `recording` is true while the recorder holds a running or paused recording of that DID. `LoginUi` shows the message above the field; the strings are `login_session_expired` and `login_session_expired_recording`. `LoginSamples` gets both states for the screenshot tests.

## Risks / Trade-offs

- **A misbehaving server that always answers 5xx keeps a dead session alive.** → Every request fails with a server error until it recovers or the athlete signs out; that's better than silently signing them out on every outage.
- **Sign-out can wait for a slow refresh.** → It's bounded by the HTTP timeout, and the refresh's result is then revoked rather than lost.
- **A recording keeps running with nobody signed in.** → It's the athlete's own run, the notification shows it, and it was always going to be published only after signing in. Another account signing in stops it first.
- **`expired` and `session` are both set for a moment.** → Screens see requests fail quickly in that window, and `endExpiredSessions` runs as soon as the flow emits.
- **Reading the token back from the request's header** ties `shouldRetry` to the `DPoP <token>` format `authenticate` writes, in the same class.
