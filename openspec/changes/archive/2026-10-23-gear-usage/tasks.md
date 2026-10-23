# Tasks

## 1. Lexicons

- [x] 1.1 Add `startingDistance` and `startingTime` to `gear/item.json`; add `gear/getGear.json`, `gear/getActorGear.json`, `gear/getGearActivities.json` and `gear/defs.json#gearUsage`; verify the lexicon-auditor agent reports no mismatches
- [x] 1.2 Mirror them in `core/lexicon` with `GearType.mainTotal`; verify round-trip tests and a `mainTotal` test for every known type and an unknown one (`./gradlew :core-lexicon:jvmTest`)

## 2. AppView

- [x] 2.1 Add the migration for the starting usage columns on `gear_items`, and a local-date and dive bottom-time column on `activities` if the portable expression isn't enough, backfilled; verify the migration runs on H2 in `:server-appview:test`
- [x] 2.2 Implement `countedFor` and the totals, by-sport and by-year aggregates in `GearViews`; verify `GearUsageTest` cases for three runs on two dates, a manual ski day without moving time, ski vertical, dives, and a combined activity counted once and again after a split
- [x] 2.3 Apply viewer access: the athlete counts everything, others count only references and activities they may see of an item they may see, and a hidden item is `NotFound`; verify tests for a follower, a Followers Gear category on a Public ride, a stranger and an Only me item, and starting usage added for an admitted viewer
- [x] 2.4 Add the `getGear`, `getActorGear` (one grouped totals query) and `getGearActivities` (keyset on start) routes; verify route tests for paging and an unknown URI, and update `.agents/rules/appview.md` with gear usage; verify `scripts/check-agent-sync.sh` and `./gradlew :server-appview:test` pass

## 3. Client

- [x] 3.1 Add `usage`, `actorGear` and `activitiesPage` to `GearRepository`; verify `GearRepositoryTest` cases for the params sent and the outputs parsed
- [x] 3.2 Add `GearScreen` with the `new-screen` skill (header, totals with starting usage, breakdowns, paged activities, Edit for the owner); verify presenter tests for the owner, a follower and a `NotFound`
- [x] 3.3 Show each item's main total in `GearListScreen` and open `GearScreen` from it and from the activity page's gear rows; verify presenter tests for a new item with no activities and the mixed list in the spec
- [x] 3.4 Add starting distance and time to `EditGearScreen` in the athlete's units; verify a presenter test for metric and imperial input, a Compose Hot Reload screenshot of the gear page, and `./gradlew composeStabilityCheck`

## 4. Integration

- [x] 4.1 Run `./gradlew check` and verify it passes
- [x] 4.2 On the local stack, import three rides with the Tarmac, make one Only me, and confirm the athlete's and a follower's gear pages show different totals that match their activity lists

## Workflow follow-up

- Archive the change with `/opsx:archive` once implemented and reviewed, after `activity-gear`.
