# Design

## Context

- `ActivityPage.build` (`app/common/presenter`) makes the page's content for the detail screen and the import preview: the hero by `SportFamily`, one `MetricCard` per `DetailSection` in section order, the stats groups and the avg/max table.
- The Compose page (`feature/detail/ui/ActivityDetailUi.kt`) and the iOS page (`iosApp/iosApp/Screens/ActivityPage.swift`) draw that content in three widths. Map and photos come from `MediaRow` (Compose) and `RouteMapView` with `MediaStrip` (iOS).
- Sketches agreed with the user: `.impeccable/mocks/decision/mosaic.html`, `mosaic-density.html` and `all-data.html`.

## Decisions

- **Card order lives in the presenter.** `ActivityPage.build` orders `cards` by a per-family priority list; unlisted sections keep `DetailSection` order after the listed ones. The first card is the lead. Both UIs read the order, so they never disagree, and the import preview gets the same order.

  | Family | Priority |
  |---|---|
  | Ride | Power, HeartRate, Speed, Cadence, Elevation, CyclingDynamics, Gears, EBike |
  | OnFoot | Speed (Pace), HeartRate, Elevation, Cadence, Power, RunningDynamics |
  | Swim | Speed (Pace), HeartRate, Cadence, Lengths |
  | Dive | Depth, TankPressure, Temperature, Oxygen, HeartRate |
  | Snow | Laps (runs), Speed, Elevation, HeartRate |
  | NoRate | Sets, HeartRate |
  | Other | HeartRate, Speed, Power, Elevation, Cadence |

- **Which cards are open is presenter state.** `ActivityDetailPage.openCards` starts as the lead and changes on `ToggleCard(section)`, so iOS and Compose behave alike and it survives a configuration change. Tables (`Laps`, `Legs`, `Lengths`, `Sets`) are never strips: they always show, five rows at a time.
- **All data is a screen, and a pane on wide windows.** `ActivityDataScreen(uri, section)` has its own presenter, so system back and iOS's stack work on phones. On an Expanded window, `OpenAllData(section, inPane = true)` instead sets `ActivityDetailPage.allData`, and the side pane draws the same content (`ActivityData`, built by one shared function from the page).
- **Mosaic and chips are shared rules, drawn natively.** `MediaMosaic.of(hasRoute, photos)` says which tiles show and the "+N"; `ActivityPageTexts.jumps(page)` lists the chips. The UIs only lay them out.

## Risks / Trade-offs

- A strip costs one tap to see its chart. The lead covers the common case, and the chips take a tap anyway.
- The avg/max table leaves the wide page for All data; each strip's headline carries the average and maximum instead.
