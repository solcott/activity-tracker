# Design

## Context

- Settings (`feature/settings`) is a `SettingsScreen` list whose rows open `SettingsPage`s, laid out list-detail by `SettingsListDetailDecoration`. Sign out is its last row (`SettingsEvent.SignOut` -> `AuthRepository.signOut()`).
- `SessionClients` already knows the account: `did`, `handle`, `pdsUrl`, `hasSpacesScope`, `canWriteToSpaces(collection)` (reads the collections the PDS filled into the `space:` grant) and `spacesSupport` (`describeServer`'s alpha flag).
- Several presenters already offer "sign in again" as a plain `auth.signOut()` (`FollowRequestsPresenter`, `EditProfilePresenter`, `AddPeopleUi`), which drops the athlete on an empty login screen.
- `AuthRepository.endSessionNow` distinguishes a chosen sign-out from an expiry through `lastExpiry: StateFlow<ExpiredSession?>`, which `LoginPresenter` uses to prefill the handle and show the expiry note. It keeps the account's outbox rows; `ImportSaves.signedOut(did)` drops a batch save's unsaved activities.
- `UploadQueue.summary` (`UploadSummary`: `own`, `imported`, `other`, `refused`) and `ImportSaves.running` already describe pending work per account.
- The athlete space's declared collections exist only in `lexicons/app/activitytracker/space/athlete.json`; no Kotlin mirror lists them.

## Goals / Non-Goals

**Goals:**
- One place that decides "is this grant current", used by the Account page and the Settings row.
- One sign-in-again path that every screen offering it can share.
- The pending-work question built from data the app already keeps.

**Non-Goals:**
- No change to what sign-out clears or keeps (the outbox stays).
- No new OAuth scopes, no PDS calls beyond what the session already makes.
- No new copy on other screens' "sign in again" buttons: they switch to the shared path, so they keep the handle too, but say what they said before.

## Decisions

### Grant status lives in `SessionClients`, against a mirrored declaration
Add `AthleteSpaceDeclaration.collections` to `core/lexicon` (next to `Nsid.SPACE_ATHLETE`), mirroring `athlete.json`'s `collections`, plus a jvm test that reads the JSON and asserts the lists are equal, so a lexicon change without the mirror fails `check`. `SessionClients.grantStatus` returns a small sealed type: `Current`, `SpacesUnsupported`, or `Outdated(missing: Set<GrantFeature>)` where `GrantFeature` groups collections into what the athlete would recognise (private activities, private profile details, interactions on private activities, gear, segment and grouping settings). `Outdated` when `!hasSpacesScope` (everything missing) or some declared collection fails `canWriteToSpaces`.
*Alternative:* compare `session.scope` with `DEFAULT_SCOPE` as strings. Rejected: the PDS rewrites the `space:` scope with the declared collections at issue, so the strings never match.
*Alternative:* ask the PDS. There's no endpoint that reports a token's effective scope beyond what we already hold.

### Sign in again is a reason on the session's end, not a new flow
Generalize `lastExpiry` to `lastEnd: StateFlow<EndedSession?>` with `EndedSession(did, handle, reason)` and `reason` `Expired | SignInAgain`. `AuthRepository.signInAgain()` runs the chosen sign-out path (revoke, `suspendForSignOut`, `saves.signedOut`, clear caches) and then sets `lastEnd` with `SignInAgain`. `LoginPresenter` prefills the handle for either reason and shows the expiry note only for `Expired` (and the recording note only for `Expired`, as today, since a chosen end already suspended the recording). `lastEnd` stays until the next successful sign-in, so a cancelled browser flow keeps the handle.
*Alternative:* pass the handle through the login screen's arguments. Rejected: `LoginScreen` renders from `App` on `AppCircuit` when the session drops, not by navigation, so there's no screen to pass it in.

### Pending work is a `SignOutCheck` read once, on demand
`AccountPresenter` handles `SignOut`/`SignInAgain` by reading `UploadQueue.summary.first()` and `ImportSaves.running` for the DID. If `own + imported + other + refused > 0` or a save is running it puts a `PendingSignOut(changes, importSaving, signInAgain)` in state, and the UI shows an `AppAlertDialog` with Cancel and Sign out anyway (or Sign in again anyway). Confirming runs the chosen end through `rememberSingleFlight`. Nothing pending ends the session directly.
*Alternative:* collect the summary continuously on the page. Unneeded; the count is only read at the moment of asking, and collecting would keep `UploadQueue`'s queries live for no visible gain.

### Account is a `SettingsPage` in `feature/settings`
`AccountScreen : SettingsPage` (`SessionScope`), `AccountPresenter`, `AccountUi`. State: `handle`, `did`, `pdsHost` (host of `pdsUrl`), `grant` (the sealed status), `pending: PendingSignOut?`, `endingSession: Boolean`. Events: `CopyDid`, `SignOut`, `SignInAgain`, `ConfirmEnd`, `DismissEnd`, `Back`. Copying uses Compose's clipboard in the UI (the presenter can't touch platform APIs), so `CopyDid` isn't an event: the UI copies `state.did` and shows its own confirmation.
`SettingsState` gains `handle` and `needsSignIn: Boolean` for the row; `SettingsEvent.SignOut` is removed.
*Alternative:* a new `feature/account` module. Rejected for now: one page, and Settings' list-detail wiring is already in this feature; the later export/leave pages can move it out if it grows.

### The PDS pointer is copy, not a link
The page names `pdsHost` and lists what the host manages. No link: atproto defines no account-settings URL, and guessing (`bsky.app/settings` for `bsky.social`) would be wrong for every other host.

## Risks / Trade-offs

- [The declaration mirror drifts from `athlete.json`] -> the equality test fails `check`; the `lexicon-change` skill gets a line naming the mirror.
- [`GrantFeature` grouping goes stale when a collection is added] -> `GrantFeature.of(collection)` is an exhaustive `when` over the mirrored list with a test that every declared collection maps to a feature.
- [Sign in again on a PDS whose authorization server is down leaves the athlete signed out] -> same as any sign-out today; the handle stays filled in so retrying is one tap.
- [Removing the Sign out row moves a familiar control] -> the Account section is first in the list, so sign-out is one tap further, not hidden.

## Migration Plan

UI-only and client-only: no data migration. `lastExpiry` callers move to `lastEnd` in the same change. Screenshot references for Settings and the list-detail layout are regenerated.
