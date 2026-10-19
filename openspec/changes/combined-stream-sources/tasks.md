# Tasks

## 1. Data kinds and attribution

- [ ] 1.1 Add `DataKind` with its channels (design D1) in `core/data`, and a function listing the kinds two or more members of a combined activity recorded. CommonTests:
  - a watch plus a bike computer lists map, distanceSpeed, elevation and heartRate but not power;
  - a dive pair lists depth/dive.

  Verify: `./gradlew :core-data:jvmTest`.
- [ ] 1.2 Add span-based attribution (D3) from `combined.sources` and member spans. Tests:
  - a single source gives one device;
  - a watch dying at 10:40 then a phone gives two;
  - a lower-ranked member never shows while a higher one covers the time.

  Verify: `./gradlew :core-data:jvmTest`.

## 2. Choosing a source

- [ ] 2.1 Add `ActivityManager.setSources(combined, kind, member)` (D2): move the member first for each of the kind's channels, then rewrite and rebuild. Tests:
  - choosing heart rate changes the combined average and maximum;
  - choosing power moves balance and pedal channels too;
  - with GPS lost under trees, the next member fills in;
  - members' records and streams are untouched;
  - likes and title are kept.

  Verify: `./gradlew :core-data:jvmTest`.

## 3. Screen and UI

- [ ] 3.1 Add the sources State (kinds, the chosen member, the members offered) and a `ChooseSource` Event to `ActivityDetailScreen`, owner only, using `flights` for the rebuild. Presenter tests:
  - the owner sees sources and a follower doesn't;
  - choosing updates the State after the rebuild;
  - a failed rebuild shows an error.

  Verify: `./gradlew :app-presenters:jvmTest`.
- [ ] 3.2 Build the Data sources section and chart device captions in `composeApp` from `ui/adaptive` wrappers, with strings. Verify: `./gradlew composeStabilityCheck`, plus a desktop screenshot of the section and of a captioned heart rate chart naming two devices.

## 4. End to end

- [ ] 4.1 On the local stack, combine a watch and a bike computer ride, choose the bike computer for heart rate, and check:
  - the combined heart rate chart and figures change;
  - a follower sees the caption but not the section;
  - both members are unchanged.

  Verify: notes of each check, plus `./gradlew check`.

## Workflow follow-up

- Depends on `combine-activities` being applied first.
