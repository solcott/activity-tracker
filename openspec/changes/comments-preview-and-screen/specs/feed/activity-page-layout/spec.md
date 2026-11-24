# Spec Delta

## MODIFIED Requirements

### Requirement: Layout by window width
The page SHALL arrange the same content by its window's width, on every platform alike. Below 600dp: one column of hero, map, likes and comments block, training load card, metric cards and stats list. From 600dp to under 840dp: hero and map side by side, then likes and the comments block, cards two to a row, laps full width. From 840dp: a side pane with the map, hero, likes, comments block and avg/max table beside the cards, the map staying in view as the cards scroll.

#### Scenario: Phone
- **WHEN** the page is shown on a 390dp-wide iPhone or a 412dp-wide Android phone
- **THEN** it is one column, the hero first

#### Scenario: Narrow desktop window
- **WHEN** a desktop or web window is resized to 500dp wide
- **THEN** the page shows the same one-column layout as a phone

#### Scenario: Wide desktop window
- **WHEN** the page is shown in a 1280dp-wide desktop or web window
- **THEN** the map, hero and avg/max table sit in a side pane, the cards beside them, and the map stays in view while the cards scroll

#### Scenario: Comments before the cards
- **WHEN** a ride with eight metric cards and 14 comments is shown on a phone
- **THEN** the comments block shows right after the likes, before the training load card and the metric cards

### Requirement: The activity page is native on iOS
On iOS an activity's page SHALL be drawn with the system's own list and bar, in the shared page's words, its loading and failures as `ui/content-loading` says, with the sections the shared page has: header, route and photos, hero, likes, comments block, Together with, training load, metric cards with their charts, zones and laps, segment efforts and lifts, and stats.

#### Scenario: A ride with charts
- **WHEN** an athlete on iOS opens a ride with a route, heart rate, power and elevation streams and a comment
- **THEN** the page shows the ride's map, its hero figures, the comment in the comments block, and a native chart in each of those cards, drawn natively

#### Scenario: Failed load
- **WHEN** the activity fails to load on iOS
- **THEN** the page says why with Retry, as other native pages do

### Requirement: The native activity page lays out by width
On iOS, in a regular-width window, the native page SHALL put the map, header, hero, likes, comments block, Together with, avg/max table, training load and stats in a side pane beside the metric cards, segment efforts and lifts, each scrolling on its own; in a compact window it SHALL show one list in the shared page's compact order.

#### Scenario: iPad
- **WHEN** an activity with cards is opened on an iPad at full screen
- **THEN** its map, comments block and stats show in a pane beside its cards

#### Scenario: iPhone
- **WHEN** the same activity is opened on an iPhone
- **THEN** it shows as one list, the cards after the hero, likes, comments block and training load
