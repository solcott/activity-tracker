# Design

## Context

`RoomOAuthStore` encrypts OAuth state with whatever `SecretCipher` the platform binds in `AppScope`. On desktop that's `FileKeySecretCipher` (`core/data/src/jvmMain`), which wraps `AesGcmSecretCipher` (`nonWebMain`) and keeps the raw 32-byte key in `DesktopAppDirs.data/secret.key`. iOS's `KeychainSecretCipher` follows the same shape: a `loadKey` that reads the key from secure storage or creates it. The `jvm` target serves both the desktop app and every JVM test. Tests build `RoomOAuthStore` with `InMemorySecretCipher` directly, so no test touches the bound desktop cipher.

## Goals / Non-Goals

**Goals:**
- The desktop key lives in the OS credential store, with the migration and fallback behavior in `specs/auth/secret-storage`.
- The key-loading decisions can be tested on any CI host, without a real keychain.

**Non-Goals:**
- Changing the ciphertext format, `SecretCipher`, `AesGcmSecretCipher` or the other platforms.
- Gating the key on user presence (Touch ID, Windows Hello).

## Decisions

**`java-keyring` for the store, on macOS and Windows only.** It offers one `get/set/deletePassword(service, account)` API over the macOS Keychain and Windows Credential Manager, both of which its maintainers have tested. Its Linux backends (Secret Service, KWallet) go through `secret-service` 1.8.1, which is built on dbus-java 4.2. filekit already pins dbus-java 5.2.0 on the desktop classpath, and 5.x removed what it calls (`DBusConnection.getConnection(DBusBusType)`, the public `MethodCall` constructors, `org.freedesktop.dbus.errors.Error`). So on Linux the store reports itself unavailable without touching `java-keyring`, and the key stays in the fallback file. A Linux store is a later change, either `secret-service` 3.x (on dbus-java 5) or libsecret over JNA. Hand-written JNA bindings were the alternative, considered and declined: more FFI code, and only the macOS path could be exercised here. The cost is a dependency last released in 2023, along with what it brings (JNA, dbus-java, jkeychain, slf4j-api). Only `jvmMain` uses it.

**A narrow seam: `DesktopKeyStore`.** It's a tiny interface in `jvmMain` with `read(): String?`, `write(String)` and `delete()` for our one entry. `KeyringKeyStore` implements it over `java-keyring`, with service `io.github.solcott.activitytracker` and account `oauth_store_key` (the iOS names). The key is stored as base64, because the store holds strings. `Keyring.create()` throws `BackendNotSupportedException` when no backend exists, and the seam reports that, and any other failure creating the backend (a `LinkageError` included), as "unavailable". Tests use an in-memory fake that can be made unavailable or made to refuse writes.

**Load order: file first, then store, then create.** `DesktopSecretCipher` replaces `FileKeySecretCipher` and keeps the same `AesGcmSecretCipher` delegation. Its `loadKey`:

```
 key file exists? --yes--> store.write(file key) ok? --yes--> delete file, use key
        |                         |
        no                        no --> warn, use file key (fallback)
        v
 store.read() has key? --yes--> use it
        |
        no / unavailable
        v
 new random key --> store.write ok? --yes--> use it
                         |
                         no --> warn, write owner-only file, use it (fallback)
```

The file is checked first because it only exists when it is the most recent key. The migration deletes it once the store has the key, and the fallback writes it only after the store failed. So when both exist, the file won. `java-keyring` throws the same `PasswordAccessException` for "not found" and for real errors, so a failed read is treated as "no key". If that read was a transient failure, the next write replaces the stored key. The session then fails to decrypt and reads as signed out (`RoomOAuthStore`), which is the spec's "unreadable state signs the user out".

**Blocking calls off the main thread.** Keyring calls are JNA/D-Bus I/O and run on the injected `ioDispatcher`, as the file read does today. `AesGcmSecretCipher` loads the key once per process, under its mutex.

**Logging.** The cipher takes a Kermit `Logger` (`withTag("DesktopSecretCipher")`) and warns on fallback with the backend error's class and message only, never the key.

## Risks / Trade-offs

- [The macOS Keychain ACL is per executable, so a different JVM or a re-signed app bundle prompts again or can't read the item] → Denial reads as "no key". The user is signed out once and the next key is saved fresh. That's acceptable for a desktop app that isn't shipped yet.
- [Linux keeps the weaker file key] → It's logged on every launch, and the spec says so. Linux desktop isn't a release target yet.
- [`java-keyring` is unmaintained] → It's isolated behind `DesktopKeyStore`, so replacing it with our own JNA bindings later touches one file.
- [Fallback weakens security silently] → It's logged as a warning, and the spec limits it to when the store is missing or refuses.

## Migration Plan

Nothing to deploy. The first launch of the new build migrates `secret.key` into the store. Rolling back to an older build finds no key file, creates a new one, and signs the user out once. The keychain entry is left behind, and a later upgrade replaces it with the newer file key.
