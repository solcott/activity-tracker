# Tasks

## 1. Shared texts and conditions

- [x] 1.1 Add `GearPageTexts` and `GearFigure` to `:feature-gear-api` (kind line, maker, notes, figures, starting usage, breakdowns, component line and dates, mount dates, actions label, picker title, candidate line), with `commonTest` tests for skis, a dive item, one activity type, starting usage and past component dates (`./gradlew :feature-gear-api:jvmTest`)
- [x] 1.2 Add getters to `GearState` (title, `showsEdit`, `showsCollections`, `showsComponents`, `managesComponents`, `mountEnabled`, `componentsError`, `activitiesError`, `authorClickable`, `unmountFirstDay`, `unmountInitialDay`) with `MOUNT_ERRORS` moved into the contract, and tests for each (`:feature-gear-api:jvmTest`)
- [x] 1.3 Make `GearUi.kt` and `GearComponentsUi.kt` resolve them; the existing gear UI and presenter tests and the content-loading conformance test still pass (`:feature-gear-ui:jvmTest`, `:composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"`), and the Android screenshot references are unchanged

## 2. Samples

- [x] 2.1 Add `GearSamples.variants` (linked, follower, components, components for a follower, chain, replacing, unmounting, a failed mount, mount saving, not found, loading more) and keep `GearPagesSamplesTest` passing (`:app-fixtures:jvmTest`)

## 3. Native page

- [x] 3.1 Type aliases in `KotlinTypes.swift` and `Samples.gear`/`Samples.gearVariants` in `Samples.swift` (additions only)
- [x] 3.2 `GearItemView`: header, collections, components, previous, mounted on, linked devices and profiles, totals, breakdowns, activities as `ActivityCardView`s through `PagedList`, Edit in the bar, not found; previews over both sample maps
- [x] 3.3 The mount sheet and the unmount calendar (`GearItemSheets.swift`), bound to the state's picker and unmounting component; previews
- [x] 3.4 Register `GearScreen` in `ScreenRegistry` and update `ScreenRegistryTests` (it no longer lists `GearScreen` as shared)
- [ ] 3.5 Text tests (`GearItemTextTests`) reading the samples' lines and conditions through Swift, and snapshot tests (`GearItemSnapshotTests`) of every sample and variant, the sheets included, recorded on iOS 26.0

## 4. Verification

- [x] 4.1 `./gradlew check` passes
- [ ] 4.2 The Debug and Release iOS builds succeed (`CONFIGURATION=Release` exports no fixtures)
- [ ] 4.3 The Swift tests pass on an iOS 26.0 and an iOS 18.4 simulator of this change's own
- [ ] 4.4 The page is seen on a device (the shared signed-in simulator when free, on the local stack, anything created Only me and deleted afterwards) or, failing that, in the snapshots; what couldn't be seen is listed under "Not verified" below
- [ ] 4.5 The roadmap records the probes and findings for this change, its Phases bullet, and the updated open items (`GearScreen` closed)

## Not verified

(Filled in during verification.)

## Workflow follow-up

- Archive with `/opsx:archive`, syncing the delta specs.
- Leave merge notes in the shared notes folder; the parent session merges into the migration branch.
