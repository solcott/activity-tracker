# Proposal

## Why

The activity page shows its summary as one grid of equal 96dp tiles. At our reading width that is seven tiles to a row with about 65dp per label, so labels are cut off ("Avg caden…"), nothing stands out, and avg/max pairs repeat across tiles. Below it, each metric is a collapsed section that shows its chart only after a tap. The page also keeps one centred column on every window, which leaves desktop and web mostly empty. The import screen is a form with a four-stat preview mixed in, so the athlete sees much less of the activity before saving than after. A better shape: a few headline numbers, one consistent card per metric with its chart open, and a complete stats list for the rest. This change adopts that shape on Android, iOS, desktop and web.

## What Changes

- **Hero row.** The page opens with 4–6 headline numbers chosen by sport: large values with smaller units and labels that wrap rather than truncate. They replace the tile grid in the header.
- **One card per metric, open by default.** Each metric with data (power, heart rate, speed, cadence, elevation, temperature and the rest of today's sections) is a card. It has the same parts every time: a title with the category's visibility badge for the owner, an avg/max headline (plus what matters for that metric, like normalized power), the chart with average line and elevation behind it, a few extra rows, then its zones or power curve. The expand/collapse sections and their "Expand all" control go away.
- **Effort card.** Training load (TSS, IF, aerobic and anaerobic effect, RMSSD) gets its own card near the top, for viewers who may see the activity's zones.
- **All stats.** A grouped label/value list (Timing, Speed, Heart rate, Power, Cadence, Elevation, Work & energy, Temperature, Device…) holds every summary figure, so nothing is lost when the hero and cards show fewer.
- **Avg/max table on wide windows.** Next to the map, wide layouts show a compact table with rows for each metric and columns for Avg and Max.
- **Layout by window width, not platform.** Compact windows (any phone, or a narrow desktop or web window) get one column. Medium windows get two columns of cards. Expanded windows get a pane with the map, hero and avg/max table next to the cards. Platform differences stay in `ui/adaptive` wrappers: Material cards on Android, desktop and web, and inset grouped sections on iOS.
- **Privacy on the card.** The owner's row of category visibility chips moves into each card's header as a badge, beside the data it locks. Categories without a card (start time, map) keep a badge where their data shows.
- **Import as a preview of the page.** The import screen shows the same hero, cards and all-stats list as the page will, read-only. The choices it saves (type, title, description, photos, visibility) go in a save panel: beside the preview on wide windows, and below it with Save in the top bar on iOS or a bottom action bar on Android. A logbook of dives keeps its batch form.
- **Not in this change:** a synchronized "analysis" chart stack with a shared scrub position across charts and map, chart scrubbing and hover values, previewing the page as another audience, and data we don't collect (weather, gear, segments, goals). The design leaves room for the analysis view as a follow-up.

## Capabilities

### New Capabilities
- `feed/activity-page-layout`: How an activity's page and its import preview present its data: the hero row by sport, the per-metric card and its parts, the effort card, the all-stats list, the avg/max table, the width-based layouts, where the owner sees each category's visibility, and the import preview's save panel.

### Modified Capabilities

None. `feed/activity-stats` (which figures each sport shows), `feed/detail-visibility` (hidden categories left out, the owner sees narrower ones marked) and `feed/activity-file-data` (new fields shown among the stats, a chart per channel, the lap table) keep their requirements. This change only decides where those figures appear.

## Impact

- **composeApp:** `detail/ActivityDetailUi`, `DetailSectionsUi`, `SectionOverviewUi` (`StatTiles` goes), `ActivityChartsUi`, `LapTableUi`; `record/ImportActivityUi`, `RideDetails`; new shared pieces for the hero row, metric card, stats list and avg/max table, used by both screens. A new `ui/adaptive` card or section wrapper with an iOS actual. A width-class helper for the three layouts.
- **app-screens / app-presenters:** `ActivityDetailState` loses `expanded` / `anyExpanded` and the `ToggleSection` / `ToggleAll` events. The hero figures by sport and the all-stats groups are worked out in the presenter, or in shared code both screens use. `ImportActivityState` gains what the preview needs to render cards (its charts and zones) where it doesn't have them already.
- **Tests:** presenter tests for the section toggles are removed. New tests cover the hero figures by sport and the stats groups.
- **No** lexicon, AppView, data-layer or migration changes.
