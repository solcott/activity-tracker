# Tasks

## 1. Shared rules

- [ ] 1.1 Order `ActivityPageContent.cards` by sport in `ActivityPage.build` (the design's table). Verify: `ActivityPageTest` cases for a ride with and without power, a run, a dive, a ski day and strength.
- [ ] 1.2 Add `MediaMosaic.of(hasRoute, photos)` and `TABLE_PREVIEW_ROWS` to `:app-common-api`, and `ActivityPageTexts.jumps` for the chips. Verify: unit tests for each mosaic case and the chips of a rich ride and a manual walk.

## 2. Detail presenter and All data

- [ ] 2.1 Add `openCards`, `allData`, `ToggleCard`, `OpenAllData(section, inPane)` and `CloseAllData` to the detail contract and presenter. Verify: presenter tests for toggling a strip, and opening All data in the pane and as a screen.
- [ ] 2.2 Add `ActivityDataScreen`, its state (`ActivityData`, built by one shared function) and presenter. Verify: a presenter test that loads it and starts at a section.
- [ ] 2.3 Fixtures: page variants with photos, a ski day, strength, a ride without power and a minimal manual entry; `ActivityDataSamples`. Verify: `ContentLoadingConformanceTest` passes.

## 3. Compose

- [ ] 3.1 `MediaMosaic` composable in `:app-common-ui` (map tile, photo tiles, "+N", blur and reveal, takedown).
- [ ] 3.2 Strips, five-row tables with Show all, section chips, the All data row; rebuild `ColumnPage` and `PanedPage`; larger hero for three figures or fewer.
- [ ] 3.3 `ActivityDataUi` and the side pane's All data.
- [ ] 3.4 Verify: update and review the screenshot references; desktop hot-reload screenshots at 500, 760 and 1280dp.

## 4. iOS

- [ ] 4.1 Mosaic, strips, tables, chips and the side pane in `ActivityPage.swift` and `ActivityPageCards.swift`; a native All data page.
- [ ] 4.2 Verify: the simulator build, the snapshot suites on iOS 26.0, iPhone and iPad captures in light and dark.

## 5. Finish

- [ ] 5.1 `./gradlew check`; sync the delta into `openspec/specs/feed/activity-page-layout/spec.md` when archiving.
