# Tasks

## 1. Dependencies

- [ ] 1.1 Add `java-keyring` to `gradle/libs.versions.toml` and `core/data` `jvmMain`; verify `./gradlew :core-data:compileKotlinJvm :desktopApp:compileKotlin` and that dependency sorting passes

## 2. Desktop cipher

- [ ] 2.1 Add `DesktopKeyStore` and its `java-keyring` implementation `KeyringKeyStore` (base64 key, iOS service/account names, unavailable on Linux and when no backend can be created); verify it compiles and a manual round trip on macOS
- [ ] 2.2 Replace `FileKeySecretCipher` with `DesktopSecretCipher` (file, then store, then create; owner-only file fallback with a warning; blocking calls on `ioDispatcher`) bound in `AppScope`; verify `DesktopSecretCipherTest` with a fake store covering first launch, restart, migration, migration refused, store unavailable (Linux), write refused, and a warning that never contains the key (`./gradlew :core-data:jvmTest --tests "*.DesktopSecretCipherTest"`)
- [ ] 2.3 Update `.agents/rules/data-and-auth.md` (desktop cipher, fallback) and `.agents/rules/hot-reload.md` (`secret.key` only on fallback), plus the `InMemorySecretCipher` doc; verify `scripts/check-agent-sync.sh`

## 3. Integration

- [ ] 3.1 Run `./gradlew check`; verify it passes
- [ ] 3.2 Run the desktop app, sign in, quit and relaunch; verify the session survives, the Keychain holds `io.github.solcott.activitytracker`/`oauth_store_key` (`security find-generic-password`), and no `secret.key` remains in the app data dir

## Workflow follow-up

- Archive the change once implemented, syncing `auth/secret-storage` into `openspec/specs`.
