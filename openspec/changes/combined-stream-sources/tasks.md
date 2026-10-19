# Tasks

## 1. Data kinds and attribution

- [ ] 1.1 Add `DataKind` with its channels (design D1) in `core/data`, and a function listing every kind any member of a combined activity recorded, with the members that have it and whether it's excluded. CommonTests:
  - a watch with GPS and heart rate plus a bike computer with GPS, heart rate, power and altitude lists map, distanceSpeed and heartRate with both members, and elevation and power with the bike computer only;
  - a kind stored with `members: []` is listed as excluded;
  - a dive pair lists dive.

  Verify: `./gradlew :core-data:jvmTest`.
- [ ] 1.2 Add span-based attribution (D3) from `combined.sources` and member spans. Tests:
  - a single source gives one device;
  - a watch dying at 10:40 then a phone gives two;
  - a lower-ranked member never shows while a higher one covers the time.

  Verify: `./gradlew :core-data:jvmTest`.

- [ ] 1.3 Make `ActivityCombiner.rankings` keep a stored empty list as excluded (D2a), so the combiner builds nothing from that channel, and update the `#channelSource.members` description in `lexicons/app/activitytracker/feed/activity.json`. Tests:
  - excluding power builds no power, balance or pedal channels and no average or normalized power;
  - an empty list isn't refilled with the default ranking;
  - a channel with no stored entry still gets the default.

  Verify: `./gradlew :core-data:jvmTest`.

## 2. Choosing a source

- [ ] 2.1 Add `ActivityManager.setSources(combined, kind, choice)` (D2): for each of the kind's channels move a chosen member first, store `[]` for `Exclude`, or drop the entry for `Default`, then rewrite and rebuild. Tests:
  - choosing heart rate changes the combined average and maximum;
  - choosing power moves balance and pedal channels too;
  - with GPS lost under trees, the next member fills in;
  - excluding power drops balance and pedal channels too;
  - including power again brings it back from the bike computer;
  - members' records and streams are untouched, after a choice and after an exclude;
  - likes and title are kept.

  Verify: `./gradlew :core-data:jvmTest`.

## 3. Screen and UI

- [ ] 3.1 Add the sources State (kinds, whether each is included, the chosen member, the members offered when two or more have it) and `ChooseSource` and `SetIncluded` Events to `ActivityDetailScreen`, owner only, using `flights` for the rebuild. Presenter tests:
  - the owner sees sources and a follower doesn't;
  - choosing updates the State after the rebuild;
  - excluding a kind, then including it again, updates the State after each rebuild;
  - a failed rebuild shows an error.

  Verify: `./gradlew :app-presenters:jvmTest`.
- [ ] 3.2 Build the Data sources section and chart device captions in `composeApp` from `ui/adaptive` wrappers, with strings. Verify: `./gradlew composeStabilityCheck`, plus desktop screenshots of the section, with one kind switched off, and of a captioned heart rate chart naming two devices.

## 4. End to end

- [ ] 4.1 On the local stack, combine a watch and a bike computer ride, choose the bike computer for heart rate, then exclude power, and check:
  - the combined heart rate chart and figures change;
  - the combined power chart and figures go, and the bike computer's own activity keeps them;
  - a follower sees the caption but not the section;
  - both members are unchanged.

  Verify: notes of each check, plus `./gradlew check`.
