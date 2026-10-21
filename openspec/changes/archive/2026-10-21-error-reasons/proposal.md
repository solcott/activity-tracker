# Proposal

## Why

Only the feed and the segment page say why something failed, through `loadErrorText` (archived change `adopt-dataresult`). Every other error, whether a load on another screen, an action such as save, like, block or follow, or sign-in, still shows the exception's raw text. That's how "Fail to fetch", "XRPC 500 HttpError: …" and "Could not resolve handle alic" reach athletes. The cause is `UiError(kind, detail = cause.message)` and `AuthCoordinator.Status.Failed(stage, detail)`.

This is the first step of moving the rest of the app onto kmp-dataresult. It is the step with the widest reach, and it doesn't depend on how each screen loads.

## What Changes

- **`UiError` carries a reason, not a message.**
  - `UiError(kind, detail: String?)` becomes `UiError(kind, reason: DataError? = null)`.
  - `UiError.of(kind, cause)` keeps its signature and stores `cause.toDataError()`, so its existing call sites don't change.
  - **BREAKING** (internal API): the `detail` field is removed.
- **One renderer for every error.**
  - `errorText(UiError)` shows the localized text for what failed, plus why when that is known: offline, a server problem, or an unreadable reply.
  - It replaces `loadErrorText`, which `ContentStateLayout` and `RefreshStatus` now reach through `errorText`.
  - No error text shows an exception message, status code or error name.
- **Sign-in says why.**
  - `AuthCoordinator.Status.Failed(stage, detail)` becomes `Failed(stage, reason: DataError?)`.
  - A handle that resolves nowhere becomes its own failure ("no account found for that handle"), distinct from being offline. To support this, handle resolution starts telling "not found" apart from "couldn't ask".
- **Moderation's bad AT-URI gets its own kind.** Typed text that isn't an AT-URI is rejected up front, as a new `UiError.Kind`, instead of surfacing `require`'s message.
- **Unchanged:** the existing mappings to specific kinds stay as they are, including scope missing → `SignInAgain`, `PendingWritesException` → `WritesPending` and `TooManyZonesException` → `TooManyZones`.

## Capabilities

### New Capabilities
- `ui/error-messages`: what any error the app shows says. It names what failed, and says why when the failure is known to be offline, a server problem or an unreadable reply. It never shows raw technical text. Sign-in tells an unknown handle apart from being offline, and a mistyped moderation subject is reported as such. It covers every screen, action and sign-in. `ui/content-loading` keeps its load-specific rules.

### Modified Capabilities
None. `ui/content-loading`'s "Load errors say why" still holds as written. `profile/private-details`, `feed/comments` and others that require a "sign in again" message are unaffected: that stays its own kind.

## Impact

- **`app/screens`:** `UiError` (the field, `of`, and a new kind for an invalid moderation subject).
- **`app/presenters`:**
  - `uiErrorOf` and `privacyErrorOf` build reasons.
  - The few `UiError(kind, null)` literals become `UiError(kind)`.
  - `ModerationPresenter` validates the typed URI before reading.
- **`composeApp`:**
  - `ui/ErrorText.kt`: `errorText` becomes the only renderer, and `loadErrorReason` becomes `errorReason`.
  - `ui/ContentStateLayout.kt`, `login/LoginUi.kt`, `moderation` UI.
  - New strings.
- **`core/data`:** `AuthCoordinator.Status.Failed`.
- **`core/atproto`:** `IdentityResolver.resolveHandle` reports a handle that every lookup answered as missing (`HandleNotFoundException`) apart from lookups that couldn't be reached.
- **Tests:**
  - `UiError` literals in presenter tests.
  - `ModerationPresenterTest` and `LoginPresenterTest` (they assert on the raw `detail`).
  - `LoadErrorTextTest`, renamed.
  - New identity-resolution and `AuthCoordinator` tests.
- **Not in scope:** loads keep their current structure, since moving screens onto `ContentState` is a later step, and library paging is separate work.
