# Tasks

## 1. Classify refresh failures (C1)

- [x] 1.1 In `core/atproto` `oauth/`, add `OAuthErrorException(status, error)`, `SessionExpiredException` and `OAuthServerException(status)`, all subclasses of `OAuthException`. Make `postWithDpop` throw `OAuthErrorException` for a non-2xx answer, and `refresh` map it as design decision 1: `invalid_grant`/`invalid_client`/`unauthorized_client`, a missing refresh token or another `sub` → `SessionExpiredException`; any other non-2xx → `OAuthServerException`. Leave `IOException` untouched. Verify `OAuthFlowTest` still passes with `./gradlew :core-atproto:jvmTest --tests "*.OAuthFlowTest"`.
- [x] 1.2 Add a `toDataError` branch mapping `OAuthServerException` to `DataError.Http(status)`, with `OutcomeOfTest` cases for a 503 (server problem) and a 429. Verify with `./gradlew :core-data:jvmTest --tests "*.OutcomeOfTest"`.

## 2. Session manager (C1, C2, C6, the expiry signal)

- [x] 2.1 In `OAuthSessionManager`, add `expired: StateFlow<OAuthSession?>`. On `SessionExpiredException`, `freshSession` sets it, keeps `session`, logs once and rethrows; while it's set, `freshSession` throws `SessionExpiredException` without a request. Any other refresh failure leaves both as they are. Replace `rejectedRefreshSignsOutAndLogs` with `MockNetwork` tests: a `503` and a `429` keep the session and throw `OAuthServerException`; an offline refresh keeps it and throws `IOException`; `invalid_grant` marks it expired, and three concurrent `freshSession(forceRefresh = true)` calls make one token request and all throw.
- [x] 2.2 Run `signOut` and `completeSignIn` under `mutex`, give `signOut` a `revoke` parameter (default true) that also clears `expired`, and read the session to revoke inside the lock. Test with a token endpoint that suspends until released: `signOut` during the refresh leaves `session` and the store null after the refresh succeeds, and the revocation request carries the refreshed refresh token.
- [x] 2.3 Replace `lastTokenRejected` with `refreshIfCurrent(token)`, called from `shouldRetry` with the token read back from the request's `Authorization` header (design decision 4). Test through `XrpcClient` on `MockNetwork`: two concurrent requests rejected with the same token cause one refresh and both retry with the new token; a request rejected after another already refreshed retries with the new token and makes no token request. Verify group 2 with `./gradlew :core-atproto:jvmTest`.

## 3. One way to end a session (cleanup gap)

- [x] 3.1 In `AuthRepository`, move `signOut`'s body into `endSession(expired)`: the recorder is stopped only when `expired` is false, then `sessionManager.signOut(revoke = !expired)`, `db.feed().clearAll()` and the HTTP cache. Add `lastExpiry: StateFlow<ExpiredSession?>` (handle and DID), set for an expiry and cleared by a completed sign-in or a chosen sign-out, and `endExpiredSessions()`, which collects `sessionManager.expired` and ends each one. Launch it once from `App`'s root next to `restore()`.
- [x] 3.2 Add `core/data` tests (in `AuthCoordinatorTest` or a new `AuthRepositoryTest`): an `invalid_grant` refresh empties the feed cache, nulls the session and the store, sets `lastExpiry` to the account's handle, makes no revocation request and leaves a running recording running; a chosen sign-out still stops the recording, revokes and leaves `lastExpiry` null; a completed sign-in clears `lastExpiry`. Verify with `./gradlew :core-data:jvmTest`.

## 4. Recording across an expiry (record/gps-recording)

- [x] 4.1 Change `ActivityRecorder.recover(did)`: when a recording of another account is running or paused, run `suspendForSignOut()` first, then recover `did`'s unfinished recording; leave a running recording of `did` itself as it is. Add `ActivityRecorderTest` cases for both, and for a recording with no DID being left alone. Verify with `./gradlew :core-location:jvmTest --tests "*.ActivityRecorderTest"`.
- [x] 4.2 In `App`, open `RecordScreen` at sign-in when the recorder holds a running or paused recording of the signed-in account, as well as when `recover` recovered one. Check on the desktop app (`:desktopApp:hotRun`, or a JVM UI test if the recorder can be faked there) that signing back in after a simulated expiry lands on the running recording.

## 5. Login screen after an expiry

- [x] 5.1 Add `Expiry(recording: Boolean)` to `LoginState` and have `LoginPresenter` read `AuthRepository.lastExpiry` and `ActivityRecorder.state`, pre-filling the handle with the expired handle (design decision 6). Add `LoginPresenterTest` cases: after an expiry the state carries it with the handle filled in; `recording` follows the recorder; after a chosen sign-out there's no expiry and the field is empty.
- [x] 5.2 Show the message in `LoginUi` with new strings `login_session_expired` and `login_session_expired_recording` in `composeResources/values/strings.xml`, add both states to `LoginSamples`, and update the screenshot references with `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite`, checking the new images by eye. Verify with `./gradlew :composeApp:jvmTest :app-presenters:jvmTest`.

## 6. Integration

- [x] 6.1 Run `./gradlew check` and confirm it passes (tests on all targets, detekt, ktfmt, lint, screenshots, sorted dependencies).
- [x] 6.2 On the local stack, signed in as `alice.test` in the web app: stop the PDS (`docker compose -f docker/compose.yaml stop pds`) past the access token's expiry and confirm a refresh failure leaves her signed in with an error saying what failed; start it again and confirm the feed loads. Then revoke the session's refresh token at the PDS (or delete it from the PDS's database) and confirm the next request shows the login screen with the expiry message and `alice.test` filled in.
- [x] 6.3 Update `docs/reviews/2026-10-23-codebase-review.md` to remove C1, C2 and C6 and note the fixing commits, leaving the file uncommitted.

## Workflow follow-up

- Commit to `main` in small commits by concern (refresh classification, session manager, session cleanup and recording, login screen) when asked.
- Archive the change with `/opsx:archive` once the tasks are done.
