# auth/secret-storage Specification

## Purpose

Keeps each client's stored OAuth state (tokens, the DPoP private key, PKCE verifiers) encrypted at rest under a key held by the platform's secure storage, and defines what happens when that key is missing or unreadable.

## Requirements

### Requirement: Stored OAuth state is encrypted at rest
Every client SHALL store its OAuth session and pending authorizations only as AES-256-GCM ciphertext. The key SHALL be held by the platform's secure storage: the Android Keystore (non-exportable), the iOS Keychain (this device only), a non-extractable WebCrypto key on the web, and the OS credential store on desktop.

#### Scenario: Session written to local storage
- **WHEN** a client saves a signed-in session
- **THEN** the local database holds only ciphertext for it, and the plaintext tokens and DPoP key appear in no local file

### Requirement: Unreadable state signs the user out
When stored OAuth state can't be decrypted, the client SHALL treat it as absent and show the user as signed out, never crash or retry forever. This applies when the key was lost, replaced or never restored.

#### Scenario: Key replaced
- **WHEN** a client starts and its secret key differs from the one that encrypted the stored session
- **THEN** the client discards the stored session and shows sign-in

### Requirement: Desktop key in the OS credential store
On macOS and Windows, the desktop key SHALL live in the OS credential store: the macOS Keychain or the Windows Credential Manager. It SHALL be created there on first use. While the store works, the app SHALL leave no copy of the key in its data directory.

#### Scenario: First launch on a machine with a credential store
- **WHEN** the desktop app first needs its key and no key exists in the store or the data directory
- **THEN** it generates a key, saves it in the OS credential store, and writes no key file

#### Scenario: Signed in across restarts
- **WHEN** a desktop user signs in, quits and relaunches
- **THEN** the app reads the same key from the credential store and the user is still signed in

### Requirement: Desktop key file migrates into the credential store
When the desktop app finds a key file left in its data directory, it SHALL move that key into the OS credential store and then delete the file. The file is left by an earlier build or by a past fallback. The file SHALL be deleted only after the store accepted the key.

#### Scenario: Upgrading from a build with a key file
- **WHEN** a signed-in user upgrades from a build that kept the key in a file and launches the app
- **THEN** the key is saved in the credential store, the file is deleted, and the user is still signed in

#### Scenario: Store refuses during migration
- **WHEN** a key file exists and the credential store refuses to save the key
- **THEN** the file is kept and its key is used, so the user stays signed in

### Requirement: Desktop falls back to an owner-only key file
On Linux, where no credential store is supported yet, and on macOS or Windows when the store is unavailable or refuses to save the key, the desktop app SHALL keep the key in an owner-only file in its data directory and log a warning. The warning SHALL NOT contain the key.

#### Scenario: Linux desktop
- **WHEN** the desktop app runs on Linux
- **THEN** sign-in still works and persists across restarts, with the key in an owner-only file and a warning in the log

#### Scenario: Keychain access denied
- **WHEN** the macOS Keychain refuses to save a newly created key
- **THEN** the key is written to the owner-only file instead, sign-in works, and a warning is logged
