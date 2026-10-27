# Proposal

## Why

Two small client findings from the 2026-10-23 review (`docs/reviews/2026-10-23-codebase-review.md`) are still open:

- **C12.** The edit-gear form shows each date in a different zone. The acquired date is a calendar day stored at UTC midnight and read back in UTC, which is consistent. The mounted date uses the device's zone. The retired date uses the system's default zone instead. On today's platforms those two zones agree, so the impact is low. But the form then has two sources for "the device's zone", and the retired date is the one date that bypasses the zone the app chose deliberately (`TimeZones.device()`, which on the web is the browser's named zone). Any future difference between the two would show a retired date a day off from the day the athlete retired the item.
- **C13.** When the OS credential store refuses the desktop key (Linux, or a denied Keychain), the fallback `secret.key` file is created and then written in a second step, and a key file found at launch is used without checking its length. A crash between the two steps leaves an empty file. From then on, every session save fails until the athlete deletes the file by hand, and a migration would even copy that empty key into the store.

## What Changes

- The edit-gear form shows the retired date as the day in the device's zone, the same zone it uses for the mounted date and for the day a component is mounted.
- The desktop fallback key file is written whole or not at all: written to a temporary file, then moved into place in one step.
- A key file of the wrong length is treated as no key. It's never used or moved into the store, and it's replaced: by the store's key when there is one, else by a new key, which signs the athlete out as a lost key does now.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `gear/gear-items`: one requirement added. The retired date is the day the item was retired in the device's zone.
- `auth/secret-storage`: two requirements added. The desktop key file is written atomically, and a damaged key file is replaced instead of used.

## Impact

- `app/presenters`: `gear/EditGearPresenter.kt` (`retiredOn`), with a test in `EditGearPresenterTest`.
- `core/data` (jvmMain): `auth/DesktopSecretCipher.kt` (`loadOrCreateKey`, `writeOwnerOnly`), with tests in `DesktopSecretCipherTest`.
- No lexicon, AppView, UI layout or migration changes. The key file keeps its name and owner-only permissions.
