# Design

## Context

**C12.** `EditGearPresenter` already keeps `val zone = remember { TimeZones.device() }` and uses it for `mountedOn` and for the day a new component is mounted (`clock.todayIn(zone)`). Only `retiredOn` calls `TimeZone.currentSystemDefault()`. `acquiredOn` is a date picked in the form and stored at UTC midnight, read back in UTC, so it round-trips whatever the zone and stays as it is. `retiredAt` is the instant of `clock.now()` when the athlete retires the item.

**C13.** `loadOrCreateKey` (`DesktopSecretCipher.kt`) does the following:
- If `secret.key` exists, it returns `readBytes()` unchecked, and first tries to move those bytes into the store.
- Otherwise it decodes the store's value, which `decodeKey` already length-checks.
- Otherwise it makes a new key and, if the store refuses it, calls `writeOwnerOnly`. That function `createFile`s with `rw-------` and then `Files.write`s, two steps.

`AesGcmSecretCipher` calls the loader lazily, on the IO dispatcher.

## Goals / Non-Goals

**Goals:**
- One zone for every date in the edit-gear form that comes from an instant.
- A key file that is either absent or whole, and a damaged one that never becomes the key.

**Non-Goals:**
- Injecting a `TimeZone` through Metro so tests can pick one. The presenter's other date already reads `TimeZones.device()`, and a test can set the JVM default zone.
- `LeaderboardBracketsRepository`'s use of `currentSystemDefault()` for today's date, which the review didn't flag. It can follow separately if wanted.
- Linux credential-store support (Secret Service), still blocked on dbus-java.
- Encrypting or otherwise protecting the fallback file beyond owner-only permissions.

## Decisions

**C12: read `retiredOn` in the presenter's `zone`.** This is a one-line change to the value the presenter already holds.
- *Alternative:* store the retired date as a UTC-midnight day like `acquiredAt`. That changes what's written to the record and still has to decide which local day "now" is, so it's no better.

**C13: write to a temporary file, then rename it over the key file.** `writeOwnerOnly` does the following, in order:
1. Deletes any `secret.key.tmp` left by an earlier crash.
2. Creates `secret.key.tmp` with the same owner-only attributes.
3. Writes the key and forces it to disk (`FileChannel.force(true)`).
4. Moves it to `secret.key` with `ATOMIC_MOVE` and `REPLACE_EXISTING`.

Both files are in the same directory, so the move is a rename on every desktop filesystem we target, and the key file appears only once it's whole.
- *Alternative:* write straight to the file and check its length on read. That fixes the symptom but still loses the key on a crash mid-write. The length check below is kept anyway, as a second guard and for files left by older builds.
- *Alternative:* `Files.createTempFile` with a random name. That leaves orphans after a crash, and a fixed name is cleaned up on the next write.

**C13: a key file of the wrong length is no key.** When `secret.key` exists but isn't `KEY_BYTES` long, the loader:
1. Logs a warning naming the file but not its contents.
2. Deletes the file.
3. Carries on as if there were no file: the store's key if it has one, else a new key, which goes to the store or, when refused, to a freshly written file.

Its bytes are never moved into the store. A new key signs the athlete out, as the existing "Unreadable state signs the user out" requirement already says for a lost key.
- *Alternative:* keep the damaged file aside for diagnosis. It holds no usable secret, and keeping it would only confuse the next launch.

## Risks / Trade-offs

- [The atomic move isn't supported on some filesystem, such as an unusual network-mounted home directory] → `AtomicMoveNotSupportedException` propagates like any other I/O failure: the key load fails, and the app reports it as today. No partial file is left, because the temporary file was never renamed.
- [Replacing a damaged key file signs the athlete out] → The athlete is already effectively signed out, since every save fails and the old state can't be decrypted with an empty key. This trades a permanent breakage for one sign-in.
- [The C12 test can't tell the two zone calls apart on the JVM, where both read the same default] → The test sets the JVM default zone and checks the day near midnight, which pins the behavior the spec requires. Keeping one zone in the code is what prevents the drift.
