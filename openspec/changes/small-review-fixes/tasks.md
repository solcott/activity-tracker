# Tasks

## 1. Retired date in the device's zone (C12)

- [x] 1.1 In `EditGearPresenter`, read `retiredOn` in the presenter's `zone` instead of `TimeZone.currentSystemDefault()`, and drop the import if nothing else uses it. Add an `EditGearPresenterTest` case for "Retired late in the evening": set the JVM default zone to `America/New_York` (restoring it afterwards), store an item retired at 03:30Z, and assert `retiredOn` is the previous calendar day. Run `./gradlew :app-presenters:jvmTest --tests "*.EditGearPresenterTest"`.

## 2. Desktop key file (C13)

- [x] 2.1 Rewrite `writeOwnerOnly` in `DesktopSecretCipher.kt` as follows: delete a leftover `secret.key.tmp`, create it owner-only, write and `force` the key, then move it onto `secret.key` with `ATOMIC_MOVE` and `REPLACE_EXISTING`. Add a `DesktopSecretCipherTest` case for "Crash while writing the key file": a partial `secret.key.tmp` and no key file, with a store that's unavailable, leave a 32-byte owner-only `secret.key` and no temporary file. Check that the existing fallback tests still pass.
- [x] 2.2 In `loadOrCreateKey`, treat a `secret.key` whose length isn't `KEY_BYTES` as no key: log a warning without its contents, delete it, and go on to the store's key or a new key. Never pass its bytes to the store. Add `DesktopSecretCipherTest` cases for "Empty key file on Linux" (empty file, no store: a new 32-byte file replaces it, old sealed state doesn't decrypt, new state round-trips) and "Damaged key file beside a stored key" (3-byte file and a stored key: state sealed with the stored key still decrypts, the file is gone, and the store still holds its key). Run `./gradlew :core-data:jvmTest --tests "*.DesktopSecretCipherTest"`.
- [x] 2.3 Update the `DesktopSecretCipher` KDoc and the desktop sentence of the OAuth section in `.agents/rules/data-and-auth.md`: the fallback file is written atomically, and a damaged one is replaced. Verify with `scripts/check-agent-sync.sh`.

## 3. Integration

- [x] 3.1 Run `./gradlew check` and confirm it passes. Where the full run runs out of memory on `:webApp:compileDevelopmentExecutableKotlinWasmJs`, rerun that task on its own and record that it passes.

## Workflow follow-up

- Mark C12 and C13 fixed in `docs/reviews/2026-10-23-codebase-review.md` with the commit ids.
- Archive the change with `/opsx:archive` once it's reviewed.
