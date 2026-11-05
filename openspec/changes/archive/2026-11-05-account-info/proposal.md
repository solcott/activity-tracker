# Proposal

## Why

Settings has no notion of the account: athletes can't see which handle, DID or PDS they're signed in with, can't tell that their sign-in predates permissions the app now asks for until a save fails, and get no answer to "where do I change my password, turn on two-factor or delete my account?", which in atproto is the PDS's job, not ours. Sign-out also silently leaves queued uploads behind. This is the first of the account-management changes (export, leaving Activity Tracker and multiple accounts follow separately), and the page they'll hang from.

## What Changes

- New **Account** section at the top of Settings with one row, the signed-in handle, opening an **Account** page.
- The Account page shows the handle, the DID (copyable), and the host of the PDS the account lives on.
- It says whether the session's sign-in grants everything the app currently asks for (the `space:` scope and every collection the athlete space declares, on a PDS that supports Spaces). When it doesn't, it says what's missing in plain terms and offers **Sign in again**, which signs out and returns to the login screen with the handle filled in and a note saying why.
- It explains that the password, two-factor sign-in, app passwords, handle, deactivating and deleting the account are managed by the PDS host shown, not by Activity Tracker; the app never sees the athlete's credentials because sign-in happens on the PDS's own page.
- **Sign out** moves from the bottom of the Settings list onto the Account page.
- Sign-out with work still pending on this device asks first: it says how many changes haven't uploaded (they stay on the device and upload when this account signs in here again) and, when an import is still saving, that its unsaved activities will be dropped. Sign out anyway keeps the queued writes, as today.
- **BREAKING** (UI only): the Sign out row leaves the Settings list.

Out of scope: data export, deleting the app's data ("leave Activity Tracker"), multiple accounts, in-app handle or password changes (would need `account:`/`identity:` OAuth scopes).

## Capabilities

### New Capabilities
- `auth/account-page`: the Account page: what it shows about the signed-in account and its grant, signing in again, the pointer to the PDS for account-level actions, and sign-out with its pending-work warning.

### Modified Capabilities
- `ui/settings-layout`: Settings gains an Account section first, and Sign out leaves the list for the Account page; the Account page opens in the detail pane like other settings pages.
- `auth/oauth-session`: the login screen after a chosen **Sign in again** fills in the handle and says why, unlike a plain sign-out.

## Impact

- `feature/settings` (`-api`: new `AccountScreen : SettingsPage`, `SettingsEvent.SignOut` removed; `-presenter`, `-ui`), `:app-fixtures` samples, Settings UI tests and `:androidApp` screenshot references.
- `core/data`: `AuthRepository` (sign in again with the handle kept, pending-work summary for the warning), `SessionClients` (whether the grant is current; the athlete space's declared collections mirrored from `lexicons/app/activitytracker/space/athlete.json`).
- `feature/login` presenter/UI for the sign-in-again note.
- `:ui-resources` strings. No lexicon, AppView or migration changes.
