# Spec Delta

## ADDED Requirements

### Requirement: The type picker is native on iOS
On iOS the manual entry form SHALL show the chosen type as a row with its icon that opens a sheet of every type: a search field, the quick types while nothing is searched, and the categories with each sport collapsible, every match shown while searching, and "No sports match" when nothing does. Choosing a type SHALL close the sheet. Under the row, the chosen sport's generic form and its sub-sports SHALL be offered as choices when it has sub-sports.

#### Scenario: Choosing from the sheet
- **WHEN** alice opens the type sheet, searches "gravel" and taps Gravel Ride
- **THEN** the sheet closes, the row shows Gravel Ride, and Ride and its sub-sports are offered under it with Gravel Ride chosen

#### Scenario: A sport with no sub-sports
- **WHEN** the chosen type is Yoga
- **THEN** no sub-sport choices show
