# Spec Delta

## ADDED Requirements

### Requirement: The activity page is native on iOS
On iOS an activity's page SHALL be drawn with the system's own list and bar, in the shared page's words, its loading and failures as `ui/content-loading` says, with the sections the shared page has: header, route and photos, hero, likes, Together with, training load, metric cards with their charts, zones and laps, segment efforts and lifts, stats and comments.

#### Scenario: A ride with charts
- **WHEN** an athlete on iOS opens a ride with a route, heart rate, power and elevation streams and a comment
- **THEN** the page shows the ride's map, its hero figures, a native chart in each of those cards, and the comment, drawn natively

#### Scenario: Failed load
- **WHEN** the activity fails to load on iOS
- **THEN** the page says why with Retry, as other native pages do

### Requirement: The native activity page lays out by width
On iOS, in a regular-width window, the native page SHALL put the map, header, hero, likes, Together with, avg/max table, training load, stats and comments in a side pane beside the metric cards, segment efforts and lifts, each scrolling on its own; in a compact window it SHALL show one list in the shared page's compact order.

#### Scenario: iPad
- **WHEN** an activity with cards is opened on an iPad at full screen
- **THEN** its map and stats show in a pane beside its cards

#### Scenario: iPhone
- **WHEN** the same activity is opened on an iPhone
- **THEN** it shows as one list, the cards after the hero, likes and training load

### Requirement: The native activity page offers what the shared page offers
On iOS the owner's page SHALL offer in its bar the shared owner's menu, asking before Split and Delete as the shared page does, and someone else's page SHALL offer Report through the native report sheet. Export SHALL hand the built file to the system's share sheet. Whatever the page opens SHALL be the same screen the shared page opens.

#### Scenario: Deleting a grouped activity
- **WHEN** the owner chooses Delete on an activity with one other recording
- **THEN** an alert asks whether to delete both or only this one, in the shared page's words

#### Scenario: Reporting
- **WHEN** a viewer chooses Report on someone else's activity on iOS
- **THEN** the native report sheet opens for that activity

### Requirement: The native activity page leaves some charts and tables to later
Until the native chart can draw them, the native page SHALL draw each card's lines as plain charts without the elevation behind them, a left/right pair's right line, a dive's markers and ceiling, the power curve or the gears chart, and SHALL show the legs, lengths and sets cards' headlines and rows without their tables; a combined activity's members SHALL open their activities and offer no edit sheet.

#### Scenario: A power card
- **WHEN** a ride with a power curve is opened on iOS
- **THEN** its power card shows its headline, the power chart, its zones and its rows, and no power curve
