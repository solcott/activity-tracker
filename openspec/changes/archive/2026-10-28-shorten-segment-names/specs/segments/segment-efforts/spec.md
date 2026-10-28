# Spec Delta

## MODIFIED Requirements

### Requirement: Nested efforts drop their parent's trail name
A nested effort SHALL show its segment's name without the trail name it shares with the effort it's nested under, when its name is that trail name followed by ": " or " - ". The trail name of the effort above SHALL be its name up to the first ": ", " (", " › ", " → ", " via " or " from ". Every other effort SHALL show its full name, and the segment screen SHALL always show the full name.

#### Scenario: Stretch under its trail
- **WHEN** "Rattler: Upper Rattler Connector → Ghost Falls" is nested under "Rattler (eastbound)"
- **THEN** it is shown as "Upper Rattler Connector → Ghost Falls"

#### Scenario: Section under a parent trail
- **WHEN** "Rush - Exit Stage Left" is nested under "Rush › Exit Stage Left → Canyon Hollow"
- **THEN** it is shown as "Exit Stage Left"

#### Scenario: Section under a span from a section
- **WHEN** "Rush - 2112" is nested under "Rush from Mystic Rhythms → Canyon Hollow"
- **THEN** it is shown as "2112"

#### Scenario: Section under a span to a junction
- **WHEN** "Rush - Dreamline" is nested under "Rush → Bonneville Shoreline"
- **THEN** it is shown as "Dreamline"

#### Scenario: Name published before shortening
- **WHEN** "Rush Trail - Exit Stage Left Section" is nested under "Rush Trail via Exit Stage Left Section to Canyon Hollow Trail"
- **THEN** it is shown as "Exit Stage Left Section"

#### Scenario: Different trail
- **WHEN** "Ghost Falls: Ghost Connector → Corner Canyon Road" is nested under "Rattler (eastbound)"
- **THEN** it is shown with its full name
