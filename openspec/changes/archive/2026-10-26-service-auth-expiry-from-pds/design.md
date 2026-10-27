# Design

## Context

`ServiceAuthAuthenticator` (`core/atproto`) keeps one token per method NSID, under a `Mutex`. When there's no cached token, or the cached one is within 10 seconds of the `exp` it computed, it calls `getServiceAuth(aud, lxm, exp = now + 60 s)` and caches the token against that `exp`. `shouldRetry` clears the cache on a `401`, and `XrpcClient` then retries once. The PDS checks `exp` against its own clock: it refuses one that's already past or more than an hour away, and sets 60 seconds when none is given. The AppView checks the token's `exp` against its own clock (`ServiceAuthVerifier`). So the device clock affects two things today: whether the request is refused, and how long the client trusts the cache.

`core/atproto` already has `jwtPayload(token)`, which decodes a token's claims without verifying them. `SpaceCredentials` uses it to read a space credential's `exp`. The fakes in `core/data`, `app/presenters` and `server/segment-importer` return tokens that aren't JWTs (`"service-jwt"`, `"svc:<aud>:<lxm>"`).

## Goals / Non-Goals

**Goals:**
- Getting a token never depends on the device clock.
- The cache keeps each token for its real lifetime, less a margin, whatever the device clock's offset.

**Non-Goals:**
- Device clock skew elsewhere: DPoP proofs, OAuth token expiry, and recording timestamps. The PDS already copes with DPoP skew, and OAuth expiry is out of scope.
- Changing how long tokens live. The PDS's default of 60 seconds stays.
- Telling the athlete their clock is wrong.

## Decisions

**Don't send `exp`.** `GetServiceAuth.Params.exp` is already nullable, so the request just leaves it out. The PDS then picks an expiry by its own clock that it will always accept. *Alternative:* keep sending `exp`, but correct it by an offset learned from the PDS's `Date` header. Rejected: it needs a first request to learn the offset, and the header is only accurate to the second.

**Measure lifetime from the token, and elapsed time on the device.** After minting, read `iat` and `exp` from the token with `jwtPayload`. The lifetime is `exp - iat`, which is in the PDS's clock but is a duration, so the offset cancels out. The cache entry expires at `received + lifetime - MARGIN`, with `received` taken from the injected `clock` and `MARGIN` staying 10 seconds. The check `expiresAt > clock.now()` compares two device-clock readings, so a constant skew doesn't matter. A token with a lifetime of `MARGIN` or less isn't cached. *Alternative:* compare the token's `exp` with the device clock directly. Rejected: that's the bug, from the other side. *Alternative:* a monotonic `TimeSource`. Rejected: it would mean a second time abstraction alongside the injected `Clock`, which tests already control, and a wall-clock jump mid-life only changes one cache entry, which a `401` clears anyway.

**An unreadable token isn't cached.** If the token isn't a compact JWS, its payload doesn't decode, or `iat`/`exp` are missing or `exp <= iat`, the authenticator sends it for this call and caches nothing. The AppView is the judge of a token, so refusing to send it would only add a failure. This also keeps the existing test fakes working. They will now mint a token on every call, which no current test counts.

**Remove `clear()`.** It has no callers, and it touched `tokens` without the mutex (C8). Sign-out drops the whole `SessionGraph` and its authenticator with it, so no replacement is needed.

## Risks / Trade-offs

- [A PDS that sets no `iat`] → The reference PDS sets `iat` on service tokens. Without one, the token falls back to being used once, which is correct, just with an extra mint per call.
- [A PDS whose default lifetime is shorter than `MARGIN`] → Tokens aren't cached, so each call mints one. That's correct but slower.
- [Fakes now mint on every call] → Only call counts would notice, and no test asserts on `getServiceAuth` calls today.
