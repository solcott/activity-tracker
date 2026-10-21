# Tasks

## 1. UiError carries a reason

- [x] 1.1 Change `UiError` to `UiError(kind, reason: DataError? = null)` with `of(kind, cause) = UiError(kind, cause.toDataError())`, and update its KDoc. Turn the `UiError(kind, null)` literals in presenters (`NewActivityVisibility.kt`, `PrivacyZonesPresenter.kt`, `OwnRoute.kt`) and tests into `UiError(kind)`. Verify with `./gradlew :app-screens:composeStabilityCheck` (strict mode, so `UiError` must stay stable) and `:app-presenters:compileTestKotlinJvm`.
- [x] 1.2 `grep` for every remaining reader of `.detail` on a `UiError` in `app/` and `composeApp/`, and confirm that none is left besides those this change replaces (`errorText`, `ModerationPresenterTest`). Verify that the `grep` comes back empty.

## 2. One renderer

- [x] 2.1 In `ui/ErrorText.kt`, make `errorText(UiError)` render the kind's text plus `errorReason(reason)` through `error_with_reason`. Remove `loadErrorText`, rename `loadErrorReason` to `errorReason`, and extract a `failureText(what, reason)` helper that login reuses. Change `ContentStateLayout` and `RefreshStatus` to call `errorText(UiError(errorKind, error))`. Verify by renaming `LoadErrorTextTest` to `ErrorTextTest`, adding a case showing that `UiError.of(kind, IllegalStateException("boom"))` gives no reason (so the kind text only), and running `:composeApp:jvmTest`.

## 3. Moderation validates the typed subject

- [x] 3.1 Add `UiError.Kind.InvalidSubjectUri` and its string (not a valid record address). Have `ModerationPresenter` check the typed text against the AT-URI prefix before launching the read, setting that kind and requesting nothing. Verify by updating `ModerationPresenterTest.aRefusalOrABadUriIsAnError` to assert the new kind and that no request reached the mock AppView.

## 4. Handle resolution and sign-in

- [x] 4.1 In `IdentityResolver`, make each lookup return `Found`, `Missing` or `Unreachable` as in design.md, and make `resolveHandle` throw `HandleNotFoundException` (a subclass of `IdentityException`) only on evidence from the resolver, DoH or an answering well-known host, and rethrow the transport failure otherwise. Verify with a new `core/atproto` jvmTest using `MockEngine`:
  - the resolver answers → found;
  - the resolver answers an error and well-known has a DNS failure → `HandleNotFoundException`;
  - every lookup has an `IOException` → the `IOException` is rethrown, which `toDataError` classifies as `Network`.
- [x] 4.2 Change `AuthCoordinator.Status.Failed` to `(stage, reason: DataError?)`, add `Stage.FindAccount` for `HandleNotFoundException`, and set `reason = cause.toDataError()` otherwise. Verify by extending `AuthCoordinatorTest`: an unknown handle → `FindAccount`; offline → `Start` with `Network`.
- [x] 4.3 Render `Failed` in `LoginUi.failedText` with the stage's text (add `error_sign_in_find_account`) plus the reason through `failureText`. Verify by updating `LoginPresenterTest:81` to assert the stage and reason instead of the handle in `detail`, and by running `:composeApp:compileKotlinJvm`.

## 5. Integration

- [x] 5.1 Run `./gradlew check` and verify it passes. `:app-screens` strict stability, detekt and ktfmt are included.
- [x] 5.2 Verify in the web app: with AppView requests blocked, opening an activity says "Couldn't load activity. You're offline." instead of "Fail to fetch"; saving the profile offline says it couldn't save the profile because the device is offline; signing in as a handle that doesn't exist says no account was found for it.

## Workflow follow-up

- Next migration steps: the activity and profile content screens on `ContentState`, the form-seeding screens, ActivityDetail, and library paging in kmp-dataresult.
- Archive the change after review.
