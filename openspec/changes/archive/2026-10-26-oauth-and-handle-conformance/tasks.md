# Tasks

## 1. OAuth callback issuer (P4)

- [x] 1.1 Add `authorization_response_iss_parameter_supported` (default `false`) to `AuthorizationServerMetadata` and `issRequired` (default `false`) to `PendingAuthorization`, and set it from the metadata in `OAuthClient.startAuthorization`. Verify `./gradlew :core-atproto:jvmTest` compiles and `OAuthFlowTest` still passes.
- [x] 1.2 In `OAuthClient.authorizationCode`, refuse a callback without `iss` when `pending.issRequired`, keeping the mismatch check for any `iss` present. Add tests: missing `iss` refused when advertised (no token request made), accepted when not advertised, a wrong `iss` refused either way, and a `PendingAuthorization` JSON without `issRequired` decodes as not required. Verify with `./gradlew :core-atproto:jvmTest`.

## 2. Conflicting DNS handle records (C10)

- [x] 2.1 In `IdentityResolver`, make the DNS lookup return `Found` for exactly one distinct `did=` DID, `Missing` for none and a new `HandleLookup.Invalid` for more than one, and make `resolveHandle` throw `HandleNotFoundException` on `Invalid` without trying `.well-known`. Add `HandleResolutionTest` cases: two different DIDs fail even with a `.well-known` that answers, duplicate records of one DID resolve, and a `did=` record among unrelated TXT records resolves. Verify with `./gradlew :core-atproto:jvmTest --tests "*.HandleResolutionTest"`.

## 3. Integration

- [x] 3.1 Run `./gradlew check` and confirm it passes.

## Workflow follow-up

- Commit in small commits by concern (OAuth issuer, handle DNS) when asked.
- Archive the change with `/opsx:archive`.
