# Tasks

## 1. Lexicons

- [x] 1.1 Add `lexicons/app/activitytracker/gear/item.json` (name, type, kind, brand, model, sports, weight, notes, photo, acquiredAt, retiredAt, createdAt) and list `gear.item` in `space/athlete.json`'s collections and description; verify the lexicon-auditor agent reports no mismatches
- [x] 1.2 Mirror it in `core/lexicon`: `GearItemRecord`, `Nsid.GEAR_ITEM`, and `GearType` with each type's kind tokens; verify round-trip tests for a full bike, a name-only item and an unknown type and kind (`./gradlew :core-lexicon:jvmTest`)

## 2. Data

- [x] 2.1 Add `GearRepository` in `core/data/.../gear/` with `items()` reading the repo and three tier spaces, keeping a duplicate at the narrower tier; verify `GearRepositoryTest` (Ktor `MockEngine`) cases for items in all four places and a duplicated rkey
- [x] 2.2 Add `save` (write the new place, then delete the old, keeping the rkey; create a tier space through `AthleteSpaces`; upload the photo to the item's place) and `delete`; verify tests for a new Only me item, Only me to Public, a failed delete after the first write, a first Followers item creating the space, and a photo moving with its item
- [x] 2.3 Refuse a non-Public save when `canWriteToSpaces(Nsid.GEAR_ITEM)` is false, with a typed error; verify a test that a Public save still succeeds for the same session

## 3. Screens

- [x] 3.1 Scaffold `GearListScreen` and `EditGearScreen` with the `new-screen` skill (State, Event, presenters, UIs, navigation) and add "Gear" to Settings; verify `SettingsPresenterTest` opens the gear list
- [x] 3.2 Implement the list presenter: grouped by type, active first, a Retired group, an empty state; verify presenter tests for items in every tier, the empty state and an unknown type under Other
- [x] 3.3 Implement the edit presenter: fields in `TextFieldState`, type and kind pickers, sports, visibility starting at Only me, photo, retire/un-retire, and delete with a Retire / Delete / Cancel confirmation; verify presenter tests for a blank name, retire instead of delete, a confirmed delete and the sign-in-again error keeping the form
- [x] 3.4 Build the UIs from `ui/adaptive` wrappers, with string resources for every type and kind label; verify on the desktop app via Compose Hot Reload (screenshots of the list and the edit form) and `./gradlew composeStabilityCheck`

## 4. Integration

- [x] 4.1 Run `./gradlew check` and verify it passes
- [x] 4.2 On the local stack (`scripts/dev.sh --seed alice`), add a Public bike and Only me shoes with a photo, move the shoes to Followers, retire them, and confirm with `listRecords` where each record is

## Workflow follow-up

- Archive the change with `/opsx:archive` once implemented and reviewed.
