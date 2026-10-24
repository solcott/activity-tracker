# Proposal

## Why

The desktop app encrypts its stored OAuth state (tokens, the DPoP private key, the PKCE verifier) with a key kept in a plain `secret.key` file. Any process running as the user can read that file and decrypt the session. Android, iOS and the web keep their keys in platform secure storage, and `FileKeySecretCipher` already says the desktop needs the OS keychain before it ships.

## What Changes

- **Desktop key in the OS credential store.** On macOS and Windows, the desktop `SecretCipher` keeps its AES-256-GCM key in the Keychain or the Credential Manager, through `java-keyring`. Ciphertext stays in Room as it is today.
- **Existing installs keep their session.** On first use, a `secret.key` left by an earlier build is moved into the credential store and deleted, so users stay signed in.
- **Fallback when there's no credential store.** On Linux, or when the store refuses the key (a denied Keychain prompt), the desktop falls back to today's owner-only key file and logs a warning. Sign-in still works there. Linux has no store for now: `java-keyring`'s Secret Service backend is built on dbus-java 4, and filekit already pins dbus-java 5, which removed the APIs it calls.
- `FileKeySecretCipher` is replaced by the new desktop cipher. The file-key code survives only as its fallback.

Out of scope: a Linux credential store (a later change, on a Secret Service client built for dbus-java 5). Android, iOS and the web, which keep their ciphers unchanged. DataStore, which we decided not to adopt. Biometric or user-presence gating on the key.

## Capabilities

### New Capabilities
- `auth/secret-storage`: how each client keeps its stored OAuth state encrypted at rest, where each platform's key lives, and what happens when the key can't be read.

### Modified Capabilities
None.

## Impact

- **core/data `jvmMain`:** a new desktop `SecretCipher` bound in `AppScope` replaces `FileKeySecretCipher`, plus a small credential-store seam so tests run without a real keychain.
- **Dependencies:** `com.github.javakeyring:java-keyring` on the JVM target only. It brings JNA, jna-platform, jkeychain and `secret-service`. Most of what that pulls in (dbus-java, slf4j-api) is already on the desktop classpath through filekit.
- **Docs:** `.agents/rules/data-and-auth.md` (which cipher each platform uses) and `.agents/rules/hot-reload.md` (`secret.key` no longer lives in the app dir).
- **Behavior:** on macOS the Keychain may ask once whether the JVM may read the item, because Keychain ACLs are per executable.
