# Tasks

## 1. Lexicons

- [ ] 1.1 Add `sensors`, `profiles` and `ignored` (`#sensorLink`, `#profileLink`, `#sensorKey`) to `gear/autoAssign.json`, with a description that serials live only in `self`, and mirror them in `core/lexicon`; verify the lexicon-auditor agent and round-trip tests
- [ ] 1.2 Add `SensorKey` (`of`, `strength`, matching rules) and `SensorKind.suggestedGear`; verify unit tests for the SRAM serial, an ANT-only sensor, a model-only strap, a kind-only device with no key, case-folded makers, and every kind's suggestion (`./gradlew :core-lexicon:jvmTest`)

## 2. Client data

- [ ] 2.1 Extend the `gear.autoAssign` writer with links, profile links and ignores, keeping keys unique across links and ignores; verify tests that linking removes an ignore and ignoring removes a link
- [ ] 2.2 Add `GearAutoAssign.resolve` (sensors and recorder, lifted through mounts at the start; then the profile; then type defaults, per gear type; retired skipped; conflicts reported); verify tests for every scenario in the spec's matching, mounted-match and conflict requirements
- [ ] 2.3 Add `ActivityDevicesStore.list()` and `PastImportsMatcher` (count, then edit through the outbox, skipping activities that already have the gear type); verify tests for the 12-imports-2-with-a-bike case and a resumed pass

## 3. Import screen

- [ ] 3.1 Seed the import's and each batch activity's `GearField` from `resolve`, and show conflicts; verify `ImportActivityPresenterTest` cases for the crank beating the default, a profile match and two bikes flagged
- [ ] 3.2 Add the new-device cards (once per batch; name kept or renamed; suggested type and kind; mount on; collections; Not gear; Later) and the profile-link offer, hidden for older sessions; verify presenter tests for the Edge 840 import, a batch of five, Later, Not gear, and the power meter added and mounted
- [ ] 3.3 Save in order (items, mounts and collections, links, re-resolve untouched activities, publish), with an error that keeps the preview before publishing and a notice after; verify presenter tests for both failure points
- [ ] 3.4 Offer applying a new link to past imports after saving, with progress; verify a presenter test for accept and decline

## 4. Gear screens

- [ ] 4.1 Add Linked sensors and profiles with Unlink to `GearScreen` for the owner, and `IgnoredSensorsScreen` with Un-ignore; verify presenter tests for unlinking the sold power meter and un-ignoring the Edge 840
- [ ] 4.2 Build the UIs from `ui/adaptive` wrappers; verify Compose Hot Reload screenshots of the new-device cards on an Edge ride export with paired sensors and of Linked sensors, and `./gradlew composeStabilityCheck`

## 5. Integration

- [ ] 5.1 Run `./gradlew check` and verify it passes
- [ ] 5.2 On the local stack, import a real Edge FIT ride with paired sensors (the repo has no FIT sample), add the power meter mounted on a bike and ignore the head unit, import a second ride and confirm the bike is filled in and nothing is offered, then apply the link to the first ride; confirm with `listRecords` that no serial is in the repo

## Workflow follow-up

- Archive the change with `/opsx:archive` once implemented and reviewed, after `gear-components` and `gear-collections`.
