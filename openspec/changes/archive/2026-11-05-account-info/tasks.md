# Tasks

## 1. Grant status

- [x] 1.1 Add `AthleteSpaceDeclaration.collections` in `core/lexicon` mirroring `lexicons/app/activitytracker/space/athlete.json`, with a jvm test that parses the JSON and asserts the lists match; verify with `./gradlew :core-lexicon:jvmTest`
- [x] 1.2 Add `GrantFeature` (private activities, private profile details, interactions on private activities, gear, segment and grouping settings) with an exhaustive mapping from every declared collection, and a test that every declared collection maps to one
- [x] 1.3 Add `SessionClients.grantStatus` (`Current`, `SpacesUnsupported`, `Outdated(missing)`) per design; cover no space scope, a grant missing one collection, an empty-collection grant, Unsupported and Unknown spaces support in `core-data` jvm tests; verify with `./gradlew :core-data:jvmTest --tests "*GrantStatus*"`
- [x] 1.4 Add a line about the declaration mirror to `.agents/skills/lexicon-change` and run `scripts/check-agent-sync.sh`

## 2. Ending a session to sign in again

- [x] 2.1 Generalize `AuthRepository.lastExpiry` to `lastEnd: StateFlow<EndedSession?>` with reason `Expired | SignInAgain`, add `signInAgain()` (chosen sign-out path, then `lastEnd` set), and clear `lastEnd` on successful sign-in; update `AuthRepository` tests for both reasons and for `lastEnd` surviving a cancelled sign-in
- [x] 2.2 `LoginPresenter`/`LoginUi`: prefill the handle for either reason, show the expiry and recording notes only for `Expired`, and a "signing in again updates permissions" note for `SignInAgain`; add login strings to `:ui-resources`, a `LoginSamples` entry, and presenter + UI tests for each oauth-session scenario
- [x] 2.3 Point the existing sign-in-again events (`FollowRequestsPresenter`, `EditProfilePresenter`, `AddPeople` and any other `SignInAgain -> auth.signOut()`) at `auth.signInAgain()`; verify their presenter tests still pass

## 3. Account page

- [x] 3.1 Add `AccountScreen : SettingsPage`, `AccountState` (handle, did, pdsHost, grant, pending, endingSession) and `AccountEvent` in `feature/settings/api`; `composeStabilityCheck` passes for the module
- [x] 3.2 `AccountPresenter`: grant from `SessionClients.grantStatus` (recomputed when `spacesSupport` changes), Sign out / Sign in again reading `UploadQueue.summary` and `ImportSaves.running` once and asking when pending, confirm/dismiss through `rememberSingleFlight`; presenter tests for nothing pending, uploads waiting, refused only, import saving, cancel, confirm, and a double tap ending the session once
- [x] 3.3 `AccountUi`: identity rows (DID copied with the platform clipboard and a confirmation), grant status with Sign in again when `Outdated`, the PDS pointer copy naming the host, Sign out, and the pending `AppAlertDialog`; strings and plurals in `:ui-resources`
- [x] 3.4 `AccountSamples` in `:app-fixtures` (current, outdated, spaces unsupported, pending uploads, pending with import saving) and an `AccountUiTest`; verify with `./gradlew :feature-settings-ui:jvmTest`
- [x] 3.5 `AccountScreenshots.kt` in `:androidApp` screenshot tests; `ScreenshotCoverageTest` passes

## 4. Settings list

- [x] 4.1 `SettingsState` gains `handle` and `needsSignIn`; remove `SettingsEvent.SignOut` and its row; `SettingsPresenter` fills both from `SessionClients`; update `SettingsPresenterTest`
- [x] 4.2 `SettingsUi`: Account section first with the handle row and its sign-in-again note, selected when `AccountScreen` is the shown page; update `SettingsSamples`, `SettingsUiTest` for the new section order and the absent Sign out row
- [x] 4.3 Check list-detail behaviour for Account (opens in the pane without back, row marked; narrow replaces the list) in `SettingsListDetailScreenshots` and the decoration's tests
- [x] 4.4 Regenerate screenshot references with `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite`, review the changed Settings, list-detail, Account and Login images, and commit them

## 5. Integration

- [x] 5.1 `./gradlew check` passes (tests, lint, ktfmt, detekt, module boundaries, screenshots)
- [x] 5.2 On the desktop app (`:desktopApp:hotRun`) against the local stack: open Settings > Account and confirm handle, DID, PDS host and "up to date"; queue an edit offline, Sign out and confirm the pending question; sign back in and see the edit upload; Sign in again and see the handle filled in

## Workflow follow-up

- Sync the delta specs and archive the change once reviewed (`/opsx:archive`).
