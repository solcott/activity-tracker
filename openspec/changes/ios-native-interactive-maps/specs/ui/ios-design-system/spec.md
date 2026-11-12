# Spec Delta

## ADDED Requirements

### Requirement: A native map of many segments
On iOS a native page SHALL be able to show many segments on the system's map, panned and zoomed north-up: runs in the accent colour on a
white casing, lifts thin, dashed and muted, and the selected run highlighted over them. Previews and snapshots SHALL draw the lines as a
stand-in on the page's colour, so they fetch nothing and look the same on every run.

#### Scenario: A lift and a selected run
- **WHEN** the browse map shows two runs, one selected, and a lift
- **THEN** the lift is dashed and muted under the runs, and the selected run is drawn over the other

### Requirement: A native route map can fill its page
A native route map SHALL be able to take all the space its page gives it rather than a fixed height, as the enlarged map does.

#### Scenario: Enlarged map
- **WHEN** the enlarged segment map opens on an iPad
- **THEN** the map fills the page under the navigation bar and frames the line in that size
