# Proposal

## Why

Phase 4 of the native iOS UI (`openspec/explorations/ios-native-ui.md`) moves features one change at a time. Edit profile (`EditProfileScreen`) is the largest form still drawn by the shared UI on iOS, and the pieces it needs now exist: the gear editors (`ios-native-gear-editors`, archived) settled form rows, menu choices, the photo pick through Kotlin and dates crossing to Swift. Nothing else in progress touches `feature/profile`.

## What Changes

- **Edit profile is native on iOS**, pushed over the shared profile, in the system's grouped form with Save in the bar and a failed save, or the session needing to sign in again with Sign in again, at the top:
  - the profile photo, changed from the photo library or removed, compressed to a small JPEG with its metadata dropped as on every platform;
  - the display name and bio, each with the shared counter, red past its maximum, when Save waits;
  - Approve followers, a switch with what turning it on means;
  - the details, one section per group (Location, Birthdate, Gender, Height and weight), each with its fields, who may see the whole group as a menu choice, the Followers note, and the "public until now" warning under the group it is about:
    - city and state or province, and the country chosen from a searchable list of the device's country names, or Not set;
    - the birthdate from a calendar of past days, which can be cleared;
    - gender as Not set, Female, Male, Non-binary or Self-describe, the last showing a field for the athlete's own words;
    - height (cm, or feet and inches) and weight (kg or lb) in the athlete's units, marked wrong when not a height or weight the lexicon allows.
- **Kotlin chooses the text and conditions, both UIs ask.** The counters, the now-hidden warnings, the gender choices and what the menu shows as chosen, the country search's order and matching, the units' labels, the avatar compression's size and quality, which details a group's visibility covers, and getters for what the form enables and offers (`canSave`, `weightValid`, `heightValid`, who may see a group, whether a group is now hidden). The Compose form asks the same. No visible change on Android, desktop or web; the shared screenshots change only where the samples now carry who may see each group, as the presenter always fills it.
- **Swift gets option values and text, never the enum list.** `nowHidden` is an immutable list of `DetailGroup`, an enum, which can't cross to Swift; the native form asks a getter per group instead.
- **The avatar's bytes reach the presenter through Kotlin's compression.** Swift hands the picked image to a Kotlin entry that runs the shared avatar compression and sends the JPEG in the form's existing `AvatarPicked` event.
- **The birthdate crosses as a calendar date.** The state's `YYYY-MM-DD` text is offered to Swift as a `LocalDate`, and the date event gains a constructor taking one; the stored text is unchanged.
- **Samples, previews and snapshots** of the form in every shared sample, plus variants beside `all` (imperial with a self-described gender, a saved and a picked photo, a group now hidden, approving followers with Followers chosen, saving, a failed save, a session that must sign in again), so the shared screenshot set and the content-loading conformance keys are unchanged. A registry entry and its test.

Out of scope: the profile page itself (its header, tabs and activity cards wait for the native feed card), Training zones (its own screen, already in progress elsewhere), a native image cropper, and any change to what is saved or where.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `profile/private-details`: edit profile is drawn natively on iOS, with the same fields, limits, choices and warnings as the shared form.
- `dev/shared-display-text`: edit profile's counters, warnings, gender and country choices, labels and conditions are chosen in shared code.
- `dev/ios-swift-interop`: a profile photo picked natively reaches its presenter compressed by shared code, and a stored date text crosses to a native date control as the same day.

## Impact

- **`:app-common-ui`:** the country codes leave `Countries.kt` for `:feature-profile-api`, the one feature that offers them; `countryName` and `stripDiacritics` stay.
- **`:core-data`:** `CalendarDate` gains a public `LocalDate` read and write.
- **`:feature-profile-api`:** the country codes and their search rule (`CountrySearch`), `EditProfileTexts`, `EditProfileLimits` (the avatar's size and quality moved from the Compose UI), `GenderChoices`, getters on `EditProfileState`, a `LocalDate` constructor on `BirthDateChanged`; tests. **`:feature-profile-ui`:** `EditProfileUi` asks them.
- **`:ui-resources`:** one string (the inches row's label) and the regenerated iOS catalog.
- **`:app-common-ui`:** `compressAvatar` moved from `:feature-profile-ui`, beside `compressPhoto`.
- **`:iosShell`:** `IosApp.compressAvatar`, over the shared `compressAvatar`.
- **`:app-fixtures`:** `EditProfileSamples.variants`.
- **`iosApp`:** `EditProfileView`, its country sheet and small logic types, `PhotoPick` generalised over what it compresses to, type aliases, samples, previews, snapshot and text tests, a registry entry.
- **Docs:** the roadmap's phase 4 entry, "Phase 4 probes (edit profile)" and findings, open items; `.agents/rules/compose-screens.md`.
- **Risk carried:** iOS 18 can't be walked signed in; snapshots compare only on iOS 26.0; other sessions' builds share the signed-in simulators, so the walk waits for theirs; a save on alice.test changes her real profile on the local stack, so each value is put back.
