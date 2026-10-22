# Tasks

## 1. Lexicons

- [ ] 1.1 Add `gear` (at-uri array, max 16) to `feed/activity.json` and `feed/activityDetails.json`, and `gear` to `actor/activityDefaults.json#details`; add `gear/autoAssign.json` (`sports` entries) and `gear/defs.json#gearView`; add `gear` to `feed/defs.json#activityView`; list `gear.autoAssign` in `space/athlete.json`; verify the lexicon-auditor agent reports no mismatches
- [ ] 1.2 Mirror them in `core/lexicon`: the record fields, `GearAutoAssignRecord`, `GearView`, `Nsid.GEAR_AUTO_ASSIGN`, and `Nsid.GEAR_ITEM` in `indexedCollections`; verify round-trip tests (`./gradlew :core-lexicon:jvmTest`)
- [ ] 1.3 Add `DetailCategory.GEAR` with its `SummaryField` and `defaultWhenUnset`; verify `DetailCategoryTest` cases that a Followers Gear on a Public activity moves `gear` to the followers details record, that narrowing the activity drags Gear, and that the merge restores it for an admitted viewer

## 2. Client data

- [ ] 2.1 Make `ActivityDefaults` return Only me for an unset Gear default; verify tests for never set, only the activity default set, and Gear set to Public
- [ ] 2.2 Add `GearDefaults` (read and write `gear.autoAssign` in `self`, and `forType` resolution per gear type, sub-sport over sport, ignoring retired and deleted items); verify tests for the Tarmac/Stumpjumper/helmet case and a retired default
- [ ] 2.3 Add `ActivityDraft.gear` and `gearTouched`, carry `gear` through `ActivityRepository` publish and edit, and merge members' gear in `ActivityCombiner`; verify `ActivityRepositoryTest` and `ActivityCombinerTest` cases for a published ride with gear, an edit removing it, and combining two recordings

## 3. AppView

- [ ] 3.1 Add the Flyway migration for `gear_items` and `activity_gear` (H2-portable), and index `gear.item` from the repo and every tier with origins and shadows; verify `GearIndexTest` cases for a repo item, a `self` item, a move between tiers and an invalid name
- [ ] 3.2 Replace each activity and details row's `gear` refs in `activity_gear` by origin, keeping only the author's own `gear.item` URIs, and remove them on delete and `dropSpace`; verify tests for a public ride, Gear at Only me, a foreign URI, and a dropped space
- [ ] 3.3 Hydrate `activityView.gear` and filter `record.gear` and each details entry's `gear` to items the viewer may see; verify `ViewsTest` cases for a stranger, a follower with a private item, and the athlete
- [ ] 3.4 Update the AppView rules (`.agents/rules/appview.md`) and the `space/athlete.json` description for gear items and activity gear; verify `scripts/check-agent-sync.sh` passes and `./gradlew :server-appview:test` passes

## 4. Screens

- [ ] 4.1 Add the `GearField` sub-circuit (sorted by sports, retired only if selected, defaults until touched); verify presenter tests for ordering, a retired selected item, a type change before and after touching, and removing all gear
- [ ] 4.2 Put `GearField` in record save, manual entry, import, batch import and edit, and offer the Gear visibility picker whenever gear is selected; verify presenter tests in each screen's test class, including a recording that offers Gear after a pick
- [ ] 4.3 Add "Use by default for" to `EditGearScreen`, writing `gear.autoAssign`; verify an `EditGearPresenterTest` case and the sign-in-again error for older sessions
- [ ] 4.4 Show the gear row in the activity page's `DetailSection.Device` from `activityView.gear`; verify an `ActivityDetailPresenterTest` case and a Compose Hot Reload screenshot of a ride with a bike and a helmet, and `./gradlew composeStabilityCheck`

## 5. Integration

- [ ] 5.1 Run `./gradlew check` and verify it passes
- [ ] 5.2 On the local stack, as alice set the Tarmac as the ride default, import a ride, make Gear Public and the item Public, and confirm a second account sees the bike, then set the item to Only me and confirm they don't

## Workflow follow-up

- Archive the change with `/opsx:archive` once implemented and reviewed, after `gear-items`.
