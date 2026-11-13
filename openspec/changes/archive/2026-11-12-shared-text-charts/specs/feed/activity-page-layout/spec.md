## ADDED Requirements

### Requirement: Every UI draws a card's charts from the shared charts
The shared activity page and import preview SHALL draw each metric card's charts from the charts shared code builds for the card, the ones the native iOS page draws: each line with its title and header detail where the card's own don't say it, the power curve and the gears, each with its axis, labels, readouts, description, average, backdrop, other lines and marks.

#### Scenario: Several tanks
- **WHEN** a dive with two tanks is opened on Android
- **THEN** its tank pressure card draws both tanks on one chart headed by each tank's maximum, with no second "Tank pressure" title, as on iOS

#### Scenario: A dive's marks
- **WHEN** a dive that switched to EAN50 at 25:00 is opened on desktop
- **THEN** its depth chart marks 25:00 with "EAN50", as on iOS
