# Design

## Context

See proposal.md for the findings. Both fixes sit in `core/atproto`, which the client apps and the AppView share.

- `OAuthClient.authorizationCode` validates the redirect against the persisted `PendingAuthorization`. The web redirect unloads the page, so anything the check needs must be in that persisted record; the authorization server metadata is fetched only in `startAuthorization`.
- `IdentityResolver.resolveHandle` tries the configured XRPC resolver, then DNS-over-HTTPS, then `.well-known`, returning the first `Found`. It throws `HandleNotFoundException` when some lookup answered "missing", else the first transport failure. `AuthCoordinator` maps `HandleNotFoundException` to the "find account" failure and the AppView's `HandleLookup` maps it to `handle.invalid`.

## Goals / Non-Goals

**Goals:** close RFC 9207 mix-up protection for servers that support it; follow the atproto handle spec on conflicting TXT records.

**Non-Goals:** requiring `iss` from servers that don't advertise it (the atproto OAuth profile expects support, but refusing older or third-party servers outright is a separate decision); changing the order of handle lookups or how a configured XRPC resolver's answer is trusted.

## Decisions

1. **Record the flag in `PendingAuthorization`.** Add `issParameterSupported: Boolean = false` to `AuthorizationServerMetadata` (`authorization_response_iss_parameter_supported`) and `issRequired: Boolean = false` to `PendingAuthorization`, set from the metadata in `startAuthorization`. Re-fetching metadata on return was rejected: it adds a network round-trip and lets the metadata change between start and return. The `false` default keeps pending records written by older builds decodable and keeps their current behavior for the one sign-in in flight during an upgrade.
2. **Check order.** In `authorizationCode`: error → state → issuer present but different → issuer missing while required → code missing. A wrong `iss` still fails even when the server didn't advertise support.
3. **Ambiguous DNS is a definite "no account".** The DNS lookup collects the distinct DIDs from `did=` answers: none → `Missing`, one → `Found`, more → a new `HandleLookup.Invalid`. `resolveHandle` throws `HandleNotFoundException` as soon as it sees `Invalid`, without trying `.well-known`. Reusing `HandleNotFoundException` gives the right outcome in both callers (sign-in can't find the account; the AppView stores `handle.invalid`) without touching them. Falling through to `.well-known` (what the reference TypeScript resolver does) was rejected because the spec says resolution should fail, and the review asked for that. A separate exception type was rejected because callers would have to learn it to reach the same outcome.

## Risks / Trade-offs

- [A handle owner mid-migration publishes old and new `did=` records at once] → their handle shows `handle.invalid` until the stale record is removed, which matches the spec and is the owner's fix to make; the AppView re-checks every 24 hours.
- [An authorization server advertises the flag but omits `iss`] → sign-in fails; that server is non-conformant, and the error says the issuer is missing.
