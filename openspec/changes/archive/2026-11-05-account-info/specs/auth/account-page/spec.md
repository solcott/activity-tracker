# Spec Delta

## Purpose

The Account page in Settings: what the app shows about the signed-in atproto account and its sign-in grant, how an athlete renews an out-of-date grant, where account-level actions live (the PDS, not the app), and signing out without losing track of pending work.

## ADDED Requirements

### Requirement: The Account page identifies the account
The Account page SHALL show the signed-in account's handle, its DID and the host name of the PDS it is hosted on. The DID SHALL be copyable. The handle SHALL be shown as the session knows it, without a network read.

#### Scenario: Account details
- **WHEN** alice, signed in as `alice.test` on the PDS at `https://pds.example.com`, opens the Account page
- **THEN** it shows `alice.test`, her `did:plc:` DID and `pds.example.com`

#### Scenario: Copying the DID
- **WHEN** alice chooses to copy her DID
- **THEN** her DID is on the clipboard and the page confirms it was copied

### Requirement: The Account page says whether the sign-in is current
The Account page SHALL say the sign-in is up to date when the session holds the athlete-space scope covering every collection the athlete space declares, and the PDS is not known to lack Spaces. Otherwise it SHALL say which features need signing in again (private activities and details, likes and comments on private activities, and so on), never a raw scope string.

#### Scenario: Current grant
- **WHEN** alice signed in after the app's latest scope and space declaration changes and opens the Account page
- **THEN** it says her sign-in is up to date and offers no Sign in again

#### Scenario: Sign-in predates the space scope
- **WHEN** bob's session has no athlete-space scope
- **THEN** the Account page says he must sign in again to use private activities and details

#### Scenario: Sign-in predates a declared collection
- **WHEN** carol's space grant lists collections but not one the athlete space now declares
- **THEN** the Account page says some features need signing in again and offers Sign in again

### Requirement: A PDS without Spaces is reported, not blamed on the sign-in
When the athlete's PDS is known not to support Spaces, the Account page SHALL say that the server hosting the account can't keep private data, and SHALL NOT offer Sign in again for it. While support is unknown, the grant alone SHALL decide.

#### Scenario: PDS without Spaces
- **WHEN** dave's PDS answers `describeServer` without Spaces support
- **THEN** the Account page says his server can't keep private data and offers no Sign in again on that account

#### Scenario: Support not yet known
- **WHEN** the PDS hasn't answered yet and erin's grant is current
- **THEN** the Account page says her sign-in is up to date

### Requirement: Signing in again keeps the handle
Sign in again SHALL end the session the way a chosen sign-out does (revoking the refresh token and clearing what sign-out clears) and SHALL return to the login screen with the account's handle filled in.

#### Scenario: Renewing the grant
- **WHEN** bob chooses Sign in again on the Account page
- **THEN** he is signed out and the login screen shows `bob.test` filled in, ready to continue

### Requirement: Account-level actions are pointed to the PDS
The Account page SHALL explain that the password, two-factor sign-in, app passwords, the handle, and deactivating or deleting the account are managed by the PDS host it shows, not by Activity Tracker, and that the app never receives the athlete's password because sign-in happens on the PDS's page. It SHALL NOT link to a URL it can't know is that PDS's account settings.

#### Scenario: Where to change the password
- **WHEN** alice looks on the Account page for how to change her password or turn on two-factor sign-in
- **THEN** the page tells her these are managed by `pds.example.com`, her account's host

### Requirement: Sign out lives on the Account page
The Account page SHALL offer Sign out. Signing out SHALL behave as a chosen sign-out does today: a recording in progress is kept for the account's next sign-in on this device, the login screen shows no expiry note and an empty handle, and queued writes stay on the device.

#### Scenario: Signing out with nothing pending
- **WHEN** alice, with nothing waiting to upload and no import saving, chooses Sign out
- **THEN** she is signed out at once without being asked, and the login screen's handle field is empty

### Requirement: Signing out with pending work asks first
When the account has changes on this device that haven't uploaded (waiting, uploading or refused) or an import still saving, Sign out SHALL first say how many changes haven't uploaded, that they stay on this device and upload when this account signs in here again, and, if an import is saving, that its unsaved activities will be dropped. The athlete SHALL be able to cancel or sign out anyway; signing out anyway SHALL keep the queued writes.

#### Scenario: Uploads waiting
- **WHEN** alice has 3 activities waiting to upload and chooses Sign out
- **THEN** she is told 3 changes haven't uploaded and will upload when she signs in again on this device, and can cancel or sign out anyway

#### Scenario: Signing out anyway
- **WHEN** alice signs out anyway with 3 changes waiting, then later signs in as `alice.test` on the same device
- **THEN** the 3 changes upload after she signs in

#### Scenario: Cancelling
- **WHEN** alice cancels the sign-out question
- **THEN** she stays signed in on the Account page

#### Scenario: Import still saving
- **WHEN** bob chooses Sign out while a batch import is still saving
- **THEN** he is also told the import's unsaved activities will be dropped before he confirms

### Requirement: Signing in again with pending work asks first
Sign in again SHALL ask the same question as Sign out when work is pending, since it ends the session the same way.

#### Scenario: Sign in again with uploads waiting
- **WHEN** bob has 2 changes waiting and chooses Sign in again
- **THEN** he is asked first, and confirming signs him out with `bob.test` filled in on the login screen
