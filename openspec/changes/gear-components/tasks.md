# Tasks

## 1. Lexicons

- [ ] 1.1 Add `mounts` (`#mount`: parent, from, to) to `gear/item.json` and mirror it in `core/lexicon`; verify the lexicon-auditor agent and round-trip tests
- [ ] 1.2 Add `GearMounts` validation (no overlap, no loop, three levels at most); verify unit tests for the chain, the moved power meter, tires on a wheelset, a loop, a fourth level and an overlap (`./gradlew :core-lexicon:jvmTest`)

## 2. Client data

- [ ] 2.1 Add `mount`, `unmount` and `replace` to `GearRepository`, validating against the athlete's items, and end an open mount when retiring; verify `GearRepositoryTest` cases for each, including replace's write order and retire ending a mount

## 3. AppView

- [ ] 3.1 Add the `gear_mounts` migration and index mounts from item records, dropping invalid ones and keeping those whose parent isn't indexed yet; verify `GearIndexTest` cases for a valid mount, an overlap, a loop, a foreign parent and a parent indexed later
- [ ] 3.2 Extend `countedFor` with mounted paths (three unrolled self-joins), per-viewer path visibility, and once-only counting; verify `GearUsageTest` cases for the chain's 300 km, tires through a wheelset, a ride listing the bike and its power meter, a swap, a follower and an Only me bike, and a deleted parent
- [ ] 3.3 List current and previous components in `getGear` for the viewer; verify a route test, and update `.agents/rules/appview.md`; verify `scripts/check-agent-sync.sh` and `./gradlew :server-appview:test` pass

## 4. Screens

- [ ] 4.1 Add the Components section to `GearScreen` with Mount, Unmount (date) and Replace, and the mount history on a component's page; verify `GearPresenterTest` cases for each action and an overlap error
- [ ] 4.2 Show the current parent on `EditGearScreen`; verify a presenter test, a Compose Hot Reload screenshot of a bike's Components section, and `./gradlew composeStabilityCheck`

## 5. Integration

- [ ] 5.1 Run `./gradlew check` and verify it passes
- [ ] 5.2 On the local stack, mount a chain on the Tarmac, import rides before and after, replace the chain, and confirm each chain's totals

## Workflow follow-up

- Archive the change with `/opsx:archive` once implemented and reviewed, after `gear-usage`.
