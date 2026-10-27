# Spec Delta

## ADDED Requirements

### Requirement: The desktop key file is written whole or not at all
When the desktop app keeps its key in the owner-only fallback file, it SHALL write the file so that at every moment the file is either absent or holds the whole key. A crash or power loss while the key is being written SHALL never leave a partial or empty key file in place. The file SHALL stay owner-only.

#### Scenario: Crash while writing the key file
- **WHEN** the desktop app is killed while writing a new fallback key file
- **THEN** on the next launch there is either no key file, and a new key is made, or a key file holding the whole key

### Requirement: A damaged desktop key file is replaced
When the desktop app finds a key file that doesn't hold a key of the right length, it SHALL NOT use that file's contents as a key or move them into the credential store. It SHALL use the store's key when the store has one, and otherwise make a new key, replacing the damaged file. Stored state that the new key can't decrypt SHALL read as signed out.

#### Scenario: Empty key file on Linux
- **WHEN** the desktop app on Linux finds an empty key file left by an earlier crash
- **THEN** it replaces the file with a new key, the athlete is asked to sign in once, and later session saves work

#### Scenario: Damaged key file beside a stored key
- **WHEN** a key file of the wrong length exists and the credential store holds a key
- **THEN** the app uses the store's key, deletes the damaged file, and the athlete stays signed in
