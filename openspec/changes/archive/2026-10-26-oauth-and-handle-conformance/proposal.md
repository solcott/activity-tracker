# Proposal

## Why

The 2026-10-23 codebase review found two places where the client's atproto code is looser than the specs it implements. The OAuth client accepts an authorization redirect with no `iss` parameter, which turns off RFC 9207 mix-up protection: a malicious authorization server the person also uses could replay its own response into our callback (P4). And handle resolution takes the first of several `did=` TXT records, where the atproto handle spec says conflicting records make resolution fail (C10). Both are small, local fixes in `core/atproto`.

## What Changes

- **The OAuth callback must carry `iss` when the authorization server says it sends one.** When the server's metadata advertises `authorization_response_iss_parameter_supported: true`, a redirect without `iss` is refused. An `iss` that is present is always compared with the expected issuer, as today. The flag is recorded with the pending authorization when sign-in starts, so the check needs no metadata fetch on return; a pending authorization saved by an older build counts as not advertised.
- **Conflicting `did=` TXT records make a handle fail to resolve.** When the DNS lookup for `_atproto.<handle>` returns `did=` records naming more than one DID, the handle resolves to no account (the same outcome as a handle with no DID), without falling back to the `.well-known` lookup. Duplicate records naming the same DID still resolve. Because that outcome is "not found", sign-in reports that it couldn't find the account and the AppView stores `handle.invalid`, as it does for any handle that resolves to nothing.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `auth/oauth-session`: adds a requirement that the authorization response's issuer is checked, and required when the server advertises it.
- `identity/handle-verification`: adds a requirement that a handle whose DNS records name more than one DID resolves to no account.

## Impact

- **core/atproto**: `OAuthClient` (callback validation), `OAuthModels` (`AuthorizationServerMetadata` reads the flag; `PendingAuthorization` stores it with a backward-compatible default), `IdentityResolver` (DNS TXT parsing and how `resolveHandle` treats an ambiguous answer). Tests in `OAuthFlowTest`/a new OAuth callback test and `HandleResolutionTest`.
- **Callers**: `AuthCoordinator` (core/data) and the AppView's `HandleLookup` already map `HandleNotFoundException` to "find account" failure and `handle.invalid`, so neither changes.
- No lexicon, migration or dependency changes.
