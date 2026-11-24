## MODIFIED Requirements

### Requirement: The Segment page shows its parts in one order
The Segment page SHALL show, top to bottom: the map, the heading, the sport and direction choices, the figure row, the elevation profile, the Conditions card (`segments/trail-reports`), the boards row, Trail details, the names, the viewer's efforts, and a footer with the creator, the data credit and the status. A part with nothing to show SHALL be left out without a placeholder. The page's bar SHALL keep its title and Star.

#### Scenario: Active ride segment
- **WHEN** a viewer opens an active ride segment with a profile, current conditions, a Mayor and trail details
- **THEN** the page shows the map, then the heading, the choices, the figures, the profile, the Conditions card, the Leaderboard and Mayor cards, Trail details, the names and the efforts, in that order, with the creator, credit and status last

#### Scenario: Segment with nothing optional
- **WHEN** a viewer opens a proposed segment with no elevation, no profile, no reports, no trail details and no segment group
- **THEN** the page shows the map, the heading, the length in the figure row, the names, the efforts and the footer, with no empty cards

### Requirement: The native Segment page keeps the same order
On iOS the native Segment page SHALL show the same parts in the same order and words as the shared page, with the system's own bar, list and controls.

#### Scenario: Active segment on iOS
- **WHEN** a viewer opens an active ride segment with current conditions and a Mayor on iOS
- **THEN** the native page shows the map, heading, choices, figures, profile, Conditions, Leaderboard and Mayor cards, Trail details, names and efforts, in the shared page's order
