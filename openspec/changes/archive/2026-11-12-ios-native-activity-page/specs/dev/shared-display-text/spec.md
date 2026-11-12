# Spec Delta

## ADDED Requirements

### Requirement: A figure reads the same in both UIs
What a figure on the activity page says SHALL be chosen once in shared code and resolved by each UI: its label in full and its value with its unit in the viewer's units, a stats group's name, an avg/max row's name, a card's title, and a stream's title and unit.

#### Scenario: Imperial distance
- **WHEN** a viewer in imperial units opens a ride of 12.6 km
- **THEN** both UIs read its Distance figure as 7.83 mi

#### Scenario: A dive's gases
- **WHEN** a dive switched from air to EAN50
- **THEN** both UIs read its gas figure as "Air → EAN50"

### Requirement: A card's lines, zones and laps read the same in both UIs
What a metric card's charts, time in zones and laps table say SHALL be chosen once in shared code: each line's title where the card's doesn't name it, its average and maximum line, its description, each zone's label, name, range and time, and each lap's cells and spoken row.

#### Scenario: Power zones
- **WHEN** a ride spent 12 minutes in power zone 2, 150–200 W
- **THEN** both UIs read that zone's range as 150–200 W and its time as 12:00

### Requirement: The activity page reads the same in both UIs
What the activity page says beyond its figures SHALL be chosen once in shared code: its title, the like and likers buttons, Together with and its buttons, the owner's and others' menus, the delete and split confirmations, the export sheet, the notices after a zone, segment or dive site is added, the dive site line, and the combine suggestion's words.

#### Scenario: Liked
- **WHEN** a viewer has liked an activity with 3 likes
- **THEN** both UIs read its like button as liked, with 3
