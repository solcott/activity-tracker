# Design

## Context

See proposal.md (Why) for the motivation and `specs/ui/error-messages` for the behaviour. This section covers only the current state that shapes the approach.

**`UiError`** (`app/screens/.../app/UiError.kt`) is `data class UiError(kind: Kind, detail: String?)`.
- `UiError.of(kind, cause)` sets `detail = cause.message`. There are 69 calls across 37 presenter files.
- `errorText` (`composeApp/.../ui/ErrorText.kt`) returns `detail ?: kindText(kind)`, so a failure with a message always shows it raw.
- `loadErrorText(kind, DataError)` and `loadErrorReason` already render "what failed. why" for `ContentStateLayout` and `RefreshStatus`.

**`toDataError`** (`core/data/.../OutcomeOf.kt`) already classifies failures:
- XRPC errors become `Http` or `Api`.
- `IOException`, and a rejected web `fetch` (detected by `isPlatformNetworkFailure`), become `Network`.
- Serialization failures become `Serialization`.
- Anything else becomes `Unknown`.

`:app-screens` gets `core/data` through `api(projects.coreData)`.

**Readers of `detail`** outside `errorText`: only `ModerationPresenterTest:108`, which expects the "Not an AT-URI" message from `AtUri`'s `require`.

**`UiError(kind, null)` literals:**
- Production: `uiErrorOf` (`visibility/NewActivityVisibility.kt:109-110`), `PrivacyZonesPresenter.kt:77,98,128` and `OwnRoute.kt:106`.
- Tests: 7.

**Sign-in.** `AuthCoordinator.Status.Failed(stage, detail)` (`core/data/.../auth/AuthCoordinator.kt:39`) sets `detail = it.message`, and `LoginUi.failedText` shows it raw. `LoginPresenterTest:81` asserts the handle appears in `detail`.

**Handle resolution.** `IdentityResolver.resolveHandle` (`core/atproto/.../identity/IdentityResolver.kt:55-59`) tries three lookups in turn: XRPC `resolveHandle` against `handleResolverUrl`, DNS-over-HTTPS for `_atproto.<handle>`, then `https://<handle>/.well-known/atproto-did`.
- Each lookup turns *any* failure into null with `getOrNull`. All three nulls end in `IdentityException("Could not resolve handle …")`.
- Being offline therefore reads as "no such handle" today.
- The well-known lookup fails with a DNS `IOException` for an unknown handle even when the device is online, so its failure alone says nothing about connectivity.

## Goals / Non-Goals

**Goals:**
- One renderer and one rule for every error string the app shows.
- `UiError.of`'s call sites stay unchanged.
- At sign-in, being offline is never reported as an unknown handle.

**Non-Goals:**
- Moving any screen's loads onto `ContentState`. That is a later step of the migration.
- Library paging.
- Splitting screens' shared load and action `error` fields.
- New `DataError` cases for domain failures such as zones being unreadable. Those keep their own `UiError.Kind`s.

## Decisions

### `UiError(kind, reason: DataError? = null)` replaces `detail`
- `of(kind, cause) = UiError(kind, cause.toDataError())`.
- The default lets the kind-only literals become `UiError(Kind.X)`.
- `DataError` is a `@Immutable` sealed class (runtime-annotation), so `UiError` stays stable for `composeStabilityCheck` in strict mode.
- `DataError.Unknown` holds the `Throwable`. It only stays in state and is never rendered. Its `equals` is the `Throwable`'s identity. That is fine, since tests compare kinds and reasons such as `Network` and `Http(500)`, never `Unknown` instances.

**Alternative considered:** keep `detail`, but stop rendering it. That leaves a field nobody may read, and it invites the leak back.

### One renderer: `errorText(UiError)`
```kotlin
@Composable fun errorText(error: UiError): String
// what = kindText(kind); why = error.reason?.let(::errorReason)
// why?.let { stringResource(error_with_reason, what, it) } ?: what
```
- `loadErrorText` is removed. `ContentStateLayout` and `RefreshStatus` call `errorText(UiError(errorKind, error))`.
- `loadErrorReason` is renamed `errorReason`, with the same table: Network → offline; `Http` ≥ 500 or `Api(code = InternalServerError)` → server; `Serialization` → unreadable; anything else → none.
- `LoadErrorTextTest` becomes `ErrorTextTest`.

### Special cases keep their kinds
- `uiErrorOf` still maps scope missing → `SignInAgain` and `PendingWritesException` → `WritesPending`. Those kinds carry no reason, since their text already says everything.
- `privacyErrorOf` keeps `TooManyZones` and `LoadPrivacyZones`.
- `ZonesUnreadableException` becomes `DataError.Unknown`, so it shows the kind's text only. That's what its kind already says.

### Moderation validates before it reads
- `ModerationPresenter` parses the typed text before launching the read. Text that doesn't start with `at://` (the rule `AtUri`'s `require` enforces) sets a new `UiError.Kind.InvalidSubjectUri` with its own string, and requests nothing.
- **Why up front rather than mapping `IllegalArgumentException`:** that exception isn't specific to bad input, and validating before the request matches the spec's "without asking the server".

### Sign-in: `Failed(stage, reason: DataError?)` plus a third stage
- `AuthCoordinator` sets `reason = cause.toDataError()`. It is in `core/data`, beside `toDataError`.
- A new `Status.Stage.FindAccount`, used when the cause is `HandleNotFoundException`, means no account was found for that handle. It gets its own string and no reason.
- `LoginUi.failedText` renders stage text plus reason, with the same `errorReason` table, through a small `failureText(what, reason)` helper shared with `errorText`. That way the reason table has one home.

### Handle resolution tells "not found" from "couldn't ask"
Each lookup reports one of three results, `Found(did)`, `Missing` or `Unreachable(cause)`, instead of `Did?`:

| Lookup | `Found` | `Missing` | `Unreachable` |
|---|---|---|---|
| XRPC resolver | answered with a DID | any XRPC error answer (`XrpcException`) | a transport failure |
| DNS-over-HTTPS | a DID in the answer | answered without one | a transport failure or a non-success status |
| well-known | a DID | any HTTP answer without one | a transport failure; on its own this isn't evidence of being offline, since unknown domains fail this way |

`resolveHandle` then decides:
- the first `Found` wins;
- otherwise, if the resolver or DNS-over-HTTPS answered `Missing`, it throws `HandleNotFoundException(handle)`, a subclass of `IdentityException`;
- otherwise it rethrows the first `Unreachable` cause, which `toDataError` classifies as `Network`.

Only the resolver and DoH lookups count as evidence, because they reach hosts that exist whatever handle is typed. A well-known `Missing` from a domain that answered is evidence too.

## Risks / Trade-offs

- **[Sign-in on an offline device with a resolver configured but DoH reachable]** For example, a local PDS down while the internet is up. DoH answers `Missing` for a local `.test` handle, so the result reads as "no account" rather than "couldn't reach". → That's acceptable for a misconfigured local stack. The resolver XRPC `Unreachable` is logged.
- **[`Unknown` swallows useful local messages]** Any other presenter that relied on `detail` for user-meaningful text would now show kind text only. → Moderation and login are the only readers found, both handled here. A grep for `.detail` is part of the tasks.
- **[`UiError` equality]** Equality now includes `reason`, so a state holding `UiError.of(kind, e)` compares by `DataError`, not message text. → Tests asserting `UiError(kind, null)` for special kinds still hold, since those have no reason.

## Migration Plan

- Nothing persisted, served or written changes. This is UI text and an internal API.
- Rollback is reverting the change.
- Later steps of the migration (activity and profile screens, forms, ActivityDetail, paging) build on `errorText(UiError)`.
