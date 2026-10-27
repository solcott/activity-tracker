# Tasks

## 1. Time tokens by the PDS

- [x] 1.1 In `ServiceAuthAuthenticator`, call `getServiceAuth` without `exp`, read `iat` and `exp` from the minted token with `jwtPayload`, and cache it until `received + (exp - iat) - MARGIN` on the injected clock. Don't cache a token whose claims can't be read, or whose lifetime is `MARGIN` or less. Update the class KDoc. Verify `./gradlew :core-atproto:jvmTest` still passes
- [x] 1.2 Remove `ServiceAuthAuthenticator.clear()`. Verify it has no callers (`grep -rn "\.clear()"` over its users in `core/data` and `server/segment-importer`) and that `./gradlew :core-data:compileKotlinJvm :server-segment-importer:compileKotlin` succeeds

## 2. Test it

- [x] 2.1 Add `ServiceAuthAuthenticatorTest` in `core/atproto/src/commonTest`, using `MockNetwork`, a PDS fake that mints an unsigned compact JWT with `iat`/`exp` from its own clock, and a test `Clock` for the device. Cover:
  - the request carries no `exp` and the device clock is two minutes slow, and a token is still sent
  - the device clock is two minutes fast, and a second call 10 s later reuses the token
  - a second call 55 s after a 60 s token mints a new one
  - a token that isn't a JWT is sent and isn't reused
  - a `401` clears the cache

  Verify with `./gradlew :core-atproto:jvmTest --tests "*.ServiceAuthAuthenticatorTest"`
- [x] 2.2 Run `./gradlew :core-data:jvmTest :app-presenters:jvmTest :server-segment-importer:test` to confirm the fakes that return tokens that aren't JWTs still pass

## 3. Verify and record

- [x] 3.1 Run `./gradlew check` and confirm it passes
- [x] 3.2 After committing, move C5 in `docs/reviews/2026-10-23-codebase-review.md` to "Fixed since the review" with the commit hash, and narrow C8 to the zone maps it still covers (`stale`, `batch`). Verify that C5 no longer appears in the Correctness table

## Workflow follow-up

- Commit only when asked, as one commit (one concern: the authenticator and its test).
- Archive the change once it is committed.
