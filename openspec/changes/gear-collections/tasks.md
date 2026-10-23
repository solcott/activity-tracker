# Tasks

## 1. Lexicons

- [x] 1.1 Add `gear/collection.json` (name, notes, items, createdAt), list it in `space/athlete.json` as never indexed, and mirror it in `core/lexicon` (`GearCollectionRecord`, `Nsid.GEAR_COLLECTION`); verify the lexicon-auditor agent and round-trip tests

## 2. Data

- [x] 2.1 Add `GearCollections` (`all`, `save`, `delete` in `self`, hiding members whose items are gone, and the sign-in-again error for older sessions); verify `GearCollectionsTest` cases for Race kit, an item in two collections, a deleted member and an older session
- [x] 2.2 Remove a deleted item from every collection in `GearRepository.delete`; verify a test that "Race kit" then lists three items, and that a failure in that pass doesn't fail the delete
- [x] 2.3 Note in `Indexer.spaceCollections`' doc that `gear.collection` is never stored, and in `.agents/rules/appview.md`; verify `scripts/check-agent-sync.sh` passes

## 3. Screens

- [x] 3.1 Scaffold `GearCollectionScreen` and `EditGearCollectionScreen` with the `new-screen` skill, and add the Collections section to `GearListScreen`; verify presenter tests for listing, a retired member, a blank name, reordering, and deleting a collection
- [x] 3.2 Add the collections field to `EditGearScreen` and `GearScreen`; verify presenter tests for adding the helmet to Race kit and removing it
- [x] 3.3 Add "Add collection" to `GearField` (collection order, skip retired, deleted and listed items, the 16 limit message); verify `GearFieldPresenterTest` cases for Race kit, an already listed Tarmac and the limit
- [x] 3.4 Build the UIs from `ui/adaptive` wrappers; verify Compose Hot Reload screenshots of the Collections section and the picker's collection menu, and `./gradlew composeStabilityCheck`

## 4. Integration

- [x] 4.1 Run `./gradlew check` and verify it passes
- [x] 4.2 On the local stack, create Race kit, add it to an import, edit the collection, and confirm the activity's gear didn't change and the collection is only in `self`

## Workflow follow-up

- Archive the change with `/opsx:archive` once implemented and reviewed, after `activity-gear`.
