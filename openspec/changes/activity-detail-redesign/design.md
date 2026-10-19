# Design

## Context

See proposal.md (Why) for the motivation. The current state that shapes the approach:

- **Activity page.** `ActivityDetailUi` is one `ReadableList` capped at the reading width on every window. Its first item is an overview card: author, title, type, a `StatTiles` grid built from `overviewStats` (core stats, then `fileStats`, `countStats` and `trainingLoadStats`), the owner's category chips, the map and likes. Then comes a "Details" heading with `SectionCard`s, one per `DetailSection` the presenter lists (`detailSections`). Each section is collapsed to its `SectionOverview` (avg/max tiles) until it is toggled (`expanded`, `ToggleSection`, `ToggleAll`). An open section shows `SectionCharts`: line charts, zones, the power curve and gears. Laps and the device are sections too.
- **`StatTiles`** fits as many 96dp tiles as the width allows, with one-line, ellipsized `labelSmall` labels. That is the source of the truncation.
- **Charts** are Vico, drawn through `ui/chart` (`StaticChart`, `TimeSeriesChart`, `ZoneBars`): fitted, with no scroll, zoom or markers.
- **Import.** `ImportActivityUi.PreviewForm` shows `ImportPreview`, a file-only summary (four stats, map, `RideDetails`), then the type picker, the details form, threshold changes, visibility and Save. On saving, `importDraft` builds an `ActivityDraft`, and that draft already carries the file's `ActivityStreamData`, zone snapshots and training load.
- **Adaptive UI.** `AppTheme` and the `ui/adaptive` wrappers vary look by `LocalUiPlatform`. Layout varies by width only in `AppShell` (sidebar vs bars) and a few `BoxWithConstraints`. No screen has width classes.

## Goals / Non-Goals

**Goals:**
- Build the hero row, metric card, effort card, all-stats list and avg/max table once and use them on both the page and the import preview.
- Decide layout by window width, the same way on every platform. Keep platform look inside `ui/adaptive`.
- Never truncate a stat label anywhere on the page.

**Non-Goals:**
- Changing which figures exist, who may see them, or how they are stored. `feed/activity-stats`, `feed/detail-visibility` and `feed/activity-file-data` stand as they are.
- Interactive charts: scrubbing, hover values, or a shared position across charts and the map. Decision 6 keeps room for them.
- Redesigning the dive logbook batch form or the feed card.

## Decisions

### 1. One "activity page" model for both screens

The page renders from an `ActivityPageContent` built in `:app-presenters`: hero figures, metric cards (each with its kind, headline figures, chart series, extra rows, zones and visibility badge), the effort card, the stats groups and the avg/max rows. The detail presenter builds it from the served `ActivityView` and `ActivityCharts`. The import presenter builds it from the `ActivityDraft` it would publish, turning the draft's streams, zones and training load into `ActivityCharts` with the same `ChartsBuilder`. So the preview is exactly what the page will show.

- Figures and groups are decided in the presenter, as plain `Stat`-like values with label keys. The UI only chooses strings and arranges them. This keeps the per-sport rules testable on the JVM.
- *Alternative:* keep two UIs and share only composables. That lets the screens drift, which is the problem today: the preview shows four stats and the page shows eleven.

### 2. The hero row is chosen by sport family

Each sport family has an ordered list of up to six candidate figures. The hero shows the ones the viewer was served, skipping absent ones under `feed/activity-stats`' rules. Starting lists, which the specs will fix:

| Family | Hero |
|---|---|
| Ride (road, gravel, MTB, e-bike) | Distance, Moving time, Elevation gain, Avg speed, Avg power, Calories |
| Run, walk, hike | Distance, Moving time, Avg pace, Elevation gain, Avg heart rate, Calories |
| Pool / open-water swim | Distance, Moving time, Avg pace /100, Avg heart rate, Calories |
| Dive | Max depth, Dive time, Water temp (min), Avg depth |
| Ski / snowboard | Runs, Descent, Moving time, Max speed |
| Strength, yoga, HIIT and other no-rate sports | Time, Avg heart rate, Calories, Sets or reps when recorded |
| Any other sport | The ride list without avg power |

A figure in the hero is not repeated in the all-stats list's lead group. Every figure stays in the list under its own group.

### 3. Metric cards replace collapsible sections

Each `DetailSection` that has data becomes a card with these parts, top to bottom:

```
title ..................................... [visibility badge]
headline figures (avg, max, and one extra such as normalized power)
chart: the series, a dashed average line, elevation behind it
extra rows (label ......... value)
zones / power curve / gears (whatever the section has today)
```

- Cards are always open, so `expanded`, `anyExpanded`, `ToggleSection` and `ToggleAll` are removed. Laps keep their table as a full-width card. Device and sensors become rows in the all-stats list.
- The elevation shown behind charts comes from the elevation series. A chart without one draws no backdrop.
- The effort card holds training load (TSS, IF, aerobic and anaerobic effect, RMSSD). It shows only where `trainingLoadStats` shows today, to viewers who may see the zones.
- *Alternative:* keep collapsing and only fix the overview. Both reference apps show every chart open, and a collapsed card hides the thing that differs most between activities.

### 4. Labels wrap; numbers and units have separate styles

`HeroFigure` and the stats rows draw the value in a large style with the unit in a smaller one (`AnnotatedString` spans), and give labels up to two lines. Stats rows use a label/value row: the label fills the free width and wraps, and the value is right-aligned and never wraps. Avg/max pairs move into card headlines and the avg/max table, so no label needs "Avg"/"Max" crammed in front of it. `StatTiles` is deleted. Other screens using it (`ThresholdHistoryUi` has its own copy) are left alone.

### 5. Layouts by width class

A small `WindowWidth` helper (compact < 600dp, medium < 840dp, expanded otherwise, read from `BoxWithConstraints` at the screen root) chooses one of three arrangements of the same content:

```
compact              medium                    expanded
+---------+          +---------+---------+     +----------+------------------+
| hero    |          | hero    | map     |     | map      | cards, 2 columns |
| map     |          +---------+---------+     | hero     | (laps full width)|
| effort  |          | card    | card    |     | avg/max  |                  |
| cards   |          | laps (full width) |     | effort   |                  |
| stats   |          | stats (2 columns) |     | stats    |                  |
| comments|          | comments          |     | comments |                  |
+---------+          +---------+---------+     +----------+------------------+
```

- The expanded left pane scrolls on its own and keeps the map on screen.
- The avg/max table shows only in medium and expanded windows, where the header has room. On compact windows each card's headline already says the same.
- We use our own width classes instead of adding `material3-adaptive`. Three thresholds don't justify a dependency, and the helper is trivial to swap later.
- *Alternative:* tabs (Overview / Stats / Charts / Laps), Garmin's pattern. Top tabs aren't idiomatic on iOS, and they would need a third structure on desktop. That's more divergence for less on screen.

### 6. Platform look stays in `ui/adaptive`

A new `AppCard` (`expect`/`actual`) gives a card's surface: a Material `Surface` with outline on Android, desktop and web, and an inset grouped section (grouped background, no outline, 10pt radius, grouped insets) on iOS, following `AppCheckbox`'s split. `DetailCard` is replaced by it. Charts and figures are shared. For the later analysis view, cards take their chart `x` range and an optional highlighted position as parameters, so a shared scrub position can be threaded in without reshaping them.

### 7. The import save panel

The import screen shows the page content read-only, plus a save panel with the type picker, title and details, visibility, threshold changes and Save. The duplicate and time-zone notices sit above the hero, since they change what is shown.

- **Expanded:** the panel is a right-hand pane beside the preview.
- **Compact and medium on iOS:** the panel follows the preview, and Save sits in the navigation bar's trailing slot.
- **Compact and medium on Android, desktop and web:** the panel follows the preview, with Save in a bottom action bar pinned above the content.

Changing the type in the panel rebuilds the content, because the hero depends on the sport. Changing a category's visibility does not hide anything in the preview: the athlete sees everything, as they do on their own page, with the badges updated. The dive batch form is unchanged.

## Risks / Trade-offs

- **[Risk] Long pages on phones.** Open cards make an MTB ride several screens long. → The hero and effort card are at the top, and cards keep their charts short (about 140dp). If needed, a compact "jump to" row can follow later without changing the cards.
- **[Risk] Building charts on the import screen costs time for long files.** → Build them off the main thread as the page does (`ChartsBuilder.buildOffMain`), and show the hero and map before the cards finish.
- **[Risk] Elevation behind every chart adds a second series to each Vico chart.** → Draw it as a filled layer with the low-contrast `chartColors` tone, and leave it out when the series is absent. Measured (see Measurements): dropped below 600dp.
- **[Trade-off] Removing collapse removes a way to hide uninteresting sections.** → Accepted for now. Card order puts the most-used metrics first.
- **[Trade-off] An iOS-specific card surface means one more `expect`/`actual`.** → It follows the existing `AppCheckbox` pattern, and it is the main thing that makes the page feel native on iOS.

## Measurements

**Elevation backdrop draw time** (tasks 3.4). Each chart's draw was timed with a temporary `drawWithContent` wrapper around `StaticChart`, on the MTB sample (2,700 one-second samples, thinned to 300 points per chart; the triathlon is longer but has no elevation, so no backdrop), with the JIT warmed by redrawing on window resizes.

| Target | With backdrop (median / p90 / max) | Without (median / p90 / max) |
|---|---|---|
| Desktop (JVM, M-series Mac) | 3.9 / 6.3 / 8.4 ms (n=35) | 2.7 / 3.5 / 7.6 ms (n=35) |
| Android release build (emulator, signed with the debug key) | 15.2 / 24.6 / 30.0 ms (n=56; 26 over 16 ms) | 8.5 / 20.1 / 31.1 ms (n=66; 10 over 16 ms) |

The backdrop costs about 1.2 ms a chart on desktop, nowhere near 16 ms, but on Android it nearly doubled a chart's draw time and pushed half the draws past a frame. **Decision:** the page and the import preview draw no elevation backdrop below 600dp (each screen passes `elevationBehind` from its `WindowWidth` down to `CardCharts`), on every platform alike, since layout follows width. Medium and expanded windows keep it. The emulator overstates a phone's cost, and some draws pass 16 ms even without the backdrop there, so it's worth measuring on a real device before bringing it back to phones.

## Migration Plan

This is a UI-only change, with no data, record or API migration. Activities of every age render through the same content model. Older ones simply have fewer figures and cards.

## Open Questions

- Whether the expanded layout's cards go two columns or one wide column on 840–1100dp windows. This is a visual tuning point to settle while building.
