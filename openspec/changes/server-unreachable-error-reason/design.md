# Design

## Context

See proposal.md (Why) for the problem. The relevant pipeline today:

```
request gets no response (refused, DNS, timeout, web fetch rejected)
    --> Throwable.toDataError()  (core/data OutcomeOf.kt)   --> DataError.Network
    --> errorReason()            (app/common/ui ErrorText.kt) --> error_reason_offline
    --> failureText()            "<what failed>. You're offline."
```

`DataError` is the `io.github.solcott:dataresult` library's sealed class. Its `Network` case covers both "offline" and "server unreachable", and nothing in it can tell them apart. Every screen's error goes through `errorText`/`failureText`, so `errorReason()` is the one place the wording is chosen.

## Goals / Non-Goals

**Goals:**
- Every screen, sign-in included, says something true when a request gets no response.
- Change it in one place, so no presenter, repository or fixture changes.

**Non-Goals:**
- Telling the athlete whether their device or the server is at fault.
- Changing how the outbox decides a failure is transient (`OutboxProcessor.isTransient` still treats `DataError.Network` as retryable).

## Decisions

**Change the wording, not the classification.** `DataError.Network` keeps its meaning and only its text changes, to "Couldn't reach the server." It's true in both cases and asks nothing of the platform.
- *Alternative: ask the device for its connection state when the error is shown* (Android `ConnectivityManager`, iOS `NWPathMonitor`, `navigator.onLine`, and unknown on desktop), and say "You're offline." only when the device reports no network. Rejected for now. It needs a connectivity source on four targets and a CompositionLocal. The message would also be only as reliable as the platform's report (captive portals, `navigator.onLine` being optimistic). It can still be added later on top of this wording.
- *Alternative: classify by exception type* (`ConnectException` versus `UnknownHostException`, `NSURLErrorNotConnectedToInternet`). Rejected: it differs per engine, it's fragile, and wasm's `fetch` rejection can't tell the two apart.

**Rename the resource to `error_reason_unreachable`.** A key named `offline` holding text that isn't about being offline would mislead the next reader. There's only the default locale, so the rename touches one XML file and its two Kotlin users (`ErrorText.kt`, `ErrorTextTest.kt`).

**Specs keep their offline conditions.** A scenario still starts with "while the device is offline", which is still how a tester causes the failure. Only what the error says changes. The spec deltas modify every requirement whose outcome names the device as offline (ui/error-messages, ui/content-loading, auth/oauth-session, segments/segment-creation, segments/segment-favorites, segments/segment-browse).

## Risks / Trade-offs

- [An athlete who really is offline loses the specific hint] → "Couldn't reach the server." still points them at connectivity. The platform's own offline indicator covers the rest.
- [Screenshot references change for every failed-state sample] → Regenerate them in the same change and review the diff. Only the reason text should differ.
- [The `.agents/rules/compose-screens.md` rule says errors name "offline"] → Update the rule's wording in the same change.
